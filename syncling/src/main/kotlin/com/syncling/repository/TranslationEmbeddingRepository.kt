package com.syncling.repository

/**
 * Vector index for fuzzy translation memory. One row per (projectId, sourceText) holds
 * the embedding plus an inline map of confirmed translations per target language.
 */
data class EmbeddingRow(
    val sourceText: String,
    val embedding: FloatArray,
    /** language code → confirmed translation */
    val translations: Map<String, String>
) {
    override fun equals(other: Any?): Boolean =
        other is EmbeddingRow && sourceText == other.sourceText && translations == other.translations
    override fun hashCode(): Int = sourceText.hashCode() * 31 + translations.hashCode()
}

interface TranslationEmbeddingRepository {
    /**
     * Upserts the embedding row for [sourceText] in [projectId], setting the translation for
     * [targetLanguage]. The embedding is stored on insert only; subsequent calls update the
     * per-language translation without recomputing the vector.
     */
    suspend fun upsert(
        projectId: String,
        sourceText: String,
        embedding: FloatArray,
        targetLanguage: String,
        translatedText: String
    )

    /** Returns all rows for a project that carry a translation for [targetLanguage]. */
    suspend fun listForLanguage(projectId: String, targetLanguage: String): List<EmbeddingRow>

    /** Returns every row for a project regardless of translated languages — used for source-side similarity. */
    suspend fun listForProject(projectId: String): List<EmbeddingRow>
}
