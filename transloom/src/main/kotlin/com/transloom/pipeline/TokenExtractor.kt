package com.transloom.pipeline

data class ExtractionResult(
    val cleanText: String,
    val placeholders: Map<String, String>, // token -> original (e.g. __PH_1__ -> %1$s)
    val entities: Map<String, String>,     // token -> original (e.g. __ENT_1__ -> &#8211;)
    val isTruncated: Boolean
)

object TokenExtractor {
    // Printf/Android/iOS format specifiers: %s %d %f %@ %1$s %2$d %c %i %% etc.
    private val placeholderRegex = Regex("""(%(\d+\$)?[sdfeaAtnxXobBhHcig%@])""")

    // HTML entities: &amp; &#169; &amp;#8211;
    private val entityRegex = Regex("""(&amp;#[0-9]+;|&[a-zA-Z0-9#]+;)""")

    // Android inline HTML tags used inside string values (CDATA or escaped).
    // Covers: <b> </b> <i> <u> <small> <big> <sup> <sub> <em> <strong> <font color="..."> <xliff:g ...> etc.
    // Each tag is extracted as a separate placeholder token so it survives translation intact.
    private val htmlTagRegex = Regex(
        """</?(?:b|i|u|em|strong|small|big|sup|sub|font|xliff:g)(?:\s[^>]*)?>""",
        RegexOption.IGNORE_CASE
    )

    fun extract(source: String): ExtractionResult {
        val isTruncated = source.trimEnd().let {
            it.endsWith("&") || it.matches(Regex(".*%\\d+\\$?$")) || it.endsWith("&#") || it.endsWith("&amp")
        }

        var currentText = source
        val placeholders = mutableMapOf<String, String>()
        val entities = mutableMapOf<String, String>()

        // 1. Extract HTML entities first so the tag regex doesn't see entity-encoded angle brackets
        var entCounter = 1
        currentText = entityRegex.replace(currentText) { match ->
            val token = "__ENT_${entCounter++}__"
            entities[token] = match.value
            token
        }

        // 2. Extract inline HTML tags (Android CDATA / rich text strings)
        var phCounter = 1
        currentText = htmlTagRegex.replace(currentText) { match ->
            val token = "__PH_${phCounter++}__"
            placeholders[token] = match.value
            token
        }

        // 3. Extract printf/format specifiers
        currentText = placeholderRegex.replace(currentText) { match ->
            val token = "__PH_${phCounter++}__"
            placeholders[token] = match.value
            token
        }

        return ExtractionResult(
            cleanText = currentText,
            placeholders = placeholders,
            entities = entities,
            isTruncated = isTruncated
        )
    }
}
