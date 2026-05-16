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
        currentPeriodEnd: Instant? = null
    )

    suspend fun downgradeToFree(razorpaySubscriptionId: String)

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
}
