package com.transloom.domain

data class User(
    val id: String,
    val githubId: Long,
    val githubUsername: String,
    val email: String?,
    val githubToken: String?,
    val avatarUrl: String?
)
