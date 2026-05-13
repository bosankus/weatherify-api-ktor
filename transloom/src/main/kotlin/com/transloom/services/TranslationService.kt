package com.transloom.services

import com.androidplay.core.secrets.getSecretValue
import com.transloom.repository.TranslationMemoryRepository
import com.transloom.pipeline.ExtractionResult
import com.transloom.pipeline.PlaceholderGuard
import com.transloom.pipeline.TokenExtractor
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

// Gemini API Models
@Serializable
data class GeminiRequest(
    val systemInstruction: GeminiSystemInstruction? = null,
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiSystemInstruction(val parts: List<GeminiPart>)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

@Serializable
data class GeminiCandidate(val content: GeminiContent)

data class TranslationContext(
    val appId: String,
    val appName: String,
    val category: String,
    val tone: String,
    val glossary: Map<String, String>? = null,
    val sourceText: String,
    val targetLanguage: String,
    val targetRegion: String? = null
)

class TranslationService(private val memoryStore: TranslationMemoryRepository) {
    private val log = LoggerFactory.getLogger(TranslationService::class.java)

    // Fetched once at construction time; avoids a potential GCP metadata round-trip per translation.
    private val geminiApiKey: String = getSecretValue("gemini-api-key")

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    data class TranslationResult(val text: String, val flags: List<String>)

    suspend fun translate(context: TranslationContext): String = translateWithFlags(context).text

    suspend fun translateWithFlags(context: TranslationContext): TranslationResult {
        val regionSuffix = context.targetRegion?.let { "-$it" } ?: ""
        val hashInput = "${context.sourceText}|${context.targetLanguage}${regionSuffix}|${context.appId}"
        val hashKey = hashString(hashInput)

        val cached = memoryStore.getTranslation(hashKey)
        if (cached != null) {
            memoryStore.incrementUsage(hashKey)
            return TranslationResult(cached, emptyList())
        }

        val extraction = TokenExtractor.extract(context.sourceText)
        if (extraction.isTruncated) {
            throw IllegalStateException("String is truncated, blocking translation.")
        }

        val glossaryPrompt = context.glossary?.let { glossary ->
            "Apply the following glossary exactly: " + glossary.entries.joinToString(", ") { (k, v) -> "[$k -> $v]" }
        } ?: ""

        val systemPrompt = """
            You are a professional mobile app translator for ${context.category} apps.
            Translate English to ${context.targetLanguage}${if (context.targetRegion != null) " (${context.targetRegion})" else ""}.
            Tone: ${context.tone}.
            Rules: preserve ALL __PH_X__ and __ENT_X__ tokens exactly.
            Never translate tokens. Output translated string only.
            $glossaryPrompt
        """.trimIndent()

        val llmOutput = callGeminiApi(systemPrompt, extraction.cleanText)

        val guardResult = PlaceholderGuard.validateAndRestore(context.sourceText, llmOutput, extraction)

        when (guardResult) {
            is PlaceholderGuard.GuardResult.Success -> {
                memoryStore.storeTranslation(hashKey, guardResult.restoredText)
                return TranslationResult(guardResult.restoredText, guardResult.flags)
            }
            is PlaceholderGuard.GuardResult.Rejected -> {
                throw IllegalStateException("Validation rejected: ${guardResult.reason}")
            }
        }
    }

    private suspend fun callGeminiApi(systemPrompt: String, userPrompt: String): String {
        val requestPayload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(listOf(GeminiPart(userPrompt))))
        )

        val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey") {
            contentType(ContentType.Application.Json)
            setBody(requestPayload)
        }

        if (!response.status.isSuccess()) {
            throw Exception("Gemini API failed with status: ${response.status}")
        }

        val geminiResponse: GeminiResponse = response.body()
        val translatedText = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API")

        return translatedText.trim()
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun close() { client.close() }
}
