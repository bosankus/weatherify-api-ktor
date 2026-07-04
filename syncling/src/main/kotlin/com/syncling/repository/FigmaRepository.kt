package com.syncling.repository

import com.syncling.domain.FigmaCandidateStatus
import com.syncling.domain.FigmaFramePreview
import com.syncling.domain.FigmaNodeBinding
import com.syncling.domain.FigmaStringCandidate

interface FigmaCandidateRepository {

    /**
     * Inserts a pending candidate, or refreshes the existing PENDING row for the same
     * (projectId, fileKey, nodeId) — a designer re-pushing a node must update the inbox
     * entry in place, never duplicate it. Returns the stored candidate.
     */
    suspend fun upsertPending(candidate: FigmaStringCandidate): FigmaStringCandidate

    suspend fun listForProject(
        projectId: String,
        status: FigmaCandidateStatus? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<FigmaStringCandidate>

    suspend fun countForProject(projectId: String, status: FigmaCandidateStatus? = null): Int

    suspend fun findById(id: String): FigmaStringCandidate?

    suspend fun findByIds(ids: List<String>): List<FigmaStringCandidate>

    /** Sets the dev-edited key on a PENDING candidate. Returns false when missing or not pending. */
    suspend fun updateFinalKey(id: String, projectId: String, finalKey: String): Boolean

    suspend fun markPrOpened(ids: List<String>, prUrl: String): Int

    suspend fun markRejected(ids: List<String>, projectId: String): Int

    /**
     * Texts previously rejected per node, keyed by nodeId. Used to suppress re-staging
     * a string the dev already rejected — until the designer actually changes the copy.
     */
    suspend fun findRejectedTexts(projectId: String, fileKey: String, nodeIds: List<String>): Map<String, Set<String>>
}

interface FigmaNodeBindingRepository {

    suspend fun findForNodes(projectId: String, fileKey: String, nodeIds: List<String>): List<FigmaNodeBinding>

    suspend fun upsertAll(bindings: List<FigmaNodeBinding>)
}

interface FigmaPreviewRepository {

    /** Stores/overwrites the frame screenshot — re-pushes always carry the freshest design. */
    suspend fun upsert(preview: FigmaFramePreview)

    suspend fun find(projectId: String, fileKey: String, frameId: String): FigmaFramePreview?
}
