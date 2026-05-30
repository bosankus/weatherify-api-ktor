package com.syncling.repository

import com.syncling.domain.CulturalAnalysis

interface CulturalAnalysisCacheRepository {
    suspend fun get(hashKey: String): CulturalAnalysis?
    suspend fun put(hashKey: String, analysis: CulturalAnalysis)
}
