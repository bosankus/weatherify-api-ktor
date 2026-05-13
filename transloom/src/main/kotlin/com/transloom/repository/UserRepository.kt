package com.transloom.repository

import com.transloom.domain.User

interface UserRepository {
    suspend fun upsert(
        githubId: Long,
        username: String,
        email: String?,
        avatarUrl: String?,
        githubToken: String?
    ): User

    suspend fun findByGithubId(githubId: Long): User?

    suspend fun findById(userId: String): User?
}
