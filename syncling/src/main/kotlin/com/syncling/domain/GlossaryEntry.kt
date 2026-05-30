package com.syncling.domain

data class GlossaryEntry(
    val id: String,
    val projectId: String,
    val languageCode: String,
    val sourceTerm: String,
    val targetTerm: String,
    val isActive: Boolean = true
)
