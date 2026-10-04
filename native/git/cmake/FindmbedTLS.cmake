if(TARGET mbedtls AND TARGET mbedx509 AND TARGET mbedcrypto)
    set(MBEDTLS_FOUND TRUE)
    set(MBEDTLS_INCLUDE_DIR "${mbedtls_SOURCE_DIR}/include")
    set(MBEDTLS_LIBRARIES mbedtls mbedx509 mbedcrypto)
else()
    set(MBEDTLS_FOUND FALSE)
endif()

mark_as_advanced(
    MBEDTLS_INCLUDE_DIR
    MBEDTLS_LIBRARIES
)
