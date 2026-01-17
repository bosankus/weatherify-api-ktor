package util

import bose.ankush.data.model.ServiceConfig
import bose.ankush.data.model.ServiceStatus
import bose.ankush.data.model.ServiceType
import domain.model.Result
import domain.repository.ServiceCatalogRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * ServiceTypeResolver provides backward compatibility between the legacy ServiceType enum
 * and the new database-driven ServiceConfig system.
 *
 * This utility caches resolved service configurations to minimize database queries
 * and provides methods to resolve service types to their full configurations.
 */
@Suppress("unused")
object ServiceTypeResolver {
    private val logger = LoggerFactory.getLogger(ServiceTypeResolver::class.java)
    private val cache = ConcurrentHashMap<String, ServiceConfig>()

    // Repository will be injected during initialization
    private var repository: ServiceCatalogRepository? = null

    /**
     * Initialize the resolver with a ServiceCatalogRepository instance.
     * This must be called during application startup before using the resolver.
     */
    fun initialize(serviceCatalogRepository: ServiceCatalogRepository) {
        repository = serviceCatalogRepository
        logger.info("ServiceTypeResolver initialized with repository")
    }

    /**
     * Resolves a ServiceType enum to its full ServiceConfig from the database.
     * Results are cached to improve performance.
     *
     * @param serviceType The ServiceType enum value to resolve
     * @return Result containing the ServiceConfig or an error
     */
    suspend fun resolveServiceType(serviceType: ServiceType): Result<ServiceConfig> {
        val code = serviceType.name

        // Check cache first
        cache[code]?.let {
            logger.debug("Cache hit for service type: $code")
            return Result.Success(it)
        }

        // Repository must be initialized
        val repo = repository ?: run {
            logger.error("ServiceTypeResolver not initialized - repository is null")
            return Result.Error("ServiceTypeResolver not initialized", Exception("Repository is null"))
        }

        // Fetch from database
        logger.debug("Cache miss for service type: $code, fetching from database")
        return when (val result = repo.getServiceByCode(code)) {
            is Result.Success -> {
                val serviceConfig = result.data
                if (serviceConfig != null) {
                    // Cache the result
                    cache[code] = serviceConfig
                    logger.info("Successfully resolved and cached service type: $code")
                    Result.Success(serviceConfig)
                } else {
                    logger.error("Service type not found: $code")
                    Result.Error("Service type not found: $code")
                }
            }

            is Result.Error -> {
                logger.error("Failed to resolve service type: $code - ${result.message}", result.exception)
                Result.Error(result.message, result.exception)
            }
        }
    }

    /**
     * Retrieves all active service types from the database.
     * This method returns ServiceType enum values for all active services in the catalog.
     *
     * @return Result containing a list of ServiceType enum values or an error
     */
    suspend fun getAllServiceTypes(): Result<List<ServiceType>> {
        val repo = repository ?: run {
            logger.error("ServiceTypeResolver not initialized - repository is null")
            return Result.Error("ServiceTypeResolver not initialized", Exception("Repository is null"))
        }

        // Fetch all active services
        return when (val result = repo.getAllServices(
            page = 1,
            pageSize = 100,
            status = ServiceStatus.ACTIVE,
            searchQuery = null
        )) {
            is Result.Success -> {
                val services = result.data.first
                val totalCount = result.data.second
                val serviceTypes = services.mapNotNull { service ->
                    try {
                        // Try to convert service code to ServiceType enum
                        ServiceType.valueOf(service.serviceCode)
                    } catch (_: IllegalArgumentException) {
                        // Service code doesn't match any enum value
                        logger.warn("Service code ${service.serviceCode} does not match any ServiceType enum value")
                        null
                    }
                }
                logger.info("Retrieved ${serviceTypes.size} active service types out of $totalCount total services")
                Result.Success(serviceTypes)
            }

            is Result.Error -> {
                logger.error("Failed to retrieve all service types - ${result.message}", result.exception)
                Result.Error(result.message, result.exception)
            }
        }
    }

    /**
     * Clears the cache for a specific service type.
     * This should be called when a service configuration is updated.
     *
     * @param serviceType The ServiceType to clear from cache
     */
    fun invalidateCache(serviceType: ServiceType) {
        val code = serviceType.name
        cache.remove(code)
        logger.info("Cache invalidated for service type: $code")
    }

    /**
     * Clears the entire cache.
     * This should be called when multiple services are updated or during maintenance.
     */
    fun clearCache() {
        cache.clear()
        logger.info("Service type cache cleared")
    }

    /**
     * Gets the current cache size for monitoring purposes.
     *
     * @return The number of cached service configurations
     */
    fun getCacheSize(): Int = cache.size

    /**
     * Helper method to get the default pricing tier for a service type.
     * This is useful for payment creation.
     *
     * @param serviceType The ServiceType to get pricing for
     * @return The default pricing tier amount in smallest currency unit, or null if not found
     */
    suspend fun getDefaultPricing(serviceType: ServiceType): Int? {
        return when (val result = resolveServiceType(serviceType)) {
            is Result.Success -> {
                result.data.pricingTiers.firstOrNull { it.isDefault }?.amount
                    ?: result.data.pricingTiers.firstOrNull()?.amount
            }

            is Result.Error -> {
                logger.error("Failed to get default pricing for $serviceType", result.exception)
                null
            }
        }
    }

}
