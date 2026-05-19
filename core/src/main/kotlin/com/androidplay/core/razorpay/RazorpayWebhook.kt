package com.androidplay.core.razorpay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ─── Result type ──────────────────────────────────────────────────────────────

sealed class WebhookResult {
    object Ok : WebhookResult()
    data class Error(val message: String) : WebhookResult()
    data class Skipped(val reason: String) : WebhookResult()
}

// ─── Handler interface ────────────────────────────────────────────────────────

interface RazorpayEventHandler {
    /** Prefixes of Razorpay event types to receive (e.g. "subscription."). */
    val eventPrefixes: List<String>

    /** Called after signature verification. */
    suspend fun handle(eventType: String, event: JsonObject): WebhookResult
}

// ─── Central dispatcher ───────────────────────────────────────────────────────

/** Verifies the HMAC-SHA256 signature once and fans out to every matching [RazorpayEventHandler]. */
class RazorpayWebhookDispatcher(
    private val webhookSecret: String,
    private val handlers: List<RazorpayEventHandler>
) {
    private val log = LoggerFactory.getLogger(RazorpayWebhookDispatcher::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dispatch(payload: ByteArray, signatureHeader: String?): WebhookResult {
        if (signatureHeader == null) return WebhookResult.Error("Missing X-Razorpay-Signature header")

        val bodyString = payload.toString(Charsets.UTF_8)
        val expected = hmac256(bodyString, webhookSecret)
        if (!timingSafeEquals(expected, signatureHeader)) {
            return WebhookResult.Error("Invalid Razorpay webhook signature")
        }

        val event = try {
            json.parseToJsonElement(bodyString).jsonObject
        } catch (e: Exception) {
            return WebhookResult.Error("Failed to parse webhook body: ${e.message}")
        }

        val eventType = event["event"]?.jsonPrimitive?.content
            ?: return WebhookResult.Skipped("No event type in payload")

        log.info("Razorpay webhook dispatching: {}", eventType)

        val matching = handlers.filter { h -> h.eventPrefixes.any { eventType.startsWith(it) } }

        if (matching.isEmpty()) {
            log.debug("No handler registered for event type: {}", eventType)
            return WebhookResult.Ok
        }

        val errors = mutableListOf<String>()
        for (handler in matching) {
            when (val result = handler.handle(eventType, event)) {
                is WebhookResult.Error   -> {
                    log.warn("Handler {} error on {}: {}", handler::class.simpleName, eventType, result.message)
                    errors += "${handler::class.simpleName}: ${result.message}"
                }
                is WebhookResult.Skipped -> log.debug("Handler {} skipped: {}", handler::class.simpleName, result.reason)
                is WebhookResult.Ok      -> {}
            }
        }
        return if (errors.isEmpty()) WebhookResult.Ok else WebhookResult.Error(errors.joinToString("; "))
    }

    private fun hmac256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun timingSafeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
