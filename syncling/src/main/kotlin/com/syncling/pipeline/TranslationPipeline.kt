package com.syncling.pipeline

import com.syncling.domain.ChangeType
import com.syncling.domain.CulturalAnalysis
import com.syncling.domain.LocaleProgressState
import com.syncling.domain.Project
import com.syncling.domain.TargetConfig
import com.syncling.repository.ProjectRepository
import com.syncling.repository.TranslationRepository
import com.syncling.repository.TranslationUpsert
import com.syncling.model.AppConfig
import com.syncling.model.SourceConfig
import com.syncling.model.SynclingConfig
import com.syncling.queue.WebhookPayload
import com.syncling.repository.SharedTranslationMemoryRepository
import com.syncling.services.BillingService
import com.syncling.services.GitHubService
import com.syncling.services.MemberUsageService
import com.syncling.services.PipelineEventBus
import com.syncling.services.CulturalSensitivityAnalyzer
import com.syncling.services.SemanticChangeAnalyzer
import com.syncling.services.StringParserService
import com.syncling.services.TranslationContext
import com.syncling.services.TranslationService
import com.syncling.services.requiredPluralForms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

fun buildConfigWithGlossary(project: Project, glossary: Map<String, Map<String, String>>): SynclingConfig {
    // Key by full path — basenames collide across modules (e.g. two values/strings.xml),
    // and keying by filename would silently drop all but one of them.
    val filesMap = project.sourceFilePaths.associateWith { it }
    return SynclingConfig(
        app = AppConfig(name = project.name, category = project.category, tone = project.tone),
        source = SourceConfig(
            language = "en",
            files = filesMap,
            watch_branch = project.watchBranch
        ),
        targets = project.targets,
        glossary = glossary.ifEmpty { null }
    )
}

// Plural form quantities recognised in Android string key names (e.g. "item_count.one")
private val PLURAL_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

class TranslationPipeline(
    private val gitHubService: GitHubService,
    private val translationService: TranslationService,
    private val billingService: BillingService,
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
    private val eventBus: PipelineEventBus,
    private val semanticChangeAnalyzer: SemanticChangeAnalyzer,
    private val culturalSensitivityAnalyzer: CulturalSensitivityAnalyzer,
    private val cdnPublishService: com.syncling.services.CdnPublishService,
    private val sharedMemoryRepository: SharedTranslationMemoryRepository? = null,
    /** Per-member analytics rollup. Optional so tests can omit it. */
    private val memberUsageService: MemberUsageService? = null,
    /** Outbound webhook notifications after each run. Optional — feature degrades gracefully when null. */
    private val outboundWebhookService: com.syncling.services.OutboundWebhookService? = null,
    /** Fuzzy translation memory — embeds new sources, surfaces nearest-neighbor approved pairs. Optional. */
    private val fuzzyMemoryService: com.syncling.services.FuzzyMemoryService? = null,
    /** Reviewer-correction feed; pipeline reads recent corrections as few-shot prompt examples. Optional. */
    private val reviewerFeedbackRepository: com.syncling.repository.ReviewerFeedbackRepository? = null,
    /** Per-stage timing + outcome counters + run-level totals. Optional so tests/previews can omit. */
    private val metrics: com.syncling.services.PipelineMetrics? = null,
    /** Quota-blocked run records — written on plan-limit aborts so the run auto-resumes after upgrade. Optional. */
    private val blockedRunRepository: com.syncling.repository.QuotaBlockedRunRepository? = null,
    /** Max concurrent Gemini batch calls per translation run. */
    private val translationConcurrency: Int = 8
) {
    private val log = LoggerFactory.getLogger(TranslationPipeline::class.java)

    suspend fun processWebhookPayload(
        payload: WebhookPayload,
        project: Project,
        config: SynclingConfig,
        githubToken: String
    ) {
        val userId = project.ownerId
        val runId = eventBus.startRun(
            userId, payload.repositoryFullName, payload.branchName, payload.commitHash.take(7),
            projectId = project.id, retriedFromRunId = payload.retriedFromRunId,
            triggeredByUserId = payload.triggeredByUserId
        )
        MDC.put("userId", userId)
        MDC.put("projectId", project.id)
        MDC.put("runId", runId)
        log.info("Pipeline: repo={} branch={} commit={}", payload.repositoryFullName, payload.branchName, payload.commitHash.take(7))

        try {

        // ── Pre-flight billing check ───────────────────────────────────────────
        // Fail fast before any GitHub API calls or MongoDB reads if the user has
        // hit their plan limit or their subscription payment failed — avoids
        // wasting quota on a doomed run.
        val billingBlock = billingService.accessBlockReason(userId)
        if (billingBlock != null) {
            eventBus.stepSkipped(userId, runId, "FETCHING_STRINGS")
            eventBus.stepSkipped(userId, runId, "DETECTING_CHANGES")
            eventBus.stepError(userId, runId, "BILLING_CHECK", billingBlock)
            listOf("TRANSLATING", "CREATING_PR", "CDN_PUBLISH").forEach { eventBus.stepSkipped(userId, runId, it) }
            eventBus.finishRun(userId, runId, error = billingBlock)
            // Advance the blocked-run record to this commit so the eventual post-upgrade
            // resume processes the newest push, not the one that first hit the quota.
            if (billingService.isLimitAlreadyExceeded(userId)) {
                recordQuotaBlockedRun(payload, project, stringsPending = 0, languagesPending = 0, runId = runId)
            }
            log.info("Pipeline skipped — userId={} billing block: {}", userId, billingBlock)
            return
        }

        // ── Step: Fetch source files ───────────────────────────────────────────
        val sourceFilePaths = config.source.files.values.toList()
        if (sourceFilePaths.isEmpty()) {
            eventBus.stepError(userId, runId, "FETCHING_STRINGS", "No source file configured")
            eventBus.finishRun(userId, runId, error = "No source file configured for project ${project.id}")
            log.warn("No source file configured for project={} — skipping pipeline", project.id)
            return
        }
        val fetchStartNs = System.nanoTime()
        eventBus.stepRunning(userId, runId, "FETCHING_STRINGS")
        val sourceContentByPath: Map<String, String> = try {
            coroutineScope {
                sourceFilePaths.map { filePath ->
                    async {
                        filePath to gitHubService.fetchFileContent(
                            repo = payload.repositoryFullName, branch = payload.commitHash,
                            filePath = filePath, token = githubToken
                        )
                    }
                }.awaitAll().toMap()
            }
        } catch (e: Exception) {
            eventBus.stepError(userId, runId, "FETCHING_STRINGS", e.message)
            metrics?.recordStage("FETCHING_STRINGS", System.nanoTime() - fetchStartNs, "error")
            eventBus.finishRun(userId, runId, error = "Could not read source file: ${e.message}")
            log.warn("Failed to fetch source file for repo={}: {}", payload.repositoryFullName, e.message)
            return
        }
        eventBus.stepDone(userId, runId, "FETCHING_STRINGS")
        metrics?.recordStage("FETCHING_STRINGS", System.nanoTime() - fetchStartNs, "ok")

        // Hash-based fast skip: hash is computed over all source files combined (sorted by path for
        // determinism). If byte-for-byte identical to the last successfully processed version, skip.
        val incomingHash = sha256(sourceContentByPath.entries.sortedBy { it.key }.joinToString("\n") { it.value })
        if (incomingHash == project.lastSourceFileHash) {
            listOf("DETECTING_CHANGES", "BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId)
            log.info("Source files unchanged (hash match) for repo={} commit={} — pipeline skipped",
                payload.repositoryFullName, payload.commitHash.take(7))
            return
        }

        // ── Step: Detect changes ───────────────────────────────────────────────
        val detectStartNs = System.nanoTime()
        eventBus.stepRunning(userId, runId, "DETECTING_CHANGES")
        // Merge strings from all source files; if keys collide across files the last file wins.
        val allSourceStrings: Map<String, String> = sourceContentByPath.entries
            .sortedBy { it.key }
            .fold(mutableMapOf()) { acc, (filePath, content) ->
                val parsed = when {
                    filePath.endsWith(".xml") -> StringParserService.parseAndroidXml(content)
                        .filterValues { !it.startsWith("@") }  // skip @string/ref, @drawable/ref etc.
                    filePath.endsWith(".strings") -> StringParserService.parseIosStrings(content)
                    filePath.endsWith(".json") || filePath.endsWith(".arb") ->
                        StringParserService.parseJsonStrings(content)
                    else -> {
                        log.warn("Unsupported source file format: {} — no strings extracted", filePath)
                        emptyMap()
                    }
                }
                acc.apply { putAll(parsed) }
            }
        val existingDbStrings = translationRepository.getStringKeysAndTexts(project.id)
        val allChangedStrings = allSourceStrings.filter { (key, text) -> existingDbStrings[key] != text }

        // Detect "orphaned" strings: present in the source DB with the right text but no translations yet.
        // This happens when a previous pipeline run stored the string via bulkUpsertStrings but then
        // failed before finishing (PR creation error, translation error, etc.) — the hash was never
        // updated, so the next run's hash check correctly passes, but allChangedStrings comes up empty
        // because the source text already matches what's in stringsCol.  We must re-process these so
        // they don't silently disappear.
        val orphanedKeys: Set<String> = if (allChangedStrings.isEmpty()) {
            val processedKeys = translationRepository.getProcessedStringKeys(project.id)
            allSourceStrings.keys.filter { key -> existingDbStrings.containsKey(key) && key !in processedKeys }
                .toSet()
        } else emptySet()

        val effectiveChangedStrings: Map<String, String> =
            if (orphanedKeys.isEmpty()) allChangedStrings
            else allChangedStrings + allSourceStrings.filterKeys { it in orphanedKeys }

        if (effectiveChangedStrings.isEmpty()) {
            eventBus.stepDone(userId, runId, "DETECTING_CHANGES", "No changes")
            metrics?.recordStage("DETECTING_CHANGES", System.nanoTime() - detectStartNs, "ok")
            listOf("BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            log.info("No new/modified strings in {} — skipping", payload.commitHash.take(7))
            return
        }

        if (orphanedKeys.isNotEmpty()) {
            log.info("{} orphaned string(s) (stored by a previous partial run, never translated) will be re-processed",
                orphanedKeys.size)
        }

        // Split all changed strings into brand-new ones and modifications to existing ones.
        // Orphaned keys are treated as new (force retranslation — their old and new source text are
        // identical so the semantic classifier would always mark them SURFACE and skip them).
        // Modifications are then classified: surface-only rewrites skip retranslation;
        // semantic changes (meaning shifted) retranslate all targets.
        val newStrings = effectiveChangedStrings.filter { (key, _) ->
            !existingDbStrings.containsKey(key) || key in orphanedKeys
        }
        val modifiedStrings = effectiveChangedStrings.filter { (key, _) ->
            existingDbStrings.containsKey(key) && key !in orphanedKeys
        }

        val (surfaceKeys, semanticKeys) = if (payload.forceTranslate) {
            // User explicitly overrode the classifier — treat every modified string as semantic.
            emptySet<String>() to modifiedStrings.keys
        } else {
            classifyModifiedStrings(modifiedStrings, existingDbStrings)
        }

        // Surface changes: source text drifted cosmetically (capitalization, punctuation, phrasing).
        // Persist the updated text but skip retranslation — existing translations stay valid.
        if (surfaceKeys.isNotEmpty()) {
            translationRepository.bulkUpsertStrings(
                project.id,
                surfaceKeys.associateWith { modifiedStrings.getValue(it) }
            )
        }
        if (surfaceKeys.isNotEmpty()) {
            log.info("{} surface change(s) in {} — source text updated, retranslation skipped", surfaceKeys.size, payload.commitHash.take(7))
        }

        val addedStrings = newStrings + modifiedStrings.filter { (key, _) -> key in semanticKeys }

        if (addedStrings.isEmpty()) {
            val msg = "All ${surfaceKeys.size} change${if (surfaceKeys.size != 1) "s" else ""} were surface-level — retranslation skipped"
            eventBus.stepDone(userId, runId, "DETECTING_CHANGES", msg)
            metrics?.recordStage("DETECTING_CHANGES", System.nanoTime() - detectStartNs, "ok")
            listOf("BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId, surfaceSkipped = surfaceKeys.size)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            return
        }

        val detectionSummary = buildString {
            val parts = mutableListOf<String>()
            if (newStrings.isNotEmpty()) parts += "${newStrings.size} new"
            if (semanticKeys.isNotEmpty()) parts += "${semanticKeys.size} semantic"
            if (surfaceKeys.isNotEmpty()) parts += "${surfaceKeys.size} surface skipped"
            append(parts.joinToString(", "))
        }
        eventBus.stepDone(userId, runId, "DETECTING_CHANGES", detectionSummary)
        metrics?.recordStage("DETECTING_CHANGES", System.nanoTime() - detectStartNs, "ok")
        log.info("{} string(s) to translate × {} language(s) ({} surface skipped)",
            addedStrings.size, config.targets.size, surfaceKeys.size)

        // ── Step: Billing check (plan quota + optional per-project cap) ───────
        val billingStartNs = System.nanoTime()
        eventBus.stepRunning(userId, runId, "BILLING_CHECK")
        try {
            // Per-project monthly cap check (independent of plan quota).
            val projectQuota = project.monthlyStringQuota
            if (projectQuota != null && memberUsageService != null) {
                val usedThisMonth = memberUsageService.totalForProject(project.id)
                val projected = usedThisMonth + addedStrings.size * config.targets.size
                if (projected > projectQuota) {
                    throw IllegalStateException(
                        "Project quota ($projectQuota strings/month) reached. " +
                        "Increase the project limit or wait until next month."
                    )
                }
            }
            billingService.checkAndEnforceLimits(project.ownerId, addedStrings.size * config.targets.size)
        } catch (e: Exception) {
            var friendlyMsg = e.message?.let { humanizeBillingError(it) } ?: "Plan limit reached"
            if (e is com.syncling.services.PlanLimitExceededException) {
                // Plan-level quota (not a per-project cap): the run is resumable. Record it
                // and tell the user exactly what's waiting and that no re-push is needed.
                recordQuotaBlockedRun(payload, project, addedStrings.size, config.targets.size, runId)
                friendlyMsg += " ${addedStrings.size} string${if (addedStrings.size != 1) "s" else ""} × " +
                    "${config.targets.size} language${if (config.targets.size != 1) "s" else ""} are queued " +
                    "and will be translated automatically when you upgrade."
            }
            eventBus.stepError(userId, runId, "BILLING_CHECK", friendlyMsg)
            metrics?.recordStage("BILLING_CHECK", System.nanoTime() - billingStartNs, "error")
            metrics?.incrementRun("quota_exceeded")
            eventBus.finishRun(userId, runId, error = friendlyMsg, surfaceSkipped = surfaceKeys.size)
            log.warn("Billing limit reached for {}: {}", payload.repositoryFullName, e.message)
            return
        }
        eventBus.stepDone(userId, runId, "BILLING_CHECK")
        metrics?.recordStage("BILLING_CHECK", System.nanoTime() - billingStartNs, "ok")

        // ── Step: Translate ────────────────────────────────────────────────────
        val totalLangs = config.targets.size
        val completedLangs = AtomicInteger(0)
        eventBus.stepRunning(userId, runId, "TRANSLATING", "0 / $totalLangs languages")

        // Seed per-locale lanes so the dashboard can draw one row per target right away.
        // Total = strings × 1 per locale; updates flow back as batches complete.
        val perLocaleTotal = addedStrings.size
        eventBus.seedLocales(userId, runId, config.targets.map { t ->
            LocaleProgressState(code = t.code, name = t.name, status = "queued", done = 0, total = perLocaleTotal)
        })

        val updatedFiles = ConcurrentHashMap<String, String>()
        val translatedCounts = ConcurrentHashMap<String, Int>()

        // Bulk-upsert every source string up-front — one round-trip shared by all target languages,
        // replacing the per-(key × target) upsertString() calls that used to dominate the write path.
        val stringIdByKey = translationRepository.bulkUpsertStrings(project.id, addedStrings)

        val totalCacheHits = AtomicInteger(0)
        val totalTokensIn = java.util.concurrent.atomic.AtomicLong(0)
        val totalTokensOut = java.util.concurrent.atomic.AtomicLong(0)
        val totalBlocked = AtomicInteger(0)
        val translatingStartedNs = System.nanoTime()
        coroutineScope {
            config.targets.map { target ->
                async {
                    eventBus.emitLocaleProgress(userId, runId, LocaleProgressState(
                        code = target.code, name = target.name, status = "translating",
                        done = 0, total = perLocaleTotal
                    ))
                    val outcome = processTarget(payload, project, config, target, addedStrings, stringIdByKey, runId = runId, commitShort = payload.commitHash.take(7), userId = userId)
                    translatedCounts[target.code] = outcome.count
                    totalCacheHits.addAndGet(outcome.cacheHits)
                    totalTokensIn.addAndGet(outcome.tokensIn)
                    totalTokensOut.addAndGet(outcome.tokensOut)
                    totalBlocked.addAndGet(outcome.blocked)
                    val done = completedLangs.incrementAndGet()
                    eventBus.stepRunning(userId, runId, "TRANSLATING", "$done / $totalLangs languages")
                    eventBus.emitLocaleProgress(userId, runId, LocaleProgressState(
                        code = target.code, name = target.name, status = "done",
                        done = perLocaleTotal, total = perLocaleTotal
                    ))
                    val approved = outcome.approved
                    if (approved.isNotEmpty()) {
                        val existing = gitHubService.fetchFileContent(
                            repo = payload.repositoryFullName, branch = payload.branchName,
                            filePath = target.file, token = githubToken
                        )
                        val merged = when {
                            target.file.endsWith(".xml") -> StringParserService.mergeAndroidXml(existing, approved)
                            target.file.endsWith(".strings") -> StringParserService.mergeIosStrings(existing, approved)
                            target.file.endsWith(".json") || target.file.endsWith(".arb") ->
                                StringParserService.mergeJsonStrings(existing, approved)
                            else -> existing
                        }
                        updatedFiles[target.file] = merged
                    }
                }
            }.awaitAll()
        }
        eventBus.stepDone(userId, runId, "TRANSLATING",
            "$totalLangs language${if (totalLangs != 1) "s" else ""} done")
        metrics?.recordStage("TRANSLATING", System.nanoTime() - translatingStartedNs, "ok")

        // Compute totals BEFORE finishRun so the persisted run summary and the
        // member-usage rollup share the same numbers as billing.
        val perLocaleCounts: Map<String, Int> = translatedCounts.toMap()
        val total = perLocaleCounts.values.sum()

        // Atomically record usage and enforce the limit in one MongoDB op.
        // If the limit was exceeded by a concurrent pipeline that raced past the pre-check,
        // we abort here rather than creating a PR for a quota-exceeded run.
        if (total > 0) {
            val billed = billingService.recordUsageAtomic(project.ownerId, total)
            if (!billed) {
                recordQuotaBlockedRun(payload, project, addedStrings.size, config.targets.size, runId)
                val msg = "Monthly string quota exceeded mid-run — PR suppressed. " +
                    "Upgrade your plan and this run will finish automatically."
                eventBus.stepError(userId, runId, "BILLING_CHECK", msg)
                eventBus.finishRun(userId, runId, error = msg)
                log.warn("Pipeline aborted post-translation: userId={} hit quota on atomic billing check", userId)
                return
            }
            memberUsageService?.record(
                projectId = project.id,
                triggeredByUserId = payload.triggeredByUserId,
                ownerId = project.ownerId,
                stringsTranslated = total,
                perLocale = perLocaleCounts
            )
        }

        // ── Step: Create PR ────────────────────────────────────────────────────
        val cacheHitsTotal = totalCacheHits.get()
        val tokensInTotal = totalTokensIn.get()
        val tokensOutTotal = totalTokensOut.get()
        val blockedTotal = totalBlocked.get()
        val costTotal = com.syncling.services.GeminiCostEstimator.estimateTranslation(tokensInTotal, tokensOutTotal)
        // Push per-string and per-run counters at the moment we have the final tallies — keeps
        // the Prometheus snapshot consistent with what we persist on PipelineRunSummary.
        metrics?.addStrings("translated", (total - cacheHitsTotal).toLong().coerceAtLeast(0))
        metrics?.addStrings("cache_hit", cacheHitsTotal.toLong())
        metrics?.addStrings("surface_skipped", surfaceKeys.size.toLong())
        metrics?.addStrings("blocked", blockedTotal.toLong())

        if (updatedFiles.isNotEmpty()) {
            val prStartNs = System.nanoTime()
            eventBus.stepRunning(userId, runId, "CREATING_PR")
            val pr = try {
                gitHubService.createBranchAndPr(
                    repo = payload.repositoryFullName,
                    baseBranch = payload.branchName,
                    files = updatedFiles,
                    commitMessage = "chore(i18n): auto-translate strings to ${config.targets.map { it.code }.joinToString()}",
                    prTitle = "Syncling: Auto-Translations for ${payload.commitHash.take(7)}",
                    prBody = buildPrBody(newStrings.size, semanticKeys.size, surfaceKeys.size, config.targets.size),
                    token = githubToken,
                    branchPattern = project.prBranchPattern
                )
            } catch (e: Exception) {
                eventBus.stepError(userId, runId, "CREATING_PR", e.message)
                metrics?.recordStage("CREATING_PR", System.nanoTime() - prStartNs, "error")
                eventBus.finishRun(userId, runId, error = "PR creation failed: ${e.message}", surfaceSkipped = surfaceKeys.size,
                    stringsTranslated = total, stringsPerLocale = perLocaleCounts, cacheHits = cacheHitsTotal,
                    tokensIn = tokensInTotal, tokensOut = tokensOutTotal, estimatedCostUsd = costTotal)
                metrics?.incrementRun("failed")
                log.warn("PR creation failed for {}: {}", payload.repositoryFullName, e.message)
                fireOutboundWebhook(project, runId, prUrl = null, total, cacheHitsTotal, surfaceKeys.size, config, status = "failed")
                return
            }
            eventBus.stepDone(userId, runId, "CREATING_PR", pr.prUrl)
            metrics?.recordStage("CREATING_PR", System.nanoTime() - prStartNs, "ok")
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            log.info("Translation PR created: {}", pr.prUrl)
            maybePublishCdn(project, userId, runId)
            eventBus.finishRun(userId, runId, prUrl = pr.prUrl, prBranch = pr.branchName, surfaceSkipped = surfaceKeys.size,
                stringsTranslated = total, stringsPerLocale = perLocaleCounts, cacheHits = cacheHitsTotal,
                tokensIn = tokensInTotal, tokensOut = tokensOutTotal, estimatedCostUsd = costTotal)
            metrics?.incrementRun("succeeded")
            fireOutboundWebhook(project, runId, pr.prUrl, total, cacheHitsTotal, surfaceKeys.size, config, status = "succeeded")
        } else {
            eventBus.stepSkipped(userId, runId, "CREATING_PR", "No translatable strings approved")
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            maybePublishCdn(project, userId, runId)
            eventBus.finishRun(userId, runId, surfaceSkipped = surfaceKeys.size,
                stringsTranslated = total, stringsPerLocale = perLocaleCounts, cacheHits = cacheHitsTotal,
                tokensIn = tokensInTotal, tokensOut = tokensOutTotal, estimatedCostUsd = costTotal)
            metrics?.incrementRun("succeeded")
            fireOutboundWebhook(project, runId, prUrl = null, total, cacheHitsTotal, surfaceKeys.size, config, status = "succeeded")
        }
        } catch (e: Exception) {
            // Catches TimeoutCancellationException from the caller's withTimeout wrapper,
            // any other unexpected exception that escaped the per-stage try-catch.
            val msg = "Pipeline failed unexpectedly: ${e.message}"
            log.error("Pipeline unhandled exception for repo={}: {}", payload.repositoryFullName, e.message, e)
            eventBus.finishRun(userId, runId, error = msg)
        } finally {
            MDC.remove("userId")
            MDC.remove("projectId")
            MDC.remove("runId")
        }
    }

    private fun fireOutboundWebhook(
        project: Project,
        runId: String,
        prUrl: String?,
        stringsTranslated: Int,
        cacheHits: Int,
        surfaceSkipped: Int,
        config: com.syncling.model.SynclingConfig,
        status: String
    ) {
        val svc = outboundWebhookService ?: return
        if (project.outboundWebhookUrl.isNullOrBlank()) return
        val payload = com.syncling.services.OutboundWebhookService.WebhookPayload(
            event = "pipeline.completed",
            projectId = project.id,
            projectName = project.name,
            repo = project.githubRepo,
            branch = project.watchBranch,
            commitShort = runId.take(7),
            prUrl = prUrl,
            stringsTranslated = stringsTranslated,
            cacheHits = cacheHits,
            surfaceSkipped = surfaceSkipped,
            locales = config.targets.map { it.code },
            status = status,
            timestamp = System.currentTimeMillis()
        )
        // Fire-and-forget: failures are logged inside the service.
        CoroutineScope(Dispatchers.IO).launch {
            svc.fire(project, payload)
        }
    }

    private suspend fun maybePublishCdn(project: Project, userId: String, runId: String) {
        if (!project.otaEnabled) {
            eventBus.stepSkipped(userId, runId, "CDN_PUBLISH", "OTA disabled for project")
            return
        }
        val cdnStartNs = System.nanoTime()
        runCatching { runCdnPublish(userId, runId, project.id, project.autoPromote, project.rolloutPercent) }
            .onSuccess {
                metrics?.recordStage("CDN_PUBLISH", System.nanoTime() - cdnStartNs, "ok")
            }
            .onFailure { e ->
                log.error("CDN publish failed for project={}: {}", project.id, e.message, e)
                eventBus.stepError(userId, runId, "CDN_PUBLISH", e.message ?: e.javaClass.simpleName)
                metrics?.recordStage("CDN_PUBLISH", System.nanoTime() - cdnStartNs, "error")
            }
    }

    private suspend fun runCdnPublish(userId: String, runId: String, projectId: String, promote: Boolean, rolloutPercent: Int = 100) {
        eventBus.stepRunning(userId, runId, "CDN_PUBLISH", "Compiling bundles…")
        val receipt = cdnPublishService.publish(projectId, promote = promote, rolloutPercent = rolloutPercent)
        if (receipt.skipped) {
            val reason = when (receipt.skipReason) {
                "bundle_unchanged" -> "Bundle unchanged — already live"
                "no_approved_strings" -> "No approved strings to publish"
                else -> "Skipped"
            }
            eventBus.stepSkipped(userId, runId, "CDN_PUBLISH", reason)
        } else {
            val promotedNote = when (receipt.pointer) {
                "canary" -> " (canary, ${rolloutPercent}% rollout)"
                null -> " (staged, not promoted)"
                else -> ""
            }
            val detail = "${receipt.locales.size} locale${if (receipt.locales.size != 1) "s" else ""} live on edge$promotedNote"
            eventBus.stepDone(userId, runId, "CDN_PUBLISH", detail)
            if (promote) eventBus.emitCdnReady(userId, runId, receipt.bundleVersion, receipt.locales)
        }
        log.info("CDN publish done: project={} locales={} version={}", projectId, receipt.locales, receipt.bundleVersion)
    }

    /**
     * Best-effort write of the resumable-run record. Failures are logged and swallowed —
     * the user can always re-trigger manually, so this must never fail the run handling.
     */
    private suspend fun recordQuotaBlockedRun(
        payload: WebhookPayload,
        project: Project,
        stringsPending: Int,
        languagesPending: Int,
        runId: String
    ) {
        val repo = blockedRunRepository ?: return
        runCatching {
            repo.upsert(com.syncling.domain.QuotaBlockedRun(
                projectId = project.id,
                ownerId = project.ownerId,
                repo = payload.repositoryFullName,
                branch = payload.branchName,
                commitHash = payload.commitHash,
                originalRunId = runId,
                stringsPending = stringsPending,
                languagesPending = languagesPending,
                blockedAt = System.currentTimeMillis()
            ))
        }.onFailure { log.warn("Failed to record quota-blocked run for project={}: {}", project.id, it.message) }
    }

    private fun humanizeBillingError(msg: String): String = when {
        "Project limit" in msg -> "Project limit reached for your plan"
        "Monthly string limit" in msg -> "Monthly string quota reached for your plan"
        else -> msg
    }

    private data class TargetOutcome(
        val approved: Map<String, String>,
        val count: Int,
        val cacheHits: Int,
        val tokensIn: Long = 0L,
        val tokensOut: Long = 0L,
        val blocked: Int = 0
    )

    private suspend fun processTarget(
        payload: WebhookPayload,
        project: Project,
        config: SynclingConfig,
        target: TargetConfig,
        addedStrings: Map<String, String>,
        stringIdByKey: Map<String, String>,
        runId: String,
        commitShort: String,
        userId: String
    ): TargetOutcome {
        val laneTotal = addedStrings.size
        val laneDone = AtomicInteger(0)
        val localeCacheHits = AtomicInteger(0)
        val localeTokensIn = java.util.concurrent.atomic.AtomicLong(0)
        val localeTokensOut = java.util.concurrent.atomic.AtomicLong(0)
        fun bumpLane(delta: Int) {
            if (delta <= 0) return
            val d = laneDone.addAndGet(delta).coerceAtMost(laneTotal)
            eventBus.emitLocaleProgress(userId, runId, LocaleProgressState(
                code = target.code, name = target.name, status = "translating",
                done = d, total = laneTotal
            ))
        }
        data class StringResult(val key: String, val text: String, val status: String)

        // Per-target accumulator. The pipeline used to write every translation row inline,
        // which meant N × M MongoDB round-trips. We now build the entire matrix in memory
        // and flush via a single bulkUpsertTranslations() call at the end of this function.
        val pendingUpserts = LinkedHashMap<String, TranslationUpsert>()
        val sourceTextByKey = HashMap<String, String>(addedStrings)
        // Track stringIds locally so we can backfill any plural quantities Gemini invented
        // (keys not present in `addedStrings` / `stringIdByKey`).
        val stringIds = HashMap<String, String>(stringIdByKey)

        fun queueUpsert(key: String, sourceText: String, translatedText: String, status: String, blockReason: String? = null) {
            val sid = stringIds[key] ?: return  // caller must ensure stringId exists
            pendingUpserts[key] = TranslationUpsert(
                stringId = sid,
                projectId = project.id,
                ownerId = project.ownerId,
                stringKey = key,
                sourceText = sourceText,
                projectName = project.name,
                targetLanguage = target.code,
                targetRegion = target.region,
                translatedText = translatedText,
                status = status,
                blockReason = blockReason,
                pipelineRunId = runId,
                commitShort = commitShort
            )
        }

        // ── Phase 1a: Separate plural forms from simple strings ────────────────
        // Keys like "item_count.one" / "item_count.other" are plural variants.
        // Group them so Gemini can translate all forms together and generate
        // missing CLDR categories (e.g. Russian needs one/few/many/other).
        val pluralGroups = mutableMapOf<String, MutableMap<String, String>>()
        val simpleStrings = mutableMapOf<String, String>()

        for ((key, text) in addedStrings) {
            val lastSegment = key.substringAfterLast(".")
            val baseName = key.substringBeforeLast(".")
            if (lastSegment in PLURAL_QUANTITIES && baseName != key) {
                pluralGroups.getOrPut(baseName) { mutableMapOf() }[lastSegment] = text
            } else {
                simpleStrings[key] = text
            }
        }

        val allResults = mutableListOf<StringResult?>()

        // ── Phase 1b: Translate plural groups ─────────────────────────────────
        if (pluralGroups.isNotEmpty()) {
            val sampleSource = addedStrings.values.firstOrNull() ?: ""
            val pluralContext = TranslationContext(
                appId = payload.repositoryFullName, appName = config.app.name,
                category = config.app.category, tone = config.app.tone,
                glossary = config.glossary?.get(target.code),
                sourceText = sampleSource, targetLanguage = target.name, targetRegion = target.region
            )
            val pluralResults = runCatching {
                translationService.translatePluralBatch(pluralGroups, pluralContext)
            }.getOrElse { e ->
                log.warn("Plural batch failed for lang={}: {}", target.code, e.message)
                emptyMap()
            }

            // Gemini may emit CLDR quantities not present in the source (e.g. Russian "few").
            // Bulk-upsert those new string rows first so we have stringIds when queueing translations.
            val extraStrings = mutableMapOf<String, String>()
            for ((baseName, forms) in pluralGroups) {
                val translatedForms = pluralResults[baseName] ?: continue
                for ((quantity, _) in translatedForms) {
                    val key = "$baseName.$quantity"
                    if (key !in stringIds) {
                        val src = forms[quantity] ?: forms["other"] ?: forms.values.firstOrNull() ?: ""
                        extraStrings[key] = src
                        sourceTextByKey[key] = src
                    }
                }
            }
            if (extraStrings.isNotEmpty()) {
                stringIds.putAll(translationRepository.bulkUpsertStrings(project.id, extraStrings))
            }

            for ((baseName, forms) in pluralGroups) {
                val translatedForms = pluralResults[baseName]
                if (translatedForms.isNullOrEmpty()) {
                    // Fallback: queue each form as blocked
                    for ((quantity, sourceText) in forms) {
                        val key = "$baseName.$quantity"
                        queueUpsert(key, sourceText, "", "blocked", "Plural translation failed")
                    }
                    bumpLane(forms.size)
                } else {
                    bumpLane(forms.size)
                    for ((quantity, translatedText) in translatedForms) {
                        val key = "$baseName.$quantity"
                        val sourceText = sourceTextByKey[key]
                            ?: forms[quantity] ?: forms["other"] ?: forms.values.firstOrNull() ?: ""
                        queueUpsert(key, sourceText, translatedText, "auto")
                        allResults += StringResult(key, translatedText, "auto")
                        if (project.sharedMemoryOptIn) {
                            runCatching { sharedMemoryRepository?.contribute(sourceText, target.name, translatedText) }
                        }
                    }
                }
            }
        }

        // ── Phase 1c: Translate simple strings ────────────────────────────────
        // Fetch the two prompt-augmentation signals once per locale:
        //   • reviewer corrections — newest first, project + locale specific
        //   • fuzzy TM hits — embedding nearest-neighbors over the project's approved pool
        // Both degrade silently to empty lists if the optional service is missing.
        val reviewerExamples: List<Pair<String, String>> = reviewerFeedbackRepository
            ?.runCatching { recentExamples(project.id, target.code, limit = 8) }
            ?.getOrNull()
            ?.map { it.sourceText to it.reviewerEdit }
            ?: emptyList()
        val fuzzyExamples: List<Pair<String, String>> = fuzzyMemoryService
            ?.runCatching { lookupExamples(project.id, target.code, simpleStrings.values.toList()) }
            ?.getOrNull()
            ?.map { it.source to it.translation }
            ?: emptyList()

        val semaphore = Semaphore(translationConcurrency)
        val batches = simpleStrings.entries.chunked(BATCH_SIZE).map { chunk -> chunk.associate { it.key to it.value } }

        // Each per-batch coroutine returns the translated rows it produced — translation upserts
        // are queued (not flushed) so the whole target locale lands in one bulkWrite at the end.
        data class BatchPiece(val results: List<StringResult?>, val queued: List<TranslationUpsert>)

        val simpleBatchPieces: List<BatchPiece> = coroutineScope {
            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        val keyedContexts = batch.mapValues { (_, sourceText) ->
                            TranslationContext(
                                appId = payload.repositoryFullName, appName = config.app.name,
                                category = config.app.category, tone = config.app.tone,
                                glossary = config.glossary?.get(target.code),
                                sourceText = sourceText, targetLanguage = target.name, targetRegion = target.region,
                                fuzzyExamples = fuzzyExamples,
                                reviewerExamples = reviewerExamples
                            )
                        }
                        val batchOutcome = translationService.translateBatchTracked(keyedContexts)
                        val batchResults = batchOutcome.results
                        localeCacheHits.addAndGet(batchOutcome.cacheHits)
                        localeTokensIn.addAndGet(batchOutcome.tokenUsage.inputTokens)
                        localeTokensOut.addAndGet(batchOutcome.tokenUsage.outputTokens)
                        bumpLane(batch.size)
                        val queuedHere = mutableListOf<TranslationUpsert>()
                        val results = batch.keys.map { key ->
                            val sourceText = batch[key]!!
                            val sid = stringIds[key]
                            val outcome = batchResults[key]
                            // Build a TranslationUpsert for the row's terminal state; the calling
                            // thread merges these into pendingUpserts after awaitAll().
                            fun row(text: String, status: String, reason: String?): TranslationUpsert? =
                                sid?.let {
                                    TranslationUpsert(
                                        stringId = it, projectId = project.id, ownerId = project.ownerId,
                                        stringKey = key, sourceText = sourceText, projectName = project.name,
                                        targetLanguage = target.code, targetRegion = target.region,
                                        translatedText = text, status = status, blockReason = reason,
                                        pipelineRunId = runId, commitShort = commitShort
                                    )
                                }
                            when {
                                outcome == null -> {
                                    log.warn("Missing batch result for key='{}' lang={}", key, target.code)
                                    row("", "blocked", "Missing from batch")?.let(queuedHere::add)
                                    null
                                }
                                outcome.isSuccess -> {
                                    val r = outcome.getOrThrow()
                                    val status = if (r.flags.isNotEmpty()) "review" else "auto"
                                    row(r.text, status, null)?.let(queuedHere::add)
                                    if (status != "auto") log.info("'{}' → {} flagged: {}", key, target.code, r.flags)
                                    if (status == "auto" && project.sharedMemoryOptIn && sharedMemoryRepository != null) {
                                        runCatching { sharedMemoryRepository.contribute(sourceText, target.name, r.text) }
                                    }
                                    StringResult(key, r.text, status)
                                }
                                else -> {
                                    val error = outcome.exceptionOrNull()?.message
                                    log.warn("Failed key='{}' lang={}: {}", key, target.code, error)
                                    row("", "blocked", error)?.let(queuedHere::add)
                                    null
                                }
                            }
                        }
                        BatchPiece(results, queuedHere)
                    }
                }
            }.awaitAll()
        }

        val simpleResults: List<StringResult?> = simpleBatchPieces.flatMap { it.results }
        simpleBatchPieces.flatMap { it.queued }.forEach { pendingUpserts[it.stringKey] = it }

        allResults.addAll(simpleResults)

        // ── Phase 2: Cultural sensitivity check ───────────────────────────────
        val finalResults: List<StringResult?> = if (!project.culturalSensitivityEnabled) allResults else {
            val autoResults = allResults.filterNotNull().filter { it.status == "auto" }
            val analysisInputs = autoResults.mapNotNull { r ->
                val srcText = addedStrings[r.key] ?: return@mapNotNull null
                r.key to (r.text to srcText)
            }.toMap()

            val analyses = culturalSensitivityAnalyzer.analyzeBatch(
                analysisInputs, target.name, target.region, config.app.category, config.app.tone
            )

            allResults.map { r ->
                if (r == null || r.status != "auto") return@map r
                val analysis = analyses[r.key] ?: return@map r
                if (!analysis.needsReview) return@map r
                val srcText = addedStrings[r.key] ?: return@map r
                val notes = "Cultural: ${analysis.issues.joinToString("; ")}"
                queueUpsert(r.key, srcText, r.text, "review", notes)
                log.info("Cultural flag key='{}' lang={}: {}", r.key, target.code, analysis.issues)
                StringResult(r.key, r.text, "review")
            }
        }

        // Single round-trip: write every queued translation row for this locale at once.
        if (pendingUpserts.isNotEmpty()) {
            translationRepository.bulkUpsertTranslations(pendingUpserts.values.toList())
        }

        val approved = finalResults.filterNotNull()
            .filter { it.status == "auto" }
            .associate { it.key to it.text }

        // Contribute approved (source → translation) pairs to the fuzzy TM.
        // Best-effort — the lookup half degrades gracefully if this never lands.
        val fuzzy = fuzzyMemoryService
        if (approved.isNotEmpty() && fuzzy != null) {
            val pairs = approved.mapNotNull { (key, translation) ->
                val src = sourceTextByKey[key] ?: addedStrings[key] ?: return@mapNotNull null
                src to translation
            }
            runCatching { fuzzy.contribute(project.id, target.code, pairs) }
                .onFailure { log.warn("Fuzzy TM contribution failed for locale={}: {}", target.code, it.message) }
        }
        val count = finalResults.count { it != null }
        val blocked = pendingUpserts.values.count { it.status == "blocked" }
        return TargetOutcome(
            approved = approved,
            count = count,
            cacheHits = localeCacheHits.get(),
            tokensIn = localeTokensIn.get(),
            tokensOut = localeTokensOut.get(),
            blocked = blocked
        )
    }

    private suspend fun classifyModifiedStrings(
        modifiedStrings: Map<String, String>,
        existingDbStrings: Map<String, String>
    ): Pair<Set<String>, Set<String>> {
        if (modifiedStrings.isEmpty()) return emptySet<String>() to emptySet()

        val inputPairs = modifiedStrings.mapValues { (key, newText) -> existingDbStrings.getValue(key) to newText }
        val batchResults = semanticChangeAnalyzer.analyzeBatch(inputPairs)

        val surfaceKeys = batchResults.filter { it.value.changeType == ChangeType.SURFACE }.keys
        val semanticKeys = batchResults.filter { it.value.changeType == ChangeType.SEMANTIC }.keys
        return surfaceKeys to semanticKeys
    }

    companion object {
        private const val BATCH_SIZE = 25

        private fun sha256(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun buildPrBody(newCount: Int, semanticCount: Int, surfaceSkipped: Int, languageCount: Int): String {
        val rows = buildString {
            if (newCount > 0) appendLine("| **New strings** | $newCount |")
            if (semanticCount > 0) appendLine("| **Semantically changed** | $semanticCount |")
            if (surfaceSkipped > 0) appendLine("| **Surface changes skipped** | $surfaceSkipped |")
            appendLine("| **Languages** | $languageCount |")
        }.trimEnd()
        val skipNote = if (surfaceSkipped > 0)
            "\n> **$surfaceSkipped string${if (surfaceSkipped != 1) "s" else ""} skipped** — only capitalization, punctuation, or phrasing changed. Existing translations remain valid.\n"
        else ""
        return """
            ## Syncling Auto-Translation

            | | |
            |---|---|
            $rows

            $skipNote> Flagged strings are in the review portal. A follow-up PR is created automatically on approval.

            *Generated by Syncling*
        """.trimIndent()
    }
}
