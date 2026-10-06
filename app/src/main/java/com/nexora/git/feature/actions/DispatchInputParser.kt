package com.nexora.git.feature.actions

sealed interface DispatchInputParseResult {
    data class Success(
        val inputs: Map<String, String>,
    ) : DispatchInputParseResult

    data class Failure(
        val line: Int,
        val message: String,
    ) : DispatchInputParseResult
}

fun parseDispatchInputs(
    text: String,
): DispatchInputParseResult {
    val result = linkedMapOf<String, String>()

    text.lineSequence().forEachIndexed { index, raw ->
        val line = raw.trim()
        if (line.isBlank()) return@forEachIndexed

        val separator = line.indexOf('=')
        if (separator <= 0) {
            return DispatchInputParseResult.Failure(
                line = index + 1,
                message = "Use key=value.",
            )
        }

        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()

        if (!INPUT_NAME.matches(key)) {
            return DispatchInputParseResult.Failure(
                line = index + 1,
                message = "Invalid input name.",
            )
        }

        if (result.containsKey(key)) {
            return DispatchInputParseResult.Failure(
                line = index + 1,
                message = "Duplicate input name.",
            )
        }

        result[key] = value
    }

    return DispatchInputParseResult.Success(result)
}

private val INPUT_NAME =
    Regex("^[A-Za-z_][A-Za-z0-9_-]{0,99}$")
