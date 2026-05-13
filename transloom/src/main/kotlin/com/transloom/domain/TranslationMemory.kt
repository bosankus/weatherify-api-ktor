package com.transloom.domain

data class TranslationMemoryEntry(
    val hashKey: String,
    val translatedText: String,
    val usedCount: Int
)
