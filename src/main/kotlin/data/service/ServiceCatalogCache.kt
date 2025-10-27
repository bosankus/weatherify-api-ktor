package data.service

import bose.ankush.data.model.ServiceConfig
import domain.model.Result
import domain.repository.ServiceCatalogRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for service catalog to reduce database queries.
 * Cache entries expire after a configurable TTL.
 */
class ServiceCatalogCache(
    private val repository: ServiceCatalogRepository,
    private val cacheDurationMinutes: Long = 15
) {
    private val logger = LoggerFactory.getLogger(ServiceCatalogCache::class.java)
    private val cacheByCode = ConcurrentHashMap<String, CachedService>()
    private val cacheById = ConcurrentHashMap<String, CachedService>()

    data class CachedService(
        val service: ServiceConfig,
        val expiresAt: Instant
    )

    /**
     * Get service by code with caching.
     * Returns cached value if available and not expired, otherwise fetches from repository.
     */
    suspend fun getServiceByCode(code: String): Result<ServiceConfig?> {
        val cached = cacheByCode[code]
        if (cached != null && Instant.now().isBefore(cached.expiresAt)) {
            logger.debug("Cache hit for service code: $code")
            return Result.success(cached.service)
        }

        logger.debug("Cache miss for service code: $code, fetching from repository")
        return when (val result = repository.getServiceByCode(code)) {
            is Result.Success -> {
                result.data?.let { service ->
                    val expiresAt = Instant.now().plusSeconds(cacheDurationMinutes * 60)
                    val cachedService = CachedService(service, expiresAt)
                    cacheByCode[code] = cachedService
                    cacheById[service.id] = cachedService
                    logger.debug("Cached service: $code until $expiresAt")
                }
                result
            }

            is Result.Error -> result
        }
    }

    /**
     * Get service by ID with caching.
     * Returns cached value if available and not expired, otherwise fetches from repository.
     */
    suspend fun getServiceById(id: String): Result<ServiceConfig?> {
        val cached = cacheById[id]
        if (cached != null && Instant.now().isBefore(cached.expiresAt)) {
            logger.debug("Cache hit for service ID: $id")
            return Result.success(cached.service)
        }

        logger.debug("Cache miss for service ID: $id, fetching from repository")
        return when (val result = repository.getServiceById(id)) {
            is Result.Success -> {
                result.data?.let { service ->
                    val expiresAt = Instant.now().plusSeconds(cacheDurationMinutes * 60)
                    val cachedService = CachedService(service, expiresAt)
                    cacheById[id] = cachedService
                    cacheByCode[service.serviceCode] = cachedService
                    logger.debug("Cached service: $id until $expiresAt")
                }
                result
            }

            is Result.Error -> result
        }
    }

    /**
     * Invalidate cache entry for a specific service code.
     * Should be called when a service is updated or deleted.
     */
    fun invalidateByCode(code: String) {
        cacheByCode.remove(code)?.let {
            cacheById.remove(it.service.id)
            logger.debug("Invalidated cache for service code: $code")
        }
    }

    /**
     * Invalidate cache entry for a specific service ID.
     * Should be called when a service is updated or deleted.
     */
    fun invalidateById(id: String) {
        cacheById.remove(id)?.let {
            cacheByCode.remove(it.service.serviceCode)
            logger.debug("Invalidated cache for service ID: $id")
        }
    }

    /**
     * Clear all cache entries.
     * Useful for testing or manual cache refresh.
     */
    fun clearAll() {
        val size = cacheByCode.size
        cacheByCode.clear()
        cacheById.clear()
        logger.info("Cleared all cache entries ($size services)")
    }

    /**
     * Get cache statistics for monitoring.
     */
    fun getStats(): CacheStats {
        val now = Instant.now()
        val validEntries = cacheByCode.values.count { now.isBefore(it.expiresAt) }
        val expiredEntries = cacheByCode.size - validEntries

        return CacheStats(
            totalEntries = cacheByCode.size,
            validEntries = validEntries,
            expiredEntries = expiredEntries
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int
    )
}
