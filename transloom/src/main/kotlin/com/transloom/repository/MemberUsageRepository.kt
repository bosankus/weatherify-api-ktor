package com.transloom.repository

import com.transloom.domain.MemberUsage

interface MemberUsageRepository {
    /**
     * Increments the (projectId, memberUserId, yearMonth) rollup by [stringsTranslated]
     * with the supplied per-locale delta map. `runsTriggered` is incremented by 1.
     * Upserts the document; safe to call concurrently.
     */
    suspend fun record(
        projectId: String,
        memberUserId: String,
        ownerId: String,
        yearMonth: String,
        stringsTranslated: Int,
        perLocale: Map<String, Int>
    )

    /** All rollup rows for an owner in a given yearMonth, across all their projects. */
    suspend fun listForOwner(ownerId: String, yearMonth: String): List<MemberUsage>

    /** All rollup rows for a single project in a given yearMonth. */
    suspend fun listForProject(projectId: String, yearMonth: String): List<MemberUsage>

    /** Total strings translated for a project in a given yearMonth (sum across all members). */
    suspend fun totalForProject(projectId: String, yearMonth: String): Int =
        listForProject(projectId, yearMonth).sumOf { it.stringsTranslated }
}
