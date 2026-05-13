package com.transloom.services

import com.transloom.domain.BillingPlan
import com.transloom.domain.UsageStats
import com.transloom.repository.BillingRepository
import com.androidplay.core.secrets.getSecretValue
import org.slf4j.LoggerFactory

fun BillingPlan.stripePriceId(): String? = when (this) {
    BillingPlan.SOLO -> getSecretValue("stripe-price-solo").takeIf { it.isNotBlank() }
    BillingPlan.TEAM -> getSecretValue("stripe-price-team").takeIf { it.isNotBlank() }
    else -> null
}

class BillingService(private val billingRepository: BillingRepository) {
    private val log = LoggerFactory.getLogger(BillingService::class.java)

    suspend fun getPlan(userId: String): BillingPlan = billingRepository.getSubscription(userId).plan

    suspend fun subscribe(userId: String, plan: BillingPlan) {
        billingRepository.upsertSubscription(userId, plan)
    }

    suspend fun checkAndEnforceLimits(userId: String, stringsToTranslate: Int, currentProjects: Int): Boolean {
        val plan = getPlan(userId)
        val usage = billingRepository.getUsage(userId)

        if (plan.maxProjects <= currentProjects) {
            throw IllegalStateException("Project limit exceeded for plan ${plan.name}. Upgrade to add more projects.")
        }
        val stringLimit = plan.stringLimit
        if (stringLimit != null) {
            val projected = usage.stringsTranslated + stringsToTranslate
            if (projected > stringLimit) {
                throw IllegalStateException("Monthly string limit ($stringLimit) exceeded for plan ${plan.name}. Please upgrade.")
            }
        }
        return true
    }

    suspend fun recordUsage(userId: String, stringsTranslated: Int) {
        billingRepository.recordUsage(userId, stringsTranslated)
    }

    suspend fun getUsage(userId: String): UsageStats = billingRepository.getUsage(userId)
}
