package data.atlassian

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlassianTokenStoreTest {

    @Test
    fun `updateTokens returns true when refresh rotates`() = runBlocking {
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000L })
        val rotated = store.updateTokens(
            newAccessToken = "access-NEW",
            newRefreshToken = "refresh-NEW",
            expiresInSeconds = 3600L,
            nowEpochMs = 1_000L,
        )
        assertTrue(rotated)
        val snap = store.snapshot()
        assertEquals("access-NEW", snap.accessToken)
        assertEquals("refresh-NEW", snap.refreshToken)
    }

    @Test
    fun `updateTokens returns false when refresh omitted`() = runBlocking {
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000L })
        val rotated = store.updateTokens(
            newAccessToken = "access-NEW",
            newRefreshToken = null,
            expiresInSeconds = 3600L,
            nowEpochMs = 1_000L,
        )
        assertFalse(rotated)
        assertEquals("refresh-OLD", store.snapshot().refreshToken)
    }

    @Test
    fun `health exposes persist flag without token values`() = runBlocking {
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-SECRET", clock = { 1_000L })
        store.updateTokens("access-SECRET", "refresh-NEW", 3600L, nowEpochMs = 1_000L)
        store.markRefreshTokenPersistResult(false)
        val health = store.health()
        assertTrue(health.hasRefreshToken)
        assertTrue(health.hasAccessToken)
        assertFalse(health.refreshTokenPersistOk)
        val s = health.toString()
        assertFalse(s.contains("refresh-SECRET"))
        assertFalse(s.contains("access-SECRET"))
        assertFalse(s.contains("refresh-NEW"))
    }
}
