package com.syncling.services

import com.androidplay.core.secrets.getSecretValue
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
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val EMBEDDING_MODEL = "text-embedding-004"
private const val EMBEDDING_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/$EMBEDDING_MODEL:batchEmbedContents"

/**
 * Thin wrapper over Gemini's batch embedding endpoint. Source-language only (English) — the
 * caller is responsible for de-duplicating before calling.
 *
 * Returns vectors in the same order as the input list. On failure, returns null for that
 * position rather than throwing — the fuzzy-TM lookup degrades gracefully when embeddings
 * are unavailable.
 */
class EmbeddingService {
    private val log = LoggerFactory.getLogger(EmbeddingService::class.java)

    private val apiKey: String = getSecretValue("gemini-api-key")

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        // Gemini batch endpoint accepts up to 100 inputs per call.
        val chunks = texts.chunked(100)
        val result = mutableListOf<FloatArray?>()
        for (chunk in chunks) {
            result += embedChunk(chunk)
        }
        return result
    }

    private suspend fun embedChunk(texts: List<String>): List<FloatArray?> {
        val payload = BatchRequest(
            requests = texts.map {
                EmbedRequest(
                    model = "models/$EMBEDDING_MODEL",
                    content = EmbedContent(parts = listOf(EmbedPart(text = it)))
                )
            }
        )
        repeat(3) { attempt ->
            try {
                val response = client.post("$EMBEDDING_ENDPOINT?key=$apiKey") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                if (!response.status.isSuccess()) {
                    if (response.status.value in setOf(429, 500, 502, 503, 504) && attempt < 2) {
                        delay(500L * (1L shl attempt))
                        return@repeat
                    }
                    log.warn("Embedding API failed: {}", response.status)
                    return List(texts.size) { null }
                }
                val body: BatchResponse = response.body()
                return body.embeddings.map { it.values.toFloatArray() }
            } catch (e: Exception) {
                if (attempt == 2) {
                    log.warn("Embedding batch failed after retries: {}", e.message)
                    return List(texts.size) { null }
                }
                delay(500L * (1L shl attempt))
            }
        }
        return List(texts.size) { null }
    }

    fun close() { client.close() }

    @Serializable
    private data class BatchRequest(val requests: List<EmbedRequest>)
    @Serializable
    private data class EmbedRequest(val model: String, val content: EmbedContent)
    @Serializable
    private data class EmbedContent(val parts: List<EmbedPart>)
    @Serializable
    private data class EmbedPart(val text: String)
    @Serializable
    private data class BatchResponse(val embeddings: List<EmbedValues> = emptyList())
    @Serializable
    private data class EmbedValues(val values: List<Float> = emptyList())
}
