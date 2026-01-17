package data.service

import bose.ankush.data.model.*
import domain.model.Result
import domain.repository.PaymentRepository
import domain.repository.UserRepository
import domain.service.RefundService
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import java.time.Instant
import kotlin.test.*

class FinancialServiceImplTest {

    // Mock UserRepository for testing
    private class MockUserRepository : UserRepository {
        var users = mutableMapOf<String, User>()
        var shouldReturnError = false
        var errorMessage = "Repository error"

        override suspend fun findUserByEmail(email: String): Result<User?> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                Result.success(users[email])
            }
        }

        override suspend fun createUser(user: User): Result<Boolean> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                users[user.email] = user
                Result.success(true)
            }
        }

        override suspend fun updateUser(user: User): Result<Boolean> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                users[user.email] = user
                Result.success(true)
            }
        }

        override suspend fun updateFcmTokenByEmail(email: String, fcmToken: String): Result<Boolean> {
            return Result.success(true)
        }

        override suspend fun getAllUsers(
            filter: Map<String, Any>?,
            sortBy: String?,
            sortOrder: Int?,
            page: Int?,
            pageSize: Int?
        ): Result<Pair<List<User>, Long>> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                val userList = users.values.toList()
                Result.success(Pair(userList, userList.size.toLong()))
            }
        }
    }

    // Mock PaymentRepository for testing
    private class MockPaymentRepository : PaymentRepository {
        var payments = mutableListOf<Payment>()
        var shouldReturnError = false
        var errorMessage = "Payment repository error"

        override suspend fun getPaymentByTransactionId(transactionId: String): Result<Payment?> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                Result.success(payments.find { it.paymentId == transactionId })
            }
        }

        override suspend fun getPaymentsByUserId(userId: String): Result<List<Payment>> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                Result.success(payments.filter { it.userId == userId })
            }
        }

        override suspend fun getPaymentsByUserEmail(userEmail: String): Result<List<Payment>> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                Result.success(payments.filter { it.userEmail == userEmail })
            }
        }

        override suspend fun getAllPayments(page: Int, pageSize: Int): Result<Pair<List<Payment>, Long>> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                Result.success(Pair(payments, payments.size.toLong()))
            }
        }

        override suspend fun getTotalRevenue(): Result<Double> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                val total = payments.filter { it.status == "verified" }
                    .mapNotNull { it.amount }
                    .sum()
                    .toDouble() / 100.0
                Result.success(total)
            }
        }
    }

    // Mock RefundService for testing
    private class MockRefundService : RefundService {
        var shouldReturnError = false
        var errorMessage = "Refund service error"
        var totalRefunds = 0.0
        var monthlyRefunds = 0.0
        var refundRate = 0.0

        override suspend fun initiateRefund(
            adminEmail: String,
            request: InitiateRefundRequest
        ): Result<RefundResponse> {
            return Result.success(RefundResponse(true, "Success"))
        }

        override suspend fun getRefund(refundId: String): Result<RefundDto> {
            return Result.success(
                RefundDto(
                    refundId = refundId,
                    paymentId = "pay_1",
                    amount = 100.0,
                    currency = "INR",
                    status = RefundStatus.PROCESSED,
                    speedRequested = RefundSpeed.OPTIMUM,
                    speedProcessed = RefundSpeed.NORMAL,
                    userEmail = "user@example.com",
                    processedBy = "admin@example.com",
                    reason = null,
                    createdAt = Instant.now().toString(),
                    processedAt = Instant.now().toString()
                )
            )
        }

        override suspend fun getRefundsForPayment(paymentId: String): Result<PaymentRefundSummary> {
            return Result.success(
                PaymentRefundSummary(
                    paymentId = paymentId,
                    originalAmount = 10000,
                    totalRefunded = 0,
                    remainingRefundable = 10000,
                    refunds = emptyList(),
                    isFullyRefunded = false
                )
            )
        }

        override suspend fun checkPaymentRefundStatus(paymentId: String): Result<PaymentRefundSummary> {
            return Result.success(
                PaymentRefundSummary(
                    paymentId = paymentId,
                    originalAmount = 10000,
                    totalRefunded = 0,
                    remainingRefundable = 10000,
                    refunds = emptyList(),
                    isFullyRefunded = false
                )
            )
        }

        override suspend fun getRefundHistory(
            page: Int,
            pageSize: Int,
            status: RefundStatus?,
            startDate: String?,
            endDate: String?
        ): Result<RefundHistoryResponse> {
            return Result.success(
                RefundHistoryResponse(
                    refunds = emptyList(),
                    pagination = PaginationInfo(page, pageSize, 0, 0)
                )
            )
        }

        override suspend fun getRefundMetrics(): Result<RefundMetrics> {
            return if (shouldReturnError) {
                Result.error(errorMessage)
            } else {
                Result.success(
                    RefundMetrics(
                        totalRefunds = totalRefunds,
                        monthlyRefunds = monthlyRefunds,
                        refundRate = refundRate,
                        totalRefundCount = 0,
                        monthlyRefundCount = 0,
                        instantRefundCount = 0,
                        normalRefundCount = 0,
                        averageProcessingTimeHours = 0.0,
                        monthlyRefundChart = emptyList()
                    )
                )
            }
        }

        override suspend fun exportRefunds(startDate: String, endDate: String): Result<String> {
            return Result.success("")
        }

        override suspend fun handleRefundWebhook(signature: String, payload: String): Result<Boolean> {
            return Result.success(true)
        }
    }

    private lateinit var mockUserRepository: MockUserRepository
    private lateinit var mockPaymentRepository: MockPaymentRepository
    private lateinit var mockRefundService: MockRefundService
    private lateinit var financialService: FinancialServiceImpl

    @BeforeTest
    fun setup() {
        mockUserRepository = MockUserRepository()
        mockPaymentRepository = MockPaymentRepository()
        mockRefundService = MockRefundService()
        financialService = FinancialServiceImpl(mockPaymentRepository, mockUserRepository, mockRefundService)
    }

    // Test: Financial metrics calculation
    @Test
    fun `test calculate financial metrics with verified payments`() {
        runBlocking {
            // Add verified payments
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000, // 100.00 INR
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_2",
                    userEmail = "user2@example.com",
                    orderId = "order_2",
                    paymentId = "pay_2",
                    signature = "sig_2",
                    amount = 20000, // 200.00 INR
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )

            val result = financialService.getFinancialMetrics()

            assertTrue(result.isSuccess)
            val metrics = (result as Result.Success).data
            assertEquals(300.0, metrics.totalRevenue)
            assertEquals(300.0, metrics.monthlyRevenue) // Both payments are in current month
            assertEquals(2, metrics.totalPaymentsCount)
            assertEquals(12, metrics.monthlyRevenueChart.size)
            // Check refund metrics
            assertEquals(0.0, metrics.totalRefunds)
            assertEquals(0.0, metrics.monthlyRefunds)
            assertEquals(0.0, metrics.refundRate)
            assertEquals(300.0, metrics.netRevenue) // totalRevenue - totalRefunds
        }
    }

    @Test
    fun `test calculate financial metrics with refunds`() {
        runBlocking {
            // Add verified payments
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000, // 100.00 INR
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_2",
                    userEmail = "user2@example.com",
                    orderId = "order_2",
                    paymentId = "pay_2",
                    signature = "sig_2",
                    amount = 20000, // 200.00 INR
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )

            // Set refund metrics
            mockRefundService.totalRefunds = 50.0
            mockRefundService.monthlyRefunds = 30.0
            mockRefundService.refundRate = 16.67 // (50/300) * 100

            val result = financialService.getFinancialMetrics()

            assertTrue(result.isSuccess)
            val metrics = (result as Result.Success).data
            assertEquals(300.0, metrics.totalRevenue)
            assertEquals(300.0, metrics.monthlyRevenue)
            assertEquals(2, metrics.totalPaymentsCount)
            // Check refund metrics
            assertEquals(50.0, metrics.totalRefunds)
            assertEquals(30.0, metrics.monthlyRefunds)
            assertEquals(16.67, metrics.refundRate)
            assertEquals(250.0, metrics.netRevenue) // 300 - 50
        }
    }

    @Test
    fun `test financial metrics with refund service error defaults to zero`() {
        runBlocking {
            // Add verified payments
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )

            // Simulate refund service error
            mockRefundService.shouldReturnError = true

            val result = financialService.getFinancialMetrics()

            assertTrue(result.isSuccess)
            val metrics = (result as Result.Success).data
            assertEquals(100.0, metrics.totalRevenue)
            // Refund metrics should default to zero on error
            assertEquals(0.0, metrics.totalRefunds)
            assertEquals(0.0, metrics.monthlyRefunds)
            assertEquals(0.0, metrics.refundRate)
            assertEquals(100.0, metrics.netRevenue) // totalRevenue - 0
        }
    }

    @Test
    fun `test financial metrics excludes non-verified payments`() {
        runBlocking {
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_2",
                    userEmail = "user2@example.com",
                    orderId = "order_2",
                    paymentId = "pay_2",
                    signature = "sig_2",
                    amount = 50000,
                    currency = "INR",
                    status = "pending",
                    createdAt = Instant.now().toString()
                )
            )

            val result = financialService.getFinancialMetrics()

            assertTrue(result.isSuccess)
            val metrics = (result as Result.Success).data
            assertEquals(100.0, metrics.totalRevenue) // Only verified payment
            assertEquals(1, metrics.totalPaymentsCount)
        }
    }

    @Test
    fun `test financial metrics with payments`() {
        runBlocking {
            val payment = Payment(
                id = "pay_1",
                userEmail = "user1@example.com",
                orderId = "order_1",
                paymentId = "pay_sub_1",
                signature = "sig_1",
                amount = 15000,
                currency = "INR",
                status = "verified",
                createdAt = Instant.now().toString()
            )
            mockPaymentRepository.payments.add(payment)

            val user = User(
                id = ObjectId(),
                email = "user1@example.com",
                passwordHash = "hash",
                isPremium = true
            )
            mockUserRepository.users["user1@example.com"] = user

            val result = financialService.getFinancialMetrics()

            assertTrue(result.isSuccess)
            val metrics = (result as Result.Success).data
            assertEquals(150.0, metrics.totalRevenue)
        }
    }

    // Test: Payment history with filters
    @Test
    fun `test get payment history with pagination`() {
        runBlocking {
            // Add 15 payments
            repeat(15) { i ->
                mockPaymentRepository.payments.add(
                    Payment(
                        id = "pay_$i",
                        userEmail = "user$i@example.com",
                        orderId = "order_$i",
                        paymentId = "pay_$i",
                        signature = "sig_$i",
                        amount = 10000,
                        currency = "INR",
                        status = "verified",
                        createdAt = Instant.now().minusSeconds((i * 3600).toLong()).toString()
                    )
                )
            }

            val result = financialService.getPaymentHistory(page = 1, pageSize = 10)

            assertTrue(result.isSuccess)
            val response = (result as Result.Success).data
            assertEquals(10, response.payments.size)
            assertEquals(1, response.pagination.page)
            assertEquals(10, response.pagination.pageSize)
            assertEquals(2, response.pagination.totalPages)
            assertEquals(15L, response.pagination.totalCount)
        }
    }

    @Test
    fun `test get payment history with status filter`() {
        runBlocking {
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = Instant.now().toString()
                )
            )
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_2",
                    userEmail = "user2@example.com",
                    orderId = "order_2",
                    paymentId = "pay_2",
                    signature = "sig_2",
                    amount = 20000,
                    currency = "INR",
                    status = "pending",
                    createdAt = Instant.now().toString()
                )
            )

            val result = financialService.getPaymentHistory(
                page = 1,
                pageSize = 10,
                status = "verified"
            )

            assertTrue(result.isSuccess)
            val response = (result as Result.Success).data
            assertEquals(1, response.payments.size)
            assertEquals("verified", response.payments[0].status)
        }
    }

    @Test
    fun `test get payment history with date range filter`() {
        runBlocking {
            val now = Instant.now()
            val yesterday = now.minusSeconds(24L * 60 * 60)
            val twoDaysAgo = now.minusSeconds(48L * 60 * 60)

            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = now.toString()
                )
            )
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_2",
                    userEmail = "user2@example.com",
                    orderId = "order_2",
                    paymentId = "pay_2",
                    signature = "sig_2",
                    amount = 20000,
                    currency = "INR",
                    status = "verified",
                    createdAt = twoDaysAgo.toString()
                )
            )

            val result = financialService.getPaymentHistory(
                page = 1,
                pageSize = 10,
                startDate = yesterday.toString(),
                endDate = now.plusSeconds(3600).toString()
            )

            assertTrue(result.isSuccess)
            val response = (result as Result.Success).data
            assertEquals(1, response.payments.size)
            assertEquals("pay_1", response.payments[0].id)
        }
    }

    // Test: CSV generation with special characters
    @Test
    fun `test export payments generates valid CSV`() {
        runBlocking {
            val now = Instant.now()
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = now.toString()
                )
            )

            val result = financialService.exportPayments(
                startDate = now.minusSeconds(3600).toString(),
                endDate = now.plusSeconds(3600).toString()
            )

            assertTrue(result.isSuccess)
            val csv = (result as Result.Success).data
            assertTrue(csv.contains("Payment ID,User Email,Amount,Currency"))
            assertTrue(csv.contains("user1@example.com"))
            assertTrue(csv.contains("100.0"))
        }
    }

    @Test
    fun `test export payments with special characters in email`() {
        runBlocking {
            val now = Instant.now()
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user,with,commas@example.com",
                    orderId = "order_1",
                    paymentId = "pay_1",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = now.toString()
                )
            )

            val result = financialService.exportPayments(
                startDate = now.minusSeconds(3600).toString(),
                endDate = now.plusSeconds(3600).toString()
            )

            assertTrue(result.isSuccess)
            val csv = (result as Result.Success).data
            // Email should be properly escaped with quotes
            assertTrue(csv.contains("\"user,with,commas@example.com\""))
        }
    }

    @Test
    fun `test export payments respects 10000 record limit`() {
        runBlocking {
            val now = Instant.now()
            // Add more than 10,000 payments
            repeat(10001) { i ->
                mockPaymentRepository.payments.add(
                    Payment(
                        id = "pay_$i",
                        userEmail = "user$i@example.com",
                        orderId = "order_$i",
                        paymentId = "pay_$i",
                        signature = "sig_$i",
                        amount = 10000,
                        currency = "INR",
                        status = "verified",
                        createdAt = now.toString()
                    )
                )
            }

            val result = financialService.exportPayments(
                startDate = now.minusSeconds(3600).toString(),
                endDate = now.plusSeconds(3600).toString()
            )

            assertTrue(result.isError)
            assertTrue((result as Result.Error).message.contains("10,000 records limit"))
        }
    }

    // Test: Date range filtering logic
    @Test
    fun `test date range filtering excludes payments outside range`() {
        runBlocking {
            val now = Instant.now()
            val yesterday = now.minusSeconds(24L * 60 * 60)
            val lastWeek = now.minusSeconds(7L * 24 * 60 * 60)

            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_recent",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_recent",
                    signature = "sig_1",
                    amount = 10000,
                    currency = "INR",
                    status = "verified",
                    createdAt = now.toString()
                )
            )
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_old",
                    userEmail = "user2@example.com",
                    orderId = "order_2",
                    paymentId = "pay_old",
                    signature = "sig_2",
                    amount = 20000,
                    currency = "INR",
                    status = "verified",
                    createdAt = lastWeek.toString()
                )
            )

            val result = financialService.exportPayments(
                startDate = yesterday.toString(),
                endDate = now.plusSeconds(3600).toString()
            )

            assertTrue(result.isSuccess)
            val csv = (result as Result.Success).data
            assertTrue(csv.contains("pay_recent"))
            assertFalse(csv.contains("pay_old"))
        }
    }

    @Test
    fun `test get user transactions returns payments`() {
        runBlocking {
            val now = Instant.now()
            mockPaymentRepository.payments.add(
                Payment(
                    id = "pay_1",
                    userEmail = "user1@example.com",
                    orderId = "order_1",
                    paymentId = "pay_sub_1",
                    signature = "sig_1",
                    amount = 15000,
                    currency = "INR",
                    status = "verified",
                    createdAt = now.toString()
                )
            )

            val user = User(
                id = ObjectId(),
                email = "user1@example.com",
                passwordHash = "hash",
                isPremium = true
            )
            mockUserRepository.users["user1@example.com"] = user

            val result = financialService.getUserTransactions("user1@example.com")

            assertTrue(result.isSuccess)
            val response = (result as Result.Success).data
            assertEquals("user1@example.com", response.userEmail)
            assertEquals(1, response.payments.size)
            assertEquals(150.0, response.payments[0].amount)
        }
    }

    // Test: Error handling
    @Test
    fun `test get financial metrics with payment repository error`() {
        runBlocking {
            mockPaymentRepository.shouldReturnError = true
            mockPaymentRepository.errorMessage = "Database connection failed"

            val result = financialService.getFinancialMetrics()

            assertTrue(result.isError)
            assertTrue((result as Result.Error).message.contains("Failed to fetch payments"))
        }
    }

    @Test
    fun `test get user transactions with user not found`() {
        runBlocking {
            val result = financialService.getUserTransactions("nonexistent@example.com")

            assertTrue(result.isError)
            assertTrue((result as Result.Error).message.contains("User not found"))
        }
    }
}
