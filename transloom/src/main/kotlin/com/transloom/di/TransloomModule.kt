package com.transloom.di

import com.androidplay.core.mongo.IndexSpec
import com.androidplay.core.mongo.MongoIndexer
import com.transloom.repository.*
import com.transloom.repository.mongo.*
import org.bson.Document
import org.koin.dsl.module
import com.mongodb.client.model.IndexOptions

fun transloomModule(encryptionKey: String) = module {
    single<UserRepository> { MongoUserRepository(get(), encryptionKey) }
    single<ProjectRepository> { MongoProjectRepository(get()) }
    single<GlossaryRepository> { MongoGlossaryRepository(get()) }
    single<TranslationRepository> { MongoTranslationRepository(get()) }
    single<BillingRepository> { MongoBillingRepository(get(), get()) }
    single<TranslationMemoryRepository> { MongoTranslationMemoryRepository(get()) }
    single<UserActivityRepository> { MongoUserActivityRepository(get()) }
}

fun transloomIndexes(): List<IndexSpec> {
    val unique = IndexOptions().unique(true)
    return listOf(
        IndexSpec("transloom_users", Document("githubId", 1), unique),
        IndexSpec("projects", Document("githubRepo", 1), unique),
        IndexSpec("projects", Document("ownerId", 1)),
        IndexSpec("glossary", Document(mapOf("projectId" to 1, "languageCode" to 1, "sourceTerm" to 1)), unique),
        IndexSpec("glossary", Document("projectId", 1)),
        IndexSpec("strings", Document(mapOf("projectId" to 1, "stringKey" to 1)), unique),
        IndexSpec("strings", Document("projectId", 1)),
        IndexSpec("translations", Document(mapOf("stringId" to 1, "targetLanguage" to 1)), unique),
        IndexSpec("translations", Document("stringId", 1)),
        IndexSpec("translations", Document("status", 1)),
        IndexSpec("translation_memory", Document("hashKey", 1), unique),
        IndexSpec("subscriptions", Document("userId", 1), unique),
        IndexSpec("subscriptions", Document("razorpaySubscriptionId", 1)),
        IndexSpec("usage_logs", Document(mapOf("userId" to 1, "yearMonth" to 1)), unique),
        IndexSpec("invoice_records", Document("razorpayPaymentId", 1), unique),
        IndexSpec("invoice_records", Document("userId", 1)),
        // user_events powers activity feed + lifecycle queries. The compound
        // (userId, occurredAt desc) index serves both per-user history and
        // the abandoned-payment scan which filters by event then sorts by time.
        IndexSpec("user_events", Document(mapOf("userId" to 1, "occurredAt" to -1))),
        IndexSpec("user_events", Document(mapOf("event" to 1, "occurredAt" to -1))),
    )
}
