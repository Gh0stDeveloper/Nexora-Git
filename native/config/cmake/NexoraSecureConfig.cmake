include_guard(GLOBAL)

set(
    NEXORA_LOCAL_CONFIG_FILE
    "${CMAKE_CURRENT_SOURCE_DIR}/../../nexora.local.properties"
    CACHE FILEPATH
    "Optional gitignored local public runtime configuration"
)

function(nexora_read_local_config NAME OUTPUT)
    set(value "")
    if(EXISTS "${NEXORA_LOCAL_CONFIG_FILE}")
        file(
            STRINGS
            "${NEXORA_LOCAL_CONFIG_FILE}"
            matching_lines
            REGEX "^${NAME}="
            LIMIT_COUNT 1
        )
        if(matching_lines)
            list(GET matching_lines 0 line)
            string(REGEX REPLACE "^[^=]*=" "" value "${line}")
        endif()
    endif()
    set(${OUTPUT} "${value}" PARENT_SCOPE)
endfunction()

function(nexora_config_value ENV_NAME OUTPUT)
    set(value "")
    if(ENV_NAME STREQUAL "NEXORA_GITHUB_CLIENT_ID")
        set(value "$ENV{NEXORA_GITHUB_CLIENT_ID}")
    elseif(ENV_NAME STREQUAL "NEXORA_AUTH_BROKER_BASE_URL")
        set(value "$ENV{NEXORA_AUTH_BROKER_BASE_URL}")
    elseif(ENV_NAME STREQUAL "NEXORA_GITHUB_CALLBACK_URL")
        set(value "$ENV{NEXORA_GITHUB_CALLBACK_URL}")
    else()
        message(FATAL_ERROR "Unsupported Nexora runtime configuration key: ${ENV_NAME}")
    endif()

    if(value STREQUAL "")
        nexora_read_local_config("${ENV_NAME}" value)
    endif()

    string(REPLACE "\r" "" value "${value}")
    string(REPLACE "\n" "" value "${value}")
    set(${OUTPUT} "${value}" PARENT_SCOPE)
endfunction()

function(nexora_hex_byte HEX_VALUE INDEX OUTPUT)
    math(EXPR position "${INDEX} * 2")
    string(SUBSTRING "${HEX_VALUE}" ${position} 2 byte_hex)
    math(EXPR byte_value "0x${byte_hex}")
    set(${OUTPUT} "${byte_value}" PARENT_SCOPE)
endfunction()

function(nexora_hex_to_cpp_array HEX_VALUE OUTPUT)
    string(LENGTH "${HEX_VALUE}" hex_length)
    math(EXPR byte_count "${hex_length} / 2")
    set(result "")
    if(byte_count GREATER 0)
        math(EXPR last "${byte_count} - 1")
        foreach(index RANGE 0 ${last})
            nexora_hex_byte("${HEX_VALUE}" ${index} byte_value)
            if(result STREQUAL "")
                set(result "${byte_value}")
            else()
                string(APPEND result ", ${byte_value}")
            endif()
        endforeach()
    endif()
    set(${OUTPUT} "${result}" PARENT_SCOPE)
endfunction()

function(nexora_encode_value VALUE OFFSET KEY_A_HEX KEY_B_HEX OUTPUT_ARRAY OUTPUT_SIZE)
    string(HEX "${VALUE}" value_hex)
    string(LENGTH "${value_hex}" hex_length)
    math(EXPR byte_count "${hex_length} / 2")

    if(byte_count EQUAL 0)
        set(${OUTPUT_ARRAY} "0" PARENT_SCOPE)
        set(${OUTPUT_SIZE} "0" PARENT_SCOPE)
        return()
    endif()

    set(result "")
    math(EXPR last "${byte_count} - 1")
    foreach(index RANGE 0 ${last})
        nexora_hex_byte("${value_hex}" ${index} plain_byte)
        math(EXPR key_a_index "(${index} + ${OFFSET}) % 32")
        math(EXPR key_b_index "((${index} * 7) + (${OFFSET} * 3)) % 32")
        nexora_hex_byte("${KEY_A_HEX}" ${key_a_index} key_a_byte)
        nexora_hex_byte("${KEY_B_HEX}" ${key_b_index} key_b_byte)
        math(EXPR encoded_byte "${plain_byte} ^ ${key_a_byte} ^ ${key_b_byte}")

        if(result STREQUAL "")
            set(result "${encoded_byte}")
        else()
            string(APPEND result ", ${encoded_byte}")
        endif()
    endforeach()

    set(${OUTPUT_ARRAY} "${result}" PARENT_SCOPE)
    set(${OUTPUT_SIZE} "${byte_count}" PARENT_SCOPE)
endfunction()

function(nexora_generate_secure_runtime_config OUTPUT_DIR)
    if(EXISTS "${NEXORA_LOCAL_CONFIG_FILE}")
        set_property(
            DIRECTORY
            APPEND
            PROPERTY CMAKE_CONFIGURE_DEPENDS "${NEXORA_LOCAL_CONFIG_FILE}"
        )
    endif()

    nexora_config_value("NEXORA_GITHUB_CLIENT_ID" github_client_id)
    nexora_config_value("NEXORA_AUTH_BROKER_BASE_URL" broker_base_url)
    nexora_config_value("NEXORA_GITHUB_CALLBACK_URL" github_callback_url)
    set(app_callback_uri "")

    set(configured_count 0)
    foreach(value IN ITEMS "${github_client_id}" "${broker_base_url}" "${github_callback_url}")
        if(NOT value STREQUAL "")
            math(EXPR configured_count "${configured_count} + 1")
        endif()
    endforeach()

    if(configured_count GREATER 0 AND configured_count LESS 3)
        message(FATAL_ERROR "Nexora public auth configuration must provide Client ID, broker URL and callback URL together.")
    endif()

    if(configured_count EQUAL 3)
        string(LENGTH "${github_client_id}" client_id_length)
        if(client_id_length LESS 3 OR client_id_length GREATER 128 OR
           NOT github_client_id MATCHES "^[A-Za-z0-9._-]+$")
            message(FATAL_ERROR "NEXORA_GITHUB_CLIENT_ID has an invalid format.")
        endif()

        if(NOT broker_base_url MATCHES "^https://[^/]+$")
            message(FATAL_ERROR "NEXORA_AUTH_BROKER_BASE_URL must be HTTPS without a path or trailing slash.")
        endif()

        if(NOT github_callback_url STREQUAL "${broker_base_url}/oauth/callback")
            message(FATAL_ERROR "NEXORA_GITHUB_CALLBACK_URL must equal broker base URL plus /oauth/callback.")
        endif()

        set(app_callback_uri "${broker_base_url}/oauth/android/callback")
    endif()

    string(RANDOM LENGTH 64 ALPHABET 0123456789abcdef key_a_hex)
    string(RANDOM LENGTH 64 ALPHABET 0123456789abcdef key_b_hex)
    nexora_hex_to_cpp_array("${key_a_hex}" key_a_array)
    nexora_hex_to_cpp_array("${key_b_hex}" key_b_array)

    nexora_encode_value("${github_client_id}" 3 "${key_a_hex}" "${key_b_hex}" github_array github_size)
    nexora_encode_value("${broker_base_url}" 11 "${key_a_hex}" "${key_b_hex}" broker_array broker_size)
    nexora_encode_value("${github_callback_url}" 19 "${key_a_hex}" "${key_b_hex}" callback_array callback_size)
    nexora_encode_value("${app_callback_uri}" 23 "${key_a_hex}" "${key_b_hex}" app_callback_array app_callback_size)

    file(MAKE_DIRECTORY "${OUTPUT_DIR}")
    set(generated_header "${OUTPUT_DIR}/nexora_secure_config.generated.hpp")

    file(WRITE "${generated_header}" "#pragma once\n")
    file(APPEND "${generated_header}" "#include <cstddef>\n#include <cstdint>\n#include <string>\n\n")
    file(APPEND "${generated_header}" "namespace nexora::secure_config {\n")
    file(APPEND "${generated_header}" "static const volatile std::uint8_t kKeyA[32] = { ${key_a_array} };\n")
    file(APPEND "${generated_header}" "static const volatile std::uint8_t kKeyB[32] = { ${key_b_array} };\n")
    file(APPEND "${generated_header}" "static const volatile std::uint8_t kGithubClientId[] = { ${github_array} };\n")
    file(APPEND "${generated_header}" "static const volatile std::uint8_t kBrokerBaseUrl[] = { ${broker_array} };\n")
    file(APPEND "${generated_header}" "static const volatile std::uint8_t kGithubCallbackUrl[] = { ${callback_array} };\n")
    file(APPEND "${generated_header}" "static const volatile std::uint8_t kAppCallbackUri[] = { ${app_callback_array} };\n\n")
    file(APPEND "${generated_header}" "struct EncodedValue { const volatile std::uint8_t* data; std::size_t size; std::size_t offset; };\n")
    file(APPEND "${generated_header}" "inline std::string Decode(const EncodedValue& value) {\n")
    file(APPEND "${generated_header}" "    std::string output(value.size, '\\0');\n")
    file(APPEND "${generated_header}" "    for (std::size_t i = 0; i < value.size; ++i) {\n")
    file(APPEND "${generated_header}" "        const auto a = kKeyA[(i + value.offset) % 32];\n")
    file(APPEND "${generated_header}" "        const auto b = kKeyB[((i * 7) + (value.offset * 3)) % 32];\n")
    file(APPEND "${generated_header}" "        output[i] = static_cast<char>(value.data[i] ^ a ^ b);\n")
    file(APPEND "${generated_header}" "    }\n")
    file(APPEND "${generated_header}" "    return output;\n")
    file(APPEND "${generated_header}" "}\n\n")
    file(APPEND "${generated_header}" "inline std::string GithubClientId() { return Decode({kGithubClientId, ${github_size}, 3}); }\n")
    file(APPEND "${generated_header}" "inline std::string BrokerBaseUrl() { return Decode({kBrokerBaseUrl, ${broker_size}, 11}); }\n")
    file(APPEND "${generated_header}" "inline std::string GithubCallbackUrl() { return Decode({kGithubCallbackUrl, ${callback_size}, 19}); }\n")
    file(APPEND "${generated_header}" "inline std::string AppCallbackUri() { return Decode({kAppCallbackUri, ${app_callback_size}, 23}); }\n")
    file(APPEND "${generated_header}" "}  // namespace nexora::secure_config\n")

    set(NEXORA_SECURE_CONFIG_GENERATED_DIR "${OUTPUT_DIR}" PARENT_SCOPE)
endfunction()
