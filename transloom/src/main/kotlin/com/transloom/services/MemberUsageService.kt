package com.transloom.services

import com.transloom.domain.MemberUsage
import com.transloom.repository.MemberUsageRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * Records per-member translation activity into the rollup collection.
 *
 * Called from the translation pipeline immediately after the owner-scoped
 * billing write so the two stay in lockstep — the Team-plan analytics tab
 * enforces an invariant that the per-member sum equals owner billing total.
 *
 * Failures are logged but never thrown: this is a denormalized analytics
 * write, and we must not block a successful translation run if it fails.
 */
class MemberUsageService(private val repository: MemberUsageRepository) {

    private val log = LoggerFactory.getLogger(MemberUsageService::class.java)

    suspend fun record(
        projectId: String,
        triggeredByUserId: String?,
        ownerId: String,
        stringsTranslated: Int,
        perLocale: Map<String, Int>
    ) {
        if (stringsTranslated <= 0) return
        val memberKey = triggeredByUserId ?: MemberUsage.EXTERNAL
        val ym = currentYearMonth()
        runCatching {
            repository.record(projectId, memberKey, ownerId, ym, stringsTranslated, perLocale)
        }.onFailure {
            log.warn("MemberUsageService.record failed project={} member={} ym={}: {}",
                projectId, memberKey, ym, it.message)
        }
    }

    private fun currentYearMonth(): String {
        val ldt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        return "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}"
    }
}
