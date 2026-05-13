package com.transloom.pipeline

data class ExtractionResult(
    val cleanText: String,
    val placeholders: Map<String, String>, // token -> original (e.g. __PH_1__ -> %1$s)
    val entities: Map<String, String>,     // token -> original (e.g. __ENT_1__ -> &#8211;)
    val isTruncated: Boolean
)

object TokenExtractor {
    // Matches valid printf/Android/iOS format specifiers: %s %d %f %@ %1$s %2$d %c %i %% etc.
    // %% (literal percent) is also extracted so the LLM cannot accidentally drop or mangle it.
    private val placeholderRegex = Regex("""(%(\d+\$)?[sdfeaAtnxXobBhHcig%@])""")
    
    // Regex for entities: &amp; &#169; &amp;#8211;
    // Matches standard HTML entities, numeric entities, and double encoded numeric entities
    private val entityRegex = Regex("""(&amp;#[0-9]+;|&[a-zA-Z0-9#]+;)""")
    
    fun extract(source: String): ExtractionResult {
        // Truncation check (heuristic for incomplete entities/placeholders at the end)
        val isTruncated = source.trimEnd().let { 
            it.endsWith("&") || it.matches(Regex(".*%\\d+\\$?$")) || it.endsWith("&#") || it.endsWith("&amp")
        }
        
        var currentText = source
        val placeholders = mutableMapOf<String, String>()
        val entities = mutableMapOf<String, String>()
        
        // 1. Extract Entities
        var entCounter = 1
        currentText = entityRegex.replace(currentText) { matchResult ->
            val token = "__ENT_${entCounter++}__"
            entities[token] = matchResult.value
            token
        }
        
        // 2. Extract Placeholders
        var phCounter = 1
        currentText = placeholderRegex.replace(currentText) { matchResult ->
            val token = "__PH_${phCounter++}__"
            placeholders[token] = matchResult.value
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
