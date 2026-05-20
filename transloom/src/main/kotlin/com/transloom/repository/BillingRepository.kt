package com.transloom.repository

import com.transloom.domain.BillingPlan
import com.transloom.domain.HistoricalUsage
import com.transloom.domain.InvoiceRecord
import com.transloom.domain.Subscription
import com.transloom.domain.UsageStats
import kotlinx.datetime.Instant

interface BillingRepository {
    suspend fun getSubscription(userId: String): Subscription

    suspend fun upsertSubscription(
        userId: String,
        plan: BillingPlan,
        razorpayCustomerId: String? = null,
        razorpaySubscriptionId: String? = null,
        cancelAtPeriodEnd: Boolean = false,
        currentPeriodEnd: Instant? = null,
        pendingPlan: BillingPlan? = null
    )

    /** Promotes pendingPlan → plan atomically. Returns the activated plan, or null if none was pending. */
    suspend fun activatePendingPlan(userId: String): BillingPlan?

    /** Discards pendingPlan without activating — used when checkout session expires or user abandons. */
    suspend fun clearPendingPlan(userId: String)

    suspend fun downgradeToFree(razorpaySubscriptionId: String)

    suspend fun setLimitHitAt(userId: String, at: Instant?)

    suspend fun findByRazorpaySubscription(subscriptionId: String): String?

    suspend fun getUsage(userId: String): UsageStats

    suspend fun getHistoricalUsage(userId: String): List<HistoricalUsage>

    suspend fun recordUsage(userId: String, stringsTranslated: Int)

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
