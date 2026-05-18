package com.transloom.services

import com.androidplay.core.secrets.getSecretValue
import com.transloom.domain.ChangeType
import com.transloom.domain.SemanticChangeRecord
import com.transloom.repository.SemanticChangeCacheRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

// Used only within this service — not part of the public domain model.
@Serializable
private data class SemanticAnalysisJson(val changeType: String = "", val reasoning: String = "")

/**
 * Classifies whether a change to a source string requires retranslation (SEMANTIC)
 * or can be skipped because only surface wording changed (SURFACE).
 *
 * Falls back to SEMANTIC on any error — the safe default is always to retranslate.
 */
class SemanticChangeAnalyzer(private val cache: SemanticChangeCacheRepository) {

    private val log = LoggerFactory.getLogger(SemanticChangeAnalyzer::class.java)
    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }

    // Gemini rate-limit guard: mirrors the concurrency cap used in TranslationService.
    private val semaphore = Semaphore(4)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun analyze(oldText: String, newText: String): SemanticChangeRecord {
        val hashKey = cacheKey(oldText, newText)
        cache.get(hashKey)?.let { return it }

        val result = semaphore.withPermit {
            runCatching { callGemini(oldText, newText) }
                .getOrElse { e ->
                    log.warn("Semantic analysis Gemini call failed — defaulting to SEMANTIC: {}", e.message)
                    SemanticChangeRecord(ChangeType.SEMANTIC, "Analysis failed — retranslating to be safe")
                }
        }

        cache.put(hashKey, result)
        return result
    }

    private suspend fun callGemini(oldText: String, newText: String): SemanticChangeRecord {
        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(SYSTEM_PROMPT))),
            contents = listOf(GeminiContent(listOf(GeminiPart("""Old: "$oldText"${'\n'}New: "$newText"""")))),
            generationConfig = GenerationConfig(
                temperature = 0.0,
                thinkingConfig = ThinkingConfig(0),
                responseMimeType = "application/json"
            )
        )

        val response = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (!response.status.isSuccess()) {
            log.warn("Gemini semantic analysis HTTP {} — defaulting to SEMANTIC", response.status)
            return SemanticChangeRecord(ChangeType.SEMANTIC, "Gemini HTTP error — retranslating to be safe")
        }

        val raw = response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: return SemanticChangeRecord(ChangeType.SEMANTIC, "Empty Gemini response — retranslating to be safe")

        return runCatching {
            val parsed = json.decodeFromString(SemanticAnalysisJson.serializer(), raw)
            val changeType = ChangeType.entries.firstOrNull { it.name == parsed.changeType.uppercase() }
                ?: ChangeType.SEMANTIC
            SemanticChangeRecord(changeType, parsed.reasoning)
        }.getOrElse {
            log.warn("Semantic analysis JSON parse failed for response '{}' — defaulting to SEMANTIC", raw)
            SemanticChangeRecord(ChangeType.SEMANTIC, "Parse error — retranslating to be safe")
        }
    }

    private fun cacheKey(oldText: String, newText: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$oldText|$newText".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun close() { client.close() }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a software localization expert. Analyze whether a changed mobile app string requires its existing translations to be retranslated.

            Classify the change as exactly one of:
            - SURFACE: Only formatting, capitalization, punctuation, or minor phrasing changed. The meaning, user intent, and information content are identical. Existing translations remain valid.
              Examples: "Click the button" → "Tap the button" | "Error!" → "Error." | "Sign In" → "Sign in" | "Loading…" → "Loading..."
            - SEMANTIC: The meaning, scope, information content, or user intent changed. Existing translations are no longer accurate and must be retranslated.
              Examples: "Delete" → "Permanently delete your account" | "Loading" → "Fetching your payment details" | "OK" → "I understand the risks"

            Respond ONLY with valid JSON matching this schema exactly: {"changeType":"SURFACE","reasoning":"<one concise sentence>"}
        """.trimIndent()
    }
}
