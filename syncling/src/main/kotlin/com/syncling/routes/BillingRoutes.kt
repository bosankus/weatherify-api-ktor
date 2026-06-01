package com.syncling.routes

import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.razorpay.WebhookResult
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.syncling.domain.BillingPlan
import com.syncling.domain.Subscription
import com.syncling.domain.UserEvent
import com.syncling.repository.BillingRepository
import com.syncling.repository.UserRepository
import com.syncling.model.ApiError
import com.syncling.services.InvoicePdfGenerator
import com.syncling.services.RazorpayBillingService
import com.syncling.services.SubscriptionInit
import com.syncling.services.UserActivityService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("BillingRoutes")

internal const val SESSION_COOKIE = "tl_session"
internal const val PENDING_PLAN_COOKIE = "pending_plan"

@Serializable data class SubscribePlanBody(val plan: String = "SOLO")
@Serializable data class ConfirmPaymentBody(val paymentId: String = "", val subscriptionId: String = "", val signature: String = "")
@Serializable data class SubscribeResponse(val subscriptionId: String, val keyId: String, val plan: String)
@Serializable data class ConfirmPaymentResponse(val verified: Boolean, val paymentId: String, val plan: String? = null, val displayName: String? = null)

@Serializable
data class SubscriptionResponse(
    val plan: String,
    val displayName: String,
    val monthlyPricePaise: Int?,
    val stringLimit: Int?,
    val maxProjects: Int,
    /** Max invitable teammates per project (excluding OWNER); -1 means unlimited. */
    val maxMembers: Int,
    val cancelAtPeriodEnd: Boolean,
    val currentPeriodEnd: String?,
    val trialLimitHit: Boolean,
    /** True when the user is in the 7-day free trial (paid plan, no first charge yet). */
    val inTrial: Boolean,
    /** Approximate date the trial ends (startedAt + 7 days), or null if not in trial. */
    val trialEndsOn: String?,
    /** Whole days until renewal or cancellation date; null when no active period. */
    val daysUntilRenewal: Int?
)

@Serializable
data class InvoiceResponse(
    val id: String,
    val date: String,
    val amount: String,
    val currency: String,
    val status: String
)

@Serializable
data class HistoryEntry(val month: String, val count: Int)

@Serializable
data class UsageResponse(
    val stringsTranslated: Int,
    val stringLimit: Int?,
    val projectsUsed: Int,
    val projectLimit: Int?,
    val history: List<HistoryEntry>
)

// ─── Authenticated billing routes ─────────────────────────────────────────────

fun Route.configureBillingRoutes(
    razorpayService: RazorpayBillingService,
    billingRepository: BillingRepository,
    userRepository: UserRepository,
    jwtSecret: String,
    userActivityService: UserActivityService
) {
    route("/api/billing") {

        get("/subscription") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.respond(billingRepository.getSubscription(userId).toResponse())
        }

        post("/subscribe") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val body = runCatching { call.receive<SubscribePlanBody>() }.getOrElse { SubscribePlanBody() }
            val plan = runCatching { BillingPlan.valueOf(body.plan.uppercase()) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Unknown plan: ${body.plan}"))
            }
            if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Use SOLO or TEAM for subscription"))
            }
            val init = runCatching { razorpayService.createSubscriptionForUser(userId, plan) }.getOrElse {
                log.error("Subscription creation failed for userId={}: {}", userId, it.message)
                return@post call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "Razorpay error"))
            }
            call.application.launch {
                userActivityService.record(
                    userId, UserEvent.SUBSCRIPTION_INITIATED,
                    mapOf("plan" to plan.name, "subscriptionId" to init.subscriptionId, "flow" to "authenticated")
                )
            }
            call.respond(SubscribeResponse(init.subscriptionId, init.keyId, init.plan.name))
        }

        post("/activate-now") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            runCatching { razorpayService.activateNow(userId) }.getOrElse {
                log.warn("activate-now failed for userId={}: {}", userId, it.message)
                return@post call.respond(HttpStatusCode.BadRequest, ApiError(it.message ?: "Activation failed"))
            }
            call.respond(mapOf("status" to "Plan activated — billing starts now"))
        }

        post("/dismiss-limit") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            billingRepository.setLimitHitAt(userId, null)
            call.respond(mapOf("status" to "Dismissed"))
        }

        post("/cancel") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            runCatching { razorpayService.cancelSubscription(userId) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError(it.message ?: "Cancel failed"))
            }
            call.application.launch {
                userActivityService.record(userId, UserEvent.SUBSCRIPTION_CANCELLED, mapOf("source" to "user"))
            }
            call.respond(mapOf("status" to "Subscription will cancel at end of billing period"))
        }

        get("/invoices") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val sub = billingRepository.getSubscription(userId)
            var records = billingRepository.listInvoices(userId)
            // If the user is on a paid plan with an active billing period but has no stored invoices,
            // pull from Razorpay to recover from any missed subscription.charged webhooks.
            if (records.isEmpty()
                && sub.plan != BillingPlan.FREE
                && sub.currentPeriodEnd != null
                && sub.razorpaySubscriptionId != null
            ) {
                runCatching { razorpayService.syncSubscriptionInvoices(userId, sub.razorpaySubscriptionId) }
                    .onFailure { log.warn("Invoice sync failed for userId={}: {}", userId, it.message) }
                records = billingRepository.listInvoices(userId)
            }
            val invoices = records.map { inv ->
                val dateStr = inv.createdAt.toLocalDateTime(TimeZone.UTC).date.toString()
                val rupees = "₹${"%.2f".format(inv.amountPaise / 100.0)}"
                InvoiceResponse(
                    id = inv.razorpayPaymentId,
                    date = dateStr,
                    amount = rupees,
                    currency = inv.currency.uppercase(),
                    status = inv.status.replaceFirstChar { it.uppercase() }
                )
            }
            call.respond(mapOf("invoices" to invoices))
        }

        post("/confirm-payment") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val body = runCatching { call.receive<ConfirmPaymentBody>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
            }
            if (body.paymentId.isBlank() || body.subscriptionId.isBlank() || body.signature.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Missing payment fields"))
            }
            if (!razorpayService.verifyCheckoutHandler(body.paymentId, body.subscriptionId, body.signature)) {
                log.warn("confirm-payment: invalid signature for userId={} paymentId={}", userId, body.paymentId)
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ApiError("Payment signature invalid"))
            }
            val ownerId = razorpayService.ownerOf(body.subscriptionId)
            if (ownerId != null && ownerId != userId) {
                log.warn("confirm-payment: subscription ownership mismatch userId={} owner={}", userId, ownerId)
                return@post call.respond(HttpStatusCode.Forbidden, ApiError("Subscription does not belong to this account"))
            }
            // Signature is valid — immediately promote pendingPlan → active plan so the user
            // sees their upgraded tier right away without waiting for the Razorpay webhook.
            val activated = billingRepository.activatePendingPlan(userId)
            log.info("confirm-payment: verified paymentId={} userId={} activatedPlan={}", body.paymentId, userId, activated?.name)
            call.application.launch {
                userActivityService.record(
                    userId, UserEvent.PAYMENT_VERIFIED,
                    mapOf("paymentId" to body.paymentId, "subscriptionId" to body.subscriptionId)
                )
                activated?.let {
                    userActivityService.record(userId, UserEvent.PLAN_ACTIVATED, mapOf("plan" to it.name))
                }
            }
            call.respond(ConfirmPaymentResponse(
                verified = true,
                paymentId = body.paymentId,
                plan = activated?.name,
                displayName = activated?.displayName
            ))
        }

        get("/usage") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val plan = billingRepository.getSubscription(userId).plan
            val usage = billingRepository.getUsage(userId)
            val history = billingRepository.getHistoricalUsage(userId)
            call.respond(
                UsageResponse(
                    stringsTranslated = usage.stringsTranslated,
                    stringLimit = plan.stringLimit,
                    projectsUsed = usage.projectsUsed,
                    projectLimit = if (plan.maxProjects == Int.MAX_VALUE) null else plan.maxProjects,
                    history = history.map { HistoryEntry(it.yearMonth, it.stringsTranslated) }
                )
            )
        }
    }
}

// ─── Public checkout flow ─────────────────────────────────────────────────────

/**
 * Auth-first checkout: clicking "Start trial" routes through GitHub OAuth so we know
 * who the user is before they see a payment form. The branded checkout page then
 * embeds Razorpay's Checkout.js modal — no more handing the user to Razorpay's
 * hosted short_url page where they get stranded after payment.
 */
fun Route.configurePublicCheckoutRoute(
    razorpayService: RazorpayBillingService,
    userRepository: UserRepository,
    billingRepository: BillingRepository,
    jwtSecret: String,
    userActivityService: UserActivityService
) {
    get("/billing/start-subscription") {
        val planParam = call.request.queryParameters["plan"]?.uppercase()
        val plan = runCatching { BillingPlan.valueOf(planParam ?: "") }.getOrElse {
            log.warn("Invalid plan parameter: {}", planParam)
            return@get call.respondRedirect("/syncling#pricing")
        }
        if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) {
            return@get call.respondRedirect("/auth/github")
        }

        val userId = call.sessionUserId(jwtSecret)
        if (userId != null) {
            val currentSub = billingRepository.getSubscription(userId)
            if (currentSub.plan != BillingPlan.FREE) {
                // Already on a paid plan — send them to their billing dashboard.
                return@get call.respondRedirect("/billing")
            }
            return@get call.respondRedirect("/billing/checkout?plan=${plan.name}")
        }

        // Not signed in — remember the plan, send the user through GitHub OAuth.
        call.response.cookies.append(Cookie(
            name = PENDING_PLAN_COOKIE, value = plan.name,
            path = "/", maxAge = 7 * 24 * 3600,
            httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
        ))
        call.respondRedirect("/auth/github")
    }

    get("/billing/checkout") {
        val planParam = call.request.queryParameters["plan"]?.uppercase()
        val plan = runCatching { BillingPlan.valueOf(planParam ?: "") }.getOrElse {
            return@get call.respondRedirect("/syncling#pricing")
        }
        if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) {
            return@get call.respondRedirect("/syncling#pricing")
        }

        val userId = call.sessionUserId(jwtSecret)
        if (userId == null) {
            // Lost session — restart the auth dance.
            call.response.cookies.append(Cookie(
                name = PENDING_PLAN_COOKIE, value = plan.name,
                path = "/", maxAge = 900,
                httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
            ))
            return@get call.respondRedirect("/auth/github")
        }

        val user = userRepository.findById(userId)
        if (user == null) {
            log.warn("Session cookie userId={} not found in DB — clearing session", userId)
            call.clearSession()
            return@get call.respondRedirect("/auth/github")
        }

        val currentSub = billingRepository.getSubscription(userId)
        if (currentSub.plan != BillingPlan.FREE) {
            log.info("checkout: userId={} already on plan={} — redirecting to billing dashboard", userId, currentSub.plan)
            return@get call.respondRedirect("/billing")
        }

        val init = runCatching { razorpayService.createSubscriptionForUser(userId, plan) }.getOrElse {
            log.error("Subscription creation failed for userId={} plan={}: {}", userId, plan.name, it.message, it)
            return@get call.respondRedirect("/syncling#pricing?billing_error=create_failed")
        }
        call.application.launch {
            userActivityService.record(
                userId, UserEvent.SUBSCRIPTION_INITIATED,
                mapOf("plan" to plan.name, "subscriptionId" to init.subscriptionId, "flow" to "public-checkout")
            )
        }

        call.respondHtml { checkoutPage(plan, init, user.email, user.githubUsername, user.avatarUrl) }
    }

    /**
     * Razorpay Checkout.js handler hits this endpoint with paymentId, subscriptionId,
     * and signature after a successful payment. We verify signature then redirect
     * to a polished success page. The webhook handler does the heavy lifting; this
     * is just the user-facing redirect.
     */
    get("/billing/rp-callback") {
        val paymentId = call.request.queryParameters["razorpay_payment_id"]
        val subscriptionId = call.request.queryParameters["razorpay_subscription_id"]
        val signature = call.request.queryParameters["razorpay_signature"]

        if (paymentId.isNullOrBlank() || subscriptionId.isNullOrBlank() || signature.isNullOrBlank()) {
            log.warn("rp-callback missing params: payment={} sub={} sig={}",
                paymentId, subscriptionId, signature?.let { "<set>" })
            return@get call.respondRedirect("/syncling#pricing?billing_error=missing_params")
        }
        if (!razorpayService.verifyCheckoutHandler(paymentId, subscriptionId, signature)) {
            log.warn("Invalid Checkout.js handler signature for sub={}", subscriptionId)
            return@get call.respondRedirect("/syncling#pricing?billing_error=invalid_signature")
        }
        val ownerId = razorpayService.ownerOf(subscriptionId)
        val sessionUserId = call.sessionUserId(jwtSecret)
        if (ownerId == null || ownerId != sessionUserId) {
            log.warn("rp-callback ownership mismatch: sub-owner={} session={}", ownerId, sessionUserId)
            return@get call.respondRedirect("/syncling#pricing?billing_error=owner_mismatch")
        }
        // Signature verified — immediately activate the pending plan so the dashboard
        // shows the correct tier as soon as the user lands there after redirect.
        val activated = billingRepository.activatePendingPlan(sessionUserId)
        log.info("rp-callback: activated plan={} for userId={} sub={}", activated?.name, sessionUserId, subscriptionId)
        call.application.launch {
            userActivityService.record(
                sessionUserId, UserEvent.PAYMENT_VERIFIED,
                mapOf("paymentId" to paymentId, "subscriptionId" to subscriptionId, "flow" to "rp-callback")
            )
            activated?.let {
                userActivityService.record(sessionUserId, UserEvent.PLAN_ACTIVATED, mapOf("plan" to it.name))
            }
        }
        // Remove the pending plan cookie so the user isn't re-routed on next visit.
        call.response.cookies.append(Cookie(
            name = PENDING_PLAN_COOKIE, value = "",
            path = "/", expires = GMTDate.START, maxAge = 0,
            httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
        ))
        // Ensure the session cookie is present so the success page and dashboard can
        // authenticate without putting the JWT in the URL.
        if (call.request.cookies[SESSION_COOKIE].isNullOrBlank()) {
            val user = userRepository.findById(sessionUserId)
            if (user != null) {
                val freshToken = mintJwt(jwtSecret, user.id, user.githubId, user.githubUsername)
                call.response.cookies.append(Cookie(
                    name = SESSION_COOKIE, value = freshToken,
                    path = "/", maxAge = 7 * 24 * 3600,
                    httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
                ))
                log.info("Minted and set fresh session cookie for userId={} after payment", sessionUserId)
            }
        }
        call.respondRedirect("/billing/success?sub=$subscriptionId")
    }

    get("/billing/success") {
        val subscriptionId = call.request.queryParameters["sub"].orEmpty()
        val token = call.request.cookies[SESSION_COOKIE]?.ifBlank { null }

        // Fetch plan info so the success page can show a meaningful "what's next" view.
        val userId = call.sessionUserId(jwtSecret)
        val plan: BillingPlan?
        val trialEndsOn: String?
        if (userId != null) {
            val sub = billingRepository.getSubscription(userId)
            plan = sub.plan
            // Trial is 7 days; we show the earliest possible first-charge date.
            val firstCharge = Clock.System.now() + 7.days
            trialEndsOn = firstCharge.toLocalDateTime(TimeZone.UTC).date.toString()
        } else {
            plan = null
            trialEndsOn = null
        }

        call.respondHtml { successPage(subscriptionId, token, plan, trialEndsOn) }
    }

    // Called by the checkout page's 15-minute session timer when it expires.
    // Clears pendingPlan so the next checkout attempt starts fresh.
    post("/billing/cancel-pending") {
        val userId = call.sessionUserId(jwtSecret) ?: run {
            call.respond(HttpStatusCode.Unauthorized, "No session")
            return@post
        }
        billingRepository.clearPendingPlan(userId)
        log.info("cancel-pending: cleared pendingPlan for userId={}", userId)
        call.respond(HttpStatusCode.OK, "ok")
    }
}

// ─── Razorpay webhook ─────────────────────────────────────────────────────────

fun Route.configureRazorpayWebhook(dispatcher: RazorpayWebhookDispatcher) {
    post("/razorpay/webhook") {
        val body = call.receive<ByteArray>()
        val sig = call.request.headers["X-Razorpay-Signature"]
        when (val result = dispatcher.dispatch(body, sig)) {
            is WebhookResult.Ok      -> call.respond(HttpStatusCode.OK, "ok")
            is WebhookResult.Skipped -> { log.debug("Webhook skipped: {}", result.reason); call.respond(HttpStatusCode.OK, "skipped") }
            is WebhookResult.Error   -> {
                log.warn("Webhook error: {}", result.message)
                call.respond(HttpStatusCode.InternalServerError, result.message)
            }
        }
    }
}

// ─── Invoice receipt — authenticated via httpOnly session cookie, no token in URL ──

fun Route.configureBillingReceiptRoute(
    jwtSecret: String,
    billingRepository: BillingRepository,
    userRepository: UserRepository,
    userActivityService: UserActivityService
) {
    get("/api/billing/invoices/{id}/receipt") {
        val userId = call.sessionUserId(jwtSecret)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid session"))

        val paymentId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Missing invoice id"))

        val invoices = billingRepository.listInvoices(userId)
        val invoice = invoices.find { it.razorpayPaymentId == paymentId }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Invoice not found"))

        val user = userRepository.findById(userId)
        val dateStr = invoice.createdAt.toLocalDateTime(TimeZone.UTC).date.toString()
        val amount = "₹${"%.2f".format(invoice.amountPaise / 100.0)}"
        val email = user?.email ?: "—"

        call.respondText(ContentType.Text.Html, HttpStatusCode.OK) {
            buildInvoiceReceipt(
                paymentId = invoice.razorpayPaymentId,
                date = dateStr,
                amount = amount,
                currency = invoice.currency.uppercase(),
                status = invoice.status.replaceFirstChar { it.uppercase() },
                userEmail = email,
                pdfUrl = "/api/billing/invoices/${invoice.razorpayPaymentId}/pdf"
            )
        }
    }

    // Branded PDF download — authenticated via httpOnly session cookie sent automatically by the browser.
    get("/api/billing/invoices/{id}/pdf") {
        val userId = call.sessionUserId(jwtSecret)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid session"))

        val paymentId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Missing invoice id"))

        val invoices = billingRepository.listInvoices(userId)
        val invoice = invoices.find { it.razorpayPaymentId == paymentId }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Invoice not found"))

        val user = userRepository.findById(userId)
        val subscription = billingRepository.getSubscription(userId)
        val plan = subscription.plan
        val userName = user?.githubUsername?.takeIf { it.isNotBlank() } ?: (user?.email ?: "Customer")

        val cached = billingRepository.getInvoicePdf(invoice.razorpayPaymentId)
        val pdfBytes = if (cached != null) cached else {
            val rendered = runCatching {
                InvoicePdfGenerator.render(
                    InvoicePdfGenerator.InvoiceContext(
                        invoice = invoice,
                        userEmail = user?.email,
                        userName = userName,
                        plan = plan,
                        subscription = subscription
                    )
                )
            }.getOrElse {
                log.error("PDF render failed for paymentId={}: {}", paymentId, it.message, it)
                return@get call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to generate invoice PDF"))
            }
            runCatching { billingRepository.storeInvoicePdf(invoice.razorpayPaymentId, rendered) }
                .onFailure { log.warn("PDF cache write failed for paymentId={}: {}", paymentId, it.message) }
            rendered
        }

        val filename = "syncling-invoice-${invoice.razorpayPaymentId}.pdf"
        call.response.header(HttpHeaders.ContentDisposition, """inline; filename="$filename"""")
        call.application.launch {
            userActivityService.record(
                userId, UserEvent.INVOICE_DOWNLOADED,
                mapOf("paymentId" to invoice.razorpayPaymentId)
            )
        }
        call.respondBytes(pdfBytes, ContentType.Application.Pdf, HttpStatusCode.OK)
    }
}

private fun extractUserIdFromBearer(token: String, jwtSecret: String): String? = runCatching {
    val verifier = JWT.require(Algorithm.HMAC256(jwtSecret))
        .withAudience("syncling-app")
        .withIssuer("syncling-backend")
        .build()
    verifier.verify(token).getClaim("userId")?.asString()
        ?.let { java.util.UUID.fromString(it).toString() }
}.getOrNull()

private fun buildInvoiceReceipt(
    paymentId: String,
    date: String,
    amount: String,
    currency: String,
    status: String,
    userEmail: String,
    pdfUrl: String
): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Invoice $paymentId — Syncling</title>
<style>
  body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;font-size:14px;color:#111;background:#fff;margin:0;padding:40px}
  .receipt{max-width:560px;margin:0 auto;border:1px solid #e5e7eb;border-radius:10px;padding:36px 40px}
  .receipt-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:32px;padding-bottom:20px;border-bottom:1px solid #e5e7eb}
  .brand{font-size:18px;font-weight:700;color:#111;display:flex;align-items:center;gap:8px}
  .brand-dot{width:10px;height:10px;background:#7A6DEE;border-radius:50%;display:inline-block}
  .receipt-title{font-size:12px;text-transform:uppercase;letter-spacing:1px;color:#6b7280;margin-bottom:4px}
  .receipt-id{font-family:monospace;font-size:12px;color:#374151}
  .receipt-row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #f3f4f6;font-size:14px}
  .receipt-row:last-child{border-bottom:none}
  .row-label{color:#6b7280}
  .row-value{font-weight:500;color:#111}
  .amount-row{margin-top:8px;padding-top:16px;border-top:2px solid #e5e7eb}
  .amount-value{font-size:22px;font-weight:700;color:#111}
  .status-paid{color:#7A6DEE;font-weight:600}
  .status-open{color:#f59e0b;font-weight:600}
  .footer{margin-top:28px;padding-top:16px;border-top:1px solid #e5e7eb;font-size:12px;color:#9ca3af;text-align:center}
  @media print{body{padding:20px}@page{margin:1cm}}
</style>
</head>
<body>
<div class="receipt">
  <div class="receipt-header">
    <div class="brand"><span class="brand-dot"></span>Syncling</div>
    <div>
      <div class="receipt-title">Invoice</div>
      <div class="receipt-id">$paymentId</div>
    </div>
  </div>
  <div class="receipt-row"><span class="row-label">Date</span><span class="row-value">$date</span></div>
  <div class="receipt-row"><span class="row-label">Billed to</span><span class="row-value">$userEmail</span></div>
  <div class="receipt-row"><span class="row-label">Description</span><span class="row-value">Syncling subscription</span></div>
  <div class="receipt-row"><span class="row-label">Currency</span><span class="row-value">$currency</span></div>
  <div class="receipt-row"><span class="row-label">Status</span>
    <span class="row-value ${if (status.lowercase() == "paid") "status-paid" else "status-open"}">$status</span>
  </div>
  <div class="receipt-row amount-row">
    <span class="row-label" style="font-size:15px;font-weight:600;color:#111">Total</span>
    <span class="amount-value">$amount</span>
  </div>
</div>
<div class="footer">
  Syncling by Androidplay &nbsp;·&nbsp; support@androidplay.in<br>
  <a href="$pdfUrl" style="display:inline-block;margin-top:12px;background:#8B7EFF;color:#0a0a0a;text-decoration:none;border-radius:6px;padding:8px 20px;font-size:13px;font-weight:600">Download branded PDF</a>
  <button onclick="window.print()" style="margin-top:12px;margin-left:8px;background:transparent;color:#111;border:1px solid #d1d5db;border-radius:6px;padding:8px 20px;font-size:13px;cursor:pointer">Print this page</button>
</div>
</body>
</html>
""".trimIndent()

// ─── Shared subscription response builder ─────────────────────────────────────

internal fun Subscription.toResponse(): SubscriptionResponse {
    val now = Clock.System.now()
    val trialEndsOn = if (inTrial && trialStartedAt != null)
        (trialStartedAt + 7.days).toLocalDateTime(TimeZone.UTC).date.toString()
    else null
    val daysUntilRenewal = when {
        inTrial && trialStartedAt != null -> ((trialStartedAt + 7.days) - now).inWholeDays.toInt().coerceAtLeast(0)
        currentPeriodEnd != null -> (currentPeriodEnd - now).inWholeDays.toInt().coerceAtLeast(0)
        else -> null
    }
    return SubscriptionResponse(
        plan = plan.name,
        displayName = plan.displayName,
        monthlyPricePaise = plan.monthlyPricePaise,
        stringLimit = plan.stringLimit,
        maxProjects = if (plan.maxProjects == Int.MAX_VALUE) -1 else plan.maxProjects,
        maxMembers = if (plan.maxMembers == Int.MAX_VALUE) -1 else plan.maxMembers,
        cancelAtPeriodEnd = cancelAtPeriodEnd,
        currentPeriodEnd = currentPeriodEnd?.toLocalDateTime(TimeZone.UTC)?.date?.toString(),
        trialLimitHit = inTrial && limitHitAt != null,
        inTrial = inTrial,
        trialEndsOn = trialEndsOn,
        daysUntilRenewal = daysUntilRenewal
    )
}

// ─── Session cookie helpers ───────────────────────────────────────────────────

internal fun ApplicationCall.sessionUserId(jwtSecret: String): String? {
    val token = request.cookies[SESSION_COOKIE] ?: return null
    return runCatching {
        val verifier = JWT.require(Algorithm.HMAC256(jwtSecret))
            .withAudience("syncling-app")
            .withIssuer("syncling-backend")
            .build()
        verifier.verify(token).getClaim("userId")?.asString()?.let {
            UUID.fromString(it).toString()
        }
    }.getOrElse { e ->
        if (e is JWTVerificationException) log.debug("Invalid session cookie: {}", e.message)
        null
    }
}

internal fun ApplicationCall.clearSession() {
    response.cookies.append(Cookie(
        name = SESSION_COOKIE, value = "",
        path = "/", expires = GMTDate.START, maxAge = 0,
        httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
    ))
}

