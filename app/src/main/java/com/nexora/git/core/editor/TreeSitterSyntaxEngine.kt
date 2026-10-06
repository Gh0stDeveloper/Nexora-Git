package com.nexora.git.core.editor

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TreeSitterSyntaxEngine @Inject constructor(
    private val bridge: NativeSyntaxBridge,
    private val parser: SyntaxJsonParser,
) : EditorSyntaxEngine {

    override fun supports(
        fileName: String,
        language: String?,
    ): Boolean = grammarFor(fileName, language) != null

    override suspend fun analyze(
        fileName: String,
        language: String?,
        source: String,
    ): EditorSyntaxSnapshot? = withContext(Dispatchers.Default) {
        val grammar = grammarFor(fileName, language)
            ?: return@withContext null

        parser.parse(
            bridge.nativeAnalyze(
                grammar = grammar,
                source = source,
            ),
        )
    }

    internal fun grammarFor(
        fileName: String,
        language: String?,
    ): String? {
        val lower = fileName.lowercase()
        val extension = lower.substringAfterLast(
            delimiter = '.',
            missingDelimiterValue = "",
        )

        return when {
            extension == "kt" || extension == "kts" -> "Kotlin"
            extension == "java" -> "Java"
            extension == "jsx" -> "JavaScript"
            extension == "js" -> "JavaScript"
            extension == "tsx" -> "TSX"
            extension == "ts" -> "TypeScript"
            extension == "py" -> "Python"
            extension == "json" -> "JSON"
            language == "Kotlin" -> "Kotlin"
            language == "Java" -> "Java"
            language == "JavaScript" -> "JavaScript"
            language == "TypeScript" -> "TypeScript"
            language == "Python" -> "Python"
            language == "JSON" -> "JSON"
            else -> null
        }
    }
}
