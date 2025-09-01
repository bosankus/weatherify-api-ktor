package util

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory store for mock API responses.
 * Maps a short unique id to a JSON string.
 */
object MockStore {
    private val storage = ConcurrentHashMap<String, String>()

    fun generateId(): String {
        // Short, URL-friendly id (12 hex chars)
        return UUID.randomUUID().toString().replace("-", "").take(12)
    }

    fun put(id: String, json: String) {
        storage[id] = json
    }

    fun get(id: String): String? = storage[id]

    fun remove(id: String): Boolean = storage.remove(id) != null
}
