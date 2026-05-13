package com.transloom.domain

import kotlinx.serialization.Serializable

data class StringEntry(
    val id: String,
    val projectId: String,
    val stringKey: String,
    val sourceText: String
)

/** Joined result from translation queries — includes denormalized string and project fields. */
data class Translation(
    val id: String,
    val stringId: String,
    val stringKey: String,
    val sourceText: String,
    val targetLanguage: String,
    val targetRegion: String?,
    val translatedText: String,
    val status: String,
    val blockReason: String?,
    val projectId: String,
    val projectName: String
)

@Serializable
data class TranslationSummary(
    val language: String,
    val region: String?,
    val translatedText: String,
    val status: String,
    val blockReason: String? = null
)

@Serializable
data class StringWithTranslations(
    val id: String,
    val key: String,
    val sourceText: String,
    val translations: List<TranslationSummary>
)

@Serializable
data class StringsPage(
    val strings: List<StringWithTranslations>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
