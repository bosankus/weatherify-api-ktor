package domain.service

import com.androidplay.weatherify.db.WeatherifyDb
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.PushOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

/**
 * FCM tokens per user, one per signed-in device. The legacy single `fcmToken` field (set at
 * registration, used by payment and refund notifications) is left alone.
 */
interface PushTokenStore {
    /** Registers [token] for [email] only; a device that switched accounts leaves the old one. */
    suspend fun add(email: String, token: String)

    suspend fun remove(email: String, token: String)

    suspend fun tokens(email: String): List<String>
}

/** Stores tokens in the user document's `pushTokens` array, newest last, capped at [MAX_TOKENS]. */
class MongoPushTokenStore(private val db: WeatherifyDb) : PushTokenStore {
    private val users get() = db.getUsersCollection().withDocumentClass(Document::class.java)

    override suspend fun add(email: String, token: String) {
        // Pull first so a re-registered token moves to the end instead of appearing twice.
        users.updateMany(Filters.eq(FIELD, token), Updates.pull(FIELD, token))
        users.updateOne(
            Filters.eq("email", email),
            Updates.pushEach(FIELD, listOf(token), PushOptions().slice(-MAX_TOKENS))
        )
    }

    override suspend fun remove(email: String, token: String) {
        users.updateOne(Filters.eq("email", email), Updates.pull(FIELD, token))
    }

    override suspend fun tokens(email: String): List<String> {
        val doc = users.find(Filters.eq("email", email))
            .projection(Projections.include(FIELD))
            .firstOrNull()
            ?: return emptyList()
        return doc.getList(FIELD, String::class.java).orEmpty().filter { it.isNotBlank() }
    }

    companion object {
        const val FIELD = "pushTokens"

        /** Old devices that never unregistered fall off the front. */
        const val MAX_TOKENS = 10
    }
}
