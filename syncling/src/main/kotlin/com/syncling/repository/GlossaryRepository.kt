package com.syncling.repository

import com.syncling.domain.GlossaryEntry

interface GlossaryRepository {
    suspend fun upsert(projectId: String, languageCode: String, sourceTerm: String, targetTerm: String)

    suspend fun listWithIds(projectId: String): List<GlossaryEntry>

    suspend fun deactivate(entryId: String, projectId: String): Boolean
}
