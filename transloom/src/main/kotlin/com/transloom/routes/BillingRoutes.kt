package com.transloom.routes

import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.razorpay.WebhookResult
import com.transloom.domain.BillingPlan
import com.transloom.repository.BillingRepository
import com.transloom.repository.UserRepository
import com.transloom.model.ApiError
import com.transloom.services.RazorpayBillingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BillingRoutes")

@Serializable data class SubscribePlanBody(val plan: String = "SOLO")
@Serializable data class SubscribeResponse(val subscribeUrl: String)

@Serializable
data class SubscriptionResponse(
    val plan: String,
    val displayName: String,
    val monthlyPricePaise: Int?,
    val stringLimit: Int?,
    val maxProjects: Int,
    val cancelAtPeriodEnd: Boolean,
    val currentPeriodEnd: String?
)

@Serializable
data class InvoiceResponse(
    val id: String,
    val date: String,
    val amount: String,
    val currency: String,
    val status: String
)

// ─── Authenticated billing routes ─────────────────────────────────────────────

fun Route.configureBillingRoutes(
    razorpayService: RazorpayBillingService,
    billingRepository: BillingRepository,
    userRepository: UserRepository
) {
    route("/transloom/api/billing") {

        get("/subscription") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val sub = billingRepository.getSubscription(userId)
            val plan = sub.plan
            call.respond(
                SubscriptionResponse(
                    plan = plan.name,
                    displayName = plan.displayName,
                    monthlyPricePaise = plan.monthlyPricePaise,
                    stringLimit = plan.stringLimit,
                    maxProjects = if (plan.maxProjects == Int.MAX_VALUE) -1 else plan.maxProjects,
                    cancelAtPeriodEnd = sub.cancelAtPeriodEnd,
                    currentPeriodEnd = sub.currentPeriodEnd?.toLocalDateTime(TimeZone.UTC)?.date?.toString()
                )
            )
        }

        // Authenticated upgrade — user is already logged in via dashboard.
        post("/subscribe") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val body = runCatching { call.receive<SubscribePlanBody>() }.getOrElse { SubscribePlanBody() }
            val plan = runCatching { BillingPlan.valueOf(body.plan.uppercase()) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Unknown plan: ${body.plan}"))
            }
            if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Use SOLO or TEAM for subscription"))
            }
            val url = runCatching { razorpayService.createAuthenticatedSubscription(userId, plan) }.getOrElse {
                log.error("Subscription creation failed for userId={}: {}", userId, it.message)
                return@post call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "Razorpay error"))
            }
            call.respond(SubscribeResponse(url))
        }

        post("/cancel") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            runCatching { razorpayService.cancelSubscription(userId) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError(it.message ?: "Cancel failed"))
            }
            call.respond(mapOf("status" to "Subscription will cancel at end of billing period"))
        }

        get("/invoices") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val invoices = billingRepository.listInvoices(userId).map { inv ->
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

        get("/usage") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val plan = billingRepository.getSubscription(userId).plan
            val usage = billingRepository.getUsage(userId)
            val history = billingRepository.getHistoricalUsage(userId)
            call.respond(
                mapOf(
                    "stringsTranslated" to usage.stringsTranslated,
                    "stringLimit" to plan.stringLimit,
                    "projectsUsed" to usage.projectsUsed,
                    "projectLimit" to (if (plan.maxProjects == Int.MAX_VALUE) null else plan.maxProjects),
                    "history" to history.map { mapOf("month" to it.yearMonth, "count" to it.stringsTranslated) }
                )
            )
        }
    }
}

// ─── Public (unauthenticated) routes ──────────────────────────────────────────

/**
 * Anonymous checkout: user picks a paid plan on the landing page before signing in.
 * Creates a Razorpay subscription and redirects the user to Razorpay's hosted page.
 * After card auth Razorpay calls back to /transloom/billing/rp-callback.
 */
fun Route.configurePublicCheckoutRoute(razorpayService: RazorpayBillingService) {
    get("/transloom/billing/start-subscription") {
        val planParam = call.request.queryParameters["plan"]?.uppercase()
        val plan = runCatching { BillingPlan.valueOf(planParam ?: "") }.getOrElse {
            return@get call.respondRedirect("/transloom#pricing")
        }
        if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) {
            return@get call.respondRedirect("/transloom/auth/github")
        }
        try {
            val url = razorpayService.createAnonymousSubscription(plan)
            call.respondRedirect(url)
        } catch (e: Exception) {
            log.error("Anonymous subscription failed for plan={}: {}", plan.name, e.message)
            call.respondRedirect("/transloom#pricing")
        }
    }

    /**
     * Razorpay redirects here after card authentication on the hosted subscription page.
     * Verifies the signature, stores the subscription ID in a cookie, then sends the user
     * through GitHub OAuth so we can link the subscription to the newly-created user.
     */
    get("/transloom/billing/rp-callback") {
        val paymentId = call.request.queryParameters["razorpay_payment_id"]
        val subscriptionId = call.request.queryParameters["razorpay_subscription_id"]
            ?: return@get call.respondRedirect("/transloom#pricing")
        val signature = call.request.queryParameters["razorpay_signature"]
        val plan = call.request.queryParameters["plan"] ?: "SOLO"

        if (!razorpayService.verifyCallbackSignature(paymentId, subscriptionId, signature)) {
            log.warn("Invalid Razorpay callback signature for sub={}", subscriptionId)
            return@get call.respondRedirect("/transloom#pricing")
        }

        call.response.cookies.append(Cookie(
            name = "rp_subscription", value = subscriptionId,
            path = "/transloom/auth", maxAge = 1800,
            httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
        ))
        call.response.cookies.append(Cookie(
            name = "rp_plan", value = plan.uppercase(),
            path = "/transloom/auth", maxAge = 1800,
            httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
        ))
        call.respondRedirect("/transloom/auth/github")
    }
}

// ─── Razorpay webhook ─────────────────────────────────────────────────────────

fun Route.configureRazorpayWebhook(dispatcher: RazorpayWebhookDispatcher) {
    post("/transloom/razorpay/webhook") {
        val body = call.receive<ByteArray>()
        val sig = call.request.headers["X-Razorpay-Signature"]
        when (val result = dispatcher.dispatch(body, sig)) {
            is WebhookResult.Ok      -> call.respond(HttpStatusCode.OK, "ok")
            is WebhookResult.Skipped -> { log.debug("Webhook skipped: {}", result.reason); call.respond(HttpStatusCode.OK, "skipped") }
            is WebhookResult.Error   -> {
                log.warn("Webhook error: {}", result.message)
                // 500 (not 400) so Razorpay retries on transient failures (DB hiccup, downstream down).
                // The only intentionally-rejected case (bad signature) still returns 500 — Razorpay will retry
                // a few times and then alert, which is the desired observability path.
                call.respond(HttpStatusCode.InternalServerError, result.message)
            }
        }
    }
}

