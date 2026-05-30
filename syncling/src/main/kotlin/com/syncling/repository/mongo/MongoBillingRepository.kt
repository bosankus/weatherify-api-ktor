package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.Filters.nin
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.BillingPlan
import com.syncling.domain.HistoricalUsage
import com.syncling.domain.InvoiceRecord
import com.syncling.domain.Subscription
import com.syncling.domain.UsageStats
import com.syncling.repository.BillingRepository
import com.syncling.repository.ProjectRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bson.Document
import java.util.UUID

class MongoBillingRepository(
    db: MongoDatabase,
    private val projectRepository: ProjectRepository
) : BillingRepository {

    private val subscriptions = db.getCollection<Document>("subscriptions")
    private val usageLogs = db.getCollection<Document>("usage_logs")
    private val invoices = db.getCollection<Document>("invoice_records")

    override suspend fun getSubscription(userId: String): Subscription {
        val doc = subscriptions.find(eq("userId", userId)).firstOrNull()
            ?: return Subscription(userId, BillingPlan.FREE, null, null, false, null)
        return doc.toSubscription()
    }

    override suspend fun upsertSubscription(
        userId: String,
        plan: BillingPlan,
        razorpayCustomerId: String?,
        razorpaySubscriptionId: String?,
        cancelAtPeriodEnd: Boolean?,
        currentPeriodEnd: Instant?,
        pendingPlan: BillingPlan?
    ) {
        val now = System.currentTimeMillis()
        val setUpdates = mutableListOf(
            Updates.set("plan", plan.name),
            Updates.set("updatedAt", now)
        )
        cancelAtPeriodEnd?.let { setUpdates += Updates.set("cancelAtPeriodEnd", it) }
        razorpayCustomerId?.let { setUpdates += Updates.set("razorpayCustomerId", it) }
        razorpaySubscriptionId?.let { setUpdates += Updates.set("razorpaySubscriptionId", it) }
        currentPeriodEnd?.let { setUpdates += Updates.set("currentPeriodEnd", it.toEpochMilliseconds()) }
        pendingPlan?.let { setUpdates += Updates.set("pendingPlan", it.name) }

        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("userId", userId),
            Updates.setOnInsert("startedAt", now),
            *setUpdates.toTypedArray()
        )
        subscriptions.findOneAndUpdate(eq("userId", userId), update, FindOneAndUpdateOptions().upsert(true))
    }

    override suspend fun activatePendingPlan(userId: String): BillingPlan? {
        val doc = subscriptions.find(eq("userId", userId)).firstOrNull() ?: return null
        val pendingStr = doc.getString("pendingPlan") ?: return null
        val plan = runCatching { BillingPlan.valueOf(pendingStr) }.getOrNull() ?: return null
        subscriptions.updateOne(
            eq("userId", userId),
            Updates.combine(
                Updates.set("plan", plan.name),
                Updates.unset("pendingPlan"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
        return plan
    }

    override suspend fun clearPendingPlan(userId: String) {
        subscriptions.updateOne(
            eq("userId", userId),
            Updates.combine(
                Updates.unset("pendingPlan"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
    }

    override suspend fun downgradeToFree(razorpaySubscriptionId: String) {
        subscriptions.updateOne(
            eq("razorpaySubscriptionId", razorpaySubscriptionId),
            Updates.combine(
                Updates.set("plan", BillingPlan.FREE.name),
                Updates.unset("razorpaySubscriptionId"),
                Updates.set("cancelAtPeriodEnd", false),
                Updates.unset("currentPeriodEnd"),
                Updates.unset("pendingPlan"),
                Updates.unset("limitHitAt"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
    }

    override suspend fun setLimitHitAt(userId: String, at: Instant?) {
        val update = if (at != null) {
            Updates.combine(
                Updates.set("limitHitAt", at.toEpochMilliseconds()),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        } else {
            Updates.combine(
                Updates.unset("limitHitAt"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        }
        subscriptions.updateOne(eq("userId", userId), update)
    }

    override suspend fun markTrialStarted(userId: String, at: Instant) {
        // setOnInsert wouldn't fire here (the subscription doc already exists), so use $setOnInsert-like
        // semantics manually: only set the field if it isn't already present, to preserve the original
        // trial start across downgrades and re-subscriptions.
        subscriptions.updateOne(
            and(eq("userId", userId), eq("trialStartedAt", null)),
            Updates.combine(
                Updates.set("trialStartedAt", at.toEpochMilliseconds()),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
    }

    override suspend fun findByRazorpaySubscription(subscriptionId: String): String? =
        subscriptions.find(eq("razorpaySubscriptionId", subscriptionId)).firstOrNull()?.getString("userId")

    override suspend fun getUsage(userId: String): UsageStats {
        val ym = currentYearMonth()
        val doc = usageLogs.find(and(eq("userId", userId), eq("yearMonth", ym))).firstOrNull()
        val stringsTranslated = (doc?.get("stringsTranslated") as? Number)?.toInt() ?: 0
        val projectsUsed = projectRepository.countForUser(userId)
        return UsageStats(stringsTranslated, projectsUsed)
    }

    override suspend fun getHistoricalUsage(userId: String): List<HistoricalUsage> =
        usageLogs.find(eq("userId", userId))
            .sort(Sorts.descending("yearMonth"))
            .toList()
            .map { doc ->
                HistoricalUsage(
                    yearMonth = doc.getString("yearMonth"),
                    stringsTranslated = (doc["stringsTranslated"] as? Number)?.toInt() ?: 0
                )
            }

    override suspend fun recordUsage(userId: String, stringsTranslated: Int) {
        val ym = currentYearMonth()
        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("userId", userId),
            Updates.setOnInsert("yearMonth", ym),
            Updates.setOnInsert("tokensUsed", 0),
            Updates.inc("stringsTranslated", stringsTranslated)
        )
        usageLogs.findOneAndUpdate(
            and(eq("userId", userId), eq("yearMonth", ym)),
            update,
            FindOneAndUpdateOptions().upsert(true)
        )
    }

    override suspend fun incrementUsageIfUnderLimit(userId: String, amount: Int, limit: Int): Boolean {
        if (amount > limit) return false
        val ym = currentYearMonth()
        // Server-side conditional: only increment if current + amount <= limit.
        // $ifNull handles the case where stringsTranslated doesn't exist yet (first use of month).
        val filter = and(
            eq("userId", userId),
            eq("yearMonth", ym),
            Document("\$expr", Document("\$lte", listOf(
                Document("\$add", listOf(
                    Document("\$ifNull", listOf("\$stringsTranslated", 0)),
                    amount
                )),
                limit
            )))
        )
        val update = Updates.combine(
            Updates.inc("stringsTranslated", amount),
            Updates.set("updatedAt", System.currentTimeMillis())
        )
        val updated = usageLogs.updateOne(filter, update)
        if (updated.modifiedCount > 0L) return true

        // No existing doc for this month — safe to create since usage starts at 0.
        return try {
            val doc = Document().apply {
                put("_id", UUID.randomUUID().toString())
                put("userId", userId)
                put("yearMonth", ym)
                put("stringsTranslated", amount)
                put("tokensUsed", 0)
                put("createdAt", System.currentTimeMillis())
            }
            usageLogs.insertOne(doc)
            true
        } catch (e: com.mongodb.MongoWriteException) {
            if (e.error.code == 11000) {
                // Concurrent insert won the race — retry the conditional update once.
                usageLogs.updateOne(filter, update).modifiedCount > 0L
            } else throw e
        }
    }

    override suspend fun insertInvoice(
        userId: String,
        razorpayPaymentId: String,
        amountPaise: Int,
        currency: String,
        status: String,
        periodEnd: Instant
    ) {
        val now = System.currentTimeMillis()
        val doc = Document().apply {
            put("_id", UUID.randomUUID().toString())
            put("userId", userId)
            put("razorpayPaymentId", razorpayPaymentId)
            put("amountPaise", amountPaise)
            put("currency", currency)
            put("status", status)
            put("periodEnd", periodEnd.toEpochMilliseconds())
            put("createdAt", now)
        }
        try {
            invoices.insertOne(doc)
        } catch (e: com.mongodb.MongoWriteException) {
            // Unique index on razorpayPaymentId rejects concurrent duplicates from webhook redeliveries.
            if (e.error.code != 11000) throw e
        }
    }

    override suspend fun findExpiringSubscriptions(from: Instant, to: Instant): List<Subscription> =
        subscriptions.find(
            and(
                gte("currentPeriodEnd", from.toEpochMilliseconds()),
                lte("currentPeriodEnd", to.toEpochMilliseconds()),
                nin("plan", listOf(BillingPlan.FREE.name, BillingPlan.ENTERPRISE.name))
            )
        ).toList().map { it.toSubscription() }

    override suspend fun getInvoicePdf(paymentId: String): ByteArray? {
        val doc = invoices.find(eq("razorpayPaymentId", paymentId)).firstOrNull()
        return (doc?.get("pdfBytes") as? org.bson.types.Binary)?.data
    }

    override suspend fun storeInvoicePdf(paymentId: String, bytes: ByteArray) {
        invoices.updateOne(
            eq("razorpayPaymentId", paymentId),
            Updates.set("pdfBytes", org.bson.types.Binary(bytes))
        )
    }

    override suspend fun listInvoices(userId: String, limit: Int): List<InvoiceRecord> =
        invoices.find(eq("userId", userId))
            .sort(Sorts.descending("createdAt"))
            .limit(limit)
            .toList()
            .map { doc ->
                InvoiceRecord(
                    id = doc.getString("_id"),
                    razorpayPaymentId = doc.getString("razorpayPaymentId") ?: "",
                    amountPaise = (doc["amountPaise"] as? Number)?.toInt() ?: 0,
                    currency = doc.getString("currency") ?: "INR",
                    status = doc.getString("status") ?: "captured",
                    periodEnd = Instant.fromEpochMilliseconds((doc["periodEnd"] as? Number)?.toLong() ?: 0L),
                    createdAt = Instant.fromEpochMilliseconds((doc["createdAt"] as Number).toLong())
                )
            }

    private fun currentYearMonth(): String {
        val ldt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        return "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}"
    }

    private fun Document.toSubscription(): Subscription {
        val plan = runCatching { BillingPlan.valueOf(getString("plan") ?: "") }.getOrElse { BillingPlan.FREE }
        val periodEnd = (get("currentPeriodEnd") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val limitHitAt = (get("limitHitAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val pendingPlan = runCatching { BillingPlan.valueOf(getString("pendingPlan") ?: "") }.getOrNull()
        val startedAt = (get("startedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val trialStartedAt = (get("trialStartedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        return Subscription(
            userId = getString("userId"),
            plan = plan,
            razorpayCustomerId = getString("razorpayCustomerId"),
            razorpaySubscriptionId = getString("razorpaySubscriptionId"),
            cancelAtPeriodEnd = getBoolean("cancelAtPeriodEnd", false),
            currentPeriodEnd = periodEnd,
            limitHitAt = limitHitAt,
            pendingPlan = pendingPlan,
            startedAt = startedAt,
            trialStartedAt = trialStartedAt
        )
    }
}
