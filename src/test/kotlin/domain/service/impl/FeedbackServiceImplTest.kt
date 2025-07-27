package domain.service.impl

import bose.ankush.data.model.Feedback
import domain.model.Result
import domain.repository.FeedbackRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeedbackServiceImplTest {

    // Create a mock FeedbackRepository for testing
    private class MockFeedbackRepository : FeedbackRepository {
        var shouldReturnSuccess = true
        var shouldReturnFeedback = true

        override suspend fun getFeedbackById(id: String): Result<Feedback?> {
            return if (shouldReturnSuccess) {
                if (shouldReturnFeedback) {
                    Result.success(
                        Feedback(
                            id = id,
                            deviceId = "test-device",
                            deviceOs = "Android",
                            feedbackTitle = "Test Feedback",
                            feedbackDescription = "This is a test feedback"
                        )
                    )
                } else {
                    Result.success(null)
                }
            } else {
                Result.error("Failed to get feedback")
            }
        }

        override suspend fun createOrUpdateFeedback(feedback: Feedback): Result<Boolean> {
            return if (shouldReturnSuccess) {
                Result.success(true)
            } else {
                Result.error("Failed to save feedback")
            }
        }

        override suspend fun deleteFeedbackById(id: String): Result<Boolean> {
            return if (shouldReturnSuccess) {
                Result.success(true)
            } else {
                Result.error("Failed to delete feedback")
            }
        }

        override suspend fun getAllFeedback(): Result<List<Feedback>> {
            return if (shouldReturnSuccess) {
                Result.success(
                    listOf(
                        Feedback(
                            id = "1",
                            deviceId = "test-device-1",
                            deviceOs = "Android",
                            feedbackTitle = "Test Feedback 1",
                            feedbackDescription = "This is test feedback 1"
                        ),
                        Feedback(
                            id = "2",
                            deviceId = "test-device-2",
                            deviceOs = "iOS",
                            feedbackTitle = "Test Feedback 2",
                            feedbackDescription = "This is test feedback 2"
                        )
                    )
                )
            } else {
                Result.error("Failed to get all feedback")
            }
        }
    }

    private val mockRepository = MockFeedbackRepository()
    private val feedbackService = FeedbackServiceImpl(mockRepository)

    @Test
    fun `test validateFeedbackParams with valid parameters`() {
        // Valid feedback parameters
        val result = feedbackService.validateFeedbackParams(
            deviceId = "test-device",
            deviceOs = "Android",
            title = "Test Feedback",
            description = "This is a test feedback"
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `test validateFeedbackParams with empty deviceId`() {
        // Empty device ID
        val result = feedbackService.validateFeedbackParams(
            deviceId = "",
            deviceOs = "Android",
            title = "Test Feedback",
            description = "This is a test feedback"
        )

        assertTrue(result.isError)
        assertEquals("Device ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun `test validateFeedbackParams with empty deviceOs`() {
        // Empty device OS
        val result = feedbackService.validateFeedbackParams(
            deviceId = "test-device",
            deviceOs = "",
            title = "Test Feedback",
            description = "This is a test feedback"
        )

        assertTrue(result.isError)
        assertEquals("Device OS cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun `test validateFeedbackParams with empty title`() {
        // Empty title
        val result = feedbackService.validateFeedbackParams(
            deviceId = "test-device",
            deviceOs = "Android",
            title = "",
            description = "This is a test feedback"
        )

        assertTrue(result.isError)
        assertEquals("Feedback title cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun `test validateFeedbackParams with empty description`() {
        // Empty description
        val result = feedbackService.validateFeedbackParams(
            deviceId = "test-device",
            deviceOs = "Android",
            title = "Test Feedback",
            description = ""
        )

        assertTrue(result.isError)
        assertEquals("Feedback description cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun `test validateFeedbackParams with title too long`() {
        // Title too long (> 100 characters)
        val longTitle = "A".repeat(101)
        val result = feedbackService.validateFeedbackParams(
            deviceId = "test-device",
            deviceOs = "Android",
            title = longTitle,
            description = "This is a test feedback"
        )

        assertTrue(result.isError)
        assertEquals(
            "Feedback title cannot exceed 100 characters",
            (result as Result.Error).message
        )
    }

    @Test
    fun `test validateFeedbackParams with description too long`() {
        // Description too long (> 1000 characters)
        val longDescription = "A".repeat(1001)
        val result = feedbackService.validateFeedbackParams(
            deviceId = "test-device",
            deviceOs = "Android",
            title = "Test Feedback",
            description = longDescription
        )

        assertTrue(result.isError)
        assertEquals(
            "Feedback description cannot exceed 1000 characters",
            (result as Result.Error).message
        )
    }

    @Test
    fun `test submitFeedback with valid parameters`() {
        runBlocking {
            // Set up mock repository to return success
            mockRepository.shouldReturnSuccess = true

            // Call the method
            val result = feedbackService.submitFeedback(
                deviceId = "test-device",
                deviceOs = "Android",
                title = "Test Feedback",
                description = "This is a test feedback"
            )

            // Verify the result
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
        }
    }

    @Test
    fun `test submitFeedback with invalid parameters`() {
        runBlocking {
            // Call the method with invalid parameters
            val result = feedbackService.submitFeedback(
                deviceId = "",
                deviceOs = "Android",
                title = "Test Feedback",
                description = "This is a test feedback"
            )

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Invalid feedback parameters", (result as Result.Error).message)
        }
    }

    @Test
    fun `test submitFeedback with repository failure`() {
        runBlocking {
            // Set up mock repository to return failure
            mockRepository.shouldReturnSuccess = false

            // Call the method
            val result = feedbackService.submitFeedback(
                deviceId = "test-device",
                deviceOs = "Android",
                title = "Test Feedback",
                description = "This is a test feedback"
            )

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Failed to save feedback", (result as Result.Error).message)
        }
    }

    @Test
    fun `test getFeedbackById with valid id and existing feedback`() {
        runBlocking {
            // Set up mock repository to return success with feedback
            mockRepository.shouldReturnSuccess = true
            mockRepository.shouldReturnFeedback = true

            // Call the method
            val result = feedbackService.getFeedbackById("test-id")

            // Verify the result
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
            assertEquals("test-id", result.getOrNull()?.id)
        }
    }

    @Test
    fun `test getFeedbackById with valid id but no feedback found`() {
        runBlocking {
            // Set up mock repository to return success but no feedback
            mockRepository.shouldReturnSuccess = true
            mockRepository.shouldReturnFeedback = false

            // Call the method
            val result = feedbackService.getFeedbackById("test-id")

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Feedback not found", (result as Result.Error).message)
        }
    }

    @Test
    fun `test getFeedbackById with repository failure`() {
        runBlocking {
            // Set up mock repository to return failure
            mockRepository.shouldReturnSuccess = false

            // Call the method
            val result = feedbackService.getFeedbackById("test-id")

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Feedback not found", (result as Result.Error).message)
        }
    }

    @Test
    fun `test deleteFeedback with valid id and success`() {
        runBlocking {
            // Set up mock repository to return success
            mockRepository.shouldReturnSuccess = true

            // Call the method
            val result = feedbackService.deleteFeedback("test-id")

            // Verify the result
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun `test deleteFeedback with repository failure`() {
        runBlocking {
            // Set up mock repository to return failure
            mockRepository.shouldReturnSuccess = false

            // Call the method
            val result = feedbackService.deleteFeedback("test-id")

            // Verify the result
            assertTrue(result.isError)
            assertEquals(
                "Feedback not found or could not be removed",
                (result as Result.Error).message
            )
        }
    }

    @Test
    fun `test getAllFeedback with success`() {
        runBlocking {
            // Set up mock repository to return success
            mockRepository.shouldReturnSuccess = true

            // Call the method
            val result = feedbackService.getAllFeedback()

            // Verify the result
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
            assertEquals(2, result.getOrNull()?.size)
        }
    }

    @Test
    fun `test getAllFeedback with repository failure`() {
        runBlocking {
            // Set up mock repository to return failure
            mockRepository.shouldReturnSuccess = false

            // Call the method
            val result = feedbackService.getAllFeedback()

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Failed to get all feedback", (result as Result.Error).message)
        }
    }
}