package com.transloom.routes

import com.transloom.repository.BillingRepository
import com.transloom.repository.ProjectRepository
import com.androidplay.core.secrets.getSecretValue
import com.transloom.queue.TranslationJobQueue
import com.transloom.queue.WebhookPayload
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val log = LoggerFactory.getLogger("WebhookRoutes")
// Lazy so getSecretValue (which calls runBlocking internally) runs after Ktor is fully initialized,
// not at class-load time.
private val webhookSecret: String? by lazy {
    getSecretValue("github-webhook-secret").takeIf { it.isNotBlank() }
}

// LinkedHashMap with LRU eviction avoids the clear()-under-concurrent-read race condition
// that a raw ConcurrentHashMap.clear() would have.
private val recentCommits = object : java.util.LinkedHashMap<String, Long>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 10_000
}
private val recentCommitsLock = Any()

private fun verifySignature(body: ByteArray, signatureHeader: String?): Boolean {
    // No secret configured → allow in dev mode (webhookSecret is only null locally)
    if (webhookSecret == null) return true
    if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(webhookSecret!!.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val expected = "sha256=" + mac.doFinal(body).joinToString("") { "%02x".format(it) }
    return MessageDigest.isEqual(expected.toByteArray(), signatureHeader.toByteArray())
}

fun Route.configureWebhookRoutes(
    jobQueue: TranslationJobQueue,
    projectRepository: ProjectRepository,
    billingRepository: BillingRepository
) {
    if (webhookSecret == null) {
        log.warn("GITHUB_WEBHOOK_SECRET not set — webhook signature verification is DISABLED (dev mode only, never run this in production)")
    }

    route("/transloom/webhook") {
        post("/github") {
            val eventType = call.request.headers["X-GitHub-Event"]
            if (eventType != null && eventType != "push") {
                call.respond(HttpStatusCode.NoContent)
                return@post
            }

            val bodyBytes = call.receive<ByteArray>()
            val signatureHeader = call.request.headers["X-Hub-Signature-256"]

            if (!verifySignature(bodyBytes, signatureHeader)) {
                log.warn("Rejected webhook: invalid or missing signature")
                call.respond(HttpStatusCode.Unauthorized, "Invalid webhook signature")
                return@post
            }

            val json = Json.parseToJsonElement(bodyBytes.toString(Charsets.UTF_8)).jsonObject
            val repo = json["repository"]?.jsonObject?.get("full_name")?.jsonPrimitive?.content ?: ""
            val ref = json["ref"]?.jsonPrimitive?.content ?: ""
            val commitHash = json["after"]?.jsonPrimitive?.content ?: ""
            val branchName = ref.removePrefix("refs/heads/")

            if (repo.isEmpty() || commitHash.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Invalid GitHub webhook payload")
                return@post
            }

            // Distributed per-repo rate-limit (1 per 10s across all instances via Redis SETNX).
            // Falls back to in-memory if Redis is unavailable.
            if (jobQueue.isWebhookRateLimited(repo)) {
                log.info("Ignored webhook: rate limited for repo {}", repo)
                call.respond(HttpStatusCode.TooManyRequests, "Rate limit exceeded (1 per 10s)")
                return@post
            }

            // Delivery-level dedup: GitHub sends a unique X-GitHub-Delivery UUID.
            // Prevents GitHub's own retry from double-processing the same delivery.
            val deliveryId = call.request.headers["X-GitHub-Delivery"]
            if (deliveryId != null && jobQueue.isDeliveryAlreadySeen(deliveryId)) {
                log.info("Ignored webhook: duplicate delivery {}", deliveryId)
                call.respond(HttpStatusCode.Accepted, "Ignored: duplicate delivery")
                return@post
            }

            val now = System.currentTimeMillis()
            val isDuplicate = synchronized(recentCommitsLock) {
                if (recentCommits.containsKey(commitHash)) true
                else { recentCommits[commitHash] = now; false }
            }
            if (isDuplicate) {
                log.info("Ignored webhook: duplicate commit {}", commitHash)
                call.respond(HttpStatusCode.Accepted, "Ignored: duplicate commit")
                return@post
            }

            val project = projectRepository.findByGithubRepo(repo)
            if (project == null) {
                log.info("Ignored webhook: repo {} not registered", repo)
                call.respond(HttpStatusCode.Accepted, "Ignored: repo not registered")
                return@post
            }

            if (branchName != project.watchBranch) {
                log.info("Ignored webhook: push to branch '{}' (watching '{}')", branchName, project.watchBranch)
                call.respond(HttpStatusCode.Accepted, "Ignored: branch not watched")
                return@post
            }

            val commits = json["commits"]?.jsonArray

            if (commits != null && commits.isNotEmpty()) {
                val allFromBot = commits.all { commit ->
                    val msg = commit.jsonObject["message"]?.jsonPrimitive?.content ?: ""
                    msg.startsWith("chore(i18n):")
                }
                if (allFromBot) {
                    log.info("Ignored webhook: all commits are from Transloom bot — preventing loop")
                    call.respond(HttpStatusCode.Accepted, "Ignored: Transloom bot commits")
                    return@post
                }
            }

            val sourceModified = if (commits != null) {
                commits.any { commit ->
                    val modified = commit.jsonObject["modified"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val added = commit.jsonObject["added"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val changedPaths = modified + added
                    project.sourceFilePaths.any { sourcePath ->
                        changedPaths.any { it == sourcePath || it.endsWith("/$sourcePath") }
                    }
                }
            } else true

            if (!sourceModified) {
                log.info("Webhook ignored: source files {} not modified in push to {}", project.sourceFilePaths, repo)
                call.respond(HttpStatusCode.Accepted, "Ignored: source file not modified")
                return@post
            }

            // Fast-fail before touching GitHub API: if the project owner already hit their plan limit,
            // drop the webhook immediately — no file fetch, no queue slot, no Gemini cost.
            val subscription = billingRepository.getSubscription(project.ownerId)
            if (subscription.limitHitAt != null) {
                log.info("Webhook ignored: project={} owner={} has hit usage limit", project.id, project.ownerId)
                call.respond(HttpStatusCode.Accepted, "Ignored: usage limit reached — upgrade your plan to resume translations")
                return@post
            }

            // Capture the head-commit author email for analytics attribution. The pipeline
            // worker resolves this against project_members to credit a member, or falls back
            // to the synthetic "external" actor in the analytics rollup.
            val headCommit = json["head_commit"]?.jsonObject
                ?: commits?.lastOrNull()?.jsonObject
            val triggeredByEmail = headCommit?.get("author")?.jsonObject
                ?.get("email")?.jsonPrimitive?.content
                ?.trim()?.lowercase()
                ?.takeIf { it.isNotEmpty() }

            jobQueue.enqueueJob(WebhookPayload(
                repositoryFullName = repo,
                commitHash = commitHash,
                branchName = branchName,
                projectId = project.id,
                triggeredByEmail = triggeredByEmail
            ))
            log.info("Webhook queued: repo={} commit={} branch={} authorEmail={}",
                repo, commitHash.take(7), branchName, triggeredByEmail ?: "?")
            call.respond(HttpStatusCode.Accepted, "Webhook received and queued for processing")
        }
    }
}
