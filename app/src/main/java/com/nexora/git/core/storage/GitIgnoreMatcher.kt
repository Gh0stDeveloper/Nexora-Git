package com.nexora.git.core.storage

import javax.inject.Inject
import javax.inject.Singleton

data class GitIgnoreRule(
    val basePath: String,
    val pattern: String,
    val negated: Boolean,
    val directoryOnly: Boolean,
    val anchored: Boolean,
)

@Singleton
class GitIgnoreMatcher @Inject constructor() {

    fun parse(
        content: String,
        basePath: String = "",
    ): List<GitIgnoreRule> =
        content.lineSequence()
            .mapNotNull { line ->
                parseLine(line, normalize(basePath))
            }
            .toList()

    fun isIgnored(
        relativePath: String,
        isDirectory: Boolean,
        rules: List<GitIgnoreRule>,
    ): Boolean {
        val normalizedPath = normalize(relativePath)
        var ignored = false

        rules.forEach { rule ->
            if (matches(rule, normalizedPath, isDirectory)) {
                ignored = !rule.negated
            }
        }

        return ignored
    }

    private fun parseLine(
        rawLine: String,
        basePath: String,
    ): GitIgnoreRule? {
        var line = rawLine.trim()
        if (line.isBlank()) return null

        if (line.startsWith("\#")) {
            line = line.removePrefix("\")
        } else if (line.startsWith("#")) {
            return null
        }

        val negated = line.startsWith("!")
        if (negated) {
            line = line.removePrefix("!")
        }

        if (line.isBlank()) return null

        val directoryOnly = line.endsWith("/")
        if (directoryOnly) {
            line = line.dropLast(1)
        }

        val anchored = line.startsWith("/")
        if (anchored) {
            line = line.removePrefix("/")
        }

        if (line.isBlank()) return null

        return GitIgnoreRule(
            basePath = basePath,
            pattern = line,
            negated = negated,
            directoryOnly = directoryOnly,
            anchored = anchored,
        )
    }

    private fun matches(
        rule: GitIgnoreRule,
        relativePath: String,
        isDirectory: Boolean,
    ): Boolean {
        val scopedPath = relativeToBase(
            relativePath,
            rule.basePath,
        ) ?: return false

        val pattern = normalize(rule.pattern)
        val regex = globRegex(
            pattern = pattern,
            anchored = rule.anchored || pattern.contains('/'),
        )

        if (regex.matches(scopedPath)) return true

        if (rule.directoryOnly) {
            val segments = scopedPath.split('/')
            return segments.indices.any { index ->
                regex.matches(
                    segments.take(index + 1).joinToString("/"),
                )
            }
        }

        return false
    }

    private fun relativeToBase(
        path: String,
        basePath: String,
    ): String? {
        if (basePath.isBlank()) return path
        if (path == basePath) return ""
        val prefix = "$basePath/"
        return path.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
    }

    private fun globRegex(
        pattern: String,
        anchored: Boolean,
    ): Regex {
        val out = StringBuilder()
        if (anchored) {
            out.append("^")
        } else {
            out.append("^(?:.*/)?")
        }

        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            when (char) {
                '*' -> {
                    val doubleStar =
                        index + 1 < pattern.length &&
                            pattern[index + 1] == '*'
                    if (doubleStar) {
                        out.append(".*")
                        index += 1
                    } else {
                        out.append("[^/]*")
                    }
                }

                '?' -> out.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%' ->
                    out.append("\").append(char)
                '[' -> out.append("\[")
                ']' -> out.append("\]")
                '{' -> out.append("\{")
                '}' -> out.append("\}")
                '\' -> out.append("\\")
                else -> out.append(char)
            }
            index += 1
        }

        out.append("(?:/.*)?$")
        return Regex(out.toString())
    }

    private fun normalize(path: String): String =
        path.replace('\', '/')
            .trim('/')
}
