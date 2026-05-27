package com.transloom.services

import com.androidplay.core.secrets.getSecretValue
import com.transloom.domain.ChangeType
import com.transloom.domain.SemanticChangeRecord
import com.transloom.repository.SemanticChangeCacheRepository
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
private data class SemanticAnalysisJson(val changeType: String = "", val reasoning: String = "")

/** Classifies source-string changes as SEMANTIC (retranslate) or SURFACE (skip). Defaults to SEMANTIC on any error. */
class SemanticChangeAnalyzer(private val cache: SemanticChangeCacheRepository) {

    private val log = LoggerFactory.getLogger(SemanticChangeAnalyzer::class.java)
    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }

    // One permit per batch call, not per string — batch calls are already large.
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
    suspend fun analyze(oldText: String, newText: String): SemanticChangeRecord =
        analyzeBatch(mapOf("_single" to (oldText to newText)))["_single"]
            ?: SemanticChangeRecord(ChangeType.SEMANTIC, "Batch result missing — defaulting to safe retranslation")

    /**
     * Classify a batch of source-string changes in as few Gemini calls as possible.
     *
     * Cache hits are resolved without any API call. The remainder is split into chunks
     * of [BATCH_SIZE] and each chunk is sent as one Gemini call with structured JSON
     * output — identical analysis quality to single-string mode, but N× cheaper.
     *
     * On any batch-level failure every string in that chunk defaults to SEMANTIC so
     * no translation is silently skipped. On a key-level parse miss the same fallback
     * applies with a warning logged.
     *
     * @param strings key → Pair(oldText, newText)
     * @return map with the same keys, each resolved to a [SemanticChangeRecord]
     */
    suspend fun analyzeBatch(strings: Map<String, Pair<String, String>>): Map<String, SemanticChangeRecord> {
        if (strings.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, SemanticChangeRecord>()
        val toAnalyze = mutableMapOf<String, Pair<String, String>>()

        for ((key, pair) in strings) {
            val cached = cache.get(cacheKey(pair.first, pair.second))
            if (cached != null) results[key] = cached else toAnalyze[key] = pair
        }

        if (toAnalyze.isEmpty()) return results

        for (chunk in toAnalyze.entries.chunked(BATCH_SIZE)) {
            val batch = chunk.associate { it.key to it.value }
            val batchResults = semaphore.withPermit {
                runCatching { callGeminiBatch(batch) }.getOrElse { e ->
                    log.warn("Batch semantic analysis failed for {} string(s) — defaulting all to SEMANTIC: {}",
                        batch.size, e.message)
                    batch.mapValues { SemanticChangeRecord(ChangeType.SEMANTIC, "Analysis failed — retranslating to be safe") }
                }
            }
            for ((key, record) in batchResults) {
                results[key] = record
                cache.put(cacheKey(batch.getValue(key).first, batch.getValue(key).second), record)
            }
        }

        return results
    }

    private suspend fun callGeminiBatch(strings: Map<String, Pair<String, String>>): Map<String, SemanticChangeRecord> {
        val inputJson = buildJsonObject {
            for ((key, pair) in strings) {
                put(key, buildJsonObject {
                    put("old", pair.first)
                    put("new", pair.second)
                })
            }
        }.toString()

        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(BATCH_SYSTEM_PROMPT))),
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
            throw Exception("Gemini semantic batch HTTP ${response.status}")
        }

        val raw = response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty Gemini semantic batch response")

        return parseBatchResponse(raw, strings)
    }

    private fun parseBatchResponse(
        raw: String,
        input: Map<String, Pair<String, String>>
    ): Map<String, SemanticChangeRecord> {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            log.warn("Semantic batch JSON parse failed (len={}) — all {} strings defaulting to SEMANTIC",
                raw.length, input.size)
            return input.mapValues { SemanticChangeRecord(ChangeType.SEMANTIC, "Batch parse error — retranslating to be safe") }
        }

        return input.mapValues { (key, _) ->
            val entry = parsed[key]?.jsonObject
            if (entry == null) {
                log.warn("Semantic batch: missing result for key='{}' — defaulting to SEMANTIC", key)
                SemanticChangeRecord(ChangeType.SEMANTIC, "Missing from batch response — retranslating to be safe")
            } else {
                val changeType = entry["changeType"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    ?.let { ct -> ChangeType.entries.firstOrNull { it.name == ct } }
                    ?: ChangeType.SEMANTIC
                val reasoning = entry["reasoning"]?.jsonPrimitive?.contentOrNull ?: "No reasoning provided"
                if (changeType == ChangeType.SURFACE) {
                    log.debug("Surface change key='{}': {}", key, reasoning)
                } else {
                    log.debug("Semantic change key='{}': {}", key, reasoning)
                }
                SemanticChangeRecord(changeType, reasoning)
            }
        }
    }

    private fun cacheKey(oldText: String, newText: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$oldText|$newText".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun close() { client.close() }

    companion object {
        private const val BATCH_SIZE = 20

        private val BATCH_SYSTEM_PROMPT = """
            You are a software localization expert. Analyze whether changed mobile app strings require their existing translations to be retranslated.

            For each entry classify the change as exactly one of:
            - SURFACE: Only formatting, capitalization, punctuation, or minor phrasing changed. The meaning, user intent, and information content are identical. Existing translations remain valid.
              Examples: "Click the button" → "Tap the button" | "Error!" → "Error." | "Sign In" → "Sign in" | "Loading…" → "Loading..."
            - SEMANTIC: The meaning, scope, information content, or user intent changed. Existing translations are no longer accurate and must be retranslated.
              Examples: "Delete" → "Permanently delete your account" | "Loading" → "Fetching your payment details" | "OK" → "I understand the risks"

            Input: a JSON object where each key maps to {"old": "<previous text>", "new": "<updated text>"}.
            Output: a JSON object mapping the SAME keys to {"changeType": "SURFACE" or "SEMANTIC", "reasoning": "<one concise sentence>"}.
            Rules:
            - Include every input key in the output — no omissions.
            - No extra text, markdown fences, or keys outside those provided.
            - When uncertain whether a change is surface or semantic, choose SEMANTIC to be safe.
        """.trimIndent()
    }
}
