package bose.ankush.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordUtilTest {

    @Test
    fun `test password hashing and verification`() {
        val password = "SecureP@ssw0rd"
        val hash = PasswordUtil.hashPassword(password)

        // Verify the correct password
        assertTrue(PasswordUtil.verifyPassword(password, hash))

        // Verify an incorrect password
        assertFalse(PasswordUtil.verifyPassword("WrongP@ssw0rd", hash))
    }

    @Test
    fun `test password strength validation - valid passwords`() {
        // Valid passwords with different combinations
        assertTrue(PasswordUtil.validatePasswordStrength("SecureP@ssw0rd"))
        assertTrue(PasswordUtil.validatePasswordStrength("Str0ng!Password"))
        assertTrue(PasswordUtil.validatePasswordStrength("C0mplex#Pass"))
        assertTrue(PasswordUtil.validatePasswordStrength("V3ryS3cur3!"))
    }

    @Test
    fun `test password strength validation - invalid passwords`() {
        // Too short
        assertFalse(PasswordUtil.validatePasswordStrength("Sh0rt!"))

        // No digits
        assertFalse(PasswordUtil.validatePasswordStrength("NoDigitsHere!"))

        // No lowercase letters
        assertFalse(PasswordUtil.validatePasswordStrength("UPPERCASE123!"))

        // No uppercase letters
        assertFalse(PasswordUtil.validatePasswordStrength("lowercase123!"))

        // No special characters
        assertFalse(PasswordUtil.validatePasswordStrength("NoSpecial123"))
    }

    @Test
    fun `test email format validation - valid emails`() {
        // Valid email formats
        assertTrue(PasswordUtil.validateEmailFormat("user@example.com"))
        assertTrue(PasswordUtil.validateEmailFormat("first.last@domain.co.uk"))
        assertTrue(PasswordUtil.validateEmailFormat("email123@test-domain.com"))
        assertTrue(PasswordUtil.validateEmailFormat("user.name+tag@example.com"))
    }

    @Test
    fun `test email format validation - invalid emails`() {
        // Invalid email formats
        assertFalse(PasswordUtil.validateEmailFormat("not-an-email"))
        assertFalse(PasswordUtil.validateEmailFormat("missing@domain"))
        assertFalse(PasswordUtil.validateEmailFormat("@domain.com"))
        assertFalse(PasswordUtil.validateEmailFormat("user@.com"))
        assertFalse(PasswordUtil.validateEmailFormat("user@domain."))
    }
}