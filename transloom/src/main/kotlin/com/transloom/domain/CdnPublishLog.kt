package com.transloom.domain

data class CdnPublishLog(
    val id: String,
    val projectId: String,
    val bundleVersion: String,
    val publishedAt: Long,
    val locales: List<String>,
    val status: String
)
