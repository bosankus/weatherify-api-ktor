package services

import com.syncling.services.FigmaPushNode
import com.syncling.services.FigmaSyncService
import com.syncling.services.isValidStringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FigmaSyncServiceTest {

    // ── isValidStringKey ─────────────────────────────────────────────────────

    @Test
    fun `accepts snake_case keys`() {
        assertTrue(isValidStringKey("checkout_cta_pay_now"))
        assertTrue(isValidStringKey("a"))
        assertTrue(isValidStringKey("screen2_title"))
    }

    @Test
    fun `rejects invalid keys`() {
        assertFalse(isValidStringKey(""))
        assertFalse(isValidStringKey("2fast"))
        assertFalse(isValidStringKey("_leading_underscore"))
        assertFalse(isValidStringKey("Uppercase"))
        assertFalse(isValidStringKey("has space"))
        assertFalse(isValidStringKey("has-dash"))
        assertFalse(isValidStringKey("a".repeat(81)))
    }

    // ── normalizeKey ─────────────────────────────────────────────────────────

    @Test
    fun `normalizes layer names into snake_case`() {
        assertEquals("checkout_cta_button", FigmaSyncService.normalizeKey("Checkout / CTA Button!"))
        assertEquals("pay_now", FigmaSyncService.normalizeKey("  Pay   Now  "))
    }

    @Test
    fun `prefixes keys that would start with a digit`() {
        val key = FigmaSyncService.normalizeKey("2-step verification")
        assertTrue(key.startsWith("s_"))
        assertTrue(isValidStringKey(key))
    }

    @Test
    fun `truncates long names to a valid key`() {
        val key = FigmaSyncService.normalizeKey("this is a very long figma layer name ".repeat(5))
        assertTrue(key.length <= 60)
        assertTrue(isValidStringKey(key))
    }

    // ── uniqueKey ────────────────────────────────────────────────────────────

    @Test
    fun `returns key unchanged when free`() {
        assertEquals("title", FigmaSyncService.uniqueKey("title", setOf("subtitle")))
    }

    @Test
    fun `suffixes colliding keys with incrementing counter`() {
        assertEquals("title_2", FigmaSyncService.uniqueKey("title", setOf("title")))
        assertEquals("title_3", FigmaSyncService.uniqueKey("title", setOf("title", "title_2")))
    }

    // ── fallbackKey ──────────────────────────────────────────────────────────

    @Test
    fun `falls back to layer name then text`() {
        val fromLayer = FigmaSyncService.fallbackKey(
            FigmaPushNode(nodeId = "1:2", nodeName = "Checkout Title", text = "Review your order")
        )
        assertEquals("checkout_title", fromLayer)

        val fromText = FigmaSyncService.fallbackKey(
            FigmaPushNode(nodeId = "1:3", nodeName = "", text = "Pay now")
        )
        assertEquals("pay_now", fromText)
    }

    @Test
    fun `falls back to a constant when nothing slugs cleanly`() {
        val key = FigmaSyncService.fallbackKey(FigmaPushNode(nodeId = "1:4", nodeName = "!!!", text = "!!!"))
        assertEquals("figma_string", key)
    }

    // ── mergeIntoSourceFile ──────────────────────────────────────────────────

    @Test
    fun `merges new keys into existing android xml preserving current entries`() {
        val original = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name" translatable="false">Demo</string>
                <string name="existing_title">Hello</string>
            </resources>
        """.trimIndent()

        val merged = FigmaSyncService.mergeIntoSourceFile(
            "values/strings.xml", original,
            mapOf("checkout_cta" to "Pay now", "existing_title" to "Hello updated"),
        )

        assertTrue(merged.contains("""name="app_name""""))
        assertTrue(merged.contains("""name="checkout_cta""""))
        assertTrue(merged.contains("Pay now"))
        assertTrue(merged.contains("Hello updated"))
    }

    @Test
    fun `generates a fresh android xml when the source file does not exist yet`() {
        val merged = FigmaSyncService.mergeIntoSourceFile(
            "values/strings.xml", "", mapOf("checkout_cta" to "Pay now"),
        )
        assertTrue(merged.contains("<resources>"))
        assertTrue(merged.contains("""name="checkout_cta""""))
    }

    @Test
    fun `dispatches on file extension for ios and json sources`() {
        val ios = FigmaSyncService.mergeIntoSourceFile(
            "Localizable.strings", "", mapOf("checkout_cta" to "Pay now"),
        )
        assertTrue(ios.contains(""""checkout_cta" = "Pay now";"""))

        val json = FigmaSyncService.mergeIntoSourceFile(
            "strings/en.json", "", mapOf("checkout_cta" to "Pay now"),
        )
        assertTrue(json.contains("\"checkout_cta\""))
    }

    // ── expansionWarnings ────────────────────────────────────────────────────

    private fun node(id: String, text: String, fixedWidth: Boolean) =
        FigmaPushNode(nodeId = id, nodeName = id, text = text, fixedWidth = fixedWidth)

    @Test
    fun `warns for fixed-width text when a target language expands`() {
        val warnings = FigmaSyncService.expansionWarnings(
            listOf(node("1:1", "Continue to secure checkout", fixedWidth = true)),
            listOf("de-DE", "ja"),
        )
        assertEquals(1, warnings.size)
        assertEquals("1:1", warnings[0].nodeId)
        assertTrue(warnings[0].message.contains("DE"))
        assertTrue(warnings[0].message.contains("35"))
    }

    @Test
    fun `skips auto-growing, short, and non-expanding cases`() {
        // auto-grow box
        assertTrue(
            FigmaSyncService.expansionWarnings(
                listOf(node("1:1", "Continue to secure checkout", fixedWidth = false)), listOf("de"),
            ).isEmpty(),
        )
        // short text
        assertTrue(
            FigmaSyncService.expansionWarnings(
                listOf(node("1:1", "OK", fixedWidth = true)), listOf("de"),
            ).isEmpty(),
        )
        // no expanding target language
        assertTrue(
            FigmaSyncService.expansionWarnings(
                listOf(node("1:1", "Continue to secure checkout", fixedWidth = true)), listOf("ja", "ko"),
            ).isEmpty(),
        )
    }

    // ── computeDrift ─────────────────────────────────────────────────────────

    private fun binding(key: String, lastText: String) = com.syncling.domain.FigmaNodeBinding(
        projectId = "p1", figmaFileKey = "file1", figmaNodeId = "1:$key",
        stringKey = key, lastText = lastText,
        updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `reports keys whose repo copy changed since the last sync`() {
        val drift = FigmaSyncService.computeDrift(
            bindings = listOf(
                binding("checkout_cta", "Pay now"),
                binding("welcome_title", "Hello there"),
            ),
            currentSources = mapOf(
                "checkout_cta" to "Pay securely now",   // changed in repo
                "welcome_title" to "Hello there",       // unchanged
            ),
        )
        assertEquals(1, drift.size)
        assertEquals("checkout_cta", drift[0].stringKey)
        assertEquals("Pay now", drift[0].figmaText)
        assertEquals("Pay securely now", drift[0].repoText)
    }

    @Test
    fun `ignores bindings whose key was deleted from the source file`() {
        val drift = FigmaSyncService.computeDrift(
            bindings = listOf(binding("removed_key", "Old copy")),
            currentSources = emptyMap(),
        )
        assertTrue(drift.isEmpty())
    }

    @Test
    fun `drift comparison trims surrounding whitespace`() {
        val drift = FigmaSyncService.computeDrift(
            bindings = listOf(binding("checkout_cta", "Pay now")),
            currentSources = mapOf("checkout_cta" to "  Pay now  "),
        )
        assertTrue(drift.isEmpty())
    }
}
