package com.transloom.routes

import com.transloom.domain.BillingPlan
import com.transloom.repository.BillingRepository
import com.transloom.repository.UserRepository
import com.transloom.model.ApiError
import com.transloom.services.StripeService
import com.transloom.services.WebhookResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BillingRoutes")

@Serializable
data class CheckoutPlanBody(val plan: String = "SOLO")


@Serializable
data class CheckoutResponse(val checkoutUrl: String)

@Serializable
data class PortalResponse(val portalUrl: String)

@Serializable
data class SubscriptionResponse(
    val plan: String,
    val displayName: String,
    val monthlyPriceCents: Int?,
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
    val status: String,
    val pdfUrl: String?
)

fun Route.configureBillingRoutes(
    stripeService: StripeService,
    billingRepository: BillingRepository,
    userRepository: UserRepository
) {
    route("/transloom/api/billing") {

        get("/subscription") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val sub = billingRepository.getSubscription(userId)
            val plan = sub.plan
            call.respond(
                SubscriptionResponse(
                    plan = plan.name,
                    displayName = plan.displayName,
                    monthlyPriceCents = plan.monthlyPriceCents,
                    stringLimit = plan.stringLimit,
                    maxProjects = if (plan.maxProjects == Int.MAX_VALUE) -1 else plan.maxProjects,
                    cancelAtPeriodEnd = sub.cancelAtPeriodEnd,
                    currentPeriodEnd = sub.currentPeriodEnd?.toLocalDateTime(TimeZone.UTC)?.date?.toString()
                )
            )
        }

        post("/checkout") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val body = runCatching { call.receive<CheckoutPlanBody>() }.getOrElse { CheckoutPlanBody() }
            val plan = runCatching { BillingPlan.valueOf(body.plan.uppercase()) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Unknown plan: ${body.plan}. Use SOLO or TEAM"))
            }
            if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Use plan SOLO or TEAM for checkout"))
            }

            val user = userRepository.findById(userId)
            val url = runCatching {
                stripeService.createCheckoutSession(userId, user?.email, plan)
            }.getOrElse {
                log.error("Checkout session failed for userId={}: {}", userId, it.message)
                return@post call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "Stripe error"))
            }
            call.respond(CheckoutResponse(url))
        }

        get("/portal") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val url = runCatching { stripeService.createPortalSession(userId) }.getOrElse {
                return@get call.respond(HttpStatusCode.BadRequest, ApiError(it.message ?: "Cannot open portal"))
            }
            call.respond(PortalResponse(url))
        }

        get("/invoices") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val invoices = billingRepository.listInvoices(userId).map { inv ->
                val dateStr = inv.createdAt.toLocalDateTime(TimeZone.UTC).date.toString()
                val dollars = "${"$"}${"%.2f".format(inv.amountCents / 100.0)}"
                InvoiceResponse(
                    id = inv.stripeInvoiceId,
                    date = dateStr,
                    amount = dollars,
                    currency = inv.currency.uppercase(),
                    status = inv.status.replaceFirstChar { it.uppercase() },
                    pdfUrl = inv.invoicePdfUrl
                )
            }
            call.respond(mapOf("invoices" to invoices))
        }

        get("/usage") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

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

fun Route.configureStripeWebhook(stripeService: StripeService) {
    post("/transloom/stripe/webhook") {
        val body = call.receive<ByteArray>()
        val sig = call.request.headers["Stripe-Signature"]

        when (val result = stripeService.handleWebhook(body, sig)) {
            is WebhookResult.Ok -> call.respond(HttpStatusCode.OK, "ok")
            is WebhookResult.Skipped -> {
                log.debug("Stripe webhook skipped: {}", result.reason)
                call.respond(HttpStatusCode.OK, "skipped")
            }
            is WebhookResult.Error -> {
                log.warn("Stripe webhook error: {}", result.message)
                call.respond(HttpStatusCode.BadRequest, result.message)
            }
        }
    }
}
