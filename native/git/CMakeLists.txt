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

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_SKIP_INSTALL_RULES TRUE CACHE BOOL "" FORCE)
set(CMAKE_C_STANDARD 11 CACHE STRING "" FORCE)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)

FetchContent_Declare(
    mbedtls
    GIT_REPOSITORY https://github.com/Mbed-TLS/mbedtls.git
    GIT_TAG ${NEXORA_MBEDTLS_REF}
    GIT_SHALLOW TRUE
)
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

FetchContent_Declare(
    libgit2
    GIT_REPOSITORY https://github.com/libgit2/libgit2.git
    GIT_TAG ${NEXORA_LIBGIT2_REF}
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(libgit2)


# Tree-sitter is embedded directly in the native engine so syntax parsing is
# available offline and uses the same Android ABIs as libgit2.
FetchContent_Declare(
    tree_sitter
    GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter.git
    GIT_TAG ${NEXORA_TREE_SITTER_REF}
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(tree_sitter)
if(NOT tree_sitter_POPULATED)
    FetchContent_Populate(tree_sitter)
endif()

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

function(nexora_fetch_grammar target repository ref)
    FetchContent_Declare(
        ${target}_source
        GIT_REPOSITORY ${repository}
        GIT_TAG ${ref}
        GIT_SHALLOW TRUE
    )
    FetchContent_GetProperties(${target}_source)
    if(NOT ${target}_source_POPULATED)
        FetchContent_Populate(${target}_source)
    endif()
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
    https://github.com/fwcd/tree-sitter-kotlin.git
    ${NEXORA_TREE_SITTER_KOTLIN_REF}
)
nexora_fetch_grammar(
    nexora_ts_java
    https://github.com/tree-sitter/tree-sitter-java.git
    ${NEXORA_TREE_SITTER_JAVA_REF}
)
nexora_fetch_grammar(
    nexora_ts_json
    https://github.com/tree-sitter/tree-sitter-json.git
    ${NEXORA_TREE_SITTER_JSON_REF}
)
nexora_fetch_grammar(
    nexora_ts_python
    https://github.com/tree-sitter/tree-sitter-python.git
    ${NEXORA_TREE_SITTER_PYTHON_REF}
)
nexora_fetch_grammar(
    nexora_ts_javascript
    https://github.com/tree-sitter/tree-sitter-javascript.git
    ${NEXORA_TREE_SITTER_JAVASCRIPT_REF}
)

FetchContent_Declare(
    nexora_ts_typescript_source
    GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-typescript.git
    GIT_TAG ${NEXORA_TREE_SITTER_TYPESCRIPT_REF}
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(nexora_ts_typescript_source)
if(NOT nexora_ts_typescript_source_POPULATED)
    FetchContent_Populate(nexora_ts_typescript_source)
endif()
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

if(ANDROID)
    find_library(log-lib log)

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
        nexoragit_native_tests
        tests/native_smoke.cpp
    )

    target_link_libraries(
        nexoragit_native_tests
        PRIVATE
        nexoragit_core
    )

    add_test(
        NAME nexoragit_native_tests
        COMMAND nexoragit_native_tests
    )
endif()
