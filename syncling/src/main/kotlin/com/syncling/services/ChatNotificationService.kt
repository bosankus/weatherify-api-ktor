package com.syncling.services

import com.syncling.domain.NotificationType
import com.syncling.domain.Project
import com.syncling.repository.ProjectRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Posts pipeline/quota/billing notifications to a project's configured Slack and/or
 * Microsoft Teams webhook so teams see Syncling activity where they already work.
 *
 * - Slack: incoming-webhook payload with Block Kit blocks.
 * - Teams: Power Automate Workflows payload carrying an Adaptive Card 1.4
 *   (O365 connectors are retired, so the Workflows "post a card" schema is used).
 *
 * Chat notifications are a paid-plan feature — delivery is gated through [isEligible]
 * (same pattern as [CdnPublishService]). Calls are fire-and-forget: failures are logged
 * but never propagate to the caller.
 */
class ChatNotificationService(
    private val projectRepository: ProjectRepository,
    /**
     * Gate for whether a user is on a paid plan. Chat notifications are paid-only.
     * Defaults to always-eligible so tests and callers that don't care can ignore it.
     */
    private val isEligible: suspend (ownerId: String) -> Boolean = { true }
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(ChatNotificationService::class.java)

    /** Own scope so deliveries survive the caller's coroutine and never block/fail it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    companion object {
        const val EVENT_RUN_COMPLETED = "run_completed"
        const val EVENT_RUN_FAILED = "run_failed"
        const val EVENT_QUOTA_EXCEEDED = "quota_exceeded"
        const val EVENT_BILLING = "billing"

        /** All event keys a project can subscribe to via `chatNotifyEvents`. */
        val EVENT_KEYS: Set<String> =
            setOf(EVENT_RUN_COMPLETED, EVENT_RUN_FAILED, EVENT_QUOTA_EXCEEDED, EVENT_BILLING)

        /** Maps an in-app [NotificationType] constant to a chat event key, or null when the type has no chat equivalent. */
        fun eventKeyFor(notificationType: String): String? = when (notificationType) {
            NotificationType.PIPELINE_COMPLETE -> EVENT_RUN_COMPLETED
            NotificationType.PIPELINE_FAILED,
            NotificationType.GITHUB_TOKEN_INVALID -> EVENT_RUN_FAILED
            NotificationType.TRIAL_LIMIT,
            NotificationType.TRANSLATIONS_RESUMED -> EVENT_QUOTA_EXCEEDED
            NotificationType.PAYMENT_FAILED,
            NotificationType.PAYMENT_RECEIVED,
            NotificationType.SUBSCRIPTION_ENDED,
            NotificationType.TRIAL_STARTED,
            NotificationType.PLAN_EXPIRY,
            NotificationType.CHECKOUT_ABANDONED -> EVENT_BILLING
            else -> null
        }

        /** Slack incoming webhooks are always https URLs on hooks.slack.com. */
        fun isValidSlackWebhookUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme == "https" && uri.host == "hooks.slack.com"
        }

        /**
         * Teams Workflows webhooks live on several Microsoft domains (webhook.office.com,
         * logic.azure.com, azure-api.net, Power Platform regional hosts, ...) so only
         * https + a non-empty host is enforced.
         */
        fun isValidTeamsWebhookUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme == "https" && !uri.host.isNullOrBlank()
        }
    }

    /**
     * Non-suspending entry point for the in-app notification path: maps the notification
     * type to a chat event key and delivers on this service's own scope so the caller is
     * never blocked and never sees a failure.
     */
    fun fireAndForget(userId: String, projectId: String?, notificationType: String, title: String, message: String) {
        val eventKey = eventKeyFor(notificationType) ?: return
        scope.launch {
            runCatching { dispatch(userId, projectId, eventKey, title, message) }
                .onFailure { log.warn("Chat notification dispatch failed: userId={} event={}: {}", userId, eventKey, it.message) }
        }
    }

    /**
     * Delivers [title]/[message] to every matching Slack/Teams webhook.
     *
     * When [projectId] is set only that project's webhooks are considered; for account-level
     * events (billing, quota) all of the user's projects are scanned and deduped by URL so a
     * webhook shared across projects receives a single message.
     */
    suspend fun dispatch(userId: String, projectId: String?, eventKey: String, title: String, message: String) {
        if (eventKey !in EVENT_KEYS) {
            log.debug("Skipping chat notification with unknown event key: {}", eventKey)
            return
        }
        val eligible = runCatching { isEligible(userId) }.getOrDefault(false)
        if (!eligible) {
            log.debug("Chat notification skipped: userId={} not on a paid plan (chat notifications are paid-only)", userId)
            return
        }
        val projects = runCatching {
            if (projectId != null) listOfNotNull(projectRepository.findById(projectId))
            else projectRepository.listForUser(userId)
        }.getOrElse {
            log.warn("Chat notification skipped: failed to load projects for userId={}: {}", userId, it.message)
            return
        }

        // Dedupe by webhook URL — several projects may share one channel webhook.
        val slackTargets = LinkedHashMap<String, Project>()
        val teamsTargets = LinkedHashMap<String, Project>()
        for (p in projects) {
            if (eventKey !in p.chatNotifyEvents) continue
            p.slackWebhookUrl?.takeIf { it.isNotBlank() }?.let { slackTargets.putIfAbsent(it, p) }
            p.teamsWebhookUrl?.takeIf { it.isNotBlank() }?.let { teamsTargets.putIfAbsent(it, p) }
        }
        if (slackTargets.isEmpty() && teamsTargets.isEmpty()) return

        val timestamp = Clock.System.now().toString()
        slackTargets.forEach { (url, project) ->
            post(url, slackPayload(eventKey, project.name, title, message, timestamp), "slack", project.id)
        }
        teamsTargets.forEach { (url, project) ->
            post(url, teamsPayload(eventKey, project.name, title, message, timestamp), "teams", project.id)
        }
    }

    /**
     * Posts a test message to the project's configured chat webhooks (plan gating is the
     * caller's job — the route already 403s free plans). Returns true when at least one
     * POST got a 2xx back.
     */
    suspend fun sendTest(project: Project): Boolean {
        val timestamp = Clock.System.now().toString()
        val title = "Test notification from Syncling"
        val message = "If you can read this, chat notifications for \"${project.name}\" are wired up correctly."
        var ok = false
        project.slackWebhookUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ok = post(url, slackPayload("test", project.name, title, message, timestamp), "slack", project.id) || ok
        }
        project.teamsWebhookUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ok = post(url, teamsPayload("test", project.name, title, message, timestamp), "teams", project.id) || ok
        }
        return ok
    }

    /** Fire one POST. Never throws; returns whether the endpoint answered 2xx. */
    private suspend fun post(url: String, payload: JsonObject, kind: String, projectId: String): Boolean =
        runCatching {
            val response = http.post(url) {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (response.status.isSuccess()) {
                log.debug("Chat notification delivered: kind={} project={} status={}", kind, projectId, response.status.value)
                true
            } else {
                log.warn(
                    "Chat notification rejected: kind={} project={} status={} body={}",
                    kind, projectId, response.status.value, runCatching { response.bodyAsText().take(200) }.getOrDefault("")
                )
                false
            }
        }.getOrElse {
            log.warn("Chat notification failed: kind={} project={}: {}", kind, projectId, it.message)
            false
        }

    // ── Payload builders ───────────────────────────────────────────────────────

    private fun slackPayload(eventKey: String, projectName: String, title: String, message: String, timestamp: String): JsonObject =
        buildJsonObject {
            put("text", "$title: $message")
            putJsonArray("blocks") {
                addJsonObject {
                    put("type", "header")
                    putJsonObject("text") {
                        put("type", "plain_text")
                        put("text", title.take(140)) // Slack header text is capped at 150 chars
                        put("emoji", true)
                    }
                }
                addJsonObject {
                    put("type", "section")
                    putJsonObject("text") {
                        put("type", "mrkdwn")
                        put("text", message)
                    }
                }
                addJsonObject {
                    put("type", "context")
                    putJsonArray("elements") {
                        addJsonObject {
                            put("type", "mrkdwn")
                            put("text", "event: `$eventKey`  ·  project: *$projectName*  ·  $timestamp")
                        }
                    }
                }
            }
        }

    private fun teamsPayload(eventKey: String, projectName: String, title: String, message: String, timestamp: String): JsonObject =
        buildJsonObject {
            put("type", "message")
            putJsonArray("attachments") {
                addJsonObject {
                    put("contentType", "application/vnd.microsoft.card.adaptive")
                    putJsonObject("content") {
                        put("\$schema", "http://adaptivecards.io/schemas/adaptive-card.json")
                        put("type", "AdaptiveCard")
                        put("version", "1.4")
                        putJsonArray("body") {
                            addJsonObject {
                                put("type", "TextBlock")
                                put("text", title)
                                put("weight", "bolder")
                                put("size", "medium")
                                put("wrap", true)
                            }
                            addJsonObject {
                                put("type", "TextBlock")
                                put("text", message)
                                put("wrap", true)
                            }
                            addJsonObject {
                                put("type", "FactSet")
                                putJsonArray("facts") {
                                    addJsonObject { put("title", "Event"); put("value", eventKey) }
                                    addJsonObject { put("title", "Project"); put("value", projectName) }
                                    addJsonObject { put("title", "Time"); put("value", timestamp) }
                                }
                            }
                        }
                    }
                }
            }
        }

    override fun close() {
        scope.cancel()
        http.close()
    }
}
