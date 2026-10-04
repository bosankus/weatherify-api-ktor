package bose.ankush.route

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FcmAccessTest {

    @Test
    fun `owner and admin may register an fcm token and everyone else may not`() {
        assertTrue(fcmRegistrationAllowed("user@example.com", "user@example.com", callerIsAdmin = false))
        assertTrue(fcmRegistrationAllowed("User@Example.com", "user@example.com", callerIsAdmin = false))
        assertTrue(fcmRegistrationAllowed("admin@example.com", "other@example.com", callerIsAdmin = true))
        assertFalse(fcmRegistrationAllowed("user@example.com", "other@example.com", callerIsAdmin = false))
        assertFalse(fcmRegistrationAllowed("  ", "user@example.com", callerIsAdmin = false))
    }
}
