package com.transloom.domain

import com.androidplay.core.secrets.getSecretValue
import kotlinx.datetime.Instant

enum class BillingPlan(
    val displayName: String,
    val monthlyPricePaise: Int?,   // amount in paise (₹1 = 100 paise); null = free/custom
    val stringLimit: Int?,
    val maxProjects: Int,
    val maxLanguages: Int
) {
    FREE("Free", null, 500, 1, 3),
    SOLO("Solo", 49900, 5000, 3, Int.MAX_VALUE),      // ₹499/mo
    TEAM("Team", 199900, null, 10, Int.MAX_VALUE),    // ₹1,999/mo
    ENTERPRISE("Enterprise", null, null, Int.MAX_VALUE, Int.MAX_VALUE);

    fun razorpayPlanId(): String? = when (this) {
        FREE, ENTERPRISE -> null
        SOLO -> getSecretValue("razorpay-plan-id-solo").takeIf { it.isNotBlank() }
        TEAM -> getSecretValue("razorpay-plan-id-team").takeIf { it.isNotBlank() }
    }
}

data class Subscription(
    val userId: String,
    val plan: BillingPlan,
    val razorpayCustomerId: String?,
    val razorpaySubscriptionId: String?,
    val cancelAtPeriodEnd: Boolean,
    val currentPeriodEnd: Instant?,
    val limitHitAt: Instant? = null,
    /** Plan the user is moving to; set on subscription creation, cleared on payment confirmation. */
    val pendingPlan: BillingPlan? = null,
    /** When the subscription document was first created (i.e. when the user subscribed). */
    val startedAt: Instant? = null
) {
    val inTrial: Boolean get() =
        plan != BillingPlan.FREE && plan != BillingPlan.ENTERPRISE &&
        razorpaySubscriptionId != null && currentPeriodEnd == null
}

data class InvoiceRecord(
    val id: String,
    val razorpayPaymentId: String,
    val amountPaise: Int,
    val currency: String,
    val status: String,
    val periodEnd: Instant,
    val createdAt: Instant
)

data class UsageStats(val stringsTranslated: Int, val projectsUsed: Int)

data class HistoricalUsage(val yearMonth: String, val stringsTranslated: Int)
