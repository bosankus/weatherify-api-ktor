package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.BillingPlan
import com.transloom.domain.HistoricalUsage
import com.transloom.domain.InvoiceRecord
import com.transloom.domain.Subscription
import com.transloom.domain.UsageStats
import com.transloom.repository.BillingRepository
import com.transloom.repository.ProjectRepository
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
        cancelAtPeriodEnd: Boolean,
        currentPeriodEnd: Instant?,
        pendingPlan: BillingPlan?
    ) {
        val now = System.currentTimeMillis()
        val setUpdates = mutableListOf(
            Updates.set("plan", plan.name),
            Updates.set("cancelAtPeriodEnd", cancelAtPeriodEnd),
            Updates.set("updatedAt", now)
        )
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

    override suspend fun downgradeToFree(razorpaySubscriptionId: String) {
        subscriptions.updateOne(
            eq("razorpaySubscriptionId", razorpaySubscriptionId),
            Updates.combine(
                Updates.set("plan", BillingPlan.FREE.name),
                Updates.unset("razorpaySubscriptionId"),
                Updates.set("cancelAtPeriodEnd", false),
                Updates.unset("currentPeriodEnd"),
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
        return Subscription(
            userId = getString("userId"),
            plan = plan,
            razorpayCustomerId = getString("razorpayCustomerId"),
            razorpaySubscriptionId = getString("razorpaySubscriptionId"),
            cancelAtPeriodEnd = getBoolean("cancelAtPeriodEnd", false),
            currentPeriodEnd = periodEnd,
            limitHitAt = limitHitAt,
            pendingPlan = pendingPlan
        )
    }
}
