package domain.service.impl

import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthServiceImplTest {

    // Create a mock UserRepository for testing
    private val mockUserRepository = object : UserRepository {
        override suspend fun findUserByEmail(email: String) =
            throw NotImplementedError("Not needed for these tests")

        override suspend fun createUser(user: com.androidplay.weatherify.domain.User) =
            throw NotImplementedError("Not needed for these tests")

        override suspend fun updateUser(user: com.androidplay.weatherify.domain.User) =
            throw NotImplementedError("Not needed for these tests")

        override suspend fun updateFcmTokenByEmail(email: String, fcmToken: String): Result<Boolean> =
            com.androidplay.core.common.Result.Success(true)

        override suspend fun clearFcmTokenByEmail(email: String): Result<Boolean> =
            com.androidplay.core.common.Result.Success(true)

        override suspend fun getAllUsers(
            filter: Map<String, Any>?,
            sortBy: String?,
            sortOrder: Int?,
            page: Int?,
            pageSize: Int?
        ): Result<Pair<List<com.androidplay.weatherify.domain.User>, Long>> =
            throw NotImplementedError("Not needed for these tests")

        override suspend fun deleteUserByEmail(email: String): Result<Boolean> =
            com.androidplay.core.common.Result.Success(true)
    }

    // Create an instance of AuthServiceImpl with the mock repository
    private val authService = AuthServiceImpl(mockUserRepository)

    @Test
    fun `test isValidEmail with valid emails`() {
        // Valid email formats
        assertTrue(authService.isValidEmail("user@example.com"))
        assertTrue(authService.isValidEmail("first.last@domain.co.uk"))
        assertTrue(authService.isValidEmail("email123@test-domain.com"))
        assertTrue(authService.isValidEmail("user.name@example.com"))
    }

    @Test
    fun `test isValidEmail with invalid emails`() {
        // Invalid email formats
        assertFalse(authService.isValidEmail(""))
        assertFalse(authService.isValidEmail("not-an-email"))
        assertFalse(authService.isValidEmail("missing@domain"))
        assertFalse(authService.isValidEmail("@domain.com"))
        assertFalse(authService.isValidEmail("user@.com"))
        assertFalse(authService.isValidEmail("user@domain."))
    }

    @Test
    fun `test isStrongPassword with strong passwords`() {
        // Strong passwords with all required elements
        assertTrue(authService.isStrongPassword("SecureP@ssw0rd"))
        assertTrue(authService.isStrongPassword("Str0ng!Password"))
        assertTrue(authService.isStrongPassword("C0mplex#Pass"))
        assertTrue(authService.isStrongPassword("V3ryS3cur3!"))
    }

    @Test
    fun `test isStrongPassword with weak passwords`() {
        // Too short
        assertFalse(authService.isStrongPassword("Sh0rt!"))

        // No digits
        assertFalse(authService.isStrongPassword("NoDigitsHere!"))

        // No lowercase letters
        assertFalse(authService.isStrongPassword("UPPERCASE123!"))

        // No uppercase letters
        assertFalse(authService.isStrongPassword("lowercase123!"))

        // No special characters
        assertFalse(authService.isStrongPassword("NoSpecial123"))

        // Empty password
        assertFalse(authService.isStrongPassword(""))
    }
}
