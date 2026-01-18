package data.repository

import bose.ankush.data.model.MonthlyRevenue
import bose.ankush.data.model.Payment
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.PaymentRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.conversions.Bson
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Implementation of PaymentRepository that uses MongoDB for data storage.
 * Provides read-only access to payment data.
 */
class PaymentRepositoryImpl(private val databaseModule: DatabaseModule) : PaymentRepository {
    private val logger = LoggerFactory.getLogger(PaymentRepositoryImpl::class.java)

    private fun getPaymentsCollection() = databaseModule.getPaymentsCollection()

    override suspend fun getPaymentByTransactionId(transactionId: String): Result<Payment?> {
        logger.debug("Finding payment by transaction ID: $transactionId")
        return try {
            // Use the actual MongoDB field name (razorpay_payment_id) not the Kotlin property name
            val query = Document("razorpay_payment_id", transactionId)
            val payment = getPaymentsCollection().find(query).firstOrNull()
            logger.debug("Payment found: ${payment != null}")
            Result.success(payment)
        } catch (e: Exception) {
            logger.error("Failed to find payment by transaction ID: $transactionId", e)
            Result.error("Failed to find payment: ${e.message}", e)
        }
    }

    override suspend fun getPaymentsByUserId(userId: String): Result<List<Payment>> {
        logger.debug("Finding payments by user ID: $userId")
        return try {
            val query = Document("userId", userId)
            val payments = getPaymentsCollection().find(query).toList()
            logger.debug("Found ${payments.size} payments for user ID: $userId")
            Result.success(payments)
        } catch (e: Exception) {
            logger.error("Failed to find payments by user ID: $userId", e)
            Result.error("Failed to find payments: ${e.message}", e)
        }
    }

    override suspend fun getPaymentsByUserEmail(userEmail: String): Result<List<Payment>> {
        logger.debug("Finding payments by user email: $userEmail")
        return try {
            val query = Document("userEmail", userEmail)
            val payments = getPaymentsCollection().find(query).toList()
            logger.debug("Found ${payments.size} payments for user email: $userEmail")
            Result.success(payments)
        } catch (e: Exception) {
            logger.error("Failed to find payments by user email: $userEmail", e)
            Result.error("Failed to find payments: ${e.message}", e)
        }
    }

    override suspend fun getAllPayments(page: Int, pageSize: Int): Result<Pair<List<Payment>, Long>> {
        logger.debug("Getting all payments with page: $page, pageSize: $pageSize")
        return try {
            val skip = (page - 1) * pageSize
            val totalCount = getPaymentsCollection().countDocuments()
            val payments = getPaymentsCollection()
                .find()
                .sort(Document("createdAt", -1))
                .skip(skip)
                .limit(pageSize)
                .toList()
            logger.debug("Retrieved ${payments.size} payments out of $totalCount total")
            Result.success(Pair(payments, totalCount))
        } catch (e: Exception) {
            logger.error("Failed to get all payments", e)
            Result.error("Failed to get payments: ${e.message}", e)
        }
    }

    override suspend fun getTotalRevenue(): Result<Double> {
        logger.debug("Calculating total revenue using aggregation")
        return try {
            val pipeline = listOf(
                Aggregates.match(Filters.eq("status", "verified")),
                Aggregates.group(
                    null,
                    Accumulators.sum("totalAmount", "\$amount")
                )
            )
            val result = getPaymentsCollection().aggregate<Document>(pipeline).firstOrNull()
            val totalAmount = if (result != null) {
                (result["totalAmount"] as? Number)?.toLong() ?: 0L
            } else {
                0L
            }
            // Amount is stored in paise, convert to rupees by dividing by 100
            val totalRevenue = totalAmount.toDouble() / 100.0
            logger.debug("Total revenue: $totalRevenue")
            Result.success(totalRevenue)
        } catch (e: Exception) {
            logger.error("Failed to calculate total revenue", e)
            Result.error("Failed to calculate revenue: ${e.message}", e)
        }
    }

    override suspend fun getPaymentsWithFilters(
        page: Int,
        pageSize: Int,
        status: String?,
        startDate: String?,
        endDate: String?
    ): Result<Pair<List<Payment>, Long>> {
        logger.debug("Getting payments with filters: page=$page, pageSize=$pageSize, status=$status, startDate=$startDate, endDate=$endDate")
        return try {
            // Build filter
            val filterConditions = mutableListOf<Bson>()

            if (!status.isNullOrBlank()) {
                val allowedStatuses = when (val normalized = status.trim().lowercase()) {
                    "success" -> listOf("verified", "success", "captured")
                    "verified" -> listOf("verified")
                    "captured" -> listOf("captured")
                    "failed" -> listOf("failed")
                    "pending" -> listOf("pending")
                    "refunded" -> listOf("refunded")
                    else -> listOf(normalized)
                }
                filterConditions.add(Filters.`in`("status", allowedStatuses))
            }

            if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
                try {
                    val start = Instant.parse(startDate)
                    val end = Instant.parse(endDate)
                    filterConditions.add(
                        Filters.and(
                            Filters.gte("createdAt", start.toString()),
                            Filters.lte("createdAt", end.toString())
                        )
                    )
                } catch (e: Exception) {
                    logger.warn("Invalid date format: $startDate or $endDate", e)
                }
            }

            val filter = if (filterConditions.isNotEmpty()) {
                Filters.and(filterConditions)
            } else {
                Document()
            }

            // Count total matching documents
            val totalCount = getPaymentsCollection().countDocuments(filter)

            // Apply pagination
            val skip = (page - 1) * pageSize
            val payments = getPaymentsCollection()
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .skip(skip)
                .limit(pageSize)
                .toList()

            logger.debug("Retrieved ${payments.size} payments out of $totalCount total")
            Result.success(Pair(payments, totalCount))
        } catch (e: Exception) {
            logger.error("Failed to get payments with filters", e)
            Result.error("Failed to get payments: ${e.message}", e)
        }
    }

    override suspend fun getVerifiedPaymentsAggregate(): Result<Pair<Long, Long>> {
        logger.debug("Getting verified payments aggregate using database aggregation")
        return try {
            val pipeline = listOf(
                Aggregates.match(Filters.eq("status", "verified")),
                Aggregates.group(
                    null,
                    Accumulators.sum("totalAmount", "\$amount"),
                    Accumulators.sum("count", 1)
                )
            )
            val result = getPaymentsCollection().aggregate<Document>(pipeline).firstOrNull()
            val totalAmount = if (result != null) {
                (result.get("totalAmount") as? Number)?.toLong() ?: 0L
            } else {
                0L
            }
            val count = if (result != null) {
                (result.get("count") as? Number)?.toLong() ?: 0L
            } else {
                0L
            }
            logger.debug("Verified payments aggregate: totalAmount={}, count={}", totalAmount, count)
            Result.success(Pair(totalAmount, count))
        } catch (e: Exception) {
            logger.error("Failed to get verified payments aggregate", e)
            Result.error("Failed to aggregate payments: ${e.message}", e)
        }
    }

    override suspend fun getMonthlyRevenue(startDate: String, endDate: String): Result<List<MonthlyRevenue>> {
        logger.debug("Getting monthly revenue from $startDate to $endDate using aggregation")
        return try {
            val start = Instant.parse(startDate)
            val end = Instant.parse(endDate)

            // Match verified payments in date range
            // Note: createdAt is stored as ISO string, so we compare strings
            val pipeline = listOf(
                Aggregates.match(
                    Filters.and(
                        Filters.eq("status", "verified"),
                        Filters.gte("createdAt", start.toString()),
                        Filters.lte("createdAt", end.toString())
                    )
                ),
                Aggregates.project(
                    Projections.fields(
                        Projections.include("amount", "createdAt"),
                        // Extract year-month from ISO string (format: "2024-01-15T10:30:00Z")
                        Projections.computed(
                            "yearMonth",
                            Document(
                                "\$substr",
                                listOf("\$createdAt", 0, 7) // Extract "YYYY-MM" from ISO string
                            )
                        )
                    )
                ),
                Aggregates.group(
                    "\$yearMonth",
                    Accumulators.sum("revenue", "\$amount")
                ),
                Aggregates.sort(Sorts.ascending("_id"))
            )

            val results = getPaymentsCollection().aggregate<Document>(pipeline).toList()
            val monthlyRevenue = results.map { doc ->
                val month = (doc.get("_id") as? String) ?: ""
                val revenuePaise = (doc.get("revenue") as? Number)?.toLong() ?: 0L
                MonthlyRevenue(
                    month = month,
                    revenue = revenuePaise.toDouble() / 100.0
                )
            }

            logger.debug("Retrieved monthly revenue for ${monthlyRevenue.size} months")
            Result.success(monthlyRevenue)
        } catch (e: Exception) {
            logger.error("Failed to get monthly revenue", e)
            Result.error("Failed to get monthly revenue: ${e.message}", e)
        }
    }

    override suspend fun getPaymentCountByStatus(): Result<Map<String, Long>> {
        logger.debug("Getting payment count by status using aggregation")
        return try {
            val pipeline = listOf(
                Aggregates.group(
                    "\$status",
                    Accumulators.sum("count", 1)
                )
            )
            val results = getPaymentsCollection().aggregate<Document>(pipeline).toList()
            val statusCounts = results.associate { doc ->
                val status = (doc.get("_id") as? String) ?: "unknown"
                val count = (doc.get("count") as? Number)?.toLong() ?: 0L
                status to count
            }
            logger.debug("Payment counts by status: {}", statusCounts)
            Result.success(statusCounts)
        } catch (e: Exception) {
            logger.error("Failed to get payment count by status", e)
            Result.error("Failed to get payment counts: ${e.message}", e)
        }
    }

    override suspend fun getVerifiedPaymentsCount(): Result<Long> {
        logger.debug("Getting verified payments count")
        return try {
            val count = getPaymentsCollection().countDocuments(Filters.eq("status", "verified"))
            logger.debug("Verified payments count: $count")
            Result.success(count)
        } catch (e: Exception) {
            logger.error("Failed to get verified payments count", e)
            Result.error("Failed to get count: ${e.message}", e)
        }
    }
}
