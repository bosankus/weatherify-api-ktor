package util

import com.androidplay.weatherify.domain.ServiceConfig
import com.androidplay.weatherify.domain.ServiceStatus
import com.androidplay.weatherify.domain.ServiceType
import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.ServiceCatalogRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** Backward-compatibility bridge: maps legacy ServiceType enum to database-driven ServiceConfig. */
@Suppress("unused")
object ServiceTypeResolver {
    private val logger = LoggerFactory.getLogger(ServiceTypeResolver::class.java)
    private val cache = ConcurrentHashMap<String, ServiceConfig>()

    // Repository will be injected during initialization
    private var repository: ServiceCatalogRepository? = null

    fun initialize(serviceCatalogRepository: ServiceCatalogRepository) {
        repository = serviceCatalogRepository
        logger.info("ServiceTypeResolver initialized with repository")
    }

    suspend fun resolveServiceType(serviceType: ServiceType): Result<ServiceConfig> {
        val code = serviceType.name

        cache[code]?.let { return Result.Success(it) }

        val repo = repository ?: run {
            logger.error("ServiceTypeResolver not initialized - repository is null")
            return Result.Error("ServiceTypeResolver not initialized", Exception("Repository is null"))
        }

        logger.debug("Cache miss for service type: $code, fetching from database")
        return when (val result = repo.getServiceByCode(code)) {
            is Result.Success -> {
                val serviceConfig = result.data
                if (serviceConfig != null) {
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

    suspend fun getAllServiceTypes(): Result<List<ServiceType>> {
        val repo = repository ?: run {
            logger.error("ServiceTypeResolver not initialized - repository is null")
            return Result.Error("ServiceTypeResolver not initialized", Exception("Repository is null"))
        }

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
                        ServiceType.valueOf(service.serviceCode)
                    } catch (_: IllegalArgumentException) {
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

    fun invalidateCache(serviceType: ServiceType) {
        val code = serviceType.name
        cache.remove(code)
        logger.info("Cache invalidated for service type: $code")
    }

    fun clearCache() {
        cache.clear()
        logger.info("Service type cache cleared")
    }

    fun getCacheSize(): Int = cache.size

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
