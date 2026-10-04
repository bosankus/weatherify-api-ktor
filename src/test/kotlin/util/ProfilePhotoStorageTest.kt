package util

import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.User
import com.androidplay.weatherify.repository.UserRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfilePhotoStorageTest {

    private class FakeStorage : ProfilePhotoStorage {
        val objects = mutableMapOf<String, ByteArray>()
        var available: Boolean = true

        override fun isAvailable(): Boolean = available

        override fun upload(objectName: String, bytes: ByteArray, contentType: String) {
            objects[objectName] = bytes.copyOf()
        }

        override fun signedUrl(objectName: String, ttlMinutes: Long): String =
            "https://signed.example/$objectName?ttl=$ttlMinutes"

        override fun delete(objectName: String) {
            objects.remove(objectName)
        }
    }

    private class FakeUsers : UserRepository {
        var user: User? = User(
            email = "user@example.com",
            passwordHash = "hash",
            photoObject = null
        )
        var failPhotoUpdate: Boolean = false

        override suspend fun findUserByEmail(email: String): Result<User?> =
            Result.success(if (user?.email == email) user else null)

        override suspend fun createUser(user: User): Result<Boolean> = Result.success(true)
        override suspend fun updateUser(user: User): Result<Boolean> = Result.success(true)
        override suspend fun updateFcmTokenByEmail(email: String, fcmToken: String): Result<Boolean> =
            Result.success(true)
        override suspend fun clearFcmTokenByEmail(email: String): Result<Boolean> =
            Result.success(true)

        override suspend fun updatePhotoObjectByEmail(email: String, photoObject: String): Result<Boolean> {
            if (failPhotoUpdate) return Result.error("Database operation failed")
            val current = user ?: return Result.error("User not found")
            user = current.copy(photoObject = photoObject)
            return Result.success(true)
        }

        override suspend fun clearPhotoObjectByEmail(email: String): Result<Boolean> {
            val current = user ?: return Result.error("User not found")
            user = current.copy(photoObject = null)
            return Result.success(true)
        }

        override suspend fun getAllUsers(
            filter: Map<String, Any>?,
            sortBy: String?,
            sortOrder: Int?,
            page: Int?,
            pageSize: Int?
        ): Result<Pair<List<User>, Long>> = Result.success(emptyList<User>() to 0L)

        override suspend fun deleteUserByEmail(email: String): Result<Boolean> = Result.success(true)
    }

    @Test
    fun `reuseOrCreate mints uuid when missing`() {
        val key = ProfilePhotoKeys.reuseOrCreate(null)
        assertTrue(key.isNotBlank())
        assertEquals(36, key.length)
    }

    @Test
    fun `reuseOrCreate keeps existing key`() {
        assertEquals("same-key", ProfilePhotoKeys.reuseOrCreate("same-key"))
        assertEquals("same-key", ProfilePhotoKeys.reuseOrCreate("  same-key  "))
    }

    @Test
    fun `upload reuses object key on second upload`() = runBlocking {
        val storage = FakeStorage()
        val users = FakeUsers()
        val actions = ProfilePhotoActions(storage, users)

        val first = actions.upload("user@example.com", byteArrayOf(1, 2, 3), "image/png")
        assertTrue(first is Result.Success)
        val key1 = (first as Result.Success).data.objectKey
        assertEquals(key1, users.user?.photoObject)
        assertTrue(storage.objects.containsKey(key1))

        val second = actions.upload("user@example.com", byteArrayOf(9, 9), "image/jpeg")
        assertTrue(second is Result.Success)
        val key2 = (second as Result.Success).data.objectKey
        assertEquals(key1, key2)
        assertEquals(1, storage.objects.size)
        assertTrue(storage.objects[key1].contentEquals(byteArrayOf(9, 9)))
    }

    @Test
    fun `delete removes object and clears photoObject field`() = runBlocking {
        val storage = FakeStorage()
        val users = FakeUsers()
        users.user = users.user!!.copy(photoObject = "abc-uuid")
        storage.objects["abc-uuid"] = byteArrayOf(1)
        val actions = ProfilePhotoActions(storage, users)

        val result = actions.delete("user@example.com")
        assertTrue(result is Result.Success)
        assertNull(users.user?.photoObject)
        assertTrue(storage.objects.isEmpty())
    }

    @Test
    fun `upload fails closed when storage unavailable`() = runBlocking {
        val storage = FakeStorage().apply { available = false }
        val users = FakeUsers()
        val actions = ProfilePhotoActions(storage, users)
        val result = actions.upload("user@example.com", byteArrayOf(1), "image/png")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("unavailable", ignoreCase = true))
        assertNull(users.user?.photoObject)
        assertTrue(storage.objects.isEmpty())
    }

    @Test
    fun `signedUrlFor returns null when no photoObject`() = runBlocking {
        val actions = ProfilePhotoActions(FakeStorage(), FakeUsers())
        val result = actions.signedUrlFor("user@example.com")
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `signedUrlFor never returns object name`() = runBlocking {
        val users = FakeUsers()
        users.user = users.user!!.copy(photoObject = "only-the-key")
        val actions = ProfilePhotoActions(FakeStorage(), users)
        val result = actions.signedUrlFor("user@example.com")
        assertTrue(result is Result.Success)
        val url = (result as Result.Success).data
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://"))
        assertNotEquals("only-the-key", url)
    }

    @Test
    fun `upload does not store heic`() = runBlocking {
        val storage = FakeStorage()
        val users = FakeUsers()
        val actions = ProfilePhotoActions(storage, users)
        val heic = ByteArray(24).also {
            it[3] = 24
            "ftyp".encodeToByteArray().copyInto(it, 4)
            "heic".encodeToByteArray().copyInto(it, 8)
        }
        val result = actions.upload("user@example.com", heic, "image/heic", "IMG_0001.HEIC")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("JPEG"))
        assertTrue(storage.objects.isEmpty())
        assertNull(users.user?.photoObject)
    }

    @Test
    fun `failed user update deletes a newly written object`() = runBlocking {
        val storage = FakeStorage()
        val users = FakeUsers().apply { failPhotoUpdate = true }
        val actions = ProfilePhotoActions(storage, users)
        val result = actions.upload("user@example.com", byteArrayOf(1, 2, 3), "image/png")
        assertTrue(result is Result.Error)
        assertTrue(storage.objects.isEmpty())
        assertNull(users.user?.photoObject)
    }

    @Test
    fun `failed user update keeps an existing object`() = runBlocking {
        val storage = FakeStorage()
        val users = FakeUsers().apply {
            user = user!!.copy(photoObject = "kept-key")
            failPhotoUpdate = true
        }
        storage.objects["kept-key"] = byteArrayOf(4)
        val actions = ProfilePhotoActions(storage, users)
        val result = actions.upload("user@example.com", byteArrayOf(7, 7), "image/gif")
        assertTrue(result is Result.Error)
        assertTrue(storage.objects.containsKey("kept-key"))
        assertEquals("kept-key", users.user?.photoObject)
    }
}
