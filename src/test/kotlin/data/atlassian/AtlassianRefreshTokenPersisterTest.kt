package data.atlassian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlassianRefreshTokenPersisterTest {

    @Test
    fun `persist delegates to updater and returns success`() {
        var seenName: String? = null
        var seenValue: String? = null
        val persister = AtlassianRefreshTokenPersister(
            secretName = "atlassian-oauth-refresh-token",
            update = { name, value ->
                seenName = name
                seenValue = value
                true
            },
        )
        assertTrue(persister.persistRotatedRefreshToken("refresh-NEW"))
        assertEquals("atlassian-oauth-refresh-token", seenName)
        assertEquals("refresh-NEW", seenValue)
    }

    @Test
    fun `blank token skips updater and fails soft`() {
        var called = false
        val persister = AtlassianRefreshTokenPersister(
            update = { _, _ ->
                called = true
                true
            },
        )
        assertFalse(persister.persistRotatedRefreshToken(""))
        assertFalse(persister.persistRotatedRefreshToken("   "))
        assertFalse(called)
    }

    @Test
    fun `updater false fails soft without throwing`() {
        val persister = AtlassianRefreshTokenPersister(update = { _, _ -> false })
        assertFalse(persister.persistRotatedRefreshToken("refresh-NEW"))
    }

    @Test
    fun `updater exception fails soft without throwing`() {
        val persister = AtlassianRefreshTokenPersister(
            update = { _, _ -> throw IllegalStateException("IAM denied") },
        )
        assertFalse(persister.persistRotatedRefreshToken("refresh-NEW"))
    }
}
