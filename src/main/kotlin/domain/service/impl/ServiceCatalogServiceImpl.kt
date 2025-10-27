package domain.service.impl

import bose.ankush.data.model.*
import domain.model.Result
import domain.repository.ServiceCatalogRepository
import domain.repository.PaymentRepository
import domain.repository.UserRepository
import domain.service.ServiceCatalogService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Implementation of ServiceCatalogService that handles service catalog business logic.
 */
class ServiceCatalogServiceImpl(
    private val serviceCatalogRepository: ServiceCatalogRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository
) : ServiceCatalogService {
    private val logger = LoggerFactory.getLogger(ServiceCatalogServiceImpl::class.java)

    companion object {
        private val SERVICE_CODE_REGEX = Regex("^[A-Z0-9_]+$")
        private const val MAX_FEATURE_DESCRIPTION_LENGTH = 200
        private const val MAX_DESCRIPTION_LENGTH = 500
    }

    /**
     * Internal data class for tracking monthly data
     */
    private data class MonthlyData(
        var count: Long = 0L,
        var revenue: Double = 0.0
    )

    /**
     * Create a new service in the catalog.
     * Validates service code format, checks for duplicates, validates pricing tiers and features.
     */
    override suspend fun createService(request: CreateServiceRequest, adminEmail: String): Result<ServiceConfig> {
        logger.info("Creating service: ${request.serviceCode} by admin $adminEmail")

        return try {
            // 1. Validate service code format (uppercase alphanumeric with underscores)
            val codeValidation = validateServiceCode(request.serviceCode)
            if (codeValidation is Result.Error) {
                return codeValidation.mapError { msg, ex -> Pair(msg, ex) }
            }

            // 2. Check for duplicate service codes
            val existingService = serviceCatalogRepository.getServiceByCode(request.serviceCode)
            if (existingService is Result.Success && existingService.data != null) {
                logger.warn("Service code already exists: ${request.serviceCode}")
                return Result.error("Service code already exists: ${request.serviceCode}")
            }

            // 3. Validate at least one pricing tier exists
            if (request.pricingTiers.isEmpty()) {
                logger.warn("Service must have at least one pricing tier")
                return Result.error("Service must have at least one pricing tier")
            }

            // 4. Validate feature descriptions length (max 200 chars)
            for (feature in request.features) {
                if (feature.description.length > MAX_FEATURE_DESCRIPTION_LENGTH) {
                    logger.warn("Feature description exceeds maximum length: ${feature.description.length}")
                    return Result.error("Feature description must not exceed $MAX_FEATURE_DESCRIPTION_LENGTH characters")
                }
            }

            // 5. Validate description length
            if (request.description.length > MAX_DESCRIPTION_LENGTH) {
                logger.warn("Service description exceeds maximum length: ${request.description.length}")
                return Result.error("Service description must not exceed $MAX_DESCRIPTION_LENGTH characters")
            }

            // 6. Create service record
            val now = Instant.now().toString()
            val service = ServiceConfig(
                serviceCode = request.serviceCode,
                displayName = request.displayName,
                description = request.description,
                pricingTiers = request.pricingTiers,
                features = request.features,
                status = ServiceStatus.ACTIVE,
                limits = request.limits,
                availabilityStart = request.availabilityStart,
                availabilityEnd = request.availabilityEnd,
                createdAt = now,
                updatedAt = now,
                createdBy = adminEmail,
                updatedBy = adminEmail
            )

            val createResult = serviceCatalogRepository.createService(service)
            if (createResult is Result.Error) {
                return createResult
            }

            val createdService = (createResult as Result.Success).data

            // 7. Create initial history entry
            val history = ServiceHistory(
                serviceId = createdService.id,
                serviceCode = createdService.serviceCode,
                changeType = ChangeType.CREATED,
                changedBy = adminEmail,
                changedAt = now,
                changes = mapOf(
                    "created" to ChangeDetail(
                        field = "created",
                        oldValue = null,
                        newValue = "Service created"
                    )
                )
            )

            serviceCatalogRepository.addHistory(history)

            logger.info("Service created successfully: ${createdService.serviceCode}")
            Result.success(createdService)
        } catch (e: Exception) {
            logger.error("Failed to create service: ${request.serviceCode}", e)
            Result.error("Failed to create service: ${e.message}", e)
        }
    }

    /**
     * Get detailed service information including analytics and history.
     */
    override suspend fun getService(id: String): Result<ServiceDetailResponse> {
        logger.debug("Getting service details for: $id")

        return try {
            // 1. Get service
            val serviceResult = serviceCatalogRepository.getServiceById(id)
            if (serviceResult is Result.Error) {
                return Result.error("Failed to get service: ${serviceResult.message}")
            }

            val service = (serviceResult as Result.Success).data
            if (service == null) {
                return Result.error("Service not found: $id")
            }

            // 2. Get analytics
            val analyticsResult = getServiceAnalytics(service.serviceCode)
            val analytics = if (analyticsResult is Result.Success) {
                analyticsResult.data
            } else {
                // Return empty analytics if fetch fails
                ServiceAnalytics(
                    activeSubscriptions = 0,
                    totalSubscriptions = 0,
                    totalRevenue = 0.0,
                    monthlyTrend = emptyList(),
                    popularPricingTier = null,
                    averageDuration = 0.0
                )
            }

            // 3. Get history
            val historyResult = serviceCatalogRepository.getServiceHistory(service.id)
            val history = if (historyResult is Result.Success) {
                historyResult.data
            } else {
                emptyList()
            }

            val response = ServiceDetailResponse(
                service = service,
                analytics = analytics,
                history = history
            )

            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to get service: $id", e)
            Result.error("Failed to get service: ${e.message}", e)
        }
    }

    /**
     * List all services with pagination and filtering.
     * Computes service summaries with active subscription counts.
     */
    override suspend fun listServices(
        page: Int,
        pageSize: Int,
        status: ServiceStatus?,
        searchQuery: String?
    ): Result<ServiceListResponse> {
        logger.debug("Listing services: page=$page, pageSize=$pageSize, status=$status, search=$searchQuery")

        return try {
            // 1. Get services from repository
            val servicesResult = serviceCatalogRepository.getAllServices(page, pageSize, status, searchQuery)
            if (servicesResult is Result.Error) {
                return Result.error("Failed to list services: ${servicesResult.message}")
            }

            val (services, totalCount) = (servicesResult as Result.Success).data

            // 2. Compute service summaries with active subscription counts
            val summaries = services.map { service ->
                val activeSubsResult = serviceCatalogRepository.getActiveSubscriptionCount(service.serviceCode)
                val activeSubscriptions = if (activeSubsResult is Result.Success) {
                    activeSubsResult.data
                } else {
                    0L
                }

                // Find lowest price from pricing tiers
                val lowestPrice = service.pricingTiers.minOfOrNull { it.amount } ?: 0
                val currency = service.pricingTiers.firstOrNull()?.currency ?: "INR"

                ServiceSummary(
                    id = service.id,
                    serviceCode = service.serviceCode,
                    displayName = service.displayName,
                    status = service.status,
                    activeSubscriptions = activeSubscriptions,
                    lowestPrice = lowestPrice,
                    currency = currency,
                    createdAt = service.createdAt
                )
            }

            val response = ServiceListResponse(
                services = summaries,
                totalCount = totalCount,
                page = page,
                pageSize = pageSize
            )

            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to list services", e)
            Result.error("Failed to list services: ${e.message}", e)
        }
    }

    /**
     * Update an existing service.
     * Creates diff of changes for history tracking.
     */
    override suspend fun updateService(
        id: String,
        request: UpdateServiceRequest,
        adminEmail: String
    ): Result<ServiceConfig> {
        logger.info("Updating service: $id by admin $adminEmail")

        return try {
            // 1. Fetch existing service
            val serviceResult = serviceCatalogRepository.getServiceById(id)
            if (serviceResult is Result.Error) {
                return Result.error("Failed to get service: ${serviceResult.message}")
            }

            val existingService = (serviceResult as Result.Success).data
            if (existingService == null) {
                return Result.error("Service not found: $id")
            }

            // 2. Validate update request
            if (request.pricingTiers != null && request.pricingTiers.isEmpty()) {
                return Result.error("Service must have at least one pricing tier")
            }

            if (request.features != null) {
                for (feature in request.features) {
                    if (feature.description.length > MAX_FEATURE_DESCRIPTION_LENGTH) {
                        return Result.error("Feature description must not exceed $MAX_FEATURE_DESCRIPTION_LENGTH characters")
                    }
                }
            }

            if (request.description != null && request.description.length > MAX_DESCRIPTION_LENGTH) {
                return Result.error("Service description must not exceed $MAX_DESCRIPTION_LENGTH characters")
            }

            // 3. Create diff of changes for history
            val changes = mutableMapOf<String, ChangeDetail>()

            if (request.displayName != null && request.displayName != existingService.displayName) {
                changes["displayName"] = ChangeDetail(
                    field = "displayName",
                    oldValue = existingService.displayName,
                    newValue = request.displayName
                )
            }

            if (request.description != null && request.description != existingService.description) {
                changes["description"] = ChangeDetail(
                    field = "description",
                    oldValue = existingService.description,
                    newValue = request.description
                )
            }

            if (request.pricingTiers != null && request.pricingTiers != existingService.pricingTiers) {
                changes["pricingTiers"] = ChangeDetail(
                    field = "pricingTiers",
                    oldValue = "${existingService.pricingTiers.size} tiers",
                    newValue = "${request.pricingTiers.size} tiers"
                )
            }

            if (request.features != null && request.features != existingService.features) {
                changes["features"] = ChangeDetail(
                    field = "features",
                    oldValue = "${existingService.features.size} features",
                    newValue = "${request.features.size} features"
                )
            }

            if (request.limits != null && request.limits != existingService.limits) {
                changes["limits"] = ChangeDetail(
                    field = "limits",
                    oldValue = "${existingService.limits.size} limits",
                    newValue = "${request.limits.size} limits"
                )
            }

            if (request.availabilityStart != null && request.availabilityStart != existingService.availabilityStart) {
                changes["availabilityStart"] = ChangeDetail(
                    field = "availabilityStart",
                    oldValue = existingService.availabilityStart,
                    newValue = request.availabilityStart
                )
            }

            if (request.availabilityEnd != null && request.availabilityEnd != existingService.availabilityEnd) {
                changes["availabilityEnd"] = ChangeDetail(
                    field = "availabilityEnd",
                    oldValue = existingService.availabilityEnd,
                    newValue = request.availabilityEnd
                )
            }

            // 4. Update service record
            val now = Instant.now().toString()
            val updatedService = existingService.copy(
                displayName = request.displayName ?: existingService.displayName,
                description = request.description ?: existingService.description,
                pricingTiers = request.pricingTiers ?: existingService.pricingTiers,
                features = request.features ?: existingService.features,
                limits = if (request.limits != null) request.limits else existingService.limits,
                availabilityStart = if (request.availabilityStart != null) request.availabilityStart else existingService.availabilityStart,
                availabilityEnd = if (request.availabilityEnd != null) request.availabilityEnd else existingService.availabilityEnd,
                updatedAt = now,
                updatedBy = adminEmail
            )

            val updateResult = serviceCatalogRepository.updateService(id, updatedService)
            if (updateResult is Result.Error) {
                return updateResult
            }

            // 5. Create history entry with change details
            if (changes.isNotEmpty()) {
                val history = ServiceHistory(
                    serviceId = existingService.id,
                    serviceCode = existingService.serviceCode,
                    changeType = ChangeType.UPDATED,
                    changedBy = adminEmail,
                    changedAt = now,
                    changes = changes
                )

                serviceCatalogRepository.addHistory(history)
            }

            logger.info("Service updated successfully: $id")
            Result.success((updateResult as Result.Success).data)
        } catch (e: Exception) {
            logger.error("Failed to update service: $id", e)
            Result.error("Failed to update service: ${e.message}", e)
        }
    }

    /**
     * Change the status of a service.
     * Validates status transitions and checks for active subscriptions.
     */
    override suspend fun changeServiceStatus(
        id: String,
        newStatus: ServiceStatus,
        adminEmail: String
    ): Result<ServiceConfig> {
        logger.info("Changing service status: $id to $newStatus by admin $adminEmail")

        return try {
            // 1. Fetch existing service
            val serviceResult = serviceCatalogRepository.getServiceById(id)
            if (serviceResult is Result.Error) {
                return Result.error("Failed to get service: ${serviceResult.message}")
            }

            val existingService = (serviceResult as Result.Success).data
            if (existingService == null) {
                return Result.error("Service not found: $id")
            }

            // 2. Validate status transitions
            if (existingService.status == newStatus) {
                return Result.error("Service is already in $newStatus status")
            }

            // 3. Check for active subscriptions before deactivating/archiving
            if (newStatus == ServiceStatus.INACTIVE || newStatus == ServiceStatus.ARCHIVED) {
                val activeSubsResult = serviceCatalogRepository.getActiveSubscriptionCount(existingService.serviceCode)
                val activeSubscriptions = if (activeSubsResult is Result.Success) {
                    activeSubsResult.data
                } else {
                    0L
                }

                if (activeSubscriptions > 0 && newStatus == ServiceStatus.ARCHIVED) {
                    return Result.error("Cannot archive service with $activeSubscriptions active subscriptions")
                }
            }

            // 4. Update service status
            val now = Instant.now().toString()
            val updatedService = existingService.copy(
                status = newStatus,
                updatedAt = now,
                updatedBy = adminEmail
            )

            val updateResult = serviceCatalogRepository.updateService(id, updatedService)
            if (updateResult is Result.Error) {
                return updateResult
            }

            // 5. Create history entry for status change
            val changeType = when (newStatus) {
                ServiceStatus.ARCHIVED -> ChangeType.ARCHIVED
                ServiceStatus.ACTIVE -> if (existingService.status == ServiceStatus.ARCHIVED) ChangeType.RESTORED else ChangeType.STATUS_CHANGED
                else -> ChangeType.STATUS_CHANGED
            }

            val history = ServiceHistory(
                serviceId = existingService.id,
                serviceCode = existingService.serviceCode,
                changeType = changeType,
                changedBy = adminEmail,
                changedAt = now,
                changes = mapOf(
                    "status" to ChangeDetail(
                        field = "status",
                        oldValue = existingService.status.name,
                        newValue = newStatus.name
                    )
                )
            )

            serviceCatalogRepository.addHistory(history)

            logger.info("Service status changed successfully: $id to $newStatus")
            Result.success((updateResult as Result.Success).data)
        } catch (e: Exception) {
            logger.error("Failed to change service status: $id", e)
            Result.error("Failed to change service status: ${e.message}", e)
        }
    }

    /**
     * Clone an existing service to create a new one.
     */
    override suspend fun cloneService(
        sourceId: String,
        newServiceCode: String,
        adminEmail: String
    ): Result<ServiceConfig> {
        logger.info("Cloning service: $sourceId to $newServiceCode by admin $adminEmail")

        return try {
            // 1. Fetch source service
            val serviceResult = serviceCatalogRepository.getServiceById(sourceId)
            if (serviceResult is Result.Error) {
                return Result.error("Failed to get source service: ${serviceResult.message}")
            }

            val sourceService = (serviceResult as Result.Success).data
            if (sourceService == null) {
                return Result.error("Source service not found: $sourceId")
            }

            // 2. Validate new service code
            val codeValidation = validateServiceCode(newServiceCode)
            if (codeValidation is Result.Error) {
                return codeValidation.mapError { msg, ex -> Pair(msg, ex) }
            }

            // Check for duplicate
            val existingService = serviceCatalogRepository.getServiceByCode(newServiceCode)
            if (existingService is Result.Success && existingService.data != null) {
                return Result.error("Service code already exists: $newServiceCode")
            }

            // 3. Create new service with copied data
            val createRequest = CreateServiceRequest(
                serviceCode = newServiceCode,
                displayName = "${sourceService.displayName} (Copy)",
                description = sourceService.description,
                pricingTiers = sourceService.pricingTiers,
                features = sourceService.features,
                limits = sourceService.limits,
                availabilityStart = sourceService.availabilityStart,
                availabilityEnd = sourceService.availabilityEnd
            )

            val result = createService(createRequest, adminEmail)
            result
        } catch (e: Exception) {
            logger.error("Failed to clone service: $sourceId", e)
            Result.error("Failed to clone service: ${e.message}", e)
        }
    }

    /**
     * Get analytics for a specific service.
     * Computes subscription metrics, revenue, trends, and popular pricing tiers.
     */
    override suspend fun getServiceAnalytics(serviceCode: String): Result<ServiceAnalytics> {
        logger.debug("Getting analytics for service: $serviceCode")

        return try {
            // 1. Query subscriptions for service code
            val usersResult = userRepository.getAllUsers(page = 1, pageSize = Int.MAX_VALUE)
            if (usersResult is Result.Error) {
                return Result.error("Failed to get users: ${usersResult.message}")
            }

            val (users, _) = (usersResult as Result.Success).data

            // 2. Aggregate active vs total subscriptions
            var activeSubscriptions = 0L
            var totalSubscriptions = 0L
            val pricingTierCounts = mutableMapOf<String, Long>()
            var totalDurationDays = 0L
            val monthlyData = mutableMapOf<String, MonthlyData>()

            for (user in users) {
                for (subscription in user.subscriptions) {
                    if (subscription.service.name == serviceCode) {
                        totalSubscriptions++

                        if (subscription.status == SubscriptionStatus.ACTIVE) {
                            activeSubscriptions++
                        }

                        // Track pricing tier popularity (using service name as identifier)
                        val tierKey = subscription.service.name
                        pricingTierCounts[tierKey] = pricingTierCounts.getOrDefault(tierKey, 0) + 1

                        // Calculate duration
                        try {
                            val start = Instant.parse(subscription.startDate)
                            val end = Instant.parse(subscription.endDate)
                            val durationDays = java.time.Duration.between(start, end).toDays()
                            totalDurationDays += durationDays
                        } catch (e: Exception) {
                            logger.warn("Failed to parse subscription dates", e)
                        }

                        // Track monthly trends
                        try {
                            val startDate = Instant.parse(subscription.startDate)
                            val month = YearMonth.from(startDate.atZone(java.time.ZoneId.systemDefault()))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM"))

                            val monthData = monthlyData.getOrPut(month) { MonthlyData() }
                            monthData.count += 1
                        } catch (e: Exception) {
                            logger.warn("Failed to parse subscription start date", e)
                        }
                    }
                }
            }

            // 3. Calculate total revenue from payments
            var totalRevenue = 0.0
            val paymentsResult = paymentRepository.getAllPayments(1, Int.MAX_VALUE)

            if (paymentsResult is Result.Success) {
                val (payments, _) = paymentsResult.data
                for (payment in payments) {
                    if (payment.serviceType?.name == serviceCode && payment.status == "captured") {
                        val amount = payment.amount ?: 0
                        totalRevenue += amount / 100.0 // Convert paise to rupees

                        // Add revenue to monthly data
                        try {
                            val paymentDate = Instant.parse(payment.createdAt)
                            val month = YearMonth.from(paymentDate.atZone(java.time.ZoneId.systemDefault()))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM"))

                            val monthData = monthlyData.getOrPut(month) { MonthlyData() }
                            monthData.revenue += amount / 100.0
                        } catch (e: Exception) {
                            logger.warn("Failed to parse payment date", e)
                        }
                    }
                }
            }

            // 4. Generate monthly trend data (last 12 months)
            val now = YearMonth.now()
            val monthlyTrend = (0..11).map { i ->
                val month = now.minusMonths(i.toLong())
                val monthKey = month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val data = monthlyData[monthKey]

                MonthlySubscriptionData(
                    month = monthKey,
                    count = data?.count ?: 0L,
                    revenue = data?.revenue ?: 0.0
                )
            }.reversed()

            // 5. Identify most popular pricing tier
            val popularPricingTier = pricingTierCounts.maxByOrNull { it.value }?.key

            // 6. Calculate average subscription duration
            val averageDuration = if (totalSubscriptions > 0) {
                totalDurationDays.toDouble() / totalSubscriptions
            } else {
                0.0
            }

            val analytics = ServiceAnalytics(
                activeSubscriptions = activeSubscriptions,
                totalSubscriptions = totalSubscriptions,
                totalRevenue = totalRevenue,
                monthlyTrend = monthlyTrend,
                popularPricingTier = popularPricingTier,
                averageDuration = averageDuration
            )

            Result.success(analytics)
        } catch (e: Exception) {
            logger.error("Failed to get service analytics: $serviceCode", e)
            Result.error("Failed to get service analytics: ${e.message}", e)
        }
    }

    /**
     * Validate a service code format.
     * Service code must be uppercase alphanumeric with underscores.
     */
    override suspend fun validateServiceCode(code: String): Result<Boolean> {
        return try {
            if (code.isBlank()) {
                Result.error("Service code cannot be empty")
            } else if (!SERVICE_CODE_REGEX.matches(code)) {
                Result.error("Service code must be uppercase alphanumeric with underscores (e.g., PREMIUM_ONE)")
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            logger.error("Failed to validate service code: $code", e)
            Result.error("Failed to validate service code: ${e.message}", e)
        }
    }
}
