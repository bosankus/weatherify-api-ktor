package data.atlassian

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlassianLogRedactorTest {

    @Test
    fun `redacts bearer tokens`() {
        val raw = "Authorization: Bearer secret-access-token-value-abc"
        val redacted = AtlassianLogRedactor.redact(raw)
        assertFalse(redacted.contains("secret-access-token-value-abc"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `redacts refresh body form fields`() {
        val raw = "grant_type=refresh_token&refresh_token=rt_SUPER_SECRET&client_secret=cs_SECRET"
        val redacted = AtlassianLogRedactor.redact(raw)
        assertFalse(redacted.contains("rt_SUPER_SECRET"))
        assertFalse(redacted.contains("cs_SECRET"))
        assertTrue(redacted.contains("refresh_token=[REDACTED]"))
        assertTrue(redacted.contains("client_secret=[REDACTED]"))
    }

    @Test
    fun `redacts json token fields`() {
        val raw = """{"access_token":"atk_LEAK","refresh_token":"rtk_LEAK","expires_in":3600}"""
        val redacted = AtlassianLogRedactor.redact(raw)
        assertFalse(redacted.contains("atk_LEAK"))
        assertFalse(redacted.contains("rtk_LEAK"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `safeError never includes raw token material`() {
        val msg = AtlassianLogRedactor.safeError(
            "token failed",
            status = 401,
            detail = """Bearer atk_LEAK and refresh_token=rtk_LEAK""",
        )
        assertFalse(msg.contains("atk_LEAK"))
        assertFalse(msg.contains("rtk_LEAK"))
        assertTrue(msg.contains("status=401"))
    }
}
