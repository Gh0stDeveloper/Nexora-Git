package com.nexora.git.core.editor

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeSyntaxBridge @Inject constructor() {
    external fun nativeVersion(): String

    external fun nativeAnalyze(
        grammar: String,
        source: String,
    ): String

    companion object {
        init {
            System.loadLibrary("nexoragit_native")
        }
    }
}
