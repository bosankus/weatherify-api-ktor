package data.service

import bose.ankush.data.model.*
import domain.model.Result
import domain.repository.ServiceCatalogRepository
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Service responsible for seeding initial service catalog data.
 * This ensures backward compatibility by creating the PREMIUM_ONE service if it doesn't exist.
 */
class ServiceCatalogSeedingService(
    private val serviceCatalogRepository: ServiceCatalogRepository
) {
    private val logger = LoggerFactory.getLogger(ServiceCatalogSeedingService::class.java)

    companion object {
        private const val PREMIUM_ONE_CODE = "PREMIUM_ONE"
        private const val SYSTEM_USER = "system"
    }

    /**
     * Seed the database with initial service catalog data.
     * This method is idempotent and can be called multiple times safely.
     */
    suspend fun seedInitialServices() {
        logger.info("Starting service catalog seeding process")

        try {
            // Check if PREMIUM_ONE service exists
            val existingService = serviceCatalogRepository.getServiceByCode(PREMIUM_ONE_CODE)

            if (existingService is Result.Success && existingService.data != null) {
                logger.info("PREMIUM_ONE service already exists, skipping seeding")
                return
            }

            // Create PREMIUM_ONE service with default configuration
            logger.info("Creating PREMIUM_ONE service with default configuration")

            val now = Instant.now().toString()
            val premiumOneService = ServiceConfig(
                serviceCode = PREMIUM_ONE_CODE,
                displayName = "Premium One",
                description = "Premium subscription with full access to all features",
                pricingTiers = listOf(
                    PricingTier(
                        amount = 99900, // ₹999 in paise
                        currency = "INR",
                        duration = 1,
                        durationType = DurationType.MONTHS,
                        isDefault = true,
                        isFeatured = true,
                        displayOrder = 0
                    )
                ),
                features = listOf(
                    ServiceFeature(
                        description = "Unlimited API access",
                        isHighlighted = true,
                        displayOrder = 0
                    ),
                    ServiceFeature(
                        description = "Priority support",
                        isHighlighted = true,
                        displayOrder = 1
                    ),
                    ServiceFeature(
                        description = "Advanced analytics",
                        isHighlighted = false,
                        displayOrder = 2
                    ),
                    ServiceFeature(
                        description = "Custom integrations",
                        isHighlighted = false,
                        displayOrder = 3
                    )
                ),
                status = ServiceStatus.ACTIVE,
                limits = mapOf(
                    "api_calls" to ServiceLimit(
                        value = 10000,
                        type = LimitType.HARD,
                        unit = "requests/day"
                    ),
                    "storage" to ServiceLimit(
                        value = 100,
                        type = LimitType.SOFT,
                        unit = "GB"
                    )
                ),
                availabilityStart = null,
                availabilityEnd = null,
                createdAt = now,
                updatedAt = now,
                createdBy = SYSTEM_USER,
                updatedBy = SYSTEM_USER
            )

            // Create the service
            val createResult = serviceCatalogRepository.createService(premiumOneService)

            if (createResult is Result.Error) {
                logger.error("Failed to create PREMIUM_ONE service: ${createResult.message}")
                return
            }

            val createdService = (createResult as Result.Success).data

            // Create initial history entry
            val history = ServiceHistory(
                serviceId = createdService.id,
                serviceCode = createdService.serviceCode,
                changeType = ChangeType.CREATED,
                changedBy = SYSTEM_USER,
                changedAt = now,
                changes = mapOf(
                    "created" to ChangeDetail(
                        field = "created",
                        oldValue = null,
                        newValue = "Service created during initial seeding"
                    )
                )
            )

            val historyResult = serviceCatalogRepository.addHistory(history)

            if (historyResult is Result.Error) {
                logger.warn("Failed to create history entry for PREMIUM_ONE service: ${historyResult.message}")
            }

            logger.info("PREMIUM_ONE service created successfully with ID: ${createdService.id}")
        } catch (e: Exception) {
            logger.error("Error during service catalog seeding", e)
        }
    }
}
