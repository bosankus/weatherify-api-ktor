package com.syncling.repository

import com.syncling.domain.QuotaBlockedRun

interface QuotaBlockedRunRepository {
    /**
     * Records (or refreshes) the blocked run for a project. Newest commit wins so the
     * eventual resume processes everything pushed since the quota was hit. When
     * [QuotaBlockedRun.stringsPending] is 0 (a dropped webhook — counts unknown) an
     * existing record keeps its previous counts and only the commit/branch advance.
     */
    suspend fun upsert(run: QuotaBlockedRun)

    suspend fun listForOwner(ownerId: String): List<QuotaBlockedRun>

    suspend fun delete(projectId: String)
}
