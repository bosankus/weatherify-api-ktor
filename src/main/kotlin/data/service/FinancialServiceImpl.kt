package data.service

import bose.ankush.data.model.*
import domain.model.Result
import domain.repository.PaymentRepository
import domain.repository.UserRepository
import domain.service.FinancialService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Instant
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
        logger.debug("Calculating financial metrics using database aggregations")
        return try {
            val now = Instant.now()
            val currentMonth = YearMonth.from(now.atZone(ZoneId.systemDefault()))
            val monthStart = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
            val last12MonthsStart = now.minus(java.time.Duration.ofDays(365))

            // Execute independent queries in parallel for better performance
            coroutineScope {
                val aggregateDeferred = async { paymentRepository.getVerifiedPaymentsAggregate() }
                val currentMonthRevenueDeferred = async {
                    paymentRepository.getMonthlyRevenue(monthStart.toString(), monthEnd.toString())
                }
                val last12MonthsRevenueDeferred = async {
                    paymentRepository.getMonthlyRevenue(last12MonthsStart.toString(), now.toString())
                }
                val refundMetricsDeferred = async { refundService.getRefundMetrics() }

                // Wait for all queries to complete
                val aggregateResult = aggregateDeferred.await()
                val currentMonthRevenueResult = currentMonthRevenueDeferred.await()
                val last12MonthsRevenueResult = last12MonthsRevenueDeferred.await()
                val refundMetricsResult = refundMetricsDeferred.await()

                // Process payment aggregate results
                if (aggregateResult is Result.Error) {
                    return@coroutineScope Result.error("Failed to fetch payment aggregate: ${aggregateResult.message}")
                }
                val (totalAmountPaise, totalPaymentsCount) = (aggregateResult as Result.Success).data
                val totalRevenue = totalAmountPaise.toDouble() / 100.0

                // Process current month revenue
                val monthlyRevenue = if (currentMonthRevenueResult is Result.Success) {
                    currentMonthRevenueResult.data.find { it.month == currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")) }?.revenue ?: 0.0
                } else {
                    logger.warn("Failed to get monthly revenue: ${(currentMonthRevenueResult as? Result.Error)?.message}")
                    0.0
                }

                // Process 12-month revenue chart
                val monthlyRevenueChart = if (last12MonthsRevenueResult is Result.Success) {
                    // Fill in missing months with zero revenue
                    val chartData = last12MonthsRevenueResult.data.associateBy { it.month }
                    val nowZoned = now.atZone(ZoneId.systemDefault())
                    val last12Months = (0..11).map { monthsAgo ->
                        nowZoned.minusMonths(monthsAgo.toLong()).let { YearMonth.from(it) }
                            .format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    }.reversed()

                    last12Months.map { month ->
                        chartData[month] ?: MonthlyRevenue(month, 0.0)
                    }
                } else {
                    logger.warn("Failed to get monthly revenue chart: ${(last12MonthsRevenueResult as? Result.Error)?.message}")
                    calculateMonthlyRevenueChartFallback()
                }

                // Process refund metrics
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
                    totalPaymentsCount = totalPaymentsCount.toInt(),
                    monthlyRevenueChart = monthlyRevenueChart,
                    totalRefunds = totalRefunds,
                    monthlyRefunds = monthlyRefunds,
                    refundRate = refundRate,
                    netRevenue = netRevenue
                )

                logger.debug("Financial metrics calculated successfully")
                Result.success(metrics)
            }
        } catch (e: Exception) {
            logger.error("Failed to calculate financial metrics", e)
            Result.error("Failed to calculate financial metrics: ${e.message}", e)
        }
    }

    /**
     * Fallback method to calculate monthly revenue chart if aggregation fails.
     * This should rarely be used, but provides a safety net.
     *
     * @return List of MonthlyRevenue objects with zero revenue for last 12 months
     */
    private fun calculateMonthlyRevenueChartFallback(): List<MonthlyRevenue> {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val last12Months = (0..11).map { monthsAgo ->
            now.minusMonths(monthsAgo.toLong()).let { YearMonth.from(it) }
        }.reversed()

        return last12Months.map { month ->
            MonthlyRevenue(
                month = month.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                revenue = 0.0
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
            // Use database-level filtering instead of fetching all records
            val paymentsResult = paymentRepository.getPaymentsWithFilters(
                page = page,
                pageSize = pageSize,
                status = status,
                startDate = startDate,
                endDate = endDate
            )
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }

            val (payments, totalCount) = (paymentsResult as Result.Success).data
            val totalPages = ((totalCount + pageSize - 1) / pageSize).toInt()

            // Convert to DTOs
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
            // Use database-level filtering with pagination
            // First check the count to ensure we don't exceed limits
            val countResult = paymentRepository.getPaymentsWithFilters(
                page = 1,
                pageSize = 1,
                status = null,
                startDate = startDate,
                endDate = endDate
            )
            if (countResult is Result.Error) {
                return Result.error("Failed to fetch payment count: ${countResult.message}")
            }
            val totalCount = (countResult as Result.Success).data.second

            // Limit to 10,000 records
            if (totalCount > 10000) {
                return Result.error("Export exceeds 10,000 records limit. Please narrow your date range.")
            }

            // Fetch all matching payments using pagination
            val paymentsResult = paymentRepository.getPaymentsWithFilters(
                page = 1,
                pageSize = totalCount.toInt(),
                status = null,
                startDate = startDate,
                endDate = endDate
            )
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }
            val payments = (paymentsResult as Result.Success).data.first

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
