package data.service

import bose.ankush.data.model.MonthlyRevenue
import domain.model.Result
import domain.repository.PaymentRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.RedisCache

/**
 * Caching layer over PaymentRepository for expensive aggregation queries.
 *
 * All heavy analytics queries (total revenue, monthly breakdowns, service
 * counts) are served from Redis when available. Cache entries are
 * invalidated whenever a new payment is stored or a refund is processed.
 *
 * TTLs are intentionally generous — these numbers change at most a few
 * times per hour in normal operation, and the admin dashboard should not
 * hammer MongoDB aggregations on every page load.
 */
class PaymentAnalyticsCache(
    private val paymentRepository: PaymentRepository,
    private val redis: RedisCache
) {
    private val logger = LoggerFactory.getLogger(PaymentAnalyticsCache::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PREFIX = "analytics:"
        private const val TTL_TOTALS = 30 * 60L          // 30 minutes
        private const val TTL_MONTHLY_REVENUE = 2 * 3600L // 2 hours
        private const val TTL_STATUS_COUNTS = 30 * 60L    // 30 minutes
        private const val TTL_SERVICE_COUNTS = 60 * 60L   // 1 hour
        private const val TTL_SERVICE_ANALYTICS = 2 * 3600L // 2 hours
    }

    // ---------------------------------------------------------------------------
    // Cached wrappers
    // ---------------------------------------------------------------------------

    /** Total revenue (sum of all verified payments in rupees). Cached 30 min. */
    suspend fun getTotalRevenue(): Result<Double> {
        val key = "${PREFIX}total-revenue"
        redis.get(key)?.let { cached ->
            logger.debug("Cache hit: {}", key)
            return Result.success(cached.toDouble())
        }
        return paymentRepository.getTotalRevenue().also { result ->
            if (result is Result.Success) {
                redis.set(key, result.data.toString(), TTL_TOTALS)
            }
        }
    }

    /** Verified payments aggregate (total paise, count). Cached 30 min. */
    suspend fun getVerifiedPaymentsAggregate(): Result<Pair<Long, Long>> {
        val key = "${PREFIX}verified-aggregate"
        redis.get(key)?.let { cached ->
            logger.debug("Cache hit: {}", key)
            val parts = cached.split(",")
            return Result.success(Pair(parts[0].toLong(), parts[1].toLong()))
        }
        return paymentRepository.getVerifiedPaymentsAggregate().also { result ->
            if (result is Result.Success) {
                redis.set(key, "${result.data.first},${result.data.second}", TTL_TOTALS)
            }
        }
    }

    /** Monthly revenue for a date range. Cached 2 hours keyed on the range. */
    suspend fun getMonthlyRevenue(startDate: String, endDate: String): Result<List<MonthlyRevenue>> {
        val key = "${PREFIX}monthly-revenue:${startDate.take(10)}:${endDate.take(10)}"
        redis.get(key)?.let { cached ->
            logger.debug("Cache hit: {}", key)
            return Result.success(json.decodeFromString(cached))
        }
        return paymentRepository.getMonthlyRevenue(startDate, endDate).also { result ->
            if (result is Result.Success) {
                redis.set(key, json.encodeToString(result.data), TTL_MONTHLY_REVENUE)
            }
        }
    }

    /** Payment counts grouped by status. Cached 30 min. */
    suspend fun getPaymentCountByStatus(): Result<Map<String, Long>> {
        val key = "${PREFIX}count-by-status"
        redis.get(key)?.let { cached ->
            logger.debug("Cache hit: {}", key)
            return Result.success(json.decodeFromString(cached))
        }
        return paymentRepository.getPaymentCountByStatus().also { result ->
            if (result is Result.Success) {
                redis.set(key, json.encodeToString(result.data), TTL_STATUS_COUNTS)
            }
        }
    }

    /** Payment counts grouped by service code. Cached 1 hour. */
    suspend fun getPaymentCountByServiceCode(): Result<Map<String, Long>> {
        val key = "${PREFIX}count-by-service"
        redis.get(key)?.let { cached ->
            logger.debug("Cache hit: {}", key)
            return Result.success(json.decodeFromString(cached))
        }
        return paymentRepository.getPaymentCountByServiceCode().also { result ->
            if (result is Result.Success) {
                redis.set(key, json.encodeToString(result.data), TTL_SERVICE_COUNTS)
            }
        }
    }

    /** Monthly service analytics (count, revenue, monthly breakdown). Cached 2 hours. */
    suspend fun getServiceAnalyticsAggregate(
        serviceCode: String
    ): Result<Triple<Long, Long, Map<String, Pair<Long, Long>>>> {
        val key = "${PREFIX}service-analytics:$serviceCode"
        redis.get(key)?.let { cached ->
            logger.debug("Cache hit: {}", key)
            val dto = json.decodeFromString<ServiceAnalyticsDto>(cached)
            return Result.success(Triple(dto.totalCount, dto.totalRevenue, dto.monthly.fromDto()))
        }
        return paymentRepository.getServiceAnalyticsAggregate(serviceCode).also { result ->
            if (result is Result.Success) {
                val dto = ServiceAnalyticsDto(
                    totalCount = result.data.first,
                    totalRevenue = result.data.second,
                    monthly = result.data.third.toDto()
                )
                redis.set(key, json.encodeToString(dto), TTL_SERVICE_ANALYTICS)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Cache invalidation
    // ---------------------------------------------------------------------------

    /**
     * Invalidate all analytics cache entries.
     * Call this after any payment is created, verified, or refunded.
     */
    suspend fun invalidateAll() {
        logger.info("Invalidating all payment analytics cache entries")
        redis.invalidateByPrefix(PREFIX)
    }

    /**
     * Invalidate only service-specific analytics for [serviceCode].
     * Use this for targeted invalidation when a single service's data changes.
     */
    suspend fun invalidateService(serviceCode: String) {
        logger.debug("Invalidating service analytics cache for: {}", serviceCode)
        redis.invalidateByPrefix("${PREFIX}service-analytics:$serviceCode")
        // Also invalidate the aggregates that roll up all services
        redis.invalidateByPrefix("${PREFIX}count-by-service")
    }

    // ---------------------------------------------------------------------------
    // DTOs for serialising types not natively supported by kotlinx.serialization
    // ---------------------------------------------------------------------------

    @kotlinx.serialization.Serializable
    private data class LongPairDto(val first: Long, val second: Long)

    @kotlinx.serialization.Serializable
    private data class ServiceAnalyticsDto(
        val totalCount: Long,
        val totalRevenue: Long,
        val monthly: Map<String, LongPairDto>
    )

    private fun Map<String, Pair<Long, Long>>.toDto(): Map<String, LongPairDto> =
        mapValues { (_, v) -> LongPairDto(v.first, v.second) }

    private fun Map<String, LongPairDto>.fromDto(): Map<String, Pair<Long, Long>> =
        mapValues { (_, v) -> Pair(v.first, v.second) }
}
