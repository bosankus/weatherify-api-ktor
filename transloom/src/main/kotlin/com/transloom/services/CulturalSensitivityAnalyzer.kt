package com.transloom.services

import com.androidplay.core.secrets.getSecretValue
import com.transloom.domain.CulturalAnalysis
import com.transloom.repository.CulturalAnalysisCacheRepository
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

@Serializable
private data class CulturalAnalysisJson(
    val needsReview: Boolean = false,
    val issues: List<String> = emptyList()
)

/**
 * Runs a post-translation cultural appropriateness check using Gemini.
 *
 * Only flags genuine cultural concerns — formality mismatch, market-inappropriate idioms,
 * symbol/emoji misuse, communication-style clashes. Conservative by design: the safe
 * default on any failure is "no issues" to avoid overwhelming the review queue.
 *
 * Cache key: SHA-256(translatedText|language|region?|category|tone)
 * The analysis is independent of source text — it judges the translated output's
 * cultural fit for the target market.
 */
class CulturalSensitivityAnalyzer(private val cache: CulturalAnalysisCacheRepository) {

    private val log = LoggerFactory.getLogger(CulturalSensitivityAnalyzer::class.java)
    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }
    private val semaphore = Semaphore(4)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun analyze(
        translatedText: String,
        sourceText: String,
        targetLanguage: String,
        targetRegion: String?,
        appCategory: String,
        appTone: String
    ): CulturalAnalysis {
        val hashKey = cacheKey(translatedText, targetLanguage, targetRegion, appCategory, appTone)
        cache.get(hashKey)?.let { return it }

        val result = semaphore.withPermit {
            runCatching { callGemini(translatedText, sourceText, targetLanguage, targetRegion, appCategory, appTone) }
                .getOrElse { e ->
                    log.debug("Cultural analysis Gemini call failed — defaulting to clean: {}", e.message)
                    CulturalAnalysis(emptyList(), false)
                }
        }

        cache.put(hashKey, result)
        return result
    }

    private suspend fun callGemini(
        translatedText: String,
        sourceText: String,
        targetLanguage: String,
        targetRegion: String?,
        appCategory: String,
        appTone: String
    ): CulturalAnalysis {
        val market = if (targetRegion != null) "$targetLanguage ($targetRegion)" else targetLanguage
        val systemPrompt = buildSystemPrompt(appCategory, appTone, market)
        val userPrompt = """Source (English): "$sourceText"${'\n'}Translation ($market): "$translatedText""""

        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(listOf(GeminiPart(userPrompt)))),
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
            log.debug("Gemini cultural analysis HTTP {} — skipping flag", response.status)
            return CulturalAnalysis(emptyList(), false)
        }

        val raw = response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: return CulturalAnalysis(emptyList(), false)

        return runCatching {
            val parsed = json.decodeFromString(CulturalAnalysisJson.serializer(), raw)
            CulturalAnalysis(parsed.issues.filter { it.isNotBlank() }, parsed.needsReview && parsed.issues.isNotEmpty())
        }.getOrElse {
            log.debug("Cultural analysis JSON parse failed for '{}' — skipping flag", raw.take(80))
            CulturalAnalysis(emptyList(), false)
        }
    }

    private fun buildSystemPrompt(appCategory: String, appTone: String, market: String): String = """
        You are a cultural localization consultant reviewing a mobile app translation.
        App type: $appCategory · Tone: $appTone · Target market: $market

        Review the translation ONLY for genuine cultural appropriateness issues.
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

        Respond with ONLY valid JSON:
        {"needsReview": false, "issues": []}
        or
        {"needsReview": true, "issues": ["<specific, actionable description of the cultural concern>"]}
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
}
