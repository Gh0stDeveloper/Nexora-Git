package com.nexora.git.core.editor

import javax.inject.Inject
import org.json.JSONObject

class SyntaxJsonParser @Inject constructor() {

    fun parse(payload: String): EditorSyntaxSnapshot {
        val root = JSONObject(payload)

        return EditorSyntaxSnapshot(
            engine = root.getString("engine"),
            language = root.getString("language"),
            rootType = root.getString("rootType"),
            hasErrors = root.optBoolean("hasErrors", false),
            truncated = root.optBoolean("truncated", false),
            spans = parseSpans(root),
            symbols = parseSymbols(root),
            diagnostics = parseDiagnostics(root),
        )
    }

    private fun parseSpans(
        root: JSONObject,
    ): List<EditorSyntaxSpan> {
        val values = root.optJSONArray("spans")
            ?: return emptyList()

        return buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                val kind = runCatching {
                    EditorSyntaxSpanKind.valueOf(
                        item.getString("kind").uppercase(),
                    )
                }.getOrNull() ?: continue

                val start = item.getInt("start")
                val end = item.getInt("end")
                if (start < 0 || end <= start) continue

                add(
                    EditorSyntaxSpan(
                        start = start,
                        endExclusive = end,
                        kind = kind,
                    ),
                )
            }
        }
    }

    private fun parseSymbols(
        root: JSONObject,
    ): List<EditorSymbol> {
        val values = root.optJSONArray("symbols")
            ?: return emptyList()

        return buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                add(
                    EditorSymbol(
                        name = item.getString("name"),
                        kind = item.getString("kind"),
                        start = item.getInt("start"),
                        endExclusive = item.getInt("end"),
                        line = item.getInt("line"),
                    ),
                )
            }
        }
    }

    private fun parseDiagnostics(
        root: JSONObject,
    ): List<EditorDiagnostic> {
        val values = root.optJSONArray("diagnostics")
            ?: return emptyList()

        return buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                add(
                    EditorDiagnostic(
                        severity = item.getString("severity"),
                        message = item.getString("message"),
                        start = item.getInt("start"),
                        endExclusive = item.getInt("end"),
                        line = item.getInt("line"),
                        column = item.getInt("column"),
                    ),
                )
            }
        }
    }
}
