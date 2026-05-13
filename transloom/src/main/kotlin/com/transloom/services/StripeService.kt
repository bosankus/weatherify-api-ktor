package com.transloom.services

import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Invoice
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.checkout.SessionCreateParams
import com.transloom.domain.BillingPlan
import com.transloom.repository.BillingRepository
import com.androidplay.core.secrets.getSecretValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

class StripeService(private val billingRepository: BillingRepository) {
    private val log = LoggerFactory.getLogger(StripeService::class.java)

    private val secretKey = getSecretValue("stripe-secret-key")
    private val webhookSecret = getSecretValue("stripe-webhook-secret")
    private val appUrl = getSecretValue("app-url")

    init {
        Stripe.apiKey = secretKey
        log.info("Stripe initialized")
    }

    suspend fun createCheckoutSession(userId: String, userEmail: String?, plan: BillingPlan): String {
        val priceId = plan.stripePriceId()
            ?: throw IllegalArgumentException("No Stripe price configured for plan ${plan.name}")

        return withContext(Dispatchers.IO) {
            val params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl("$appUrl/transloom/app?billing=success&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl("$appUrl/transloom/app?billing=cancelled")
                .apply { if (userEmail != null) setCustomerEmail(userEmail) }
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1)
                        .build()
                )
                .putMetadata("userId", userId)
                .putMetadata("plan", plan.name)
                .build()

            val session = Session.create(params)
            log.info("Stripe checkout session created: {} for userId={}", session.id, userId)
            session.url
        }
    }

    suspend fun createPortalSession(userId: String): String {
        val subscription = billingRepository.getSubscription(userId)
        val customerId = subscription.stripeCustomerId
            ?: throw IllegalStateException("No Stripe customer found for this account")

        return withContext(Dispatchers.IO) {
            val params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl("$appUrl/transloom/app")
                .build()
            com.stripe.model.billingportal.Session.create(params).url
        }
    }

    suspend fun handleWebhook(payload: ByteArray, signatureHeader: String?): WebhookResult {
        if (signatureHeader == null) return WebhookResult.Error("Missing Stripe-Signature header")

        val event = try {
            withContext(Dispatchers.IO) {
                Webhook.constructEvent(payload.toString(Charsets.UTF_8), signatureHeader, webhookSecret)
            }
        } catch (e: SignatureVerificationException) {
            return WebhookResult.Error("Invalid Stripe signature: ${e.message}")
        }

        log.info("Stripe webhook: {}", event.type)

        return when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.getObject().orElse(null) as? Session
                    ?: return WebhookResult.Skipped("Could not deserialize checkout.session.completed")
                handleCheckoutCompleted(session)
            }
            "customer.subscription.updated" -> {
                val sub = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Subscription
                    ?: return WebhookResult.Skipped("Could not deserialize customer.subscription.updated")
                handleSubscriptionUpdated(sub)
            }
            "customer.subscription.deleted" -> {
                val sub = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Subscription
                    ?: return WebhookResult.Skipped("Could not deserialize customer.subscription.deleted")
                billingRepository.downgradeToFree(sub.customer)
                log.info("Subscription cancelled for customer={}", sub.customer)
                WebhookResult.Ok
            }
            "invoice.payment_succeeded" -> {
                val invoice = event.dataObjectDeserializer.getObject().orElse(null) as? Invoice
                    ?: return WebhookResult.Skipped("Could not deserialize invoice.payment_succeeded")
                handleInvoicePaid(invoice)
            }
            "invoice.payment_failed" -> {
                val invoice = event.dataObjectDeserializer.getObject().orElse(null) as? Invoice
                    ?: return WebhookResult.Skipped("Could not deserialize invoice.payment_failed")
                log.warn("Payment failed for customer={} amount={}", invoice.customer, invoice.amountDue)
                WebhookResult.Ok
            }
            else -> WebhookResult.Ok
        }
    }

    private suspend fun handleCheckoutCompleted(session: Session): WebhookResult {
        val userId = session.metadata["userId"]
            ?: return WebhookResult.Error("Missing userId in session metadata")
        val planName = session.metadata["plan"] ?: return WebhookResult.Error("Missing plan in metadata")
        val plan = runCatching { BillingPlan.valueOf(planName) }.getOrElse {
            return WebhookResult.Error("Unknown plan: $planName")
        }
        billingRepository.upsertSubscription(
            userId = userId,
            plan = plan,
            stripeCustomerId = session.customer,
            stripeSubscriptionId = session.subscription
        )
        log.info("Checkout completed: userId={} plan={}", userId, plan.name)
        return WebhookResult.Ok
    }

    private suspend fun handleSubscriptionUpdated(sub: com.stripe.model.Subscription): WebhookResult {
        val userId = billingRepository.findByStripeCustomer(sub.customer)
            ?: return WebhookResult.Error("No user found for customer=${sub.customer}")

        val priceId = sub.items.data.firstOrNull()?.price?.id
        val plan = BillingPlan.entries.firstOrNull { it.stripePriceId() == priceId } ?: BillingPlan.FREE

        billingRepository.upsertSubscription(
            userId = userId,
            plan = plan,
            stripeSubscriptionId = sub.id,
            cancelAtPeriodEnd = sub.cancelAtPeriodEnd,
            currentPeriodEnd = Instant.fromEpochSeconds(sub.currentPeriodEnd)
        )
        log.info("Subscription updated: userId={} plan={} cancelAtEnd={}", userId, plan.name, sub.cancelAtPeriodEnd)
        return WebhookResult.Ok
    }

    private suspend fun handleInvoicePaid(invoice: Invoice): WebhookResult {
        if (invoice.billingReason == "subscription_create" || invoice.amountPaid == 0L) return WebhookResult.Ok

        val userId = billingRepository.findByStripeCustomer(invoice.customer)
            ?: return WebhookResult.Error("No user for customer=${invoice.customer}")

        billingRepository.insertInvoice(
            userId = userId,
            stripeInvoiceId = invoice.id,
            amountCents = invoice.amountPaid.toInt(),
            currency = invoice.currency,
            status = invoice.status ?: "paid",
            invoicePdfUrl = invoice.invoicePdf,
            periodStart = Instant.fromEpochSeconds(invoice.periodStart),
            periodEnd = Instant.fromEpochSeconds(invoice.periodEnd)
        )
        log.info("Invoice recorded: {} amount={} {}", invoice.id, invoice.amountPaid, invoice.currency)
        return WebhookResult.Ok
    }
}

sealed class WebhookResult {
    object Ok : WebhookResult()
    data class Error(val message: String) : WebhookResult()
    data class Skipped(val reason: String) : WebhookResult()
}
