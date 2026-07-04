package com.syncling.di

import com.androidplay.core.mongo.IndexSpec
import com.syncling.repository.*
import com.syncling.repository.mongo.*
import com.syncling.repository.mongo.MongoCdnPublishRepository
import com.syncling.repository.mongo.MongoNotificationRepository
import com.syncling.repository.mongo.MongoSharedTranslationMemoryRepository
import com.syncling.services.CulturalSensitivityAnalyzer
import com.syncling.services.SemanticChangeAnalyzer
import org.bson.Document
import org.koin.dsl.module
import com.mongodb.client.model.IndexOptions
import java.util.concurrent.TimeUnit

fun synclingModule(encryptionKey: String) = module {
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
    single<ReviewerFeedbackRepository> { com.syncling.repository.mongo.MongoReviewerFeedbackRepository(get()) }
    single<TranslationEmbeddingRepository> { com.syncling.repository.mongo.MongoTranslationEmbeddingRepository(get()) }
    single { com.syncling.services.EmbeddingService() }
    single { com.syncling.services.FuzzyMemoryService(get(), get()) }
    single<io.micrometer.prometheusmetrics.PrometheusMeterRegistry> {
        io.micrometer.prometheusmetrics.PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT)
    }
    single<io.micrometer.core.instrument.MeterRegistry> { get<io.micrometer.prometheusmetrics.PrometheusMeterRegistry>() }
    single { com.syncling.services.PipelineMetrics(get()) }
    single<ProjectMembershipRepository> { MongoProjectMembershipRepository(get()) }
    single<PipelineRunRepository> { MongoPipelineRunRepository(get()) }
    single<com.syncling.repository.SupportTicketRepository> { com.syncling.repository.mongo.MongoSupportTicketRepository(get()) }
    single<com.syncling.repository.ApiTokenRepository> { com.syncling.repository.mongo.MongoApiTokenRepository(get()) }
    single<MemberUsageRepository> { MongoMemberUsageRepository(get()) }
    single<FigmaCandidateRepository> { MongoFigmaCandidateRepository(get()) }
    single<FigmaNodeBindingRepository> { MongoFigmaNodeBindingRepository(get()) }
    single<com.syncling.repository.FigmaPreviewRepository> { com.syncling.repository.mongo.MongoFigmaPreviewRepository(get()) }
    single<com.syncling.repository.FigmaSettingsRepository> { com.syncling.repository.mongo.MongoFigmaSettingsRepository(get()) }
    single<QuotaBlockedRunRepository> { MongoQuotaBlockedRunRepository(get()) }
    single { com.syncling.services.MemberUsageService(get()) }
    single {
        com.syncling.services.StatusService(
            pipelineRunRepository = get(),
            cdnPublishRepository = get(),
        )
    }
    single {
        com.syncling.services.AnalyticsService(
            pipelineRunRepository = get(),
            memberUsageRepository = get(),
            billingRepository = get(),
            translationRepository = get(),
            projectRepository = get(),
            membershipRepository = get(),
            userRepository = get()
        )
    }
}

fun synclingIndexes(): List<IndexSpec> {
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
        // Denormalized read-path indexes — translations now carry projectId/ownerId/projectName/stringKey
        // so reads no longer need $lookup joins against strings/projects.
        IndexSpec("translations", Document(mapOf("ownerId" to 1, "status" to 1, "updatedAt" to -1))),
        IndexSpec("translations", Document(mapOf("ownerId" to 1, "targetLanguage" to 1, "status" to 1))),
        IndexSpec("translations", Document(mapOf("projectId" to 1, "status" to 1))),
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
        // TTL: purge semantic-change cache entries after 90 days — source text semantics are stable
        // Note: both caches key on _id (no separate hashKey field), so no hashKey index needed
        IndexSpec("semantic_change_cache", Document("createdAt", 1), IndexOptions().expireAfter(90, TimeUnit.DAYS).name("ttl_semantic_90d")),
        // TTL: purge cultural-analysis cache entries after 30 days — cultural norms shift slowly
        IndexSpec("cultural_analysis_cache", Document("createdAt", 1), IndexOptions().expireAfter(30, TimeUnit.DAYS).name("ttl_cultural_30d")),
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
        // Reviewer feedback: per (projectId, targetLanguage) descending recency for prompt few-shot.
        IndexSpec("reviewer_feedback", Document(mapOf("projectId" to 1, "targetLanguage" to 1, "createdAt" to -1))),
        // Translation embeddings: unique per (projectId, sourceText) via hashKey; project-scoped scan for fuzzy lookup.
        IndexSpec("translation_embeddings", Document("hashKey", 1), IndexOptions().unique(true)),
        IndexSpec("translation_embeddings", Document("projectId", 1)),
        // Translation history: query by project + stringKey, sorted by time
        IndexSpec("translation_history", Document(mapOf("projectId" to 1, "stringKey" to 1, "changedAt" to -1))),
        IndexSpec("translation_history", Document("translationId", 1)),
        // Project members: unique (projectId, userId) so an ACTIVE user can't double-join.
        // Partial filter — userId is null on pending INVITED rows; without the filter
        // every pending invite for a project would collide on (projectId, null).
        IndexSpec(
            "project_members",
            Document(mapOf("projectId" to 1, "userId" to 1)),
            IndexOptions().unique(true).partialFilterExpression(Document("userId", Document("\$exists", true)))
        ),
        // Email is always set, so uniqueness on (projectId, email) prevents duplicate invites.
        IndexSpec("project_members", Document(mapOf("projectId" to 1, "email" to 1)), unique),
        IndexSpec("project_members", Document("projectId", 1)),
        IndexSpec("project_members", Document("userId", 1)),
        IndexSpec("project_members", Document("inviteToken", 1)),
        // Pipeline run history — analytics queries are owner-scoped, project-scoped, or
        // member-scoped, each by descending startedAt. TTL keeps 365 days of history.
        IndexSpec("pipeline_runs", Document(mapOf("ownerId" to 1, "startedAt" to -1))),
        IndexSpec("pipeline_runs", Document(mapOf("projectId" to 1, "startedAt" to -1))),
        IndexSpec("pipeline_runs", Document(mapOf("triggeredByUserId" to 1, "startedAt" to -1))),
        IndexSpec("pipeline_runs", Document("startedAt", 1), IndexOptions().expireAfter(365, TimeUnit.DAYS).name("ttl_pipeline_runs_365d")),
        // Member usage rollup — unique on the three-tuple to prevent duplicate rows;
        // owner/project scoped reads for the analytics tab.
        IndexSpec(
            "member_usage_logs",
            Document(mapOf("projectId" to 1, "memberUserId" to 1, "yearMonth" to 1)),
            unique
        ),
        IndexSpec("member_usage_logs", Document(mapOf("ownerId" to 1, "yearMonth" to 1))),
        IndexSpec("member_usage_logs", Document(mapOf("projectId" to 1, "yearMonth" to 1))),
        IndexSpec("support_tickets", Document(mapOf("userId" to 1, "updatedAt" to -1))),
        IndexSpec("support_messages", Document(mapOf("ticketId" to 1, "sentAt" to 1))),
        // API tokens: tokenHash is the primary lookup key (must be unique); userId for list queries
        IndexSpec("api_tokens", Document("tokenHash", 1), unique),
        IndexSpec("api_tokens", Document("userId", 1)),
        // Quota-blocked runs: one per project; ownerId scan drives the post-upgrade resume.
        // TTL safety net — a record that never resumes (project deleted, user gone) expires after 60 days.
        IndexSpec("quota_blocked_runs", Document("projectId", 1), unique),
        IndexSpec("quota_blocked_runs", Document("ownerId", 1)),
        IndexSpec("quota_blocked_runs", Document("updatedAt", 1), IndexOptions().expireAfter(60, TimeUnit.DAYS).name("ttl_quota_blocked_60d")),
        // Figma inbox: project-scoped listing sorted by recency; at most one PENDING row per
        // node so a designer re-pushing the same node refreshes the inbox entry in place.
        IndexSpec("figma_candidates", Document(mapOf("projectId" to 1, "status" to 1, "updatedAt" to -1))),
        IndexSpec(
            "figma_candidates",
            Document(mapOf("projectId" to 1, "figmaFileKey" to 1, "figmaNodeId" to 1)),
            IndexOptions().unique(true).partialFilterExpression(Document("status", "PENDING")).name("uniq_pending_node")
        ),
        // Figma node bindings: nodeId ↔ stringKey map, one row per node per project.
        IndexSpec("figma_node_bindings", Document(mapOf("projectId" to 1, "figmaFileKey" to 1, "figmaNodeId" to 1)), unique),
        IndexSpec("figma_node_bindings", Document("projectId", 1)),
        // Figma frame screenshots: one row per frame, replaced on every push.
        IndexSpec("figma_previews", Document(mapOf("projectId" to 1, "figmaFileKey" to 1, "figmaFrameId" to 1)), unique),
        // Figma sync preferences: one row per project.
        IndexSpec("figma_settings", Document("projectId", 1), unique),
    )
}
