package bose.ankush.route

import com.androidplay.core.common.Result
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import util.ProfilePhotoStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountPhotoUrlTest {

    @Test
    fun `signing failure is 503 and not a null photo url`() = runBlocking {
        val storage = object : ProfilePhotoStorage {
            override fun isAvailable(): Boolean = true
            override fun upload(objectName: String, bytes: ByteArray, contentType: String) = Unit
            override fun signedUrl(objectName: String, ttlMinutes: Long): String {
                error("cannot sign")
            }
            override fun delete(objectName: String) = Unit
        }
        val result = resolveAccountPhotoUrl("object-key", storage)
        assertTrue(result is Result.Error)
        val message = (result as Result.Error).message
        assertTrue(message.contains("sign", ignoreCase = true))
        assertEquals(HttpStatusCode.ServiceUnavailable, profilePhotoErrorStatus(message))
    }

    @Test
    fun `no photo object stays null`() = runBlocking {
        val storage = object : ProfilePhotoStorage {
            override fun isAvailable(): Boolean = false
            override fun upload(objectName: String, bytes: ByteArray, contentType: String) = Unit
            override fun signedUrl(objectName: String, ttlMinutes: Long) = error("unused")
            override fun delete(objectName: String) = Unit
        }
        val result = resolveAccountPhotoUrl(null, storage)
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }
}
