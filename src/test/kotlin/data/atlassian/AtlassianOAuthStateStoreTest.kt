package data.atlassian

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AtlassianOAuthStateStoreTest {

    @Test
    fun `issue then consume succeeds once`() = runBlocking {
        val store = AtlassianOAuthStateStore(ttlMs = 60_000L, clock = { 1_000L })
        val state = store.issue()
        assertTrue(state.isNotBlank())
        assertEquals(1, store.pendingCount())
        assertTrue(store.consume(state))
        assertEquals(0, store.pendingCount())
        // Replay rejected
        assertFalse(store.consume(state))
    }

    @Test
    fun `null blank and unknown state are rejected`() = runBlocking {
        val store = AtlassianOAuthStateStore(ttlMs = 60_000L, clock = { 1_000L })
        assertFalse(store.consume(null))
        assertFalse(store.consume(""))
        assertFalse(store.consume("   "))
        assertFalse(store.consume("not-issued"))
    }

    @Test
    fun `expired state is rejected`() = runBlocking {
        var now = 1_000L
        val store = AtlassianOAuthStateStore(ttlMs = 100L, clock = { now })
        val state = store.issue()
        now = 1_000L + 101L
        assertFalse(store.consume(state))
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun `mismatched state does not consume the issued one`() = runBlocking {
        val store = AtlassianOAuthStateStore(ttlMs = 60_000L, clock = { 1_000L })
        val issued = store.issue()
        assertFalse(store.consume("other-${issued}"))
        assertEquals(1, store.pendingCount())
        assertTrue(store.consume(issued))
    }

    @Test
    fun `each issue produces a distinct state`() = runBlocking {
        val store = AtlassianOAuthStateStore(ttlMs = 60_000L, clock = { 1_000L })
        val a = store.issue()
        val b = store.issue()
        assertNotEquals(a, b)
        assertEquals(2, store.pendingCount())
    }
}
