package com.transloom.repository

import com.transloom.domain.CdnPublishLog

interface CdnPublishRepository {
    suspend fun log(
        projectId: String,
        bundleVersion: String,
        locales: List<String>,
        status: String
    ): CdnPublishLog

    suspend fun lastPublish(projectId: String): CdnPublishLog?
}
