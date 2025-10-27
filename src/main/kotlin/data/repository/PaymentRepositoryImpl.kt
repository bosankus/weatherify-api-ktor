package data.repository

import bose.ankush.data.model.Payment
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.PaymentRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.slf4j.LoggerFactory
import util.Constants

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
        logger.debug("Calculating total revenue")
        return try {
            val query = Document("status", "verified")
            val payments = getPaymentsCollection().find(query).toList()
            // Amount is stored in paise, convert to rupees by dividing by 100
            val totalRevenue = payments.mapNotNull { it.amount }.sum().toDouble() / 100.0
            logger.debug("Total revenue: $totalRevenue")
            Result.success(totalRevenue)
        } catch (e: Exception) {
            logger.error("Failed to calculate total revenue", e)
            Result.error("Failed to calculate revenue: ${e.message}", e)
        }
    }
}
