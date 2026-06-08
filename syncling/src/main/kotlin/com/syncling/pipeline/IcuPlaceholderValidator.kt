package com.syncling.pipeline

/**
 * Source-of-truth validator that compares a raw English source string against a translated string
 * and rejects translations whose placeholder shape diverges.
 *
 * Layered checks:
 *  1. printf/format specifiers (`%s`, `%1${'$'}d`, …) — multiset must match exactly.
 *  2. HTML entities (`&amp;`, `&#169;`) — multiset must match exactly.
 *  3. Inline HTML tags (`<b>`, `</b>`, `<xliff:g …>`) — multiset must match exactly.
 *  4. ICU MessageFormat: top-level named placeholders (`{count}`, `{count, plural, …}`,
 *     `{name, select, …}`) — multiset of *names* must match. Plural/select bodies are NOT
 *     descended into (their inner forms are language-specific and may legitimately differ).
 *  5. Brace balance: every `{` in the translation must close.
 *
 * Used in two places:
 *  - the pipeline, *after* PlaceholderGuard has restored tokens, as a belt-and-braces ICU
 *    check the tokenizer doesn't cover.
 *  - every reviewer-edit path (approve+editedText, hotfix) — so a human can't unknowingly
 *    ship a broken `%s` or `{count}` to the CDN/PR.
 */
object IcuPlaceholderValidator {

    sealed class Result {
        data object Ok : Result()
        data class Reject(val reasons: List<String>) : Result() {
            val message: String get() = reasons.joinToString("; ")
        }
    }

    private val printfRegex = Regex("""%(\d+\$)?[sdfeaAtnxXobBhHcig%@]""")
    private val entityRegex = Regex("""&amp;#[0-9]+;|&[a-zA-Z0-9#]+;""")
    private val htmlTagRegex = Regex(
        """</?(?:b|i|u|em|strong|small|big|sup|sub|font|xliff:g)(?:\s[^>]*)?>""",
        RegexOption.IGNORE_CASE
    )

    fun validate(source: String, translated: String): Result {
        val reasons = mutableListOf<String>()

        diffMultiset(printfRegex.findAll(source).map { it.value }.toList(),
            printfRegex.findAll(translated).map { it.value }.toList())?.let {
            reasons += "Printf specifier mismatch: $it"
        }
        diffMultiset(entityRegex.findAll(source).map { it.value }.toList(),
            entityRegex.findAll(translated).map { it.value }.toList())?.let {
            reasons += "HTML entity mismatch: $it"
        }
        diffMultiset(htmlTagRegex.findAll(source).map { it.value.lowercase() }.toList(),
            htmlTagRegex.findAll(translated).map { it.value.lowercase() }.toList())?.let {
            reasons += "HTML tag mismatch: $it"
        }

        val srcIcu = topLevelIcuNames(source)
        val tgtIcu = topLevelIcuNames(translated)
        diffMultiset(srcIcu, tgtIcu)?.let {
            reasons += "ICU placeholder mismatch: $it"
        }

        if (!bracesBalanced(translated)) {
            reasons += "Unbalanced ICU braces in translation"
        }

        return if (reasons.isEmpty()) Result.Ok else Result.Reject(reasons)
    }

    private fun diffMultiset(src: List<String>, tgt: List<String>): String? {
        if (src.size == tgt.size && src.sorted() == tgt.sorted()) return null
        val missing = (src.groupingBy { it }.eachCount().toMutableMap())
        tgt.forEach { missing.merge(it, -1) { a, b -> a + b } }
        val added = missing.filter { it.value < 0 }.keys
        val dropped = missing.filter { it.value > 0 }.keys
        return buildString {
            if (dropped.isNotEmpty()) append("missing ${dropped.joinToString(",")}")
            if (added.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append("unexpected ${added.joinToString(",")}")
            }
        }
    }

    /**
     * Walks the string and extracts the *name* of every top-level ICU placeholder.
     * Skips ICU-quoted regions (`'{'`, `''`) and never descends into nested braces.
     * Identifiers must start with a letter/underscore.
     */
    private fun topLevelIcuNames(text: String): List<String> {
        val names = mutableListOf<String>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            // ICU quote escape: ' opens a quoted region until next ', '' is a literal '
            if (c == '\'' && i + 1 < n) {
                val next = text[i + 1]
                if (next == '\'') { i += 2; continue }
                if (next == '{' || next == '}' || next == '#') {
                    val end = text.indexOf('\'', i + 2)
                    i = if (end == -1) n else end + 1
                    continue
                }
            }
            if (c == '{') {
                val nameStart = i + 1
                var j = nameStart
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                val name = text.substring(nameStart, j)
                if (name.isNotEmpty() && (text[nameStart].isLetter() || text[nameStart] == '_')) {
                    names += name
                }
                // skip to matching close brace
                var depth = 1
                i = j
                while (i < n && depth > 0) {
                    when (text[i]) {
                        '\'' -> {
                            if (i + 1 < n && text[i + 1] == '\'') { i += 2; continue }
                            if (i + 1 < n && (text[i + 1] == '{' || text[i + 1] == '}' || text[i + 1] == '#')) {
                                val end = text.indexOf('\'', i + 2)
                                i = if (end == -1) n else end + 1
                                continue
                            }
                            i++
                        }
                        '{' -> { depth++; i++ }
                        '}' -> { depth--; i++ }
                        else -> i++
                    }
                }
            } else {
                i++
            }
        }
        return names
    }

    private fun bracesBalanced(text: String): Boolean {
        var depth = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\'' && i + 1 < n) {
                val next = text[i + 1]
                if (next == '\'') { i += 2; continue }
                if (next == '{' || next == '}' || next == '#') {
                    val end = text.indexOf('\'', i + 2)
                    i = if (end == -1) n else end + 1
                    continue
                }
            }
            when (c) {
                '{' -> depth++
                '}' -> { if (--depth < 0) return false }
            }
            i++
        }
        return depth == 0
    }
}
