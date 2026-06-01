package com.syncling.services

import com.androidplay.core.razorpay.RazorpayEventHandler
import com.androidplay.core.razorpay.WebhookResult
import com.androidplay.core.secrets.getSecretValue
import com.syncling.domain.BillingPlan
import com.syncling.domain.UserEvent
import com.syncling.repository.BillingRepository
import com.syncling.repository.UserRepository
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.days

private const val RAZORPAY_API = "https://api.razorpay.com/v1"
private const val TRIAL_DAYS = 7L
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
    private val billingRepository: BillingRepository,
    private val userActivityService: UserActivityService? = null,
    private val userRepository: UserRepository? = null,
    private val notificationService: NotificationService? = null,
    private val inAppNotificationService: InAppNotificationService? = null
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
            // reflects the correct plan during the 7-day trial before any charge fires.
            "subscription.authenticated" -> handleSubscriptionAuthenticated(event)
            "subscription.activated", "subscription.charged" -> handleSubscriptionCharged(event)
            "subscription.cancelled", "subscription.completed" -> handleSubscriptionEnded(event)
            "subscription.halted" -> {
                val sub = subEntity(event)
                val subId = sub?.get("id")?.jsonPrimitive?.contentOrNull
                log.warn("Subscription halted: {}", subId)
                val haltedPlan = sub?.get("notes")?.jsonObject?.get("plan")?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { BillingPlan.valueOf(it) }.getOrNull() }
                val userId = subId?.let { billingRepository.findByRazorpaySubscription(it) }
                userId?.let { uid ->
                    runCatching {
                        val user = userRepository?.findById(uid)
                        val plan = haltedPlan ?: BillingPlan.SOLO
                        if (user != null) notificationService?.sendPaymentFailed(user, plan)
                        inAppNotificationService?.notifyPaymentFailed(uid, plan.displayName)
                    }.onFailure { log.warn("Payment-failed notification error userId={}: {}", uid, it.message) }
                }
                WebhookResult.Ok
            }
            else -> WebhookResult.Skipped("Unhandled subscription event: $eventType")
        }

    // ─── Subscription creation (authenticated only) ───────────────────────────

    suspend fun createSubscriptionForUser(userId: String, plan: BillingPlan): SubscriptionInit {
        require(plan != BillingPlan.FREE && plan != BillingPlan.ENTERPRISE) {
            "Cannot create Razorpay subscription for plan ${plan.name}"
        }

        // Idempotency: if a subscription was already created for this exact plan and is still
        // in a state that accepts checkout (created/authenticated), reuse it. Otherwise fall
        // through to create a fresh one — stale subscriptions (halted, cancelled, completed,
        // active) cause a 400 on Razorpay's preferences endpoint.
        val existing = billingRepository.getSubscription(userId)
        if (existing.pendingPlan == plan && existing.razorpaySubscriptionId != null) {
            val status = fetchSubscriptionStatus(existing.razorpaySubscriptionId)
            if (status == "created" || status == "authenticated") {
                log.info("Returning existing pending subscription {} (status={}) for userId={} plan={}",
                    existing.razorpaySubscriptionId, status, userId, plan.name)
                return SubscriptionInit(existing.razorpaySubscriptionId, keyId, plan)
            }
            log.info("Cached subscription {} is stale (status={}), creating new one for userId={} plan={}",
                existing.razorpaySubscriptionId, status, userId, plan.name)
        }

        val planId = plan.razorpayPlanId()
            ?: throw IllegalArgumentException("No Razorpay plan ID configured for ${plan.name}")

        // Trial eligibility: only first-time subscribers within their first 7 days on FREE
        // get the 7-day free trial. Anyone who has already consumed a trial, or has been on
        // the free plan for more than 7 days, is billed immediately instead.
        val now = Clock.System.now()
        val signupAge = existing.startedAt?.let { now - it }
        val trialEligible = existing.trialStartedAt == null &&
            (signupAge == null || signupAge <= TRIAL_DAYS.days)
        val startAt = if (trialEligible) {
            now.epochSeconds + (TRIAL_DAYS * 24 * 3600)
        } else {
            now.epochSeconds + 60 // small buffer so Razorpay doesn't reject as past
        }
        log.info("createSubscriptionForUser userId={} plan={} trialEligible={} (priorTrial={}, signupAgeDays={})",
            userId, plan.name, trialEligible, existing.trialStartedAt != null, signupAge?.inWholeDays)
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

        // Pre-record the subscription so webhook fallback lookup works even if notes parsing fails.
        // Store pendingPlan so confirm-payment can immediately activate it without waiting for the webhook.
        val currentSub = billingRepository.getSubscription(userId)
        billingRepository.upsertSubscription(
            userId = userId, plan = currentSub.plan,
            razorpaySubscriptionId = subscriptionId,
            pendingPlan = plan
        )
        if (trialEligible) billingRepository.markTrialStarted(userId, now)
        log.info("Created Razorpay subscription {} for userId={} plan={}", subscriptionId, userId, plan.name)
        return SubscriptionInit(subscriptionId, keyId, plan)
    }

    // ─── Checkout.js handler signature verification ───────────────────────────

    // HMAC-SHA256("$paymentId|$subscriptionId", keySecret) == signature
    fun verifyCheckoutHandler(paymentId: String, subscriptionId: String, signature: String): Boolean {
        val expected = hmac256("$paymentId|$subscriptionId", keySecret)
        return timingSafeEquals(expected, signature)
    }

    suspend fun ownerOf(subscriptionId: String): String? =
        billingRepository.findByRazorpaySubscription(subscriptionId)

    // ─── Activate trial subscription immediately ───────────────────────────────

    suspend fun activateNow(userId: String) {
        val sub = billingRepository.getSubscription(userId)
        val subscriptionId = sub.razorpaySubscriptionId
            ?: throw IllegalStateException("No active subscription found for userId=$userId")
        if (!sub.inTrial) throw IllegalStateException("Subscription is not in trial for userId=$userId")
        val nowEpoch = Clock.System.now().epochSeconds
        withContext(Dispatchers.IO) {
            http.patch("$RAZORPAY_API/subscriptions/$subscriptionId") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody("""{"start_at":$nowEpoch}""")
            }
        }
        // Optimistically mark billing as started so the UI immediately reflects the
        // non-trial state without waiting for the subscription.charged webhook.
        val estimatedPeriodEnd = Clock.System.now() + 30.days
        billingRepository.upsertSubscription(
            userId = userId,
            plan = sub.plan,
            currentPeriodEnd = estimatedPeriodEnd
        )
        billingRepository.setLimitHitAt(userId, null)
        log.info("Trial activated immediately for userId={} sub={}", userId, subscriptionId)
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
        userActivityService?.record(
            userId, UserEvent.SUBSCRIPTION_AUTHENTICATED,
            mapOf("plan" to plan.name, "subscriptionId" to subscriptionId)
        )
        // Notify user their trial has started
        runCatching {
            val user = userRepository?.findById(userId)
            val sub = billingRepository.getSubscription(userId)
            val trialEnd = sub.trialStartedAt?.let {
                (it + 7.days).toLocalDateTime(TimeZone.UTC).date.toString()
            } ?: "in 7 days"
            if (user != null) notificationService?.sendTrialStarted(user, plan, trialEnd)
            inAppNotificationService?.notifyTrialStarted(userId, plan.displayName, trialEnd)
        }.onFailure { log.warn("Trial-started notification failed userId={}: {}", userId, it.message) }
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
        // Clear any stale pendingPlan now that the subscription is definitively active.
        billingRepository.clearPendingPlan(userId)

        if (payment != null) {
            val paymentId = payment["id"]?.jsonPrimitive?.contentOrNull
            if (paymentId != null) {
                val amount = payment["amount"]?.jsonPrimitive?.intOrNull ?: 0
                billingRepository.insertInvoice(
                    userId = userId, razorpayPaymentId = paymentId,
                    amountPaise = amount,
                    currency = payment["currency"]?.jsonPrimitive?.contentOrNull ?: "INR",
                    status = payment["status"]?.jsonPrimitive?.contentOrNull ?: "captured",
                    periodEnd = currentEnd ?: Clock.System.now()
                )
                userActivityService?.record(
                    userId, UserEvent.INVOICE_GENERATED,
                    mapOf(
                        "paymentId" to paymentId,
                        "amountPaise" to amount.toString(),
                        "plan" to plan.name
                    )
                )
            }
        }
        userActivityService?.record(
            userId, UserEvent.SUBSCRIPTION_CHARGED,
            mapOf("plan" to plan.name, "subscriptionId" to subscriptionId)
        )
        // Notify user about successful charge (skip first charge — that's the trial-started email)
        if (payment != null) {
            runCatching {
                val user = userRepository?.findById(userId)
                val amount = payment["amount"]?.jsonPrimitive?.intOrNull
                    ?.let { "₹${"%.2f".format(it / 100.0)}" } ?: "—"
                val periodEndStr = currentEnd?.toLocalDateTime(TimeZone.UTC)?.date?.toString() ?: "—"
                if (user != null) notificationService?.sendPaymentReceived(user, plan, amount, periodEndStr)
                inAppNotificationService?.notifyPaymentReceived(userId, amount, plan.displayName)
            }.onFailure { log.warn("Payment-received notification failed userId={}: {}", userId, it.message) }
        }
        log.info("Subscription charged: userId={} plan={} sub={}", userId, plan.name, subscriptionId)
        return WebhookResult.Ok
    }

    private suspend fun handleSubscriptionEnded(event: JsonObject): WebhookResult {
        val sub = subEntity(event)
        val subscriptionId = sub?.get("id")?.jsonPrimitive?.contentOrNull
            ?: return WebhookResult.Skipped("No subscription ID")
        val endedPlan = sub["notes"]?.jsonObject?.get("plan")?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { BillingPlan.valueOf(it) }.getOrNull() }
        val userId = billingRepository.findByRazorpaySubscription(subscriptionId)
        billingRepository.downgradeToFree(subscriptionId)
        userId?.let {
            userActivityService?.record(
                it, UserEvent.SUBSCRIPTION_CANCELLED,
                mapOf("subscriptionId" to subscriptionId, "source" to "webhook")
            )
            runCatching {
                val user = userRepository?.findById(it)
                val plan = endedPlan ?: BillingPlan.SOLO
                if (user != null) notificationService?.sendSubscriptionEnded(user, plan)
                inAppNotificationService?.notifySubscriptionEnded(it)
            }.onFailure { e -> log.warn("Subscription-ended notification failed userId={}: {}", it, e.message) }
        }
        log.info("Subscription ended, downgraded to free: sub={}", subscriptionId)
        return WebhookResult.Ok
    }

    private suspend fun fetchSubscriptionStatus(subscriptionId: String): String? = runCatching {
        withContext(Dispatchers.IO) {
            http.get("$RAZORPAY_API/subscriptions/$subscriptionId") {
                header(HttpHeaders.Authorization, authHeader)
            }.body<JsonObject>()["status"]?.jsonPrimitive?.contentOrNull
        }
    }.onFailure { log.warn("Could not fetch subscription status for {}: {}", subscriptionId, it.message) }
        .getOrNull()

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

    /**
     * Fetches captured payments for [subscriptionId] from the Razorpay API and inserts any that
     * are missing from our invoice_records collection. Safe to call repeatedly — the unique index
     * on razorpayPaymentId silently drops duplicates. Used as a fallback when the subscription.charged
     * webhook was missed (delivery failure, server restart during trial expiry, etc.).
     */
    suspend fun syncSubscriptionInvoices(userId: String, subscriptionId: String) {
        val response = withContext(Dispatchers.IO) {
            http.get("$RAZORPAY_API/payments?subscription_id=$subscriptionId&count=50") {
                header(HttpHeaders.Authorization, authHeader)
            }.body<JsonObject>()
        }
        val items = response["items"]?.jsonArray ?: run {
            log.warn("syncSubscriptionInvoices: no items in response for sub={}", subscriptionId)
            return
        }
        var synced = 0
        for (item in items) {
            val p = item.jsonObject
            val paymentId = p["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val status = p["status"]?.jsonPrimitive?.contentOrNull ?: continue
            if (status != "captured") continue
            val amount = p["amount"]?.jsonPrimitive?.intOrNull ?: 0
            val currency = p["currency"]?.jsonPrimitive?.contentOrNull ?: "INR"
            val createdAt = p["created_at"]?.jsonPrimitive?.longOrNull
                ?.let { Instant.fromEpochSeconds(it) } ?: Clock.System.now()
            billingRepository.insertInvoice(
                userId = userId,
                razorpayPaymentId = paymentId,
                amountPaise = amount,
                currency = currency,
                status = status,
                periodEnd = createdAt + 30.days
            )
            synced++
        }
        if (synced > 0) log.info("syncSubscriptionInvoices: backfilled {} invoice(s) for userId={} sub={}", synced, userId, subscriptionId)
    }

    fun close() = http.close()
}
