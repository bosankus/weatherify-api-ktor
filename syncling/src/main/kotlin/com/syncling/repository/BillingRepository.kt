package com.syncling.repository

import com.syncling.domain.BillingPlan
import com.syncling.domain.HistoricalUsage
import com.syncling.domain.InvoiceRecord
import com.syncling.domain.Subscription
import com.syncling.domain.UsageStats
import kotlinx.datetime.Instant

interface BillingRepository {
    suspend fun getSubscription(userId: String): Subscription

    suspend fun upsertSubscription(
        userId: String,
        plan: BillingPlan,
        razorpayCustomerId: String? = null,
        razorpaySubscriptionId: String? = null,
        cancelAtPeriodEnd: Boolean? = null,
        currentPeriodEnd: Instant? = null,
        pendingPlan: BillingPlan? = null
    )

    /** Promotes pendingPlan → plan atomically. Returns the activated plan, or null if none was pending. */
    suspend fun activatePendingPlan(userId: String): BillingPlan?

    /** Discards pendingPlan without activating — used when checkout session expires or user abandons. */
    suspend fun clearPendingPlan(userId: String)

    suspend fun downgradeToFree(razorpaySubscriptionId: String)

    suspend fun setLimitHitAt(userId: String, at: Instant?)

    /**
     * Marks that the user has begun a 7-day free trial. Idempotent — only sets the
     * timestamp on first call; preserved across downgrades so the trial cannot be reused.
     */
    suspend fun markTrialStarted(userId: String, at: Instant)

    suspend fun findByRazorpaySubscription(subscriptionId: String): String?

    suspend fun getUsage(userId: String): UsageStats

    suspend fun getHistoricalUsage(userId: String): List<HistoricalUsage>

    suspend fun recordUsage(userId: String, stringsTranslated: Int)

    /**
     * Atomically increments the usage counter only if the resulting total would not exceed [limit].
     * Returns true if the increment succeeded (under limit), false if it was rejected (over limit).
     * This eliminates the read-then-write race where two concurrent pipelines both pass a limit
     * check and both proceed, causing over-billing.
     */
    suspend fun incrementUsageIfUnderLimit(userId: String, amount: Int, limit: Int): Boolean

    suspend fun insertInvoice(
        userId: String,
        razorpayPaymentId: String,
        amountPaise: Int,
        currency: String,
        status: String,
        periodEnd: Instant
    )

    suspend fun listInvoices(userId: String, limit: Int = 12): List<InvoiceRecord>

    /**
     * Returns subscriptions on paid plans whose currentPeriodEnd falls in [[from], [to]].
     * Used by the lifecycle monitor instead of scanning all users N+1 style.
     */
    suspend fun findExpiringSubscriptions(from: Instant, to: Instant): List<Subscription>

    /** Returns a previously cached PDF render for this paymentId, or null if not yet cached. */
    suspend fun getInvoicePdf(paymentId: String): ByteArray?

    /** Stores the rendered PDF bytes on the invoice document so future requests skip iText7. */
    suspend fun storeInvoicePdf(paymentId: String, bytes: ByteArray)
}
