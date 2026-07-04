package com.syncling.services

import com.androidplay.core.secrets.getSecretValue
import com.syncling.repository.TranslationMemoryRepository
import com.syncling.repository.SharedTranslationMemoryRepository
import com.syncling.pipeline.ExtractionResult
import com.syncling.pipeline.IcuPlaceholderValidator
import com.syncling.pipeline.PlaceholderGuard
import com.syncling.pipeline.TokenExtractor
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
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

/** One content part — plain text or an inline image (base64). Exactly one field should be set. */
@Serializable
data class GeminiPart(val text: String? = null, val inlineData: GeminiInlineData? = null)

@Serializable
data class GeminiInlineData(val mimeType: String, val data: String)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null
)

@Serializable
data class GeminiCandidate(val content: GeminiContent)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)

/** Per-batch token + cache accounting returned alongside translation results. */
data class TokenUsage(val inputTokens: Long, val outputTokens: Long) {
    operator fun plus(other: TokenUsage) =
        TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens)
    companion object { val ZERO = TokenUsage(0, 0) }
}

data class TranslationContext(
    val appId: String,
    val appName: String,
    val category: String,
    val tone: String,
    val glossary: Map<String, String>? = null,
    val sourceText: String,
    val targetLanguage: String,
    val targetRegion: String? = null,
    /**
     * Approved (source → translation) pairs from this project's fuzzy TM whose source is
     * semantically close to one of the batch inputs. Injected as few-shot examples so the
     * model adopts repeated phrasings without us having to memorize them in glossaries.
     */
    val fuzzyExamples: List<Pair<String, String>> = emptyList(),
    /**
     * Recent (source → reviewerEdit) pairs — what a human shipped after correcting the model.
     * Strongest signal we have for project tone/voice; presented before fuzzy examples.
     */
    val reviewerExamples: List<Pair<String, String>> = emptyList(),
    /**
     * PNG of the UI screen this string appears on (Figma frame preview). Attached to the
     * Gemini request so the model sees the widget context — resolves verb/noun ambiguity
     * ("Book", "Back") and hints at available space. Excluded from the cache key: the
     * screenshot refines the same translation, it doesn't define a different one.
     */
    val screenshot: ByteArray? = null
)

/**
 * Supplies per-string-key UI screenshots for a project — implemented by FigmaSyncService
 * from frame previews, but the pipeline only knows this seam.
 */
fun interface VisualContextProvider {
    suspend fun screenshotsForKeys(projectId: String, keys: Collection<String>): Map<String, ByteArray>
}

// CLDR plural form requirements per language name. Languages not listed use the default two-form
// system (one / other) that matches English.
private val PLURAL_FORMS_BY_LANGUAGE: Map<String, List<String>> = mapOf(
    "Russian"    to listOf("one", "few", "many", "other"),
    "Ukrainian"  to listOf("one", "few", "many", "other"),
    "Polish"     to listOf("one", "few", "many", "other"),
    "Czech"      to listOf("one", "few", "many", "other"),
    "Slovak"     to listOf("one", "few", "many", "other"),
    "Serbian"    to listOf("one", "few", "other"),
    "Croatian"   to listOf("one", "few", "other"),
    "Slovenian"  to listOf("one", "two", "few", "other"),
    "Lithuanian" to listOf("one", "few", "other"),
    "Latvian"    to listOf("zero", "one", "other"),
    "Romanian"   to listOf("one", "few", "other"),
    "Arabic"     to listOf("zero", "one", "two", "few", "many", "other"),
    // 1-form languages (everything is "other")
    "Chinese"    to listOf("other"),
    "Japanese"   to listOf("other"),
    "Korean"     to listOf("other"),
    "Vietnamese" to listOf("other"),
    "Thai"       to listOf("other"),
    "Indonesian" to listOf("other"),
    "Malay"      to listOf("other")
)

fun requiredPluralForms(languageName: String): List<String> =
    PLURAL_FORMS_BY_LANGUAGE[languageName] ?: listOf("one", "other")

private val PLURAL_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

/** At most this many distinct frame screenshots ride along per batch call — context, not a slideshow. */
private const val MAX_BATCH_SCREENSHOTS = 4

private const val GEMINI_MAX_RETRIES = 3
private const val GEMINI_RETRY_BASE_MS = 1_000L
private val GEMINI_RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)

class TranslationService(
    private val memoryStore: TranslationMemoryRepository,
    private val sharedMemoryStore: SharedTranslationMemoryRepository? = null,
    /** Optional metrics sink — emits per-call token & cost counters. Null in tests/previews. */
    private val metrics: PipelineMetrics? = null
) {
    private val log = LoggerFactory.getLogger(TranslationService::class.java)

    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis  = 60_000
        }
    }

    data class TranslationResult(val text: String, val flags: List<String>)

    /** Wraps translateBatch results together with cache accounting for dashboard display. */
    data class BatchOutcome(
        val results: Map<String, Result<TranslationResult>>,
        val cacheHits: Int,
        val tokenUsage: TokenUsage = TokenUsage.ZERO
    )

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

    /** Like [translateBatch] but also returns the number of strings served from cache. */
    suspend fun translateBatchTracked(keyedContexts: Map<String, TranslationContext>): BatchOutcome {
        val outcome = translateBatchInternal(keyedContexts)
        return BatchOutcome(outcome.first, outcome.second, outcome.third)
    }

    // Translates a batch of strings in a single Gemini call.
    // All contexts must share the same language, category, tone, and glossary.
    // Returns a map of key → Result<TranslationResult>; cache hits are resolved before the API call.
    suspend fun translateBatch(
        keyedContexts: Map<String, TranslationContext>
    ): Map<String, Result<TranslationResult>> = translateBatchInternal(keyedContexts).first

    private suspend fun translateBatchInternal(
        keyedContexts: Map<String, TranslationContext>
    ): Triple<Map<String, Result<TranslationResult>>, Int, TokenUsage> {
        if (keyedContexts.isEmpty()) return Triple(emptyMap(), 0, TokenUsage.ZERO)

        val results = mutableMapOf<String, Result<TranslationResult>>()
        val toTranslate = mutableMapOf<String, Pair<TranslationContext, ExtractionResult>>()
        var cacheHits = 0
        var tokenUsage = TokenUsage.ZERO

        for ((key, ctx) in keyedContexts) {
            val hashKey = cacheKey(ctx)
            val cached = memoryStore.getTranslation(hashKey)
                ?: sharedMemoryStore?.get(ctx.sourceText, ctx.targetLanguage)
            if (cached != null) {
                memoryStore.incrementUsage(hashKey)
                results[key] = Result.success(TranslationResult(cached, emptyList()))
                cacheHits++
            } else {
                val extraction = TokenExtractor.extract(ctx.sourceText)
                if (extraction.isTruncated) {
                    results[key] = Result.failure(IllegalStateException("String is truncated"))
                } else {
                    toTranslate[key] = ctx to extraction
                }
            }
        }

        if (toTranslate.isEmpty()) return Triple(results, cacheHits, tokenUsage)

        val firstCtx = keyedContexts.values.first()
        val batchInput = toTranslate.mapValues { (_, pair) -> pair.second.cleanText }
        val inputJson = json.encodeToString(mapSerializer, batchInput)
        val imageParts = buildImageParts(toTranslate)

        val (llmOutput, usage) = try {
            callGeminiApiJson(
                buildBatchSystemPrompt(firstCtx, withScreenshots = imageParts.isNotEmpty()),
                imageParts + GeminiPart(inputJson),
            )
        } catch (e: Exception) {
            log.warn("Batch Gemini call failed for {} strings: {}", toTranslate.size, e.message)
            toTranslate.keys.forEach { key -> results[key] = Result.failure(e) }
            return Triple(results, cacheHits, tokenUsage)
        }
        tokenUsage += usage
        recordCallMetrics("batch", usage)

        val parsed: Map<String, String> = try {
            json.decodeFromString(mapSerializer, llmOutput.trim())
        } catch (e: Exception) {
            log.warn("Batch JSON parse failed for {} strings: {}", toTranslate.size, e.message)
            toTranslate.keys.forEach { key -> results[key] = Result.failure(e) }
            return Triple(results, cacheHits, tokenUsage)
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

        return Triple(results, cacheHits, tokenUsage)
    }

    /**
     * Interleaved (label, image) parts for the batch request: strings sharing a screenshot are
     * grouped, the largest groups win the [MAX_BATCH_SCREENSHOTS] slots. Empty when no context
     * in the batch carries a screenshot — the request is then byte-identical to the old shape.
     */
    private fun buildImageParts(
        toTranslate: Map<String, Pair<TranslationContext, ExtractionResult>>
    ): List<GeminiPart> {
        class Group(val png: ByteArray) { val keys = mutableListOf<String>() }
        val groups = LinkedHashMap<Int, Group>()
        for ((key, pair) in toTranslate) {
            val png = pair.first.screenshot ?: continue
            groups.getOrPut(png.contentHashCode()) { Group(png) }.keys += key
        }
        if (groups.isEmpty()) return emptyList()
        return groups.values
            .sortedByDescending { it.keys.size }
            .take(MAX_BATCH_SCREENSHOTS)
            .flatMap { group ->
                listOf(
                    GeminiPart("UI screenshot for keys: ${group.keys.joinToString(", ")}"),
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = "image/png",
                            data = java.util.Base64.getEncoder().encodeToString(group.png),
                        )
                    ),
                )
            }
    }

    private fun recordCallMetrics(endpoint: String, usage: TokenUsage) {
        val m = metrics ?: return
        m.addTokens("in", endpoint, usage.inputTokens)
        m.addTokens("out", endpoint, usage.outputTokens)
        m.addCostUsd(endpoint, GeminiCostEstimator.estimateTranslation(usage.inputTokens, usage.outputTokens))
    }

    private suspend fun processAndStore(
        sourceText: String, extraction: ExtractionResult, llmOutput: String, hashKey: String
    ): TranslationResult {
        return when (val guardResult = PlaceholderGuard.validateAndRestore(sourceText, llmOutput, extraction)) {
            is PlaceholderGuard.GuardResult.Success -> {
                // ICU validation on raw (restored) text — PlaceholderGuard checks tokenized printf/HTML,
                // but doesn't see ICU {name, plural, ...} placeholders.
                val icu = IcuPlaceholderValidator.validate(sourceText, guardResult.restoredText)
                if (icu is IcuPlaceholderValidator.Result.Reject) {
                    throw IllegalStateException("ICU validation rejected: ${icu.message}")
                }
                memoryStore.storeTranslation(hashKey, guardResult.restoredText)
                TranslationResult(guardResult.restoredText, guardResult.flags)
            }
            is PlaceholderGuard.GuardResult.Rejected ->
                throw IllegalStateException("Validation rejected: ${guardResult.reason}")
        }
    }

    /**
     * Translates grouped plural forms as a single structured Gemini call.
     *
     * Input:  Map<baseName, Map<quantity, sourceText>>
     *   e.g.  { "item_count": {"one": "%d item", "other": "%d items"} }
     *
     * Output: Map<baseName, Map<quantity, translatedText>>
     *   e.g.  { "item_count": {"one": "%d элемент", "few": "%d элемента", "many": "%d элементов", "other": "%d элемента"} }
     *
     * The system prompt instructs Gemini to generate ALL required CLDR quantities for the target
     * language, even if the English source only has "one" and "other".
     */
    suspend fun translatePluralBatch(
        pluralGroups: Map<String, Map<String, String>>,
        context: TranslationContext
    ): Map<String, Map<String, String>> {
        if (pluralGroups.isEmpty()) return emptyMap()

        val requiredForms = requiredPluralForms(context.targetLanguage)
        val inputJson = json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                String.serializer(),
                kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer())
            ),
            pluralGroups
        )

        val systemPrompt = buildPluralSystemPrompt(context, requiredForms)
        val llmOutput = try {
            val (text, usage) = callGeminiApiJson(systemPrompt, inputJson)
            recordCallMetrics("plural", usage)
            text
        } catch (e: Exception) {
            log.warn("Plural batch Gemini call failed: {}", e.message)
            return emptyMap()
        }

        return try {
            json.decodeFromString(
                kotlinx.serialization.builtins.MapSerializer(
                    String.serializer(),
                    kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer())
                ),
                llmOutput.trim()
            )
        } catch (e: Exception) {
            log.warn("Plural batch JSON parse failed: {}", e.message)
            emptyMap()
        }
    }

    private fun buildPluralSystemPrompt(ctx: TranslationContext, requiredForms: List<String>): String {
        val glossaryLine = ctx.glossary?.entries?.joinToString(", ") { (k, v) -> "[$k -> $v]" }
            ?.let { "Apply the following glossary exactly: $it" } ?: ""
        return """
            You are a professional mobile app translator for ${ctx.category} apps.
            Translate Android plural strings from English to ${ctx.targetLanguage}${ctx.targetRegion?.let { " ($it)" } ?: ""}.
            Tone: ${ctx.tone}.
            Required CLDR plural categories for ${ctx.targetLanguage}: ${requiredForms.joinToString(", ")}.
            Rules:
            - Preserve ALL __PH_X__ and __ENT_X__ tokens exactly. Never translate tokens.
            - For each string key, provide translations for ALL required categories listed above.
            - If the source only has "one" and "other", generate the missing forms using correct grammar.
            - Return valid JSON only. No explanations.
            Input: JSON mapping string keys → { quantity: English source text }.
            Output: JSON mapping the SAME keys → { quantity: translated text } with all required categories.
            $glossaryLine
        """.trimIndent()
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

    private fun buildBatchSystemPrompt(ctx: TranslationContext, withScreenshots: Boolean = false): String {
        val glossaryLine = ctx.glossary?.entries?.joinToString(", ") { (k, v) -> "[$k -> $v]" }
            ?.let { "Apply the following glossary exactly: $it" } ?: ""
        val screenshotLine = if (!withScreenshots) "" else
            "UI screenshots are attached, each preceded by the string keys visible on that screen. " +
                "Use them to resolve ambiguity (verb vs noun, formality, abbreviations) and prefer concise phrasing for space-constrained widgets like buttons and tabs."
        val reviewerBlock = if (ctx.reviewerExamples.isEmpty()) "" else buildString {
            appendLine("Reviewer corrections — strongly prefer these phrasings, capitalization, and tone:")
            ctx.reviewerExamples.take(8).forEach { (src, edit) ->
                appendLine("  EN: ${src.replace("\n", " ")}")
                appendLine("  ${ctx.targetLanguage}: ${edit.replace("\n", " ")}")
            }
        }.trim()
        val fuzzyBlock = if (ctx.fuzzyExamples.isEmpty()) "" else buildString {
            appendLine("Previously approved translations in this project for similar source strings:")
            ctx.fuzzyExamples.take(8).forEach { (src, tgt) ->
                appendLine("  EN: ${src.replace("\n", " ")}")
                appendLine("  ${ctx.targetLanguage}: ${tgt.replace("\n", " ")}")
            }
        }.trim()
        return """
            You are a professional mobile app translator for ${ctx.category} apps.
            Translate English to ${ctx.targetLanguage}${ctx.targetRegion?.let { " ($it)" } ?: ""}.
            Tone: ${ctx.tone}.
            Rules: preserve ALL __PH_X__ and __ENT_X__ tokens exactly. Never translate tokens.
            Preserve ICU placeholders like {count} or {name, plural, ...} unchanged in name and structure.
            Input: a JSON object mapping string keys to English source text.
            Output: a JSON object mapping the SAME keys to their translations. No extra text.
            $glossaryLine
            $screenshotLine
            $reviewerBlock
            $fuzzyBlock
        """.trimIndent()
    }

    private suspend fun callGeminiApi(systemPrompt: String, userPrompt: String): String {
        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(listOf(GeminiPart(userPrompt)))),
            generationConfig = GenerationConfig(thinkingConfig = ThinkingConfig(0), temperature = 0.1)
        )
        val (text, usage) = postToGemini(payload)
        recordCallMetrics("single", usage)
        return text
    }

    private suspend fun callGeminiApiJson(systemPrompt: String, userPrompt: String): Pair<String, TokenUsage> =
        callGeminiApiJson(systemPrompt, listOf(GeminiPart(userPrompt)))

    private suspend fun callGeminiApiJson(systemPrompt: String, parts: List<GeminiPart>): Pair<String, TokenUsage> {
        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(parts)),
            generationConfig = GenerationConfig(
                thinkingConfig = ThinkingConfig(0),
                temperature = 0.1,
                responseMimeType = "application/json"
            )
        )
        return postToGemini(payload)
    }

    private suspend fun postToGemini(payload: GeminiRequest): Pair<String, TokenUsage> {
        var lastException: Exception? = null
        repeat(GEMINI_MAX_RETRIES) { attempt ->
            val response = client.post("$GEMINI_ENDPOINT?key=$geminiApiKey") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            when {
                response.status.isSuccess() -> {
                    val geminiResponse: GeminiResponse = response.body()
                    val text = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                        ?: throw Exception("Empty response from Gemini API")
                    val usage = geminiResponse.usageMetadata?.let {
                        TokenUsage(it.promptTokenCount.toLong(), it.candidatesTokenCount.toLong())
                    } ?: TokenUsage.ZERO
                    return text to usage
                }
                response.status.value in GEMINI_RETRYABLE_CODES -> {
                    lastException = Exception("Gemini API transient error: ${response.status}")
                    if (attempt < GEMINI_MAX_RETRIES - 1) {
                        val base = GEMINI_RETRY_BASE_MS * (1L shl attempt)
                        val jitter = (base * 0.3 * Math.random()).toLong()
                        val backoffMs = base + jitter
                        log.warn("Gemini {} on attempt {}/{}, retrying in {}ms", response.status, attempt + 1, GEMINI_MAX_RETRIES, backoffMs)
                        delay(backoffMs)
                    }
                }
                else -> throw Exception("Gemini API failed with status: ${response.status}")
            }
        }
        throw lastException ?: Exception("Gemini API failed after $GEMINI_MAX_RETRIES attempts")
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
