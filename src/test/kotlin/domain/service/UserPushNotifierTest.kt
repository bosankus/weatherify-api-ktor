package domain.service

import com.androidplay.core.common.Result as CoreResult
import com.androidplay.weatherify.domain.User
import com.androidplay.weatherify.repository.UserRepository
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserPushNotifierTest {

    private class FakeTokens(initial: List<String>) : PushTokenStore {
        val list = initial.toMutableList()
        override suspend fun add(email: String, token: String) { list += token }
        override suspend fun remove(email: String, token: String) { list.remove(token) }
        override suspend fun tokens(email: String): List<String> = list.toList()
    }

    private class FakeNotifications(private val dead: Set<String> = emptySet()) : NotificationService {
        val sent = mutableListOf<Triple<List<String>, String, String>>()

        override suspend fun sendNotification(token: String, title: String, body: String): Result<String> =
            error("not used")

        override suspend fun sendNotification(
            tokens: List<String>,
            title: String,
            body: String,
            imageUrl: String?,
        ): Result<NotificationFanout> {
            sent += Triple(tokens, title, body)
            val unregistered = tokens.filter { it in dead }
            return Result.success(NotificationFanout(tokens.size - unregistered.size, unregistered.size, unregistered))
        }

        override suspend fun sendData(tokens: List<String>, data: Map<String, String>): Result<List<String>> =
            error("not used")
    }

    /** Records clearFcmTokenByEmail; no other repository call is expected. */
    private val cleared = mutableListOf<String>()
    private val users = Proxy.newProxyInstance(
        UserRepository::class.java.classLoader,
        arrayOf(UserRepository::class.java),
    ) { _, method, args ->
        check(method.name == "clearFcmTokenByEmail") { "unexpected ${method.name}" }
        cleared += args[0] as String
        CoreResult.Success(true)
    } as UserRepository

    private fun user(fcmToken: String? = null) =
        User(email = "user@example.com", passwordHash = "x", fcmToken = fcmToken)

    @Test
    fun `sends to every device and the legacy token once`() = runBlocking {
        val notifications = FakeNotifications()
        val notifier = UserPushNotifier(FakeTokens(listOf("a", "b")), users, notifications)

        notifier.notify(user(fcmToken = "b"), "t", "m")

        assertEquals(listOf("a", "b"), notifications.sent.single().first)
    }

    @Test
    fun `forgets unregistered tokens, including the legacy one`() = runBlocking {
        val tokens = FakeTokens(listOf("a", "dead"))
        val notifier = UserPushNotifier(tokens, users, FakeNotifications(dead = setOf("dead", "old")))

        notifier.notify(user(fcmToken = "old"), "t", "m")

        assertEquals(listOf("a"), tokens.list)
        assertEquals(listOf("user@example.com"), cleared)
    }

    @Test
    fun `fails without any token`() {
        val notifier = UserPushNotifier(FakeTokens(emptyList()), users, FakeNotifications())

        val error = runBlocking { notifier.notify(user(), "t", "m").exceptionOrNull() }
        assertIs<NoPushTokenException>(error)
    }

    @Test
    fun `premium activation names the service and expiry date`() = runBlocking {
        val notifications = FakeNotifications()
        val notifier = UserPushNotifier(FakeTokens(listOf("a")), users, notifications)

        notifier.premiumActivated(user(), "Premium One", "2027-10-11T10:30:00Z")

        val (_, title, body) = notifications.sent.single()
        assertEquals("Subscription activated", title)
        assertEquals("Your Premium One is now active. Expiring Oct 11, 2027.", body)
        assertTrue(cleared.isEmpty())
    }
}
