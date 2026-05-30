package com.syncling.services

import com.androidplay.core.secrets.getSecretValue
import com.syncling.domain.CulturalAnalysis
import com.syncling.repository.CulturalAnalysisCacheRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest

@Serializable
private data class CulturalAnalysisJson(
    val needsReview: Boolean = false,
    val issues: List<String> = emptyList()
)

/**
 * Post-translation cultural appropriateness check via Gemini. Conservative by design:
 * defaults to "no issues" on any failure to avoid flooding the review queue.
 */
class CulturalSensitivityAnalyzer(private val cache: CulturalAnalysisCacheRepository) {

    private val log = LoggerFactory.getLogger(CulturalSensitivityAnalyzer::class.java)
    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }

    // One permit per batch call, not per string.
    private val semaphore = Semaphore(4)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis  = 45_000
        }
    }

    // Single-string API kept for callers that don't have a batch — delegates to analyzeBatch.
    suspend fun analyze(
        translatedText: String,
        sourceText: String,
        targetLanguage: String,
        targetRegion: String?,
        appCategory: String,
        appTone: String
    ): CulturalAnalysis =
        analyzeBatch(
            mapOf("_single" to (translatedText to sourceText)),
            targetLanguage, targetRegion, appCategory, appTone
        )["_single"] ?: CulturalAnalysis(emptyList(), false)

    /**
     * Check a batch of translated strings for cultural appropriateness in as few Gemini
     * calls as possible. All strings in the batch must share the same target language,
     * region, app category, and tone (natural for a single pipeline target).
     *
     * Cache hits are resolved without any API call. The remainder is split into chunks
     * of [BATCH_SIZE] and sent as one Gemini call each — same system prompt, same
     * analysis criteria, same JSON schema as single-string mode.
     *
     * On any batch-level failure every string in that chunk defaults to clean (no issues)
     * to avoid flooding the review queue. Per-key misses in the response are also defaulted
     * to clean, with a debug log.
     *
     * @param items key → Pair(translatedText, sourceText)
     */
    suspend fun analyzeBatch(
        items: Map<String, Pair<String, String>>,
        targetLanguage: String,
        targetRegion: String?,
        appCategory: String,
        appTone: String
    ): Map<String, CulturalAnalysis> {
        if (items.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, CulturalAnalysis>()
        val toAnalyze = mutableMapOf<String, Pair<String, String>>()

        for ((key, pair) in items) {
            val cached = cache.get(cacheKey(pair.first, targetLanguage, targetRegion, appCategory, appTone))
            if (cached != null) results[key] = cached else toAnalyze[key] = pair
        }

        if (toAnalyze.isEmpty()) return results

        for (chunk in toAnalyze.entries.chunked(BATCH_SIZE)) {
            val batch = chunk.associate { it.key to it.value }
            val batchResults = semaphore.withPermit {
                runCatching {
                    callGeminiBatch(batch, targetLanguage, targetRegion, appCategory, appTone)
                }.getOrElse { e ->
                    log.debug("Batch cultural analysis failed for {} string(s) — defaulting all to clean: {}",
                        batch.size, e.message)
                    batch.mapValues { CulturalAnalysis(emptyList(), false) }
                }
            }
            for ((key, analysis) in batchResults) {
                results[key] = analysis
                cache.put(
                    cacheKey(batch.getValue(key).first, targetLanguage, targetRegion, appCategory, appTone),
                    analysis
                )
            }
        }

        return results
    }

    private suspend fun callGeminiBatch(
        items: Map<String, Pair<String, String>>,
        targetLanguage: String,
        targetRegion: String?,
        appCategory: String,
        appTone: String
    ): Map<String, CulturalAnalysis> {
        val market = if (targetRegion != null) "$targetLanguage ($targetRegion)" else targetLanguage
        val systemPrompt = buildBatchSystemPrompt(appCategory, appTone, market)

        val inputJson = buildJsonObject {
            for ((key, pair) in items) {
                put(key, buildJsonObject {
                    put("source", pair.second)
                    put("translated", pair.first)
                })
            }
        }.toString()

        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(listOf(GeminiPart(inputJson)))),
            generationConfig = GenerationConfig(
                temperature = 0.0,
                thinkingConfig = ThinkingConfig(0),
                responseMimeType = "application/json"
            )
        )

        val response = client.post(
            "$GEMINI_ENDPOINT?key=$geminiApiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            log.debug("Gemini cultural batch HTTP {} — skipping flags", response.status)
            return items.mapValues { CulturalAnalysis(emptyList(), false) }
        }

        val raw = response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: return items.mapValues { CulturalAnalysis(emptyList(), false) }

        return parseBatchResponse(raw, items)
    }

    private fun parseBatchResponse(
        raw: String,
        input: Map<String, Pair<String, String>>
    ): Map<String, CulturalAnalysis> {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            log.debug("Cultural batch JSON parse failed (len={}) — all {} strings defaulting to clean",
                raw.length, input.size)
            return input.mapValues { CulturalAnalysis(emptyList(), false) }
        }

        return input.mapValues { (key, _) ->
            val entry = parsed[key]?.jsonObject
            if (entry == null) {
                log.debug("Cultural batch: missing result for key='{}' — defaulting to clean", key)
                CulturalAnalysis(emptyList(), false)
            } else {
                runCatching {
                    val needsReview = entry["needsReview"]?.jsonPrimitive?.booleanOrNull ?: false
                    val issues = entry["issues"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                        ?: emptyList()
                    CulturalAnalysis(issues, needsReview && issues.isNotEmpty())
                }.getOrElse {
                    log.debug("Cultural batch: parse error for key='{}' — defaulting to clean", key)
                    CulturalAnalysis(emptyList(), false)
                }
            }
        }
    }

    private fun buildBatchSystemPrompt(appCategory: String, appTone: String, market: String): String = """
        You are a cultural localization consultant reviewing mobile app translations.
        App type: $appCategory · Tone: $appTone · Target market: $market

        Review each translation ONLY for genuine cultural appropriateness issues.
        Do NOT comment on linguistic quality, word choice preferences, or minor style differences.

        Flag ONLY if you observe one or more of these:
        1. Formality/honorifics: Wrong register for this app type and market (e.g., casual "you" in a formal finance app in Japanese or Korean).
        2. Cultural idioms: Translation introduces idioms, metaphors, or expressions that carry unintended meaning in this market.
        3. Communication style: Significant mismatch in directness, humility, or politeness conventions for this market.
        4. Symbols or emoji: Any emoji or symbol that carries a different or offensive meaning in this target market.

        Important rules:
        - Short utility strings ("OK", "Cancel", "Loading", "Save") almost never have cultural issues — skip them.
        - Technical terms, product names, and proper nouns are not cultural issues.
        - Be conservative: when in doubt, do NOT flag. Most translations are culturally fine.
        - Only raise issues that a local speaker would genuinely find inappropriate or confusing.

        Input: a JSON object where each key maps to {"source": "<English text>", "translated": "<$market translation>"}.
        Output: a JSON object mapping the SAME keys to {"needsReview": false, "issues": []} or {"needsReview": true, "issues": ["<specific, actionable description>"]}.
        Rules:
        - Include every input key in the output — no omissions.
        - No extra text, markdown fences, or keys outside those provided.
    """.trimIndent()

    private fun cacheKey(
        translatedText: String,
        targetLanguage: String,
        targetRegion: String?,
        appCategory: String,
        appTone: String
    ): String {
        val input = "$translatedText|$targetLanguage|${targetRegion ?: ""}|$appCategory|$appTone"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun close() { client.close() }

    companion object {
        private const val BATCH_SIZE = 20
    }
}
