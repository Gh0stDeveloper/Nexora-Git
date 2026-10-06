cmake_minimum_required(VERSION 3.22.1)

project(nexoragit_git_engine LANGUAGES C CXX)

include(FetchContent)

set(NEXORA_LIBGIT2_REF "49e408b3208bc3093757a1c2db938d3590f3f412" CACHE STRING "Pinned libgit2 commit")
set(NEXORA_MBEDTLS_REF "068ff080b369adfac81509f9b57b2afabaf82dc5" CACHE STRING "Pinned Mbed TLS commit")
option(NEXORA_BUILD_NATIVE_TESTS "Build host native Git tests" OFF)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_SKIP_INSTALL_RULES TRUE CACHE BOOL "" FORCE)
set(CMAKE_C_STANDARD 99 CACHE STRING "" FORCE)
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

add_library(
    nexoragit_core
    STATIC
    src/git_core.cpp
    src/advanced_git.cpp
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
)

if(ANDROID)
    find_library(log-lib log)

    add_library(
        nexoragit_native
        SHARED
        src/git_jni.cpp
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
