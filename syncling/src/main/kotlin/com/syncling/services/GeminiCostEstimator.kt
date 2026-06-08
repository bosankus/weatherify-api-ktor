package com.syncling.services

/**
 * Rough USD cost estimate for a Gemini call. Rates are hardcoded against the model that
 * `TranslationService` uses today (`gemini-3.1-flash-lite`) and the public embedding model.
 * Update these when Google moves prices — keep them visible in code rather than fanning out
 * to env config; cost reports stay reproducible.
 *
 * Rates: dollars per 1M tokens. Source: Google AI pricing page (as of 2026-06).
 */
object GeminiCostEstimator {
    private const val FLASH_LITE_INPUT_PER_M = 0.10
    private const val FLASH_LITE_OUTPUT_PER_M = 0.40
    private const val EMBEDDING_INPUT_PER_M = 0.025

    fun estimateTranslation(inputTokens: Long, outputTokens: Long): Double {
        val input = inputTokens / 1_000_000.0 * FLASH_LITE_INPUT_PER_M
        val output = outputTokens / 1_000_000.0 * FLASH_LITE_OUTPUT_PER_M
        return input + output
    }

    fun estimateEmbedding(inputTokens: Long): Double =
        inputTokens / 1_000_000.0 * EMBEDDING_INPUT_PER_M
}
