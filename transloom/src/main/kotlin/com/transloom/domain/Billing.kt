package com.transloom.domain

import kotlinx.datetime.Instant

enum class BillingPlan(
    val displayName: String,
    val monthlyPriceCents: Int?,
    val stringLimit: Int?,
    val maxProjects: Int,
    val maxLanguages: Int
) {
    FREE("Free", null, 500, 1, 3),
    SOLO("Solo", 499, 5000, 3, Int.MAX_VALUE),
    TEAM("Team", 1999, null, 10, Int.MAX_VALUE),
    ENTERPRISE("Enterprise", null, null, Int.MAX_VALUE, Int.MAX_VALUE)
}

data class Subscription(
    val userId: String,
    val plan: BillingPlan,
    val stripeCustomerId: String?,
    val stripeSubscriptionId: String?,
    val cancelAtPeriodEnd: Boolean,
    val currentPeriodEnd: Instant?
)

data class InvoiceRecord(
    val id: String,
    val stripeInvoiceId: String,
    val amountCents: Int,
    val currency: String,
    val status: String,
    val invoicePdfUrl: String?,
    val periodStart: Instant,
    val periodEnd: Instant,
    val createdAt: Instant
)

data class UsageStats(val stringsTranslated: Int, val projectsUsed: Int)

data class HistoricalUsage(val yearMonth: String, val stringsTranslated: Int)
