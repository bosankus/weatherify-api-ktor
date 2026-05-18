package com.transloom.domain

enum class ChangeType { SURFACE, SEMANTIC }

data class SemanticChangeRecord(
    val changeType: ChangeType,
    val reasoning: String
)
