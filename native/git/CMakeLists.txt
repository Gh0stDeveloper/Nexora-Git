cmake_minimum_required(VERSION 3.22.1)

project(nexoragit_git_engine LANGUAGES C CXX)

include(FetchContent)

set(NEXORA_LIBGIT2_REF "49e408b3208bc3093757a1c2db938d3590f3f412" CACHE STRING "Pinned libgit2 commit")
set(NEXORA_MBEDTLS_REF "068ff080b369adfac81509f9b57b2afabaf82dc5" CACHE STRING "Pinned Mbed TLS commit")
set(NEXORA_TREE_SITTER_REF "752c612a1359f00e4c113593837a0c1e880214e3" CACHE STRING "Pinned Tree-sitter commit")
set(NEXORA_TREE_SITTER_KOTLIN_REF "1852ea17b7f60fb3f9d84e0b1555d56b46b39fb1" CACHE STRING "Pinned Kotlin grammar commit")
set(NEXORA_TREE_SITTER_JAVA_REF "e10607b45ff745f5f876bfa3e94fbcc6b44bdc11" CACHE STRING "Pinned Java grammar commit")
set(NEXORA_TREE_SITTER_JSON_REF "254c42a6476413b776221e03982ac8ae159eeb72" CACHE STRING "Pinned JSON grammar commit")
set(NEXORA_TREE_SITTER_PYTHON_REF "26855eabccb19c6abf499fbc5b8dc7cc9ab8bc64" CACHE STRING "Pinned Python grammar commit")
set(NEXORA_TREE_SITTER_JAVASCRIPT_REF "58404d8cf191d69f2674a8fd507bd5776f46cb11" CACHE STRING "Pinned JavaScript grammar commit")
set(NEXORA_TREE_SITTER_TYPESCRIPT_REF "75b3874edb2dc714fb1fd77a32013d0f8699989f" CACHE STRING "Pinned TypeScript grammar commit")
option(NEXORA_BUILD_NATIVE_TESTS "Build host native Git tests" OFF)
option(NEXORA_BUILD_NATIVE_FUZZERS "Build project-owned host fuzz targets" OFF)
option(NEXORA_ENABLE_PROJECT_SANITIZERS "Instrument project-owned native code with ASan/UBSan" OFF)
option(NEXORA_REQUIRE_VENDORED_NATIVE_SOURCES "Fail instead of downloading native dependencies" OFF)
set(
    NEXORA_NATIVE_VENDOR_ROOT
    "${CMAKE_CURRENT_SOURCE_DIR}/vendor"
    CACHE PATH
    "Root containing pinned native dependency source trees"
)

function(nexora_vendor_source output dependency)
    set(candidate "${NEXORA_NATIVE_VENDOR_ROOT}/${dependency}")
    if(EXISTS "${candidate}")
        set(${output} "${candidate}" PARENT_SCOPE)
    elseif(NEXORA_REQUIRE_VENDORED_NATIVE_SOURCES)
        message(FATAL_ERROR "Required vendored native source is missing: ${candidate}")
    else()
        set(${output} "" PARENT_SCOPE)
    endif()
endfunction()

set(NEXORA_CONFIG_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../config")
include("${NEXORA_CONFIG_SOURCE_DIR}/cmake/NexoraSecureConfig.cmake")
nexora_generate_secure_runtime_config(
    "${CMAKE_CURRENT_BINARY_DIR}/generated/nexora-secure-config"
)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_STANDARD 11 CACHE STRING "" FORCE)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
set(DISABLE_PACKAGE_CONFIG_AND_INSTALL ON CACHE BOOL "" FORCE)

nexora_vendor_source(NEXORA_MBEDTLS_SOURCE mbedtls)
if(NEXORA_MBEDTLS_SOURCE)
    FetchContent_Declare(
        mbedtls
        SOURCE_DIR "${NEXORA_MBEDTLS_SOURCE}"
    )
else()
    FetchContent_Declare(
        mbedtls
        GIT_REPOSITORY https://github.com/Mbed-TLS/mbedtls.git
        GIT_TAG ${NEXORA_MBEDTLS_REF}
        GIT_SHALLOW TRUE
    )
endif()
FetchContent_MakeAvailable(mbedtls)

set(NEXORA_PLACEHOLDER_CA "${CMAKE_CURRENT_BINARY_DIR}/placeholder-ca.pem")
file(WRITE "${NEXORA_PLACEHOLDER_CA}" "")
set(CERT_LOCATION "${NEXORA_PLACEHOLDER_CA}" CACHE FILEPATH "" FORCE)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_FUZZERS OFF CACHE BOOL "" FORCE)
set(USE_SSH OFF CACHE STRING "" FORCE)
set(USE_GSSAPI OFF CACHE BOOL "" FORCE)
set(USE_NTLMCLIENT OFF CACHE BOOL "" FORCE)
set(USE_HTTPS "mbedTLS" CACHE STRING "" FORCE)
set(USE_BUNDLED_ZLIB ON CACHE BOOL "" FORCE)
set(REGEX_BACKEND "builtin" CACHE STRING "" FORCE)
set(SONAME OFF CACHE BOOL "" FORCE)
set(NEXORA_LIBGIT2_SKIP_INSTALL ON CACHE BOOL "" FORCE)

nexora_vendor_source(NEXORA_LIBGIT2_SOURCE libgit2)
if(NEXORA_LIBGIT2_SOURCE)
    FetchContent_Declare(
        libgit2
        SOURCE_DIR "${NEXORA_LIBGIT2_SOURCE}"
        PATCH_COMMAND
            bash
            "${CMAKE_CURRENT_SOURCE_DIR}/cmake/apply-libgit2-no-install.sh"
            "${CMAKE_CURRENT_SOURCE_DIR}/cmake/libgit2-no-install.patch"
    )
else()
    FetchContent_Declare(
        libgit2
        GIT_REPOSITORY https://github.com/libgit2/libgit2.git
        GIT_TAG ${NEXORA_LIBGIT2_REF}
        GIT_SHALLOW TRUE
        PATCH_COMMAND
            git apply --whitespace=nowarn
            "${CMAKE_CURRENT_SOURCE_DIR}/cmake/libgit2-no-install.patch"
    )
endif()
FetchContent_MakeAvailable(libgit2)

# llhttp is generated third-party code bundled by libgit2. Its current
# x86_64 source contains two intentionally unused compatibility locals.
# Keep project warnings strict while silencing only that upstream target.
if(TARGET llhttp AND CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(llhttp PRIVATE -Wno-unused-variable)
endif()


# Tree-sitter is embedded directly in the native engine so syntax parsing is
# available offline and uses the same Android ABIs as libgit2.
nexora_vendor_source(NEXORA_TREE_SITTER_SOURCE tree-sitter)
if(NEXORA_TREE_SITTER_SOURCE)
    FetchContent_Declare(
        tree_sitter
        SOURCE_DIR "${NEXORA_TREE_SITTER_SOURCE}"
        SOURCE_SUBDIR "__nexora_source_only__"
    )
else()
    FetchContent_Declare(
        tree_sitter
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter.git
        GIT_TAG ${NEXORA_TREE_SITTER_REF}
        GIT_SHALLOW TRUE
        SOURCE_SUBDIR "__nexora_source_only__"
    )
endif()
FetchContent_MakeAvailable(tree_sitter)

add_library(
    nexora_tree_sitter
    STATIC
    "${tree_sitter_SOURCE_DIR}/lib/src/lib.c"
)
target_include_directories(
    nexora_tree_sitter
    PUBLIC
    "${tree_sitter_SOURCE_DIR}/lib/include"
    PRIVATE
    "${tree_sitter_SOURCE_DIR}/lib/src"
)
target_compile_definitions(
    nexora_tree_sitter
    PRIVATE
    _DEFAULT_SOURCE
)

function(nexora_fetch_grammar target vendor_name repository ref)
    nexora_vendor_source(vendor_source "${vendor_name}")
    if(vendor_source)
        FetchContent_Declare(
            ${target}_source
            SOURCE_DIR "${vendor_source}"
            SOURCE_SUBDIR "__nexora_source_only__"
        )
    else()
        FetchContent_Declare(
            ${target}_source
            GIT_REPOSITORY ${repository}
            GIT_TAG ${ref}
            GIT_SHALLOW TRUE
            SOURCE_SUBDIR "__nexora_source_only__"
        )
    endif()
    FetchContent_MakeAvailable(${target}_source)
    FetchContent_GetProperties(
        ${target}_source
        SOURCE_DIR source_dir
    )

    set(sources "${source_dir}/src/parser.c")
    if(EXISTS "${source_dir}/src/scanner.c")
        list(APPEND sources "${source_dir}/src/scanner.c")
    endif()

    add_library(${target} STATIC ${sources})
    target_include_directories(
        ${target}
        PRIVATE
        "${source_dir}/src"
    )
endfunction()

nexora_fetch_grammar(
    nexora_ts_kotlin
    tree-sitter-kotlin
    https://github.com/fwcd/tree-sitter-kotlin.git
    ${NEXORA_TREE_SITTER_KOTLIN_REF}
)
nexora_fetch_grammar(
    nexora_ts_java
    tree-sitter-java
    https://github.com/tree-sitter/tree-sitter-java.git
    ${NEXORA_TREE_SITTER_JAVA_REF}
)
nexora_fetch_grammar(
    nexora_ts_json
    tree-sitter-json
    https://github.com/tree-sitter/tree-sitter-json.git
    ${NEXORA_TREE_SITTER_JSON_REF}
)
nexora_fetch_grammar(
    nexora_ts_python
    tree-sitter-python
    https://github.com/tree-sitter/tree-sitter-python.git
    ${NEXORA_TREE_SITTER_PYTHON_REF}
)
nexora_fetch_grammar(
    nexora_ts_javascript
    tree-sitter-javascript
    https://github.com/tree-sitter/tree-sitter-javascript.git
    ${NEXORA_TREE_SITTER_JAVASCRIPT_REF}
)

nexora_vendor_source(NEXORA_TS_TYPESCRIPT_SOURCE tree-sitter-typescript)
if(NEXORA_TS_TYPESCRIPT_SOURCE)
    FetchContent_Declare(
        nexora_ts_typescript_source
        SOURCE_DIR "${NEXORA_TS_TYPESCRIPT_SOURCE}"
        SOURCE_SUBDIR "__nexora_source_only__"
    )
else()
    FetchContent_Declare(
        nexora_ts_typescript_source
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-typescript.git
        GIT_TAG ${NEXORA_TREE_SITTER_TYPESCRIPT_REF}
        GIT_SHALLOW TRUE
        SOURCE_SUBDIR "__nexora_source_only__"
    )
endif()
FetchContent_MakeAvailable(nexora_ts_typescript_source)
FetchContent_GetProperties(
    nexora_ts_typescript_source
    SOURCE_DIR NEXORA_TS_TYPESCRIPT_DIR
)

add_library(
    nexora_ts_typescript
    STATIC
    "${NEXORA_TS_TYPESCRIPT_DIR}/typescript/src/parser.c"
    "${NEXORA_TS_TYPESCRIPT_DIR}/typescript/src/scanner.c"
)
target_include_directories(
    nexora_ts_typescript
    PRIVATE
    "${NEXORA_TS_TYPESCRIPT_DIR}/typescript/src"
)
add_library(
    nexora_ts_tsx
    STATIC
    "${NEXORA_TS_TYPESCRIPT_DIR}/tsx/src/parser.c"
    "${NEXORA_TS_TYPESCRIPT_DIR}/tsx/src/scanner.c"
)
target_include_directories(
    nexora_ts_tsx
    PRIVATE
    "${NEXORA_TS_TYPESCRIPT_DIR}/tsx/src"
)


add_library(
    nexoragit_core
    STATIC
    src/git_core.cpp
    src/advanced_git.cpp
    src/lfs_filter.cpp
    src/syntax_engine.cpp
)

target_include_directories(
    nexoragit_core
    PUBLIC
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${libgit2_SOURCE_DIR}/include"
)

target_link_libraries(
    nexoragit_core
    PUBLIC
    libgit2package
    MbedTLS::mbedcrypto
    nexora_tree_sitter
    nexora_ts_kotlin
    nexora_ts_java
    nexora_ts_json
    nexora_ts_python
    nexora_ts_javascript
    nexora_ts_typescript
    nexora_ts_tsx
)


if(NEXORA_ENABLE_PROJECT_SANITIZERS AND NOT ANDROID)
    if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
        message(FATAL_ERROR "NEXORA_ENABLE_PROJECT_SANITIZERS requires Clang or GCC.")
    endif()

    target_compile_options(
        nexoragit_core
        PRIVATE
        -fsanitize=address,undefined
        -fno-omit-frame-pointer
    )
endif()

if(ANDROID)
    find_library(log-lib log)

    add_library(
        nexoragit
        SHARED
        "${NEXORA_CONFIG_SOURCE_DIR}/src/secure_config_jni.cpp"
    )
    target_include_directories(
        nexoragit
        PRIVATE
        "${NEXORA_SECURE_CONFIG_GENERATED_DIR}"
    )
    set_target_properties(
        nexoragit
        PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
    )
    target_compile_options(
        nexoragit
        PRIVATE
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
    )
    target_link_options(
        nexoragit
        PRIVATE
        -Wl,--gc-sections
    )

    add_library(
        nexoragit_native
        SHARED
        src/git_jni.cpp
        src/syntax_jni.cpp
    )

    target_include_directories(
        nexoragit_native
        PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/include"
    )

    target_link_libraries(
        nexoragit_native
        PRIVATE
        nexoragit_core
        ${log-lib}
    )
endif()

if(NEXORA_BUILD_NATIVE_TESTS AND NOT ANDROID)
    enable_testing()

    add_executable(
        nexora_secure_config_tests
        "${NEXORA_CONFIG_SOURCE_DIR}/tests/secure_config_smoke.cpp"
    )
    target_include_directories(
        nexora_secure_config_tests
        PRIVATE
        "${NEXORA_SECURE_CONFIG_GENERATED_DIR}"
    )
    add_test(
        NAME nexora_secure_config_tests
        COMMAND nexora_secure_config_tests
    )

    add_executable(
        nexoragit_native_tests
        tests/native_smoke.cpp
    )

    target_link_libraries(
        nexoragit_native_tests
        PRIVATE
        nexoragit_core
    )

    if(NEXORA_ENABLE_PROJECT_SANITIZERS)
        target_compile_options(
            nexoragit_native_tests
            PRIVATE
            -fsanitize=address,undefined
            -fno-omit-frame-pointer
        )
        target_link_options(
            nexoragit_native_tests
            PRIVATE
            -fsanitize=address,undefined
        )
    endif()

    add_test(
        NAME nexoragit_native_tests
        COMMAND nexoragit_native_tests
    )
endif()


if(NEXORA_BUILD_NATIVE_FUZZERS AND NOT ANDROID)
    if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang")
        message(FATAL_ERROR "NEXORA_BUILD_NATIVE_FUZZERS requires Clang.")
    endif()

    add_executable(
        nexora_secure_config_fuzzer
        "${NEXORA_CONFIG_SOURCE_DIR}/fuzz/secure_config_decode_fuzz.cpp"
    )
    target_include_directories(
        nexora_secure_config_fuzzer
        PRIVATE
        "${NEXORA_SECURE_CONFIG_GENERATED_DIR}"
    )
    target_compile_options(
        nexora_secure_config_fuzzer
        PRIVATE
        -fsanitize=fuzzer,address,undefined
        -fno-omit-frame-pointer
    )
    target_link_options(
        nexora_secure_config_fuzzer
        PRIVATE
        -fsanitize=fuzzer,address,undefined
    )
endif()
