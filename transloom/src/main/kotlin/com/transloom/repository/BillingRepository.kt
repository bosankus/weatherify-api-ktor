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
        stripeCustomerId: String? = null,
        stripeSubscriptionId: String? = null,
        cancelAtPeriodEnd: Boolean = false,
        currentPeriodEnd: Instant? = null
    )

    suspend fun downgradeToFree(stripeCustomerId: String)

    suspend fun findByStripeCustomer(stripeCustomerId: String): String?

    suspend fun getUsage(userId: String): UsageStats

    suspend fun getHistoricalUsage(userId: String): List<HistoricalUsage>

    suspend fun recordUsage(userId: String, stringsTranslated: Int)

    suspend fun insertInvoice(
        userId: String,
        stripeInvoiceId: String,
        amountCents: Int,
        currency: String,
        status: String,
        invoicePdfUrl: String?,
        periodStart: Instant,
        periodEnd: Instant
    )

    suspend fun listInvoices(userId: String, limit: Int = 12): List<InvoiceRecord>
}
