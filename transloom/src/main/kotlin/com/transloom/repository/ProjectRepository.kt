package com.transloom.repository

import com.transloom.domain.CreateProjectInput
import com.transloom.domain.Project
import com.transloom.domain.TargetConfig

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
        targets: List<TargetConfig>? = null
    ): Boolean

    suspend fun delete(projectId: String)

    suspend fun getGlossary(projectId: String): Map<String, Map<String, String>>

    suspend fun listAll(): List<Project>
}
