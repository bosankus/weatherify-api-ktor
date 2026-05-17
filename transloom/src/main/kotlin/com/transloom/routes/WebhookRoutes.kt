package com.transloom.routes

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
private val lastPushPerRepo = java.util.concurrent.ConcurrentHashMap<String, Long>()

private fun verifySignature(body: ByteArray, signatureHeader: String?): Boolean {
    // No secret configured → allow in dev mode (webhookSecret is only null locally)
    if (webhookSecret == null) return true
    if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(webhookSecret!!.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val expected = "sha256=" + mac.doFinal(body).joinToString("") { "%02x".format(it) }
    return MessageDigest.isEqual(expected.toByteArray(), signatureHeader.toByteArray())
}

fun Route.configureWebhookRoutes(jobQueue: TranslationJobQueue, projectRepository: ProjectRepository) {
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

            val now = System.currentTimeMillis()

            val lastPush = lastPushPerRepo[repo] ?: 0L
            if (now - lastPush < 10000) {
                log.info("Ignored webhook: rate limited for repo {}", repo)
                call.respond(HttpStatusCode.TooManyRequests, "Rate limit exceeded (1 per 10s)")
                return@post
            }
            lastPushPerRepo[repo] = now

            val isDuplicate = synchronized(recentCommitsLock) {
                if (recentCommits.containsKey(commitHash)) true
                else { recentCommits[commitHash] = now; false }
            }
            if (isDuplicate) {
                log.info("Ignored webhook: duplicate commit {}", commitHash)
                call.respond(HttpStatusCode.Accepted, "Ignored: duplicate commit")
                return@post
            }

            if (lastPushPerRepo.size > 1000) lastPushPerRepo.clear()

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
                    (modified + added).any { it == project.sourceFilePath || it.endsWith("/${project.sourceFilePath}") }
                }
            } else true

            if (!sourceModified) {
                log.info("Webhook ignored: '{}' not modified in push to {}", project.sourceFilePath, repo)
                call.respond(HttpStatusCode.Accepted, "Ignored: source file not modified")
                return@post
            }

            jobQueue.enqueueJob(WebhookPayload(repo, commitHash, branchName, project.id))
            log.info("Webhook queued: repo={} commit={} branch={}", repo, commitHash.take(7), branchName)
            call.respond(HttpStatusCode.Accepted, "Webhook received and queued for processing")
        }
    }
}
