package com.syncling.repository

import com.syncling.domain.CdnPublishLog
import com.syncling.domain.PlatformPublishStats

interface CdnPublishRepository {
    suspend fun log(
        projectId: String,
        bundleVersion: String,
        locales: List<String>,
        status: String
    ): CdnPublishLog

    suspend fun lastPublish(projectId: String): CdnPublishLog?

    /** Returns the most recent publishes for a project, newest first. */
    suspend fun listPublishes(projectId: String, limit: Int = 20): List<CdnPublishLog>

    /** Returns a specific published version, or null if it was never logged. */
    suspend fun findByVersion(projectId: String, bundleVersion: String): CdnPublishLog?

    /** Returns the currently-active (promoted) bundle version for a project, or null if none. */
    suspend fun getActiveVersion(projectId: String): String?

    /** Sets the active (promoted) bundle version for a project. */
    suspend fun setActiveVersion(projectId: String, bundleVersion: String)

    /** Aggregate publish counts since [sinceMillis] for the public status page. */
    suspend fun platformPublishStats(sinceMillis: Long): PlatformPublishStats
}
