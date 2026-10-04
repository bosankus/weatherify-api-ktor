package config

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionPolicyTest {

    @Test
    fun `login writes 0 when the field is missing and increments afterwards`() {
        assertEquals(0, SessionPolicy.generationForLogin(null))
        assertEquals(1, SessionPolicy.generationForLogin(0))
        assertEquals(4, SessionPolicy.generationForLogin(3))
    }

    @Test
    fun `logout increments including a missing field`() {
        assertEquals(1, SessionPolicy.generationForLogout(null))
        assertEquals(3, SessionPolicy.generationForLogout(2))
    }

    @Test
    fun `legacy token is accepted for calls and for refresh only while it is unexpired`() {
        assertEquals(
            SessionDecision.ALLOW,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = true,
                storedGeneration = 5,
                tokenGeneration = null,
                purpose = SessionPurpose.AUTHENTICATED_CALL,
                tokenExpired = false,
            )
        )
        assertEquals(
            SessionDecision.ALLOW,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = true,
                storedGeneration = 5,
                tokenGeneration = null,
                purpose = SessionPurpose.REFRESH,
                tokenExpired = false,
            )
        )
        assertEquals(
            SessionDecision.REJECT_LEGACY_EXPIRED_REFRESH,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = true,
                storedGeneration = 0,
                tokenGeneration = null,
                purpose = SessionPurpose.REFRESH,
                tokenExpired = true,
            )
        )
    }

    @Test
    fun `generation mismatch and inactive user are rejected for auth and refresh`() {
        assertEquals(
            SessionDecision.REJECT_GENERATION_MISMATCH,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = true,
                storedGeneration = 2,
                tokenGeneration = 1,
                purpose = SessionPurpose.AUTHENTICATED_CALL,
                tokenExpired = false,
            )
        )
        assertEquals(
            SessionDecision.REJECT_GENERATION_MISMATCH,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = true,
                storedGeneration = null,
                tokenGeneration = 1,
                purpose = SessionPurpose.REFRESH,
                tokenExpired = true,
            )
        )
        assertEquals(
            SessionDecision.ALLOW,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = true,
                storedGeneration = null,
                tokenGeneration = 0,
                purpose = SessionPurpose.REFRESH,
                tokenExpired = true,
            )
        )
        assertEquals(
            SessionDecision.REJECT_INACTIVE,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = false,
                storedGeneration = 0,
                tokenGeneration = 0,
                purpose = SessionPurpose.AUTHENTICATED_CALL,
                tokenExpired = false,
            )
        )
        assertEquals(
            SessionDecision.REJECT_INACTIVE,
            SessionPolicy.evaluate(
                userPresent = true,
                isActive = false,
                storedGeneration = 0,
                tokenGeneration = 0,
                purpose = SessionPurpose.REFRESH,
                tokenExpired = true,
            )
        )
        assertEquals(
            SessionDecision.REJECT_MISSING_USER,
            SessionPolicy.evaluate(
                userPresent = false,
                isActive = true,
                storedGeneration = 0,
                tokenGeneration = 0,
                purpose = SessionPurpose.REFRESH,
                tokenExpired = true,
            )
        )
    }
}
