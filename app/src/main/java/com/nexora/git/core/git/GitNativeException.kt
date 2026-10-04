package com.nexora.git.core.git

class GitNativeException(
    val nativeCode: Int,
    val nativeClass: Int,
    message: String,
) : RuntimeException(message)
