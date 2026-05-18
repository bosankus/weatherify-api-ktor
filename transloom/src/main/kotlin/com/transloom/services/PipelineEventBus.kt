package com.transloom.services

import com.transloom.domain.PipelineRunState
import com.transloom.domain.PipelineStepState
import com.transloom.domain.initialSteps
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PipelineEvent(
    val type: String,            // start | step | finish
    val runId: String,
    val stepId: String? = null,
    val status: String? = null,
    val detail: String? = null,
    val prUrl: String? = null,
    val error: String? = null,
    val snapshot: PipelineRunState? = null
)

class PipelineEventBus {
    // userId → last 20 run snapshots
    private val runsByUser = ConcurrentHashMap<String, ArrayDeque<PipelineRunState>>()
    // runId → mutable step list for in-progress runs
    private val activeSteps = ConcurrentHashMap<String, MutableList<PipelineStepState>>()
    // userId → SSE broadcast flow (replay=40 so reconnecting clients catch up)
    private val sseFlows = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    private val json = Json { encodeDefaults = false }

    fun eventsFor(userId: String): SharedFlow<String> =
        flowFor(userId).asSharedFlow()

    fun recentRuns(userId: String): List<PipelineRunState> =
        runsByUser[userId]?.toList()?.reversed() ?: emptyList()

    fun startRun(
        userId: String,
        repo: String,
        branch: String,
        commitShort: String,
        projectId: String? = null,
        retriedFromRunId: String? = null
    ): String {
        val runId = UUID.randomUUID().toString()
        val steps = initialSteps().toMutableList()
        activeSteps[runId] = steps

        val snapshot = PipelineRunState(
            runId = runId, repo = repo, branch = branch,
            commitShort = commitShort, startedAt = System.currentTimeMillis(),
            steps = steps.toList(), projectId = projectId, retriedFromRunId = retriedFromRunId
        )
        pushRun(userId, snapshot)
        emit(userId, PipelineEvent(type = "start", runId = runId, snapshot = snapshot))
        return runId
    }

    fun stepRunning(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "running", detail)

    fun stepDone(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "done", detail)

    fun stepSkipped(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "skipped", detail)

    fun stepError(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "error", detail)

    fun finishRun(userId: String, runId: String, prUrl: String? = null, error: String? = null) {
        val finishedAt = System.currentTimeMillis()
        mutateRun(userId, runId) { it.copy(finishedAt = finishedAt, prUrl = prUrl, error = error) }
        activeSteps.remove(runId)
        emit(userId, PipelineEvent(type = "finish", runId = runId, prUrl = prUrl, error = error))
    }

    private fun updateStep(userId: String, runId: String, stepId: String, status: String, detail: String?) {
        val steps = activeSteps[runId] ?: return
        val idx = steps.indexOfFirst { it.id == stepId }
        if (idx >= 0) steps[idx] = steps[idx].copy(status = status, detail = detail)
        mutateRun(userId, runId) { it.copy(steps = steps.toList()) }
        emit(userId, PipelineEvent(type = "step", runId = runId, stepId = stepId, status = status, detail = detail))
    }

    private fun pushRun(userId: String, snapshot: PipelineRunState) {
        val deque = runsByUser.getOrPut(userId) { ArrayDeque(20) }
        synchronized(deque) {
            deque.addFirst(snapshot)
            while (deque.size > 20) deque.removeLast()
        }
    }

    private fun mutateRun(userId: String, runId: String, transform: (PipelineRunState) -> PipelineRunState) {
        val deque = runsByUser[userId] ?: return
        synchronized(deque) {
            val idx = deque.indexOfFirst { it.runId == runId }
            if (idx >= 0) deque[idx] = transform(deque[idx])
        }
    }

    private fun flowFor(userId: String) =
        sseFlows.getOrPut(userId) { MutableSharedFlow(replay = 40, extraBufferCapacity = 64) }

    private fun emit(userId: String, event: PipelineEvent) {
        flowFor(userId).tryEmit(json.encodeToString(event))
    }
}
