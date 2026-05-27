package com.transloom.domain

import com.androidplay.core.secrets.getSecretValue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class BillingPlan(
    val displayName: String,
    val monthlyPricePaise: Int?,   // amount in paise (₹1 = 100 paise); null = free/custom
    val stringLimit: Int?,
    val maxProjects: Int,
    val maxLanguages: Int,
    /** Maximum invited teammates (excluding the OWNER) allowed per project. */
    val maxMembers: Int
) {
    FREE("Free", null, 500, 1, 3, 0),
    SOLO("Solo", 49900, 5000, 3, Int.MAX_VALUE, 0),      // ₹499/mo
    TEAM("Team", 199900, null, 10, Int.MAX_VALUE, 15),   // ₹1,999/mo
    ENTERPRISE("Enterprise", null, null, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE);

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
    val startedAt: Instant? = null,
    /**
     * When this account first started a 7-day free trial. Set once and preserved across
     * downgrades so a user can never claim a second trial after the first has been consumed.
     */
    val trialStartedAt: Instant? = null
) {
    val inTrial: Boolean get() {
        if (plan == BillingPlan.FREE || plan == BillingPlan.ENTERPRISE) return false
        if (razorpaySubscriptionId == null || currentPeriodEnd != null) return false
        // Only "in trial" when trialStartedAt is set and less than 7 days ago.
        // This prevents re-subscribers (whose trialStartedAt is old) and expired trials
        // from appearing as in-trial while waiting for the subscription.charged webhook.
        val trialStart = trialStartedAt ?: return false
        return (Clock.System.now() - trialStart).inWholeDays < 7
    }
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
