package com.transloom.repository

import com.transloom.domain.SemanticChangeRecord

interface SemanticChangeCacheRepository {
    suspend fun get(hashKey: String): SemanticChangeRecord?
    suspend fun put(hashKey: String, record: SemanticChangeRecord)
}
