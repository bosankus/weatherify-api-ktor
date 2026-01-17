package bose.ankush.data.model

import kotlinx.serialization.Serializable

/**
 * Financial metrics response for admin dashboard
 */
@Serializable
data class FinancialMetrics(
    val totalRevenue: Double,
    val monthlyRevenue: Double,
    val totalPaymentsCount: Int,
    val monthlyRevenueChart: List<MonthlyRevenue>,
    // Refund metrics
    val totalRefunds: Double,
    val monthlyRefunds: Double,
    val refundRate: Double,
    val netRevenue: Double  // totalRevenue - totalRefunds
)

/**
 * Monthly revenue data point for charts
 */
@Serializable
data class MonthlyRevenue(
    val month: String,  // Format: "2025-01"
    val revenue: Double
)

/**
 * Payment history response with pagination
 */
@Serializable
data class PaymentHistoryResponse(
    val payments: List<PaymentDto>,
    val pagination: PaginationInfo
)

/**
 * Payment DTO for admin display
 */
@Serializable
data class PaymentDto(
    val id: String,
    val userEmail: String,
    val amount: Double,
    val currency: String,
    val paymentMethod: String,
    val status: String,
    val transactionId: String?,
    val createdAt: String
)

/**
 * Pagination information
 */
@Serializable
data class PaginationInfo(
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalCount: Long
)

/**
 * Bill generation request
 */
@Serializable
data class BillGenerationRequest(
    val userEmail: String,
    val paymentIds: List<String>,
    val sendViaEmail: Boolean
)

/**
 * Bill types for payment-level bill generation.
 */
@Serializable
enum class BillType {
    ORIGINAL_BILL,
    REFUND_ADJUSTMENT_BILL,
    NET_AMOUNT_BILL,
    REFUND_RECEIPT
}

/**
 * Payment bill generation request.
 */
@Serializable
data class PaymentBillRequest(
    val billType: BillType
)

/**
 * Bill generation response
 */
@Serializable
data class BillGenerationResponse(
    val success: Boolean,
    val message: String,
    val invoiceNumber: String?,
    val emailSent: Boolean?
)

/**
 * Financial export request
 */
@Serializable
data class FinancialExportRequest(
    val exportType: ExportType,
    val startDate: String,  // ISO date format
    val endDate: String     // ISO date format
)

/**
 * Export type enum
 */
@Serializable
@Suppress("unused")
enum class ExportType {
    PAYMENTS
}

/**
 * User transactions response for bill generation
 */
@Serializable
data class UserTransactionsResponse(
    val userEmail: String,
    val userName: String?,
    val payments: List<PaymentDto>
)
