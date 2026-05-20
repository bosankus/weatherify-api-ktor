package com.transloom.repository

import com.transloom.domain.CreateProjectInput
import com.transloom.domain.Project
import com.transloom.domain.TargetConfig
import kotlinx.datetime.Instant

interface ProjectRepository {
    suspend fun create(ownerId: String, input: CreateProjectInput): Project

    suspend fun listForUser(ownerId: String): List<Project>

    suspend fun findById(projectId: String): Project?

    suspend fun findByGithubRepo(githubRepo: String): Project?

    suspend fun countForUser(ownerId: String): Int

    suspend fun update(
        projectId: String,
        name: String? = null,
        tone: String? = null,
        category: String? = null,
        watchBranch: String? = null,
        sourceFilePath: String? = null,
        targets: List<TargetConfig>? = null,
        culturalSensitivityEnabled: Boolean? = null,
        autoApproveEnabled: Boolean? = null
    ): Boolean

    suspend fun delete(projectId: String)

    suspend fun getGlossary(projectId: String): Map<String, Map<String, String>>

    suspend fun listAll(): List<Project>

    /**
     * Returns projects whose webhook has never been verified or was last verified before [staleBefore].
     * Used by the startup self-heal to avoid checking every project on every restart.
     */
    suspend fun listProjectsNeedingWebhookHeal(staleBefore: Instant): List<Project>

    /** Records a successful webhook verification so the project is excluded from future heal passes. */
    suspend fun markWebhookVerified(projectId: String)

    /** Stores the SHA-256 of the source file after a fully successful pipeline run. */
    suspend fun updateSourceFileHash(projectId: String, hash: String)
}
