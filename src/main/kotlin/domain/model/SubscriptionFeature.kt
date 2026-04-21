package domain.model

import bose.ankush.data.model.User
import java.time.Instant

enum class SubscriptionFeature {
    HOURLY_FORECAST,
    DAILY_FORECAST,
    WEATHER_ALERTS,
    AIR_QUALITY
}

object SubscriptionFeatureResolver {

    fun resolveFeatures(user: User): Set<SubscriptionFeature> {
        if (!user.isPremiumActive()) return emptySet()
        return SubscriptionFeature.values().toSet()
    }

    private fun User.isPremiumActive(): Boolean {
        if (!isPremium) return false
        val expiresAt = premiumExpiresAt ?: return true
        return try {
            Instant.parse(expiresAt).isAfter(Instant.now())
        } catch (e: Exception) {
            false
        }
    }
}