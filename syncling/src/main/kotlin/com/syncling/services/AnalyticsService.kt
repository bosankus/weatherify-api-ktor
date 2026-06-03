package com.syncling.services

import com.syncling.domain.BillingPlan
import com.syncling.domain.MemberUsage
import com.syncling.repository.BillingRepository
import com.syncling.repository.MemberUsageRepository
import com.syncling.repository.PipelineRunRepository
import com.syncling.repository.ProjectMembershipRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.TranslationRepository
import com.syncling.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * Pure-function aggregations for the Team-plan analytics surface.
 *
 * All numbers exposed here are traceable to a single stored field — no estimation,
 * no client-side computation that the server can't reproduce. The owner-scoped
 * billing total in [BillingRepository.getUsage] is the source of truth for
 * stringsTranslated in the current month; per-member rollups [MemberUsage] must
 * sum to that total (the analytics tab surfaces this invariant in tooltips).
 *
 * Range handling: "month" maps to the current calendar yearMonth and reads from
 * the yearMonth-partitioned [MemberUsageRepository]. "30d" / "90d" reads from
 * [PipelineRunRepository] which is the authoritative time-ranged store; member
 * rollups for those ranges are summed across yearMonth partitions.
 */
class AnalyticsService(
    private val pipelineRunRepository: PipelineRunRepository,
    private val memberUsageRepository: MemberUsageRepository,
    private val billingRepository: BillingRepository,
    private val translationRepository: TranslationRepository,
    private val projectRepository: ProjectRepository,
    private val membershipRepository: ProjectMembershipRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(AnalyticsService::class.java)

    enum class Range(val label: String) {
        DAYS_30("30d"), DAYS_90("90d"), MONTH("month");
        companion object {
            fun parse(s: String?): Range = entries.firstOrNull { it.label == s } ?: DAYS_30
        }
    }

    private fun sinceMillis(range: Range, now: Long = System.currentTimeMillis()): Long = when (range) {
        Range.DAYS_30 -> now - 30L * 86_400_000
        Range.DAYS_90 -> now - 90L * 86_400_000
        Range.MONTH -> firstOfCurrentMonthMillis()
    }

    // ── Overview ───────────────────────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class Overview(
        val plan: String,
        val planPriceInrPerMonth: Int,
        val stringsTranslatedThisMonth: Int,
        val costPerStringInr: Double?,         // null when no strings translated
        val lastMonthCostPerStringInr: Double?,
        val projectedEndOfMonthCostInr: Double?,
        val sparkline: List<HistoryPoint>,     // last 6 calendar months, ascending
        val trackingSinceMillis: Long?         // earliest pipeline_runs.startedAt for owner, null if no data
    )

    @kotlinx.serialization.Serializable
    data class HistoryPoint(val month: String, val strings: Int)

    suspend fun overview(ownerId: String): Overview {
        val sub = billingRepository.getSubscription(ownerId)
        val planPrice = planPriceInr(sub.plan)
        val usage = billingRepository.getUsage(ownerId)
        val history = billingRepository.getHistoricalUsage(ownerId).sortedBy { it.yearMonth }

        val currentMonth = currentYearMonth()
        val currentStrings = usage.stringsTranslated
        val lastMonthEntry = history.lastOrNull { it.yearMonth != currentMonth }

        val currentCost: Double? = if (planPrice > 0 && currentStrings > 0)
            planPrice.toDouble() / currentStrings else null
        val lastCost: Double? = if (planPrice > 0 && lastMonthEntry != null && lastMonthEntry.stringsTranslated > 0)
            planPrice.toDouble() / lastMonthEntry.stringsTranslated else null

        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val day = now.dayOfMonth
        val daysInMonth = LocalDate(now.year, now.monthNumber, 1).let { firstOfNext(it).minusDays(1).dayOfMonth }
        val projectedCost: Double? = if (planPrice > 0 && currentStrings > 0 && day > 5) {
            val projectedStrings = currentStrings.toDouble() * daysInMonth / day
            planPrice / projectedStrings
        } else null

        // Build last-6 sparkline including the current month even if zero
        val byMonth = history.associate { it.yearMonth to it.stringsTranslated }
            .toMutableMap().apply { putIfAbsent(currentMonth, currentStrings) }
        val sparkPoints = recentSixMonths().map { ym -> HistoryPoint(ym, byMonth[ym] ?: 0) }

        // Tracking-since = earliest pipeline_run for this owner. Index-backed single-row read.
        val trackingSince = pipelineRunRepository.earliestStartedAtForOwner(ownerId)

        return Overview(
            plan = sub.plan.name,
            planPriceInrPerMonth = planPrice,
            stringsTranslatedThisMonth = currentStrings,
            costPerStringInr = currentCost,
            lastMonthCostPerStringInr = lastCost,
            projectedEndOfMonthCostInr = projectedCost,
            sparkline = sparkPoints,
            trackingSinceMillis = trackingSince
        )
    }

    // ── Projects ───────────────────────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class ProjectRow(
        val projectId: String,
        val name: String,
        val stringsTranslated: Int,
        val runs: Int,
        val locales: Int,
        val acceptanceRatePct: Int?,   // null when no auto/review/blocked decisions yet
        val lastRunMillis: Long?
    )

    suspend fun projects(ownerId: String, range: Range): List<ProjectRow> {
        val since = sinceMillis(range)
        val projects = projectRepository.listForUser(ownerId)
        val runs = pipelineRunRepository.listForOwner(ownerId, since, limit = 5000)
        val byProject = runs.groupBy { it.projectId }

        return coroutineScope {
            projects.map { p ->
                async {
                    val pruns = byProject[p.id].orEmpty()
                    val statusCounts = runCatching { translationRepository.countByStatusForProject(p.id) }
                        .getOrDefault(emptyMap())
                    val auto = statusCounts["auto"] ?: 0
                    val review = statusCounts["review"] ?: 0
                    val blocked = statusCounts["blocked"] ?: 0
                    val decided = auto + review + blocked
                    val acceptance = if (decided > 0) (auto * 100 / decided) else null
                    ProjectRow(
                        projectId = p.id,
                        name = p.name,
                        stringsTranslated = pruns.sumOf { it.stringsTranslated },
                        runs = pruns.size,
                        locales = pruns.flatMap { it.stringsPerLocale.keys }.toSet().size,
                        acceptanceRatePct = acceptance,
                        lastRunMillis = pruns.maxOfOrNull { it.startedAt }
                    )
                }
            }.awaitAll()
        }.sortedByDescending { it.stringsTranslated }
    }

    // ── Locales ────────────────────────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class LocaleRow(val locale: String, val stringsTranslated: Int, val projectsCount: Int)

    suspend fun locales(ownerId: String, range: Range): List<LocaleRow> {
        val since = sinceMillis(range)
        val runs = pipelineRunRepository.listForOwner(ownerId, since, limit = 5000)
        val tally = mutableMapOf<String, MutableMap<String, Int>>()  // locale -> projectId -> strings
        for (r in runs) {
            for ((loc, n) in r.stringsPerLocale) {
                if (n <= 0) continue
                tally.getOrPut(loc) { mutableMapOf() }.merge(r.projectId, n, Int::plus)
            }
        }
        return tally.entries
            .map { (loc, perProject) ->
                LocaleRow(locale = loc, stringsTranslated = perProject.values.sum(), projectsCount = perProject.size)
            }
            .sortedByDescending { it.stringsTranslated }
    }

    // ── Runs table ─────────────────────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class RunRow(
        val runId: String, val projectId: String, val projectName: String?,
        val repo: String, val commitShort: String,
        val startedAtMillis: Long, val durationMs: Long?,
        val status: String, val stringsTranslated: Int,
        val triggeredByUserId: String?, val triggeredByLabel: String,
        val triggeredByDisplayName: String?
    )

    suspend fun runs(ownerId: String, range: Range, projectId: String?, limit: Int): List<RunRow> {
        val since = sinceMillis(range)
        val all = if (projectId != null)
            pipelineRunRepository.listForProject(projectId, since, limit).filter { it.ownerId == ownerId }
        else
            pipelineRunRepository.listForOwner(ownerId, since, limit)

        // Resolve display names for unique trigger users (cap N to avoid scanning a huge cohort)
        val triggerIds = all.mapNotNull { it.triggeredByUserId }.toSet()
        val nameById = resolveDisplayNames(triggerIds)

        val projects = projectRepository.listForUser(ownerId).associateBy { it.id }

        return all.map { r ->
            RunRow(
                runId = r.runId, projectId = r.projectId,
                projectName = projects[r.projectId]?.name,
                repo = r.repo, commitShort = r.commitShort,
                startedAtMillis = r.startedAt, durationMs = r.durationMs,
                status = r.status, stringsTranslated = r.stringsTranslated,
                triggeredByUserId = r.triggeredByUserId,
                triggeredByLabel = r.triggeredByLabel,
                triggeredByDisplayName = r.triggeredByUserId?.let { nameById[it] }
            )
        }
    }

    // ── Members (Team owner/admin only) ────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class MemberRow(
        val memberUserId: String,                  // sentinel "external" for unmatched webhooks
        val displayName: String,
        val email: String?,
        val stringsTranslated: Int,
        val runsTriggered: Int,
        val projectsTouched: Int,
        val lastActiveMillis: Long?,
        val perLocale: Map<String, Int>
    )

    suspend fun members(ownerId: String, range: Range): List<MemberRow> {
        val since = sinceMillis(range)
        val runs = pipelineRunRepository.listForOwner(ownerId, since, limit = 5000)

        // Aggregate per (memberUserId or "external") from pipeline_runs.
        // Using pipeline_runs (not member_usage_logs) here because runs carry the
        // timestamp for arbitrary date ranges; member_usage_logs is yearMonth-keyed.
        data class Acc(
            var strings: Int = 0,
            var runs: Int = 0,
            val projects: MutableSet<String> = mutableSetOf(),
            var lastActive: Long = 0L,
            val perLocale: MutableMap<String, Int> = mutableMapOf()
        )
        val byMember = mutableMapOf<String, Acc>()
        for (r in runs) {
            val key = r.triggeredByUserId ?: MemberUsage.EXTERNAL
            val acc = byMember.getOrPut(key) { Acc() }
            acc.strings += r.stringsTranslated
            acc.runs += 1
            acc.projects += r.projectId
            if (r.startedAt > acc.lastActive) acc.lastActive = r.startedAt
            for ((loc, n) in r.stringsPerLocale) {
                if (n > 0) acc.perLocale.merge(loc, n, Int::plus)
            }
        }

        val realIds = byMember.keys.filter { it != MemberUsage.EXTERNAL }.toSet()
        val users = coroutineScope {
            realIds.map { id -> async { runCatching { userRepository.findById(id) }.getOrNull()?.let { id to it } } }
                .awaitAll().filterNotNull().toMap()
        }

        return byMember.map { (key, acc) ->
            val u = users[key]
            MemberRow(
                memberUserId = key,
                displayName = when {
                    key == MemberUsage.EXTERNAL -> "External (webhook)"
                    u?.githubUsername?.isNotBlank() == true -> "@${u.githubUsername}"
                    u?.email?.isNotBlank() == true -> u.email ?: "unknown"
                    else -> "Unknown (${key.take(8)})"
                },
                email = u?.email,
                stringsTranslated = acc.strings,
                runsTriggered = acc.runs,
                projectsTouched = acc.projects.size,
                lastActiveMillis = acc.lastActive.takeIf { it > 0 },
                perLocale = acc.perLocale
            )
        }.sortedByDescending { it.stringsTranslated }
    }

    // ── Cost breakdown (Team owner/admin only) ─────────────────────────────────

    @kotlinx.serialization.Serializable
    data class CostBreakdown(
        val plan: String,
        val planPriceInr: Int,
        val totalStringsThisMonth: Int,
        val perMember: List<CostShare>,
        val perProject: List<CostShare>,
        /** Owner billing total per usage_logs (the source of truth). */
        val ownerBillingTotalStrings: Int,
        /** Sum of per-member rows; must equal ownerBillingTotalStrings or the invariant is broken. */
        val sumOfMemberStrings: Int
    )

    @kotlinx.serialization.Serializable
    data class CostShare(val key: String, val displayName: String, val strings: Int, val shareInr: Double)

    suspend fun costBreakdown(ownerId: String): CostBreakdown {
        val sub = billingRepository.getSubscription(ownerId)
        val price = planPriceInr(sub.plan)
        val ym = currentYearMonth()
        val billingTotal = billingRepository.getUsage(ownerId).stringsTranslated
        val rollups = memberUsageRepository.listForOwner(ownerId, ym)

        val realIds = rollups.map { it.memberUserId }.filter { it != MemberUsage.EXTERNAL }.toSet()
        val users = coroutineScope {
            realIds.map { id -> async { runCatching { userRepository.findById(id) }.getOrNull()?.let { id to it } } }
                .awaitAll().filterNotNull().toMap()
        }
        val projects = projectRepository.listForUser(ownerId).associateBy { it.id }

        // Total used as the proration base. We deliberately use the rollup sum here so a
        // single member's share is consistent with the per-member rows displayed alongside.
        // The "billing total" is surfaced separately so the owner can spot drift.
        val totalRollupStrings = rollups.sumOf { it.stringsTranslated }.coerceAtLeast(1)

        fun share(strings: Int) =
            if (price == 0) 0.0 else price.toDouble() * strings / totalRollupStrings

        val perMember = rollups
            .groupBy { it.memberUserId }
            .map { (key, rows) ->
                val total = rows.sumOf { it.stringsTranslated }
                val u = users[key]
                val name = when {
                    key == MemberUsage.EXTERNAL -> "External (webhook)"
                    u?.githubUsername?.isNotBlank() == true -> "@${u.githubUsername}"
                    u?.email?.isNotBlank() == true -> u.email ?: "unknown"
                    else -> "Unknown (${key.take(8)})"
                }
                CostShare(key = key, displayName = name, strings = total, shareInr = share(total))
            }
            .sortedByDescending { it.strings }

        val perProject = rollups
            .groupBy { it.projectId }
            .map { (pid, rows) ->
                val total = rows.sumOf { it.stringsTranslated }
                CostShare(
                    key = pid,
                    displayName = projects[pid]?.name ?: "Unknown project",
                    strings = total,
                    shareInr = share(total)
                )
            }
            .sortedByDescending { it.strings }

        return CostBreakdown(
            plan = sub.plan.name,
            planPriceInr = price,
            totalStringsThisMonth = totalRollupStrings.takeIf { rollups.isNotEmpty() } ?: 0,
            perMember = perMember,
            perProject = perProject,
            ownerBillingTotalStrings = billingTotal,
            sumOfMemberStrings = rollups.sumOf { it.stringsTranslated }
        )
    }

    // ── Quality (per-project acceptance rate) ──────────────────────────────────

    @kotlinx.serialization.Serializable
    data class QualityRow(
        val projectId: String, val name: String,
        val auto: Int, val review: Int, val blocked: Int,
        val acceptanceRatePct: Int?
    )

    suspend fun quality(ownerId: String): List<QualityRow> {
        val projects = projectRepository.listForUser(ownerId)
        return coroutineScope {
            projects.map { p ->
                async {
                    val counts = runCatching { translationRepository.countByStatusForProject(p.id) }
                        .getOrDefault(emptyMap())
                    val auto = counts["auto"] ?: 0
                    val review = counts["review"] ?: 0
                    val blocked = counts["blocked"] ?: 0
                    val decided = auto + review + blocked
                    QualityRow(
                        projectId = p.id, name = p.name,
                        auto = auto, review = review, blocked = blocked,
                        acceptanceRatePct = if (decided > 0) auto * 100 / decided else null
                    )
                }
            }.awaitAll()
        }.sortedByDescending { it.auto + it.review + it.blocked }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private suspend fun resolveDisplayNames(userIds: Set<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return coroutineScope {
            userIds.map { id ->
                async {
                    runCatching { userRepository.findById(id) }.getOrNull()?.let { u ->
                        val name = when {
                            u.githubUsername.isNotBlank() -> "@${u.githubUsername}"
                            u.email?.isNotBlank() == true -> u.email ?: id.take(8)
                            else -> id.take(8)
                        }
                        id to name
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private fun planPriceInr(plan: BillingPlan): Int =
        plan.monthlyPricePaise?.let { it / 100 } ?: 0

    private fun currentYearMonth(): String {
        val ldt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        return "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}"
    }

    private fun firstOfCurrentMonthMillis(): Long {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val firstOfMonth = LocalDate(now.year, now.monthNumber, 1)
        return firstOfMonth.atStartOfDayMillisUtc()
    }

    private fun recentSixMonths(): List<String> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val out = mutableListOf<String>()
        var year = now.year; var month = now.monthNumber
        repeat(6) {
            out.add(0, "$year-${month.toString().padStart(2, '0')}")
            month -= 1
            if (month == 0) { month = 12; year -= 1 }
        }
        return out
    }

    private fun firstOfNext(d: LocalDate): LocalDate =
        if (d.monthNumber == 12) LocalDate(d.year + 1, 1, 1)
        else LocalDate(d.year, d.monthNumber + 1, 1)

    private fun LocalDate.minusDays(days: Int): LocalDate {
        // Tiny helper — only used for daysInMonth calc where we know we won't wrap years.
        val ms = this.atStartOfDayMillisUtc() - days * 86_400_000L
        val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC)
        return LocalDate(ldt.year, ldt.monthNumber, ldt.dayOfMonth)
    }

    private fun LocalDate.atStartOfDayMillisUtc(): Long =
        LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, 0, 0)
            .toInstant(TimeZone.UTC).toEpochMilliseconds()
}
