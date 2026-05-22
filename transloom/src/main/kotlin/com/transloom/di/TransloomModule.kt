package com.transloom.di

import com.androidplay.core.mongo.IndexSpec
import com.transloom.repository.*
import com.transloom.repository.mongo.*
import com.transloom.repository.mongo.MongoCdnPublishRepository
import com.transloom.repository.mongo.MongoNotificationRepository
import com.transloom.repository.mongo.MongoSharedTranslationMemoryRepository
import com.transloom.services.CulturalSensitivityAnalyzer
import com.transloom.services.SemanticChangeAnalyzer
import org.bson.Document
import org.koin.dsl.module
import com.mongodb.client.model.IndexOptions
import java.util.concurrent.TimeUnit

fun transloomModule(encryptionKey: String) = module {
    single<UserRepository> { MongoUserRepository(get(), encryptionKey) }
    single<ProjectRepository> { MongoProjectRepository(get()) }
    single<GlossaryRepository> { MongoGlossaryRepository(get()) }
    single<TranslationRepository> { MongoTranslationRepository(get()) }
    single<BillingRepository> { MongoBillingRepository(get(), get()) }
    single<TranslationMemoryRepository> { MongoTranslationMemoryRepository(get()) }
    single<UserActivityRepository> { MongoUserActivityRepository(get()) }
    single<SemanticChangeCacheRepository> { MongoSemanticChangeCacheRepository(get()) }
    single { SemanticChangeAnalyzer(get()) }
    single<CulturalAnalysisCacheRepository> { MongoCulturalAnalysisCacheRepository(get()) }
    single { CulturalSensitivityAnalyzer(get()) }
    single<CdnPublishRepository> { MongoCdnPublishRepository(get()) }
    single<NotificationRepository> { MongoNotificationRepository(get()) }
    single<SharedTranslationMemoryRepository> { MongoSharedTranslationMemoryRepository(get()) }
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
        // targetLanguage + status enables review portal language filters without a collection scan
        IndexSpec("translations", Document(mapOf("targetLanguage" to 1, "status" to 1))),
        // projectId on translations is resolved via strings join; this index speeds up CDN publish queries
        IndexSpec("strings", Document(mapOf("projectId" to 1, "updatedAt" to -1))),
        // Compound ownerId+createdAt for project listing (sorted by creation time)
        IndexSpec("projects", Document(mapOf("ownerId" to 1, "createdAt" to -1))),
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
        IndexSpec("semantic_change_cache", Document("hashKey", 1)),
        // TTL: purge semantic-change cache entries after 90 days — source text semantics are stable
        IndexSpec("semantic_change_cache", Document("createdAt", 1), IndexOptions().expireAfter(90, TimeUnit.DAYS)),
        IndexSpec("cultural_analysis_cache", Document("hashKey", 1)),
        // TTL: purge cultural-analysis cache entries after 30 days — cultural norms shift slowly
        IndexSpec("cultural_analysis_cache", Document("createdAt", 1), IndexOptions().expireAfter(30, TimeUnit.DAYS)),
        IndexSpec("cdn_publish_log", Document("projectId", 1)),
        IndexSpec("cdn_publish_log", Document(mapOf("projectId" to 1, "bundleVersion" to 1)), unique),
        IndexSpec("cdn_publish_log", Document("publishedAt", -1)),
        // Notifications: userId+createdAt for list, userId+readAt for unread count
        IndexSpec("notifications", Document(mapOf("userId" to 1, "createdAt" to -1))),
        IndexSpec("notifications", Document(mapOf("userId" to 1, "readAt" to 1))),
        IndexSpec("notifications", Document(mapOf("userId" to 1, "type" to 1, "createdAt" to -1))),
        // TTL: auto-purge read notifications older than 30 days to keep the collection lean
        IndexSpec("notifications", Document("createdAt", 1), IndexOptions().expireAfter(30, TimeUnit.DAYS)),
        // Shared translation memory: hashKey is the primary lookup key
        IndexSpec("shared_translation_memory", Document("hashKey", 1), IndexOptions().unique(true)),
        // Translation history: query by project + stringKey, sorted by time
        IndexSpec("translation_history", Document(mapOf("projectId" to 1, "stringKey" to 1, "changedAt" to -1))),
        IndexSpec("translation_history", Document("translationId", 1)),
    )
}
