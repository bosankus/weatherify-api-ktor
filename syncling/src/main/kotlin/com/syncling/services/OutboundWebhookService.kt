package com.syncling.services

import com.syncling.domain.Project
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Fires a signed POST to a project's configured outbound webhook URL after each pipeline run.
 *
 * Payload is signed with `X-Syncling-Signature: sha256=<hmac>` using the project's stored
 * webhook secret so recipients can verify authenticity without sharing credentials.
 *
 * Calls are fire-and-forget — failures are logged but never propagate to the caller.
 */
class OutboundWebhookService : AutoCloseable {

    private val log = LoggerFactory.getLogger(OutboundWebhookService::class.java)
    private val json = Json { encodeDefaults = true }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    @Serializable
    data class WebhookPayload(
        val event: String,
        val projectId: String,
        val projectName: String,
        val repo: String,
        val branch: String,
        val commitShort: String,
        val prUrl: String?,
        val stringsTranslated: Int,
        val cacheHits: Int,
        val surfaceSkipped: Int,
        val locales: List<String>,
        val status: String,
        val timestamp: Long
    )

    suspend fun fire(project: Project, payload: WebhookPayload) {
        val url = project.outboundWebhookUrl ?: return
        val secret = project.outboundWebhookSecret

        val body = json.encodeToString(payload)
        val signature = secret?.let { sign(body, it) }

        runCatching {
            http.post(url) {
                contentType(ContentType.Application.Json)
                if (signature != null) header("X-Syncling-Signature", "sha256=$signature")
                setBody(body)
            }
            log.debug("Outbound webhook fired: project={} url={} status=ok", project.id, url)
        }.onFailure {
            log.warn("Outbound webhook failed: project={} url={}: {}", project.id, url, it.message)
        }
    }

    private fun sign(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    override fun close() = http.close()
}
