package data.repository

import bose.ankush.data.model.MonthlyRefundData
import bose.ankush.data.model.Refund
import bose.ankush.data.model.RefundSpeed
import bose.ankush.data.model.RefundStatus
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.RefundRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Implementation of RefundRepository that uses MongoDB for data storage.
 * Provides operations for managing refund data.
 */
class RefundRepositoryImpl(private val databaseModule: DatabaseModule) : RefundRepository {
    private val logger = LoggerFactory.getLogger(RefundRepositoryImpl::class.java)

    private fun getRefundsCollection() = databaseModule.getRefundsCollection()

    override suspend fun createRefund(refund: Refund): Result<Boolean> {
        logger.debug("Creating refund: ${refund.refundId}")
        return try {
            getRefundsCollection().insertOne(refund)
            logger.info("Refund created successfully: ${refund.refundId}")
            Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to create refund: ${refund.refundId}", e)
            Result.error("Failed to create refund: ${e.message}", e)
        }
    }

    override suspend fun getRefundById(refundId: String): Result<Refund?> {
        logger.debug("Finding refund by ID: $refundId")
        return try {
            val query = Document("refundId", refundId)
            val refund = getRefundsCollection().find(query).firstOrNull()
            logger.debug("Refund found: ${refund != null}")
            Result.success(refund)
        } catch (e: Exception) {
            logger.error("Failed to find refund by ID: $refundId", e)
            Result.error("Failed to find refund: ${e.message}", e)
        }
    }

    override suspend fun getRefundsByPaymentId(paymentId: String): Result<List<Refund>> {
        logger.debug("Finding refunds by payment ID: $paymentId")
        return try {
            val query = Document("paymentId", paymentId)
            val refunds = getRefundsCollection()
                .find(query)
                .sort(Document("createdAt", -1))
                .toList()
            logger.debug("Found ${refunds.size} refunds for payment ID: $paymentId")
            Result.success(refunds)
        } catch (e: Exception) {
            logger.error("Failed to find refunds by payment ID: $paymentId", e)
            Result.error("Failed to find refunds: ${e.message}", e)
        }
    }

    override suspend fun getRefundsByUserEmail(userEmail: String): Result<List<Refund>> {
        logger.debug("Finding refunds by user email: $userEmail")
        return try {
            val query = Document("userEmail", userEmail)
            val refunds = getRefundsCollection()
                .find(query)
                .sort(Document("createdAt", -1))
                .toList()
            logger.debug("Found ${refunds.size} refunds for user email: $userEmail")
            Result.success(refunds)
        } catch (e: Exception) {
            logger.error("Failed to find refunds by user email: $userEmail", e)
            Result.error("Failed to find refunds: ${e.message}", e)
        }
    }

    override suspend fun getAllRefunds(
        page: Int,
        pageSize: Int,
        status: RefundStatus?,
        startDate: String?,
        endDate: String?
    ): Result<Pair<List<Refund>, Long>> {
        logger.debug("Getting all refunds with page: $page, pageSize: $pageSize, status: $status")
        return try {
            // Build query with filters
            val filters = mutableListOf<org.bson.conversions.Bson>()

            if (status != null) {
                filters.add(Filters.eq("status", status.name))
            }

            if (startDate != null) {
                filters.add(Filters.gte("createdAt", startDate))
            }

            if (endDate != null) {
                filters.add(Filters.lte("createdAt", endDate))
            }

            val query = if (filters.isNotEmpty()) {
                Filters.and(filters)
            } else {
                Document()
            }

            val skip = (page - 1) * pageSize
            val totalCount = getRefundsCollection().countDocuments(query)
            val refunds = getRefundsCollection()
                .find(query)
                .sort(Sorts.descending("createdAt"))
                .skip(skip)
                .limit(pageSize)
                .toList()

            logger.debug("Retrieved ${refunds.size} refunds out of $totalCount total")
            Result.success(Pair(refunds, totalCount))
        } catch (e: Exception) {
            logger.error("Failed to get all refunds", e)
            Result.error("Failed to get refunds: ${e.message}", e)
        }
    }

    override suspend fun updateRefundStatus(
        refundId: String,
        status: RefundStatus,
        processedAt: String?,
        errorCode: String?,
        errorDescription: String?
    ): Result<Boolean> {
        logger.debug("Updating refund status: $refundId to $status")
        return try {
            val query = Document("refundId", refundId)
            val updates = Document("\$set", Document().apply {
                append("status", status.name)
                if (processedAt != null) {
                    append("processedAt", processedAt)
                }
                if (status == RefundStatus.FAILED) {
                    append("failedAt", Instant.now().toString())
                }
                if (errorCode != null) {
                    append("errorCode", errorCode)
                }
                if (errorDescription != null) {
                    append("errorDescription", errorDescription)
                }
            })

            val result = getRefundsCollection().updateOne(query, updates)
            val success = result.modifiedCount > 0

            if (success) {
                logger.info("Refund status updated successfully: $refundId to $status")
            } else {
                logger.warn("No refund found to update: $refundId")
            }

            Result.success(success)
        } catch (e: Exception) {
            logger.error("Failed to update refund status: $refundId", e)
            Result.error("Failed to update refund status: ${e.message}", e)
        }
    }

    override suspend fun getTotalRefundedAmount(): Result<Double> {
        logger.debug("Calculating total refunded amount")
        return try {
            val pipeline = listOf(
                Aggregates.match(Filters.eq("status", RefundStatus.PROCESSED.name)),
                Aggregates.group(null, Accumulators.sum("totalAmount", "\$amount"))
            )

            val result = getRefundsCollection()
                .aggregate<Document>(pipeline)
                .firstOrNull()

            val totalAmount = result?.getInteger("totalAmount")?.toDouble() ?: 0.0
            logger.debug("Total refunded amount: $totalAmount paise")
            Result.success(totalAmount)
        } catch (e: Exception) {
            logger.error("Failed to calculate total refunded amount", e)
            Result.error("Failed to calculate total refunded amount: ${e.message}", e)
        }
    }

    override suspend fun getMonthlyRefundedAmount(month: String): Result<Double> {
        logger.debug("Calculating monthly refunded amount for: $month")
        return try {
            // Parse month (format: "YYYY-MM")
            val yearMonth = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"))
            val startDate = yearMonth.atDay(1).atStartOfDay().toString()
            val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59).toString()

            val pipeline = listOf(
                Aggregates.match(
                    Filters.and(
                        Filters.eq("status", RefundStatus.PROCESSED.name),
                        Filters.gte("createdAt", startDate),
                        Filters.lte("createdAt", endDate)
                    )
                ),
                Aggregates.group(null, Accumulators.sum("totalAmount", "\$amount"))
            )

            val result = getRefundsCollection()
                .aggregate<Document>(pipeline)
                .firstOrNull()

            val totalAmount = result?.getInteger("totalAmount")?.toDouble() ?: 0.0
            logger.debug("Monthly refunded amount for $month: $totalAmount paise")
            Result.success(totalAmount)
        } catch (e: Exception) {
            logger.error("Failed to calculate monthly refunded amount for: $month", e)
            Result.error("Failed to calculate monthly refunded amount: ${e.message}", e)
        }
    }

    override suspend fun getRefundCountBySpeed(): Result<Pair<Int, Int>> {
        logger.debug("Calculating refund count by speed")
        return try {
            // Count instant refunds (speedProcessed = OPTIMUM or "instant")
            val instantQuery = Filters.or(
                Filters.eq("speedProcessed", RefundSpeed.OPTIMUM.name),
                Filters.eq("speedProcessed", "instant")
            )
            val instantCount = getRefundsCollection().countDocuments(instantQuery).toInt()

            // Count normal refunds
            val normalQuery = Filters.or(
                Filters.eq("speedProcessed", RefundSpeed.NORMAL.name),
                Filters.eq("speedProcessed", "normal")
            )
            val normalCount = getRefundsCollection().countDocuments(normalQuery).toInt()

            logger.debug("Refund count by speed - Instant: $instantCount, Normal: $normalCount")
            Result.success(Pair(instantCount, normalCount))
        } catch (e: Exception) {
            logger.error("Failed to calculate refund count by speed", e)
            Result.error("Failed to calculate refund count by speed: ${e.message}", e)
        }
    }

    override suspend fun getAverageProcessingTime(): Result<Double> {
        logger.debug("Calculating average processing time")
        return try {
            val query = Filters.and(
                Filters.eq("status", RefundStatus.PROCESSED.name),
                Filters.ne("processedAt", null)
            )

            val refunds = getRefundsCollection()
                .find(query)
                .toList()

            if (refunds.isEmpty()) {
                logger.debug("No processed refunds found")
                return Result.success(0.0)
            }

            val totalHours = refunds.mapNotNull { refund ->
                try {
                    val createdAt = Instant.parse(refund.createdAt)
                    val processedAt = refund.processedAt?.let { Instant.parse(it) }

                    if (processedAt != null) {
                        val duration = Duration.between(createdAt, processedAt)
                        duration.toHours().toDouble()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse timestamps for refund: ${refund.refundId}", e)
                    null
                }
            }.sum()

            val averageHours = totalHours / refunds.size
            logger.debug("Average processing time: $averageHours hours")
            Result.success(averageHours)
        } catch (e: Exception) {
            logger.error("Failed to calculate average processing time", e)
            Result.error("Failed to calculate average processing time: ${e.message}", e)
        }
    }

    override suspend fun getMonthlyRefundData(months: Int): Result<List<MonthlyRefundData>> {
        logger.debug("Getting monthly refund data for last $months months")
        return try {
            val now = YearMonth.now()
            val monthlyData = mutableListOf<MonthlyRefundData>()

            for (i in (months - 1) downTo 0) {
                val targetMonth = now.minusMonths(i.toLong())
                val monthStr = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val startDate = targetMonth.atDay(1).atStartOfDay().toString()
                val endDate = targetMonth.atEndOfMonth().atTime(23, 59, 59).toString()

                val pipeline = listOf(
                    Aggregates.match(
                        Filters.and(
                            Filters.eq("status", RefundStatus.PROCESSED.name),
                            Filters.gte("createdAt", startDate),
                            Filters.lte("createdAt", endDate)
                        )
                    ),
                    Aggregates.group(
                        null,
                        Accumulators.sum("totalAmount", "\$amount"),
                        Accumulators.sum("count", 1)
                    )
                )

                val result = getRefundsCollection()
                    .aggregate<Document>(pipeline)
                    .firstOrNull()

                val totalAmount = result?.getInteger("totalAmount")?.toDouble() ?: 0.0
                val count = result?.getInteger("count") ?: 0

                monthlyData.add(
                    MonthlyRefundData(
                        month = monthStr,
                        refundAmount = totalAmount / 100.0, // Convert paise to rupees
                        refundCount = count
                    )
                )
            }

            logger.debug("Retrieved monthly refund data for $months months")
            Result.success(monthlyData)
        } catch (e: Exception) {
            logger.error("Failed to get monthly refund data", e)
            Result.error("Failed to get monthly refund data: ${e.message}", e)
        }
    }

    override suspend fun getTotalRefundedForPayment(paymentId: String): Result<Int> {
        logger.debug("Calculating total refunded for payment: $paymentId")
        return try {
            val pipeline = listOf(
                Aggregates.match(
                    Filters.and(
                        Filters.eq("paymentId", paymentId),
                        Filters.eq("status", RefundStatus.PROCESSED.name)
                    )
                ),
                Aggregates.group(null, Accumulators.sum("totalAmount", "\$amount"))
            )

            val result = getRefundsCollection()
                .aggregate<Document>(pipeline)
                .firstOrNull()

            val totalAmount = result?.getInteger("totalAmount") ?: 0
            logger.debug("Total refunded for payment $paymentId: $totalAmount paise")
            Result.success(totalAmount)
        } catch (e: Exception) {
            logger.error("Failed to calculate total refunded for payment: $paymentId", e)
            Result.error("Failed to calculate total refunded for payment: ${e.message}", e)
        }
    }
}
