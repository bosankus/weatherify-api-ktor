package com.syncling.pipeline

import org.slf4j.LoggerFactory

object PlaceholderGuard {
    private val log = LoggerFactory.getLogger(PlaceholderGuard::class.java)

    private val phPattern = Regex("__PH_\\d+__")
    private val entPattern = Regex("__ENT_\\d+__")

    sealed class GuardResult {
        data class Success(val restoredText: String, val flags: List<String>) : GuardResult()
        data class Rejected(val reason: String) : GuardResult()
    }

    fun validateAndRestore(
        sourceEnglish: String,
        translatedText: String,
        extractionResult: ExtractionResult
    ): GuardResult {
        val flags = mutableListOf<String>()

        if (sourceEnglish.trim() == translatedText.trim() && sourceEnglish.isNotEmpty()) {
            flags.add("Output equals source (possible skip/untranslated)")
        }

        val sourceLen = sourceEnglish.length.toDouble()
        val transLen = translatedText.length.toDouble()
        if (sourceLen > 0) {
            val ratio = transLen / sourceLen
            if (ratio < 0.3 || ratio > 4.0) {
                flags.add("Length ratio anomaly (ratio: ${"%.2f".format(ratio)})")
            }
        }

        // Ensure every placeholder token is present in the translation
        extractionResult.placeholders.keys.forEach { token ->
            if (!translatedText.contains(token)) {
                return GuardResult.Rejected("Missing placeholder token in output: $token")
            }
        }

        // Ensure every entity token is present in the translation
        extractionResult.entities.keys.forEach { token ->
            if (!translatedText.contains(token)) {
                return GuardResult.Rejected("Missing entity token in output: $token")
            }
        }

        // Placeholder count must match exactly (catches duplicated tokens)
        val srcPhCount = extractionResult.placeholders.size
        val outPhCount = phPattern.findAll(translatedText).count()
        if (srcPhCount != outPhCount) {
            return GuardResult.Rejected("Placeholder count mismatch. Expected: $srcPhCount, Found: $outPhCount")
        }

        // Entity count must match exactly
        val srcEntCount = extractionResult.entities.size
        val outEntCount = entPattern.findAll(translatedText).count()
        if (srcEntCount != outEntCount) {
            return GuardResult.Rejected("Entity count mismatch. Expected: $srcEntCount, Found: $outEntCount")
        }

        if (flags.isNotEmpty()) {
            log.warn("Translation flags: {}", flags)
        }

        var restored = translatedText
        extractionResult.placeholders.forEach { (token, original) ->
            restored = restored.replace(token, original)
        }
        extractionResult.entities.forEach { (token, original) ->
            restored = restored.replace(token, original)
        }

        return GuardResult.Success(restored, flags)
    }
}
