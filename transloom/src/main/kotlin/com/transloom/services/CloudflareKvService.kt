package com.transloom.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

/**
 * Cloudflare R2 storage client using the S3-compatible API with AWS Signature V4.
 *
 * Credentials: create an R2 API token in the Cloudflare dashboard with
 * Object Read & Write permissions and note the Access Key ID + Secret.
 */
class CloudflareR2Service(
    accountId: String,
    bucketName: String,
    accessKeyId: String,
    secretAccessKey: String
) : AutoCloseable {
    private val accountId = accountId.trim()
    private val bucketName = bucketName.trim()
    private val accessKeyId = accessKeyId.trim()
    private val secretAccessKey = secretAccessKey.trim()

    private val log = LoggerFactory.getLogger(CloudflareR2Service::class.java)
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
    private val host = "$accountId.r2.cloudflarestorage.com"

    init {
        val missing = buildList {
            if (accountId.isBlank()) add("accountId")
            if (bucketName.isBlank()) add("bucketName")
            if (accessKeyId.isBlank()) add("accessKeyId")
            if (secretAccessKey.isBlank()) add("secretAccessKey")
        }
        if (missing.isNotEmpty()) {
            log.error("CloudflareR2Service initialized with BLANK credentials: {} — R2 writes will fail. Check GCP Secret Manager entries: cloudflare-account-id, cloudflare-r2-bucket-name, cloudflare-r2-access-key-id, cloudflare-r2-secret-access-key", missing)
        } else {
            log.info("CloudflareR2Service ready: host={} bucket={} accessKeyIdLen={}", host, bucketName, accessKeyId.length)
        }
    }

    /**
     * Returns the value stored under [key], or null if the object does not exist.
     * Throws [CdnPublishException] on unexpected errors.
     */
    suspend fun get(key: String): String? {
        val now = Instant.now()
        val amzDate = DATE_TIME_FMT.format(now)
        val dateStamp = DATE_FMT.format(now)
        val path = "/$bucketName/$key"

        val signedHeaders = treeMapOf(
            "host" to host,
            "x-amz-content-sha256" to EMPTY_HASH,
            "x-amz-date" to amzDate
        )
        val auth = sign("GET", path, EMPTY_HASH, signedHeaders, dateStamp, amzDate)

        val response: HttpResponse = http.get("https://$host$path") {
            header("x-amz-date", amzDate)
            header("x-amz-content-sha256", EMPTY_HASH)
            header(HttpHeaders.Authorization, auth)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "" }
            log.error("R2 GET failed: host={} bucket={} key={} status={} body={}", host, bucketName, key, response.status, body)
            throw CdnPublishException("R2 GET returned ${response.status} for key '$key' (bucket=$bucketName)")
        }
        return response.bodyAsText()
    }

    /**
     * Writes [value] under [key] in the R2 bucket.
     * Pass [metadataJson] as `{"bundleVersion":"..."}` to store the version in object metadata.
     * The [expirationTtl] parameter is accepted for API compatibility but ignored — configure
     * Object Lifecycle Rules on the R2 bucket to expire old versioned bundles instead.
     */
    suspend fun put(key: String, value: String, metadataJson: String? = null, @Suppress("UNUSED_PARAMETER") expirationTtl: Int? = null): Boolean {
        val bodyBytes = value.toByteArray(Charsets.UTF_8)
        val bodyHash = sha256(bodyBytes)
        val now = Instant.now()
        val amzDate = DATE_TIME_FMT.format(now)
        val dateStamp = DATE_FMT.format(now)
        val path = "/$bucketName/$key"
        val isJson = metadataJson != null
        val contentType = if (isJson) "application/json; charset=utf-8" else "text/plain; charset=utf-8"
        val bundleVersion = metadataJson?.let { extractBundleVersion(it) }

        val signedHeaders = treeMapOf(
            "content-type" to contentType,
            "host" to host,
            "x-amz-content-sha256" to bodyHash,
            "x-amz-date" to amzDate
        )
        if (bundleVersion != null) signedHeaders["x-amz-meta-bundle-version"] = bundleVersion

        val auth = sign("PUT", path, bodyHash, signedHeaders, dateStamp, amzDate)

        val response: HttpResponse = http.put("https://$host$path") {
            header("x-amz-date", amzDate)
            header("x-amz-content-sha256", bodyHash)
            if (bundleVersion != null) header("x-amz-meta-bundle-version", bundleVersion)
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.parse(contentType))
            setBody(bodyBytes)
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "" }
            log.error("R2 PUT failed: host={} bucket={} key={} status={} contentType={} bodyLen={} respBody={}", host, bucketName, key, response.status, contentType, bodyBytes.size, body)
            throw CdnPublishException("R2 PUT returned ${response.status} for key '$key' (bucket=$bucketName): $body")
        }
        return true
    }

    /**
     * Deletes the object at [key]. No-ops silently if the key does not exist.
     */
    suspend fun delete(key: String) {
        val now = Instant.now()
        val amzDate = DATE_TIME_FMT.format(now)
        val dateStamp = DATE_FMT.format(now)
        val path = "/$bucketName/$key"

        val signedHeaders = treeMapOf(
            "host" to host,
            "x-amz-content-sha256" to EMPTY_HASH,
            "x-amz-date" to amzDate
        )
        val auth = sign("DELETE", path, EMPTY_HASH, signedHeaders, dateStamp, amzDate)

        val response: HttpResponse = http.delete("https://$host$path") {
            header("x-amz-date", amzDate)
            header("x-amz-content-sha256", EMPTY_HASH)
            header(HttpHeaders.Authorization, auth)
        }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
            log.warn("R2 DELETE non-fatal: key={} status={}", key, response.status)
        }
    }

    override fun close() = http.close()

    private fun sign(
        method: String,
        path: String,
        payloadHash: String,
        headers: TreeMap<String, String>,
        dateStamp: String,
        amzDate: String
    ): String {
        // Canonical headers: each entry lowercased, trimmed, ending with \n
        val canonicalHeaders = headers.entries.joinToString("\n") { "${it.key}:${it.value.trim()}" } + "\n"
        val signedHeaderNames = headers.keys.joinToString(";")

        val canonicalRequest = "$method\n$path\n\n$canonicalHeaders\n$signedHeaderNames\n$payloadHash"
        val credScope = "$dateStamp/auto/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credScope\n${sha256(canonicalRequest.toByteArray())}"

        val sigKey = hmac(hmac(hmac(hmac("AWS4$secretAccessKey".toByteArray(), dateStamp), "auto"), "s3"), "aws4_request")
        val signature = hmac(sigKey, stringToSign).joinToString("") { "%02x".format(it) }
        return "AWS4-HMAC-SHA256 Credential=$accessKeyId/$credScope, SignedHeaders=$signedHeaderNames, Signature=$signature"
    }

    companion object {
        private const val EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        private fun treeMapOf(vararg pairs: Pair<String, String>) = TreeMap<String, String>().also { m -> pairs.forEach { (k, v) -> m[k] = v } }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun hmac(key: ByteArray, data: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data.toByteArray(Charsets.UTF_8))
        }

        private fun extractBundleVersion(metadataJson: String): String? = runCatching {
            val el = Json.parseToJsonElement(metadataJson)
            ((el as? JsonObject)?.get("bundleVersion") as? JsonPrimitive)?.content
        }.getOrNull()
    }
}

class CdnPublishException(message: String, cause: Throwable? = null) : Exception(message, cause)
