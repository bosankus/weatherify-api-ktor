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
        IndexSpec("subscriptions", Document("stripeCustomerId", 1)),
        IndexSpec("usage_logs", Document(mapOf("userId" to 1, "yearMonth" to 1)), unique),
        IndexSpec("invoice_records", Document("stripeInvoiceId", 1), unique),
        IndexSpec("invoice_records", Document("userId", 1)),
    )
}
