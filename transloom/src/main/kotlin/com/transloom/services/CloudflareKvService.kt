package com.transloom.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

private const val CF_API_BASE = "https://api.cloudflare.com/client/v4"
private const val KV_MAX_VALUE_BYTES = 25 * 1024 * 1024 // 25 MB

class CloudflareKvService(
    private val accountId: String,
    private val namespaceId: String,
    private val apiToken: String
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(CloudflareKvService::class.java)
    private val http = HttpClient(CIO)

    /**
     * Writes [value] under [key] in the configured KV namespace.
     * Returns true on success. Throws [CdnPublishException] if the payload exceeds KV limits
     * or the Cloudflare API returns a non-2xx status.
     */
    suspend fun put(key: String, value: String, metadataJson: String? = null): Boolean {
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        if (valueBytes.size > KV_MAX_VALUE_BYTES) {
            throw CdnPublishException("Bundle for key '$key' is ${valueBytes.size} bytes — exceeds KV 25 MB limit")
        }

        val url = "$CF_API_BASE/accounts/$accountId/storage/kv/namespaces/$namespaceId/values/$key"

        val response: HttpResponse = if (metadataJson != null) {
            http.put(url) {
                header(HttpHeaders.Authorization, "Bearer $apiToken")
                contentType(ContentType.MultiPart.FormData)
                setBody(
                    io.ktor.client.request.forms.MultiPartFormDataContent(
                        io.ktor.client.request.forms.formData {
                            append("value", value)
                            append("metadata", metadataJson)
                        }
                    )
                )
            }
        } else {
            http.put(url) {
                header(HttpHeaders.Authorization, "Bearer $apiToken")
                contentType(ContentType.Text.Plain)
                setBody(value)
            }
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "" }
            log.error("Cloudflare KV PUT failed: key={} status={} body={}", key, response.status, body)
            throw CdnPublishException("Cloudflare KV PUT returned ${response.status} for key '$key'")
        }
        return true
    }

    override fun close() {
        http.close()
    }
}

class CdnPublishException(message: String, cause: Throwable? = null) : Exception(message, cause)
