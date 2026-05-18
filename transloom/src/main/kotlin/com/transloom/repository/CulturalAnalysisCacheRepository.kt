package com.transloom.repository

import com.transloom.domain.CulturalAnalysis

interface CulturalAnalysisCacheRepository {
    suspend fun get(hashKey: String): CulturalAnalysis?
    suspend fun put(hashKey: String, analysis: CulturalAnalysis)
}
