package core

import com.androidplay.core.mongo.IndexSpec
import com.androidplay.core.mongo.mongoDefaultIndexName
import com.androidplay.core.mongo.planIndex
import com.androidplay.core.mongo.withExplicitName
import com.androidplay.weatherify.di.weatherifyIndexes
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexDecisionTest {

    @Test
    fun `same keys are kept even when the mongo name differs from the keys json`() {
        val keys = Document("email", 1)
        val existing = Document("name", "email_1")
            .append("key", Document("email", 1))
            .append("unique", true)
        val plan = planIndex(
            existingIndexes = listOf(existing),
            keysJson = keys.toJson(),
            desiredUnique = true,
            desiredSparse = false,
        )
        assertFalse(plan.create)
        assertNull(plan.warning)
        assertTrue(keys.toJson() != "email_1")
    }

    @Test
    fun `unique or sparse mismatch is a warning and still does not create or drop`() {
        val keys = Document(mapOf("status" to 1, "createdAt" to -1))
        val existing = Document("name", "status_1_createdAt_-1")
            .append("key", Document(mapOf("status" to 1, "createdAt" to -1)))
            .append("unique", false)
        val plan = planIndex(
            existingIndexes = listOf(existing),
            keysJson = keys.toJson(),
            desiredUnique = true,
            desiredSparse = true,
        )
        assertFalse(plan.create)
        assertNotNull(plan.warning)
        assertTrue(plan.warning!!.contains("Not dropping"))
    }

    @Test
    fun `missing key pattern is created and the id index is ignored`() {
        val keys = Document("email", 1)
        val idOnly = Document("name", "_id_").append("key", Document("_id", 1))
        val plan = planIndex(
            existingIndexes = listOf(idOnly),
            keysJson = keys.toJson(),
            desiredUnique = true,
            desiredSparse = false,
        )
        assertTrue(plan.create)
        assertNull(plan.warning)
    }

    @Test
    fun `explicit names are per spec and do not mutate a shared options object`() {
        val shared = IndexOptions().unique(true)
        val email = IndexSpec("users", Document("email", 1), shared).withExplicitName()
        val refund = IndexSpec("refunds", Document("refundId", 1), shared).withExplicitName()
        assertEquals("email_1", email.options.name)
        assertEquals("refundId_1", refund.options.name)
        assertTrue(email.options !== refund.options)
        assertNull(shared.name)
        assertTrue(email.options.isUnique)
        assertEquals("content_text", mongoDefaultIndexName(Document("content", "text")))
    }

    @Test
    fun `an already named spec is left unchanged`() {
        val named = IndexOptions().expireAfter(90, java.util.concurrent.TimeUnit.DAYS).name("ttl_semantic_90d")
        val spec = IndexSpec("semantic_change_cache", Document("createdAt", 1), named).withExplicitName()
        assertEquals("ttl_semantic_90d", spec.options.name)
        assertTrue(spec.options === named)
    }

    @Test
    fun `weatherify email index stays unique and razorpay payment id is not made unique`() {
        val specs = weatherifyIndexes()
        val email = specs.single { it.collection == "users" && it.keys.containsKey("email") }
        assertTrue(email.options.isUnique)
        assertEquals("email_1", email.options.name)
        val payment = specs.single { it.collection == "payments" && it.keys.containsKey("razorpay_payment_id") }
        assertFalse(payment.options.isUnique)
        assertEquals("razorpay_payment_id_1", payment.options.name)
        assertTrue(specs.all { !it.options.name.isNullOrBlank() })
    }
}
