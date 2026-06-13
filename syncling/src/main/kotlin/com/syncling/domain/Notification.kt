package com.syncling.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val message: String,
    val level: String = "info",        // info | success | warning | error
    val actionUrl: String? = null,
    val actionLabel: String? = null,
    val createdAt: Long,               // epoch millis — JS-friendly
    val readAt: Long? = null
) {
    val isRead: Boolean get() = readAt != null
}

object NotificationType {
    const val PIPELINE_COMPLETE   = "PIPELINE_COMPLETE"
    const val REVIEW_QUEUE        = "REVIEW_QUEUE"
    const val TRIAL_LIMIT         = "TRIAL_LIMIT"
    const val CHECKOUT_ABANDONED  = "CHECKOUT_ABANDONED"
    const val PLAN_EXPIRY         = "PLAN_EXPIRY"
    const val ONBOARDING          = "ONBOARDING"
    const val GITHUB_TOKEN_INVALID  = "GITHUB_TOKEN_INVALID"
    const val PIPELINE_FAILED       = "PIPELINE_FAILED"
    const val INVITE_ACCEPTED       = "INVITE_ACCEPTED"
    const val TRIAL_STARTED         = "TRIAL_STARTED"
    const val PAYMENT_FAILED        = "PAYMENT_FAILED"
    const val PAYMENT_RECEIVED      = "PAYMENT_RECEIVED"
    const val SUBSCRIPTION_ENDED    = "SUBSCRIPTION_ENDED"
    const val TRANSLATIONS_RESUMED  = "TRANSLATIONS_RESUMED"
}
