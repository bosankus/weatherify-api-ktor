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
// Razorpay requires total_count >= 1. 120 monthly cycles = 10 years, effectively indefinite.
private const val SUBSCRIPTION_CYCLES = 120

/**
 * Init payload returned to the branded checkout page so Checkout.js can open
 * a hosted modal on our domain instead of redirecting to Razorpay's short_url.
 */
data class SubscriptionInit(
    val subscriptionId: String,
    val keyId: String,
    val plan: BillingPlan
)

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

    val publicKeyId: String get() = keyId

    // ─── RazorpayEventHandler ─────────────────────────────────────────────────

    override val eventPrefixes = listOf("subscription.")

    override suspend fun handle(eventType: String, event: JsonObject): WebhookResult =
        when (eventType) {
            // Card saved for trial — upgrade the plan immediately so the user's dashboard
            // reflects the correct plan during the 60-day trial before any charge fires.
            "subscription.authenticated" -> handleSubscriptionAuthenticated(event)
            "subscription.activated", "subscription.charged" -> handleSubscriptionCharged(event)
            "subscription.cancelled", "subscription.completed" -> handleSubscriptionEnded(event)
            "subscription.halted" -> {
                val subId = subEntity(event)?.get("id")?.jsonPrimitive?.contentOrNull
                log.warn("Subscription halted: {}", subId)
                WebhookResult.Ok
            }
            else -> WebhookResult.Skipped("Unhandled subscription event: $eventType")
        }

    // ─── Subscription creation (authenticated only) ───────────────────────────

    /**
     * Creates a Razorpay subscription for an already-known userId and pre-records the
     * subscription against the user so webhooks land on the right account even before
     * the first charge fires. Returns the IDs the embedded Checkout.js modal needs.
     */
    suspend fun createSubscriptionForUser(userId: String, plan: BillingPlan): SubscriptionInit {
        require(plan != BillingPlan.FREE && plan != BillingPlan.ENTERPRISE) {
            "Cannot create Razorpay subscription for plan ${plan.name}"
        }
        val planId = plan.razorpayPlanId()
            ?: throw IllegalArgumentException("No Razorpay plan ID configured for ${plan.name}")

        val startAt = Clock.System.now().epochSeconds + (TRIAL_DAYS * 24 * 3600)
        val body = buildJsonObject {
            put("plan_id", planId)
            put("total_count", SUBSCRIPTION_CYCLES)
            put("quantity", 1)
            put("customer_notify", 1)
            put("start_at", startAt)
            put("notes", buildJsonObject {
                put("plan", plan.name)
                put("userId", userId)
                put("flow", "authenticated")
            })
        }
        val response = withContext(Dispatchers.IO) {
            http.post("$RAZORPAY_API/subscriptions") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.body<JsonObject>()
        }
        val subscriptionId = response["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Razorpay did not return subscription id: $response")

        // Do NOT upgrade plan here — wait for subscription.authenticated webhook (card saved).
        // userId is embedded in notes so the webhook handler can resolve the user without a DB lookup.
        log.info("Created Razorpay subscription {} for userId={} plan={}", subscriptionId, userId, plan.name)
        return SubscriptionInit(subscriptionId, keyId, plan)
    }

    // ─── Checkout.js handler signature verification ───────────────────────────

    /**
     * Verifies the signature the embedded Checkout.js modal returns in its `handler`
     * callback after a successful payment. Formula:
     *   HMAC-SHA256("$paymentId|$subscriptionId", keySecret) == signature
     * For trial-only subscriptions (no payment yet), Checkout.js still produces a payment
     * id for the first auth-charge of ₹0 / ₹1, so this single path covers both.
     */
    fun verifyCheckoutHandler(paymentId: String, subscriptionId: String, signature: String): Boolean {
        val expected = hmac256("$paymentId|$subscriptionId", keySecret)
        return timingSafeEquals(expected, signature)
    }

    /** Resolves the userId that owns this subscription (set at create time). */
    suspend fun ownerOf(subscriptionId: String): String? =
        billingRepository.findByRazorpaySubscription(subscriptionId)

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

    private suspend fun handleSubscriptionAuthenticated(event: JsonObject): WebhookResult {
        val sub = subEntity(event) ?: return WebhookResult.Skipped("No subscription entity")
        val subscriptionId = sub["id"]?.jsonPrimitive?.contentOrNull ?: return WebhookResult.Skipped("No sub ID")
        val notes = sub["notes"]?.jsonObject
        val userId = notes?.get("userId")?.jsonPrimitive?.contentOrNull
            ?: billingRepository.findByRazorpaySubscription(subscriptionId)
            ?: return WebhookResult.Skipped("No userId for authenticated sub=$subscriptionId")
        val plan = runCatching { BillingPlan.valueOf(notes?.get("plan")?.jsonPrimitive?.contentOrNull ?: "") }
            .getOrDefault(BillingPlan.SOLO)
        billingRepository.upsertSubscription(
            userId = userId, plan = plan,
            razorpayCustomerId = sub["customer_id"]?.jsonPrimitive?.contentOrNull,
            razorpaySubscriptionId = subscriptionId
        )
        log.info("Subscription authenticated (card saved): userId={} plan={} sub={}", userId, plan.name, subscriptionId)
        return WebhookResult.Ok
    }

    private suspend fun handleSubscriptionCharged(event: JsonObject): WebhookResult {
        val sub = subEntity(event) ?: return WebhookResult.Skipped("No subscription entity")
        val payment = event["payload"]?.jsonObject?.get("payment")?.jsonObject?.get("entity")?.jsonObject

        val subscriptionId = sub["id"]?.jsonPrimitive?.contentOrNull ?: return WebhookResult.Skipped("No sub ID")
        val notes = sub["notes"]?.jsonObject

        val userId = notes?.get("userId")?.jsonPrimitive?.contentOrNull
            ?: billingRepository.findByRazorpaySubscription(subscriptionId)
            ?: return WebhookResult.Skipped("No userId for sub=$subscriptionId")

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
