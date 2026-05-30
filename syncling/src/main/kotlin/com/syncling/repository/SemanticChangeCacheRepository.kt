package com.syncling.repository

import com.syncling.domain.SemanticChangeRecord

interface SemanticChangeCacheRepository {
    suspend fun get(hashKey: String): SemanticChangeRecord?
    suspend fun put(hashKey: String, record: SemanticChangeRecord)
}
