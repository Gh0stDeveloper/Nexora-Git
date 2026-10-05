package com.nexora.git.core.editor

import kotlin.math.max
import kotlin.math.min

class EditorTextOperations {

    fun findAll(
        text: String,
        query: String,
        matchCase: Boolean,
    ): List<EditorSearchMatch> {
        if (query.isEmpty()) return emptyList()

        val source = if (matchCase) text else text.lowercase()
        val needle = if (matchCase) query else query.lowercase()
        val matches = mutableListOf<EditorSearchMatch>()

        var from = 0
        while (from <= source.length - needle.length) {
            val index = source.indexOf(
                string = needle,
                startIndex = from,
            )
            if (index < 0) break

            matches += EditorSearchMatch(
                start = index,
                endExclusive = index + needle.length,
            )
            from = max(index + needle.length, index + 1)
        }

        return matches
    }

    fun replace(
        text: String,
        match: EditorSearchMatch,
        replacement: String,
    ): EditorRevision {
        val safeStart = match.start.coerceIn(0, text.length)
        val safeEnd = match.endExclusive.coerceIn(
            safeStart,
            text.length,
        )

        val updated = buildString(
            text.length - (safeEnd - safeStart) + replacement.length,
        ) {
            append(text, 0, safeStart)
            append(replacement)
            append(text, safeEnd, text.length)
        }

        val cursor = safeStart + replacement.length
        return EditorRevision(
            text = updated,
            selectionStart = cursor,
            selectionEnd = cursor,
        )
    }

    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        matchCase: Boolean,
    ): EditorRevision {
        val matches = findAll(text, query, matchCase)
        if (matches.isEmpty()) {
            return EditorRevision(text, 0, 0)
        }

        val updated = StringBuilder(text)
        matches.asReversed().forEach { match ->
            updated.replace(
                match.start,
                match.endExclusive,
                replacement,
            )
        }

        return EditorRevision(
            text = updated.toString(),
            selectionStart = updated.length,
            selectionEnd = updated.length,
        )
    }

    fun insertIndent(
        revision: EditorRevision,
        style: EditorIndentStyle,
    ): EditorRevision {
        val text = revision.text
        val start = min(
            revision.selectionStart,
            revision.selectionEnd,
        ).coerceIn(0, text.length)
        val end = max(
            revision.selectionStart,
            revision.selectionEnd,
        ).coerceIn(start, text.length)

        if (start == end) {
            val updated = text.substring(0, start) +
                style.unit +
                text.substring(start)
            val cursor = start + style.unit.length
            return EditorRevision(
                text = updated,
                selectionStart = cursor,
                selectionEnd = cursor,
            )
        }

        val firstLineStart = text.lastIndexOf(
            '\n',
            startIndex = start - 1,
        ).let { index ->
            if (index < 0) 0 else index + 1
        }

        val selected = text.substring(
            firstLineStart,
            end,
        )

        val lineCount = selected.count { it == '\n' } + 1
        val indented = selected.lineSequence()
            .joinToString("\n") { line ->
                style.unit + line
            }

        val updated = text.substring(0, firstLineStart) +
            indented +
            text.substring(end)

        return EditorRevision(
            text = updated,
            selectionStart = start + style.unit.length,
            selectionEnd = end + style.unit.length * lineCount,
        )
    }

    fun localDiff(
        original: String,
        current: String,
        relativePath: String,
    ): EditorDiffPreview {
        if (original == current) {
            return EditorDiffPreview(
                patch = "No changes.",
                additions = 0,
                deletions = 0,
            )
        }

        val before = original.split('\n')
        val after = current.split('\n')

        var prefix = 0
        while (
            prefix < before.size &&
            prefix < after.size &&
            before[prefix] == after[prefix]
        ) {
            prefix += 1
        }

        var suffix = 0
        while (
            suffix < before.size - prefix &&
            suffix < after.size - prefix &&
            before[before.lastIndex - suffix] ==
                after[after.lastIndex - suffix]
        ) {
            suffix += 1
        }

        val removed = before.subList(
            prefix,
            before.size - suffix,
        )
        val added = after.subList(
            prefix,
            after.size - suffix,
        )

        val contextStart = (prefix - 3).coerceAtLeast(0)
        val beforeContext = before.subList(
            contextStart,
            prefix,
        )
        val afterSuffixStart = after.size - suffix
        val afterContextEnd = (
            afterSuffixStart + 3
        ).coerceAtMost(after.size)
        val afterContext = after.subList(
            afterSuffixStart,
            afterContextEnd,
        )

        val patch = buildString {
            append("--- a/")
            append(relativePath)
            append('\n')
            append("+++ b/")
            append(relativePath)
            append('\n')
            append("@@ -")
            append(prefix + 1)
            append(',')
            append(removed.size)
            append(" +")
            append(prefix + 1)
            append(',')
            append(added.size)
            append(" @@\n")

            beforeContext.forEach {
                append(' ')
                append(it)
                append('\n')
            }
            removed.forEach {
                append('-')
                append(it)
                append('\n')
            }
            added.forEach {
                append('+')
                append(it)
                append('\n')
            }
            afterContext.forEach {
                append(' ')
                append(it)
                append('\n')
            }
        }.trimEnd()

        return EditorDiffPreview(
            patch = patch,
            additions = added.size,
            deletions = removed.size,
        )
    }
}
