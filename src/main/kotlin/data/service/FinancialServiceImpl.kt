package data.service

import bose.ankush.data.model.*
import domain.model.Result
import domain.repository.PaymentRepository
import domain.repository.UserRepository
import domain.service.FinancialService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Implementation of FinancialService.
 * Handles financial metrics, payment history, and data exports.
 */
class FinancialServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val refundService: domain.service.RefundService
) : FinancialService {
    private val logger = LoggerFactory.getLogger(FinancialServiceImpl::class.java)

    override suspend fun getFinancialMetrics(): Result<FinancialMetrics> {
        logger.debug("Calculating financial metrics")
        return try {
            // Fetch all payments from repository
            // Note: Using large page size to get all records. In production, consider implementing
            // aggregation queries at the database level for better performance.
            val paymentsResult = paymentRepository.getAllPayments(1, 100000)
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }
            val allPayments = (paymentsResult as Result.Success).data.first

            // Filter only verified/successful payments for revenue calculations
            // Excludes failed, pending, or refunded payments
            val verifiedPayments = allPayments.filter { it.status == "verified" }

            // Calculate total revenue from all verified payments
            // Amounts are stored in paise/cents, so divide by 100 to get currency units
            val totalRevenue = verifiedPayments.mapNotNull { it.amount }.sum().toDouble() / 100.0

            // Calculate monthly revenue for the current calendar month
            val now = Instant.now()
            val currentMonth = YearMonth.from(now.atZone(ZoneId.systemDefault()))
            val monthlyRevenue = verifiedPayments.filter {
                try {
                    // Parse payment timestamp and extract year-month
                    val paymentDate = Instant.parse(it.createdAt)
                    val paymentMonth = YearMonth.from(paymentDate.atZone(ZoneId.systemDefault()))
                    paymentMonth == currentMonth
                } catch (_: Exception) {
                    // Skip payments with invalid date formats
                    false
                }
            }.mapNotNull { it.amount }.sum().toDouble() / 100.0

            // Count total number of verified payments for dashboard display
            val totalPaymentsCount = verifiedPayments.size

            // Generate monthly revenue trend data for the last 12 months
            // Used for chart visualization in the admin dashboard
            val monthlyRevenueChart = calculateMonthlyRevenueChart(verifiedPayments)

            // Fetch refund metrics
            val refundMetricsResult = refundService.getRefundMetrics()
            val totalRefunds: Double
            val monthlyRefunds: Double
            val refundRate: Double

            if (refundMetricsResult is Result.Success) {
                val refundMetrics = refundMetricsResult.data
                totalRefunds = refundMetrics.totalRefunds
                monthlyRefunds = refundMetrics.monthlyRefunds
                // Use the refund rate calculated by RefundService
                refundRate = refundMetrics.refundRate
            } else {
                // If refund metrics fail, default to zero values
                logger.warn("Failed to fetch refund metrics: ${(refundMetricsResult as? Result.Error)?.message}")
                totalRefunds = 0.0
                monthlyRefunds = 0.0
                refundRate = 0.0
            }

            // Calculate net revenue (total revenue - total refunds)
            val netRevenue = totalRevenue - totalRefunds

            val metrics = FinancialMetrics(
                totalRevenue = totalRevenue,
                monthlyRevenue = monthlyRevenue,
                totalPaymentsCount = totalPaymentsCount,
                monthlyRevenueChart = monthlyRevenueChart,
                totalRefunds = totalRefunds,
                monthlyRefunds = monthlyRefunds,
                refundRate = refundRate,
                netRevenue = netRevenue
            )

            logger.debug("Financial metrics calculated successfully")
            Result.success(metrics)
        } catch (e: Exception) {
            logger.error("Failed to calculate financial metrics", e)
            Result.error("Failed to calculate financial metrics: ${e.message}", e)
        }
    }

    /**
     * Calculates monthly revenue for the last 12 months for chart visualization.
     *
     * @param payments List of verified payments to aggregate
     * @return List of MonthlyRevenue objects with month identifier and revenue amount
     */
    private fun calculateMonthlyRevenueChart(payments: List<Payment>): List<MonthlyRevenue> {
        val now = Instant.now().atZone(ZoneId.systemDefault())

        // Generate list of last 12 months (including current month)
        // Start from 11 months ago and go to current month
        val last12Months = (0..11).map { monthsAgo ->
            now.minusMonths(monthsAgo.toLong()).let { YearMonth.from(it) }
        }.reversed() // Reverse to get chronological order (oldest to newest)

        return last12Months.map { month ->
            // Sum all payments that occurred in this specific month
            val revenue = payments.filter {
                try {
                    val paymentDate = Instant.parse(it.createdAt)
                    val paymentMonth = YearMonth.from(paymentDate.atZone(ZoneId.systemDefault()))
                    paymentMonth == month
                } catch (_: Exception) {
                    // Skip payments with invalid timestamps
                    false
                }
            }.mapNotNull { it.amount }.sum().toDouble() / 100.0

            MonthlyRevenue(
                month = month.format(DateTimeFormatter.ofPattern("yyyy-MM")), // Format as "2024-02"
                revenue = revenue
            )
        }
    }

    override suspend fun getPaymentHistory(
        page: Int,
        pageSize: Int,
        status: String?,
        startDate: String?,
        endDate: String?
    ): Result<PaymentHistoryResponse> {
        logger.debug("Getting payment history: page=$page, pageSize=$pageSize, status=$status")
        return try {
            // Get all payments (we'll filter in memory for simplicity)
            val paymentsResult = paymentRepository.getAllPayments(1, 100000)
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }

            var payments = (paymentsResult as Result.Success).data.first

            // Apply status filter
            if (!status.isNullOrBlank()) {
                val allowedStatuses = when (val normalized = status.trim().lowercase()) {
                    "success" -> setOf("verified", "success", "captured")
                    "verified" -> setOf("verified")
                    "captured" -> setOf("captured")
                    "failed" -> setOf("failed")
                    "pending" -> setOf("pending")
                    "refunded" -> setOf("refunded")
                    else -> setOf(normalized)
                }

                payments = payments.filter { payment ->
                    payment.status?.lowercase() in allowedStatuses
                }
            }

            // Apply date range filter
            if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
                val start = LocalDate.parse(startDate)
                val end = LocalDate.parse(endDate)
                payments = payments.filter {
                    try {
                        val paymentDate = Instant.parse(it.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        !paymentDate.isBefore(start) && !paymentDate.isAfter(end)
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            // Sort by date descending
            payments = payments.sortedByDescending { it.createdAt }

            // Calculate pagination
            val totalCount = payments.size.toLong()
            val totalPages = ((totalCount + pageSize - 1) / pageSize).toInt()
            val skip = (page - 1) * pageSize
            val paginatedPayments = payments.drop(skip).take(pageSize)

            // Convert to DTOs
            val paymentDtos = paginatedPayments.map { payment ->
                PaymentDto(
                    id = payment.id,
                    userEmail = payment.userEmail,
                    amount = (payment.amount ?: 0).toDouble() / 100.0,
                    currency = payment.currency ?: "INR",
                    paymentMethod = "Razorpay",
                    status = payment.status ?: "unknown",
                    transactionId = payment.paymentId,
                    createdAt = payment.createdAt
                )
            }

            val response = PaymentHistoryResponse(
                payments = paymentDtos,
                pagination = PaginationInfo(
                    page = page,
                    pageSize = pageSize,
                    totalPages = totalPages,
                    totalCount = totalCount
                )
            )

            logger.debug("Payment history retrieved: ${paymentDtos.size} payments")
            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to get payment history", e)
            Result.error("Failed to get payment history: ${e.message}", e)
        }
    }

    override suspend fun exportPayments(startDate: String, endDate: String): Result<String> {
        logger.debug("Exporting payments from $startDate to $endDate")
        return try {
            val paymentsResult = paymentRepository.getAllPayments(1, 100000)
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }

            var payments = (paymentsResult as Result.Success).data.first

            // Apply date range filter
            val start = Instant.parse(startDate)
            val end = Instant.parse(endDate)
            payments = payments.filter {
                try {
                    val paymentDate = Instant.parse(it.createdAt)
                    !paymentDate.isBefore(start) && !paymentDate.isAfter(end)
                } catch (_: Exception) {
                    false
                }
            }

            // Limit to 10,000 records
            if (payments.size > 10000) {
                return Result.error("Export exceeds 10,000 records limit. Please narrow your date range.")
            }

            val csv = generatePaymentsCsv(payments)
            logger.debug("Exported ${payments.size} payments")
            Result.success(csv)
        } catch (e: Exception) {
            logger.error("Failed to export payments", e)
            Result.error("Failed to export payments: ${e.message}", e)
        }
    }


    override suspend fun getUserTransactions(userEmail: String): Result<UserTransactionsResponse> {
        logger.debug("Getting transactions for user: $userEmail")
        return try {
            // Get user
            val userResult = userRepository.findUserByEmail(userEmail)
            if (userResult is Result.Error) {
                return Result.error("User not found: ${userResult.message}")
            }
            if ((userResult as Result.Success<User?>).data == null) {
                return Result.error("User not found")
            }

            // Get payments
            val paymentsResult = paymentRepository.getPaymentsByUserEmail(userEmail)
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }
            val payments = (paymentsResult as Result.Success).data

            // Convert payments to DTOs
            val paymentDtos = payments.map { payment ->
                PaymentDto(
                    id = payment.id,
                    userEmail = payment.userEmail,
                    amount = (payment.amount ?: 0).toDouble() / 100.0,
                    currency = payment.currency ?: "INR",
                    paymentMethod = "Razorpay",
                    status = payment.status ?: "unknown",
                    transactionId = payment.paymentId,
                    createdAt = payment.createdAt
                )
            }

            val response = UserTransactionsResponse(
                userEmail = userEmail,
                userName = null, // We don't have user names in the model
                payments = paymentDtos
            )

            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to get user transactions", e)
            Result.error("Failed to get user transactions: ${e.message}", e)
        }
    }

    /**
     * Generates CSV export for payment records.
     * Properly escapes special characters to prevent CSV injection and parsing errors.
     *
     * @param payments List of payment records to export
     * @return CSV string with header and data rows
     */
    private fun generatePaymentsCsv(payments: List<Payment>): String {
        val header = "Payment ID,User Email,Amount,Currency,Payment Method,Status,Transaction ID,Created At\n"

        val rows = payments.joinToString("\n") { payment ->
            listOf(
                payment.id,
                payment.userEmail,
                ((payment.amount ?: 0).toDouble() / 100.0).toString(),
                payment.currency ?: "INR",
                "Razorpay",
                payment.status ?: "unknown",
                payment.paymentId,
                payment.createdAt
            ).joinToString(",") { field ->
                // Wrap each field in quotes and escape existing quotes by doubling them
                // This prevents CSV injection and handles fields containing commas or quotes
                "\"${field.replace("\"", "\"\"")}\""
            }
        }

        return header + rows
    }

}
