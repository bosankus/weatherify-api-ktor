package domain.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountChangeNotifierTest {

    private class FakeTokens(initial: List<String>) : PushTokenStore {
        val byEmail = mutableMapOf("user@example.com" to initial.toMutableList())

        override suspend fun add(email: String, token: String) {
            byEmail.getOrPut(email) { mutableListOf() } += token
        }

        override suspend fun remove(email: String, token: String) {
            byEmail[email]?.remove(token)
        }

        override suspend fun tokens(email: String): List<String> = byEmail[email].orEmpty().toList()
    }

    private class FakeNotifications(
        private val result: Result<List<String>> = Result.success(emptyList()),
    ) : NotificationService {
        val sent = mutableListOf<Pair<List<String>, Map<String, String>>>()

        override suspend fun sendNotification(token: String, title: String, body: String): Result<String> =
            Result.success("id")

        override suspend fun sendData(tokens: List<String>, data: Map<String, String>): Result<List<String>> {
            sent += tokens to data
            return result
        }
    }

    @Test
    fun `photo change sends a silent push to every device`() = runBlocking {
        val notifications = FakeNotifications()
        AccountChangeNotifier(FakeTokens(listOf("phone", "tablet")), notifications)
            .photoChanged("user@example.com")

        assertEquals(1, notifications.sent.size)
        val (tokens, data) = notifications.sent.single()
        assertEquals(listOf("phone", "tablet"), tokens)
        assertEquals(mapOf("type" to "account_photo_changed"), data)
    }

    @Test
    fun `no devices means no push`() = runBlocking {
        val notifications = FakeNotifications()
        AccountChangeNotifier(FakeTokens(emptyList()), notifications).photoChanged("user@example.com")
        assertTrue(notifications.sent.isEmpty())
    }

    @Test
    fun `unregistered tokens are forgotten`() = runBlocking {
        val tokens = FakeTokens(listOf("phone", "stale"))
        AccountChangeNotifier(tokens, FakeNotifications(Result.success(listOf("stale"))))
            .photoChanged("user@example.com")
        assertEquals(listOf("phone"), tokens.tokens("user@example.com"))
    }

    @Test
    fun `a failed send keeps every token and does not throw`() = runBlocking {
        val tokens = FakeTokens(listOf("phone"))
        AccountChangeNotifier(tokens, FakeNotifications(Result.failure(Exception("fcm down"))))
            .photoChanged("user@example.com")
        assertEquals(listOf("phone"), tokens.tokens("user@example.com"))
    }
}
