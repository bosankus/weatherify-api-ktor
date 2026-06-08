package com.syncling.services

import com.syncling.repository.EmbeddingRow
import com.syncling.repository.TranslationEmbeddingRepository
import org.slf4j.LoggerFactory

/**
 * Surfaces approved translations whose source is *semantically* close to a batch of new sources.
 *
 * Cosine similarity over Gemini text embeddings; we load all rows for (project, language) into
 * memory and pick the top-K matches per batch (deduped, capped at [maxExamples]). For projects
 * with thousands of strings this is fast enough — but the right next step is to swap the
 * repo for an Atlas Vector Search index when row counts justify it.
 */
class FuzzyMemoryService(
    private val embeddingService: EmbeddingService,
    private val embeddingRepository: TranslationEmbeddingRepository
) {
    private val log = LoggerFactory.getLogger(FuzzyMemoryService::class.java)

    data class FuzzyExample(val source: String, val translation: String, val similarity: Float)

    /**
     * For a batch of source strings in one (project, locale), returns up to [maxExamples]
     * distinct nearest-neighbor (source, translation) pairs. Pairs whose top similarity to
     * any batch source is below [threshold] are filtered out.
     */
    suspend fun lookupExamples(
        projectId: String,
        targetLanguage: String,
        sourceTexts: List<String>,
        maxExamples: Int = 5,
        threshold: Float = 0.78f
    ): List<FuzzyExample> {
        if (sourceTexts.isEmpty()) return emptyList()
        val candidates = runCatching { embeddingRepository.listForLanguage(projectId, targetLanguage) }
            .getOrElse {
                log.warn("Fuzzy memory load failed: {}", it.message)
                return emptyList()
            }
        if (candidates.isEmpty()) return emptyList()

        val queryVectors = embeddingService.embedBatch(sourceTexts)
        val normalizedCandidates = candidates.map { CandidateWithNorm(it, norm(it.embedding)) }

        val scored = mutableMapOf<String, FuzzyExample>()
        for ((idx, qv) in queryVectors.withIndex()) {
            if (qv == null) continue
            val qNorm = norm(qv)
            if (qNorm == 0f) continue
            val source = sourceTexts[idx]
            for (cand in normalizedCandidates) {
                if (cand.row.sourceText == source) continue // skip self-match if previously stored
                if (cand.norm == 0f) continue
                val sim = cosine(qv, qNorm, cand.row.embedding, cand.norm)
                if (sim < threshold) continue
                val existing = scored[cand.row.sourceText]
                if (existing == null || sim > existing.similarity) {
                    val translation = cand.row.translations[targetLanguage] ?: continue
                    scored[cand.row.sourceText] = FuzzyExample(cand.row.sourceText, translation, sim)
                }
            }
        }
        return scored.values.sortedByDescending { it.similarity }.take(maxExamples)
    }

    /**
     * Embeds the given (source, translation) pairs and contributes them to the project's TM.
     * Failures are swallowed — TM enrichment is a best-effort side channel.
     */
    suspend fun contribute(
        projectId: String,
        targetLanguage: String,
        pairs: List<Pair<String, String>>
    ) {
        if (pairs.isEmpty()) return
        val sources = pairs.map { it.first }.distinct()
        val vectors = embeddingService.embedBatch(sources)
        val vectorBySource = sources.zip(vectors).toMap()
        for ((source, translation) in pairs) {
            val vec = vectorBySource[source] ?: continue
            runCatching {
                embeddingRepository.upsert(projectId, source, vec, targetLanguage, translation)
            }.onFailure { log.warn("Embedding upsert failed for source='{}': {}", source.take(40), it.message) }
        }
    }

    private data class CandidateWithNorm(val row: EmbeddingRow, val norm: Float)

    private fun norm(v: FloatArray): Float {
        var s = 0.0
        for (x in v) s += x * x
        return kotlin.math.sqrt(s).toFloat()
    }

    private fun cosine(a: FloatArray, normA: Float, b: FloatArray, normB: Float): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return (dot / (normA * normB)).toFloat()
    }
}
