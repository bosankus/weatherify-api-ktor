package com.transloom.services

import com.androidplay.core.razorpay.RazorpayEventHandler
import com.androidplay.core.razorpay.WebhookResult
import com.androidplay.core.secrets.getSecretValue
import com.transloom.domain.BillingPlan
import com.transloom.repository.BillingRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val RAZORPAY_API = "https://api.razorpay.com/v1"
private const val TRIAL_DAYS = 60L

class RazorpayBillingService(
    private val billingRepository: BillingRepository
) : RazorpayEventHandler {

    private val log = LoggerFactory.getLogger(RazorpayBillingService::class.java)

    private val keyId = getSecretValue("razorpay-key-id")
    private val keySecret = getSecretValue("razorpay-secret")

    private val authHeader: String by lazy {
        "Basic ${Base64.getEncoder().encodeToString("$keyId:$keySecret".toByteArray(Charsets.UTF_8))}"
    }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ─── RazorpayEventHandler ─────────────────────────────────────────────────

    override val eventPrefixes = listOf("subscription.")

    override suspend fun handle(eventType: String, event: JsonObject): WebhookResult =
        when (eventType) {
            "subscription.activated", "subscription.charged" -> handleSubscriptionCharged(event)
            "subscription.cancelled", "subscription.completed" -> handleSubscriptionEnded(event)
            "subscription.halted" -> {
                val subId = subEntity(event)?.get("id")?.jsonPrimitive?.contentOrNull
                log.warn("Subscription halted: {}", subId)
                WebhookResult.Ok
            }
            else -> WebhookResult.Skipped("Unhandled subscription event: $eventType")
        }

    // ─── Subscription creation ─────────────────────────────────────────────────

    suspend fun createAnonymousSubscription(plan: BillingPlan): String {
        val planId = plan.razorpayPlanId()
            ?: throw IllegalArgumentException("No Razorpay plan ID configured for ${plan.name}")
        return createSubscription(
            planId = planId,
            notes = buildJsonObject { put("plan", plan.name); put("flow", "anonymous") }
        )
    }

    suspend fun createAuthenticatedSubscription(userId: String, plan: BillingPlan): String {
        val planId = plan.razorpayPlanId()
            ?: throw IllegalArgumentException("No Razorpay plan ID configured for ${plan.name}")
        return createSubscription(
            planId = planId,
            notes = buildJsonObject { put("plan", plan.name); put("userId", userId); put("flow", "authenticated") }
        )
    }

    private suspend fun createSubscription(planId: String, notes: JsonObject): String {
        val startAt = Clock.System.now().epochSeconds + (TRIAL_DAYS * 24 * 3600)
        val body = buildJsonObject {
            put("plan_id", planId)
            put("total_count", 0)
            put("quantity", 1)
            put("customer_notify", 1)
            put("start_at", startAt)
            put("notes", notes)
        }
        val response = withContext(Dispatchers.IO) {
            http.post("$RAZORPAY_API/subscriptions") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.body<JsonObject>()
        }
        return response["short_url"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Razorpay did not return short_url: $response")
    }

    // ─── Anonymous linking ─────────────────────────────────────────────────────

    suspend fun linkAnonymousSubscription(userId: String, subscriptionId: String, planName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val plan = runCatching { BillingPlan.valueOf(planName.uppercase()) }.getOrElse {
                    log.warn("Invalid plan '{}' for subscription {}", planName, subscriptionId)
                    return@withContext false
                }
                val sub = fetchSubscription(subscriptionId) ?: return@withContext false
                billingRepository.upsertSubscription(
                    userId = userId, plan = plan,
                    razorpayCustomerId = sub["customer_id"]?.jsonPrimitive?.contentOrNull,
                    razorpaySubscriptionId = subscriptionId
                )
                log.info("Linked subscription {} → userId={} plan={}", subscriptionId, userId, plan.name)
                true
            } catch (e: Exception) {
                log.error("Failed to link subscription {}: {}", subscriptionId, e.message, e)
                false
            }
        }
    }

    // ─── Callback signature verification ──────────────────────────────────────

    /**
     * Verifies the signature Razorpay sends to the callback_url after card auth.
     *  - Normal case (immediate payment): HMAC-SHA256(payment_id|subscription_id, key_secret)
     *  - Trial case (no payment yet):     no signature exists, so we fall back to a direct
     *    server-side lookup via Razorpay's API to prove the subscription is real and ours.
     *    This blocks attackers from forging callback URLs with arbitrary sub IDs.
     */
    suspend fun verifyCallbackSignature(paymentId: String?, subscriptionId: String, signature: String?): Boolean {
        if (!paymentId.isNullOrBlank() && !signature.isNullOrBlank()) {
            val expected = hmac256("$paymentId|$subscriptionId", keySecret)
            return timingSafeEquals(expected, signature)
        }
        // Trial path: confirm the subscription actually exists in our Razorpay account and is
        // in a status consistent with "card just authenticated". Anything else is rejected.
        val sub = fetchSubscription(subscriptionId)
        if (sub == null) {
            log.warn("Trial callback rejected — sub {} not found in Razorpay", subscriptionId)
            return false
        }
        val status = sub["status"]?.jsonPrimitive?.contentOrNull
        val ok = status in setOf("authenticated", "active", "created")
        if (!ok) log.warn("Trial callback rejected — sub {} has unexpected status={}", subscriptionId, status)
        return ok
    }

    // ─── Cancel subscription ───────────────────────────────────────────────────

    suspend fun cancelSubscription(userId: String) {
        val sub = billingRepository.getSubscription(userId)
        val subscriptionId = sub.razorpaySubscriptionId
            ?: throw IllegalStateException("No active subscription found")
        withContext(Dispatchers.IO) {
            http.post("$RAZORPAY_API/subscriptions/$subscriptionId/cancel") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody("""{"cancel_at_cycle_end":1}""")
            }
        }
        billingRepository.upsertSubscription(userId, sub.plan, cancelAtPeriodEnd = true)
        log.info("Subscription cancel-at-period-end set for userId={}", userId)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private suspend fun handleSubscriptionCharged(event: JsonObject): WebhookResult {
        val sub = subEntity(event) ?: return WebhookResult.Skipped("No subscription entity")
        val payment = event["payload"]?.jsonObject?.get("payment")?.jsonObject?.get("entity")?.jsonObject

        val subscriptionId = sub["id"]?.jsonPrimitive?.contentOrNull ?: return WebhookResult.Skipped("No sub ID")
        val notes = sub["notes"]?.jsonObject

        if (notes?.get("flow")?.jsonPrimitive?.contentOrNull == "anonymous") {
            return WebhookResult.Skipped("anonymous flow — will be linked during OAuth callback")
        }

        val userId = notes?.get("userId")?.jsonPrimitive?.contentOrNull
            ?: billingRepository.findByRazorpaySubscription(subscriptionId)
            ?: return WebhookResult.Skipped("No userId for sub=$subscriptionId (pre-OAuth)")

        val plan = runCatching { BillingPlan.valueOf(notes?.get("plan")?.jsonPrimitive?.contentOrNull ?: "") }
            .getOrDefault(BillingPlan.SOLO)
        val currentEnd = sub["current_end"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }

        billingRepository.upsertSubscription(
            userId = userId, plan = plan,
            razorpayCustomerId = sub["customer_id"]?.jsonPrimitive?.contentOrNull,
            razorpaySubscriptionId = subscriptionId,
            currentPeriodEnd = currentEnd
        )

        if (payment != null) {
            val paymentId = payment["id"]?.jsonPrimitive?.contentOrNull
            if (paymentId != null) {
                billingRepository.insertInvoice(
                    userId = userId, razorpayPaymentId = paymentId,
                    amountPaise = payment["amount"]?.jsonPrimitive?.intOrNull ?: 0,
                    currency = payment["currency"]?.jsonPrimitive?.contentOrNull ?: "INR",
                    status = payment["status"]?.jsonPrimitive?.contentOrNull ?: "captured",
                    periodEnd = currentEnd ?: Clock.System.now()
                )
            }
        }
        log.info("Subscription charged: userId={} plan={} sub={}", userId, plan.name, subscriptionId)
        return WebhookResult.Ok
    }

    private suspend fun handleSubscriptionEnded(event: JsonObject): WebhookResult {
        val subscriptionId = subEntity(event)?.get("id")?.jsonPrimitive?.contentOrNull
            ?: return WebhookResult.Skipped("No subscription ID")
        billingRepository.downgradeToFree(subscriptionId)
        log.info("Subscription ended, downgraded to free: sub={}", subscriptionId)
        return WebhookResult.Ok
    }

    private suspend fun fetchSubscription(subscriptionId: String): JsonObject? = try {
        withContext(Dispatchers.IO) {
            http.get("$RAZORPAY_API/subscriptions/$subscriptionId") {
                header(HttpHeaders.Authorization, authHeader)
            }.body<JsonObject>()
        }
    } catch (e: Exception) {
        log.error("Failed to fetch subscription {}: {}", subscriptionId, e.message)
        null
    }

    private fun subEntity(event: JsonObject): JsonObject? =
        event["payload"]?.jsonObject?.get("subscription")?.jsonObject?.get("entity")?.jsonObject

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

    fun close() = http.close()
}
