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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

// Single source of truth for the Gemini model — change here to upgrade everywhere.
internal const val GEMINI_MODEL = "gemini-3.1-flash-lite"
internal const val GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

// Gemini API Models
@Serializable
data class ThinkingConfig(val thinkingBudget: Int)

@Serializable
data class GenerationConfig(
    val temperature: Double = 0.1,
    val maxOutputTokens: Int = 2048,
    val thinkingConfig: ThinkingConfig? = null,
    val responseMimeType: String? = null
)

@Serializable
data class GeminiRequest(
    val systemInstruction: GeminiSystemInstruction? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig = GenerationConfig(thinkingConfig = ThinkingConfig(0))
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

    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    data class TranslationResult(val text: String, val flags: List<String>)

    suspend fun translate(context: TranslationContext): String = translateWithFlags(context).text

    suspend fun translateWithFlags(context: TranslationContext): TranslationResult {
        val hashKey = cacheKey(context)
        val cached = memoryStore.getTranslation(hashKey)
        if (cached != null) {
            memoryStore.incrementUsage(hashKey)
            return TranslationResult(cached, emptyList())
        }

        val extraction = TokenExtractor.extract(context.sourceText)
        if (extraction.isTruncated) {
            throw IllegalStateException("String is truncated, blocking translation.")
        }

        val llmOutput = callGeminiApi(buildSystemPrompt(context), extraction.cleanText)
        return processAndStore(context.sourceText, extraction, llmOutput, hashKey)
    }

    // Translates a batch of strings in a single Gemini call.
    // All contexts must share the same language, category, tone, and glossary.
    // Returns a map of key → Result<TranslationResult>; cache hits are resolved before the API call.
    suspend fun translateBatch(
        keyedContexts: Map<String, TranslationContext>
    ): Map<String, Result<TranslationResult>> {
        if (keyedContexts.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, Result<TranslationResult>>()
        val toTranslate = mutableMapOf<String, Pair<TranslationContext, ExtractionResult>>()

        for ((key, ctx) in keyedContexts) {
            val hashKey = cacheKey(ctx)
            val cached = memoryStore.getTranslation(hashKey)
            if (cached != null) {
                memoryStore.incrementUsage(hashKey)
                results[key] = Result.success(TranslationResult(cached, emptyList()))
            } else {
                val extraction = TokenExtractor.extract(ctx.sourceText)
                if (extraction.isTruncated) {
                    results[key] = Result.failure(IllegalStateException("String is truncated"))
                } else {
                    toTranslate[key] = ctx to extraction
                }
            }
        }

        if (toTranslate.isEmpty()) return results

        val firstCtx = keyedContexts.values.first()
        val batchInput = toTranslate.mapValues { (_, pair) -> pair.second.cleanText }
        val inputJson = json.encodeToString(mapSerializer, batchInput)

        val llmOutput = try {
            callGeminiApiJson(buildBatchSystemPrompt(firstCtx), inputJson)
        } catch (e: Exception) {
            log.warn("Batch Gemini call failed for {} strings: {}", toTranslate.size, e.message)
            toTranslate.keys.forEach { key -> results[key] = Result.failure(e) }
            return results
        }

        val parsed: Map<String, String> = try {
            json.decodeFromString(mapSerializer, llmOutput.trim())
        } catch (e: Exception) {
            log.warn("Batch JSON parse failed for {} strings: {}", toTranslate.size, e.message)
            toTranslate.keys.forEach { key -> results[key] = Result.failure(e) }
            return results
        }

        for ((key, pair) in toTranslate) {
            val (ctx, extraction) = pair
            val translated = parsed[key]
            if (translated == null) {
                results[key] = Result.failure(IllegalStateException("Missing from batch response"))
                continue
            }
            results[key] = try {
                Result.success(processAndStore(ctx.sourceText, extraction, translated, cacheKey(ctx)))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        return results
    }

    private suspend fun processAndStore(
        sourceText: String, extraction: ExtractionResult, llmOutput: String, hashKey: String
    ): TranslationResult {
        return when (val guardResult = PlaceholderGuard.validateAndRestore(sourceText, llmOutput, extraction)) {
            is PlaceholderGuard.GuardResult.Success -> {
                memoryStore.storeTranslation(hashKey, guardResult.restoredText)
                TranslationResult(guardResult.restoredText, guardResult.flags)
            }
            is PlaceholderGuard.GuardResult.Rejected ->
                throw IllegalStateException("Validation rejected: ${guardResult.reason}")
        }
    }

    private fun buildSystemPrompt(ctx: TranslationContext): String {
        val glossaryLine = ctx.glossary?.entries?.joinToString(", ") { (k, v) -> "[$k -> $v]" }
            ?.let { "Apply the following glossary exactly: $it" } ?: ""
        return """
            You are a professional mobile app translator for ${ctx.category} apps.
            Translate English to ${ctx.targetLanguage}${ctx.targetRegion?.let { " ($it)" } ?: ""}.
            Tone: ${ctx.tone}.
            Rules: preserve ALL __PH_X__ and __ENT_X__ tokens exactly.
            Never translate tokens. Output translated string only.
            $glossaryLine
        """.trimIndent()
    }

    private fun buildBatchSystemPrompt(ctx: TranslationContext): String {
        val glossaryLine = ctx.glossary?.entries?.joinToString(", ") { (k, v) -> "[$k -> $v]" }
            ?.let { "Apply the following glossary exactly: $it" } ?: ""
        return """
            You are a professional mobile app translator for ${ctx.category} apps.
            Translate English to ${ctx.targetLanguage}${ctx.targetRegion?.let { " ($it)" } ?: ""}.
            Tone: ${ctx.tone}.
            Rules: preserve ALL __PH_X__ and __ENT_X__ tokens exactly. Never translate tokens.
            Input: a JSON object mapping string keys to English source text.
            Output: a JSON object mapping the SAME keys to their translations. No extra text.
            $glossaryLine
        """.trimIndent()
    }

    private suspend fun callGeminiApi(systemPrompt: String, userPrompt: String): String {
        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(listOf(GeminiPart(userPrompt)))),
            generationConfig = GenerationConfig(thinkingConfig = ThinkingConfig(0), temperature = 0.1)
        )
        return postToGemini(payload)
    }

    private suspend fun callGeminiApiJson(systemPrompt: String, userPrompt: String): String {
        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(listOf(GeminiPart(userPrompt)))),
            generationConfig = GenerationConfig(
                thinkingConfig = ThinkingConfig(0),
                temperature = 0.1,
                responseMimeType = "application/json"
            )
        )
        return postToGemini(payload)
    }

    private suspend fun postToGemini(payload: GeminiRequest): String {
        val response = client.post(
            "$GEMINI_ENDPOINT?key=$geminiApiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gemini API failed with status: ${response.status}")
        }
        val geminiResponse: GeminiResponse = response.body()
        return geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty response from Gemini API")
    }

    // Cache key covers every input that changes the Gemini output: source text, target
    // language/region, app category, tone, and glossary. appId (the repo name) is
    // intentionally excluded — it does not appear in the system prompt, so identical
    // strings across different projects with the same category/tone share one cache entry.
    private fun cacheKey(ctx: TranslationContext): String {
        val regionSuffix = ctx.targetRegion?.let { "-$it" } ?: ""
        val glossaryPart = ctx.glossary?.entries
            ?.sortedBy { it.key }
            ?.joinToString(";") { "${it.key}=${it.value}" } ?: ""
        val hashInput = "${ctx.sourceText}|${ctx.targetLanguage}${regionSuffix}|${ctx.category}|${ctx.tone}|${glossaryPart}"
        return hashString(hashInput)
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun close() { client.close() }
}
