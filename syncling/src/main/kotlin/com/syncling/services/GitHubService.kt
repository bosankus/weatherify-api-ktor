package com.syncling.services

import com.androidplay.core.secrets.getSecretValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

data class PrSummary(val status: String, val prUrl: String, val branchName: String)

class GitHubAuthException(message: String) : Exception(message)
class GitHubRepoNotFoundException(message: String) : Exception(message)
class GitHubBranchNotFoundException(message: String) : Exception(message)

@Serializable
data class GitRefResponse(
    @SerialName("object") val gitObject: GitObject
)

@Serializable
data class GitObject(val sha: String, val type: String, val url: String)

@Serializable
data class BlobRequest(val content: String, val encoding: String = "utf-8")

@Serializable
data class BlobResponse(val sha: String)

@Serializable
data class TreeItem(val path: String, val mode: String, val type: String, val sha: String)

@Serializable
data class TreeRequest(val base_tree: String, val tree: List<TreeItem>)

@Serializable
data class TreeResponse(val sha: String)

@Serializable
data class CommitRequest(val message: String, val tree: String, val parents: List<String>)

@Serializable
data class CommitResponse(val sha: String)

@Serializable
data class RefUpdateRequest(val sha: String, val force: Boolean = false)

@Serializable
data class PullRequestPayload(val title: String, val head: String, val base: String, val body: String)

@Serializable
data class PullRequestResponse(val html_url: String)

@Serializable
data class WebhookConfig(val url: String, val content_type: String, val secret: String)

@Serializable
data class WebhookCreateRequest(val name: String, val active: Boolean, val events: List<String>, val config: WebhookConfig)

@Serializable
data class WebhookListItem(val id: Long, val config: WebhookListConfig)

@Serializable
data class WebhookListConfig(val url: String = "")

class GitHubService {
    private val log = LoggerFactory.getLogger(GitHubService::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun HttpRequestBuilder.gitHubAuth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    /**
     * Checks that [repo] exists and the [token] has at least read access to it.
     * Returns null on success, or a human-readable error string the API can surface directly.
     */
    suspend fun validateRepo(repo: String, token: String): String? {
        if (token == "dummy-token") return null
        val response = client.get("https://api.github.com/repos/$repo") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        return when (response.status) {
            io.ktor.http.HttpStatusCode.OK -> null
            io.ktor.http.HttpStatusCode.NotFound ->
                "Repository '$repo' not found. Check the name and make sure it exists."
            io.ktor.http.HttpStatusCode.Forbidden, io.ktor.http.HttpStatusCode.Unauthorized ->
                "No access to '$repo'. Re-authenticate with GitHub to grant the required 'repo' scope."
            else -> "GitHub returned ${response.status.value} for '$repo'. Check your token and repo name."
        }
    }

    /** Returns the full commit SHA at the tip of [branch], or throws if the branch doesn't exist. */
    suspend fun getLatestCommitHash(repo: String, branch: String, token: String): String {
        val response = client.get("https://api.github.com/repos/$repo/git/ref/heads/$branch") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        when (response.status.value) {
            in 200..299 -> Unit
            401, 403 -> throw GitHubAuthException("GitHub token is missing or lacks access to '$repo'. Re-authenticate and try again.")
            404 -> {
                // Distinguish "repo doesn't exist" from "branch doesn't exist" by probing the repo root.
                val repoProbe = client.get("https://api.github.com/repos/$repo") {
                    gitHubAuth(token)
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                }
                if (!repoProbe.status.isSuccess())
                    throw GitHubRepoNotFoundException("Repository '$repo' was not found or is not accessible.")
                else
                    throw GitHubBranchNotFoundException("Branch '$branch' does not exist in '$repo'. Update the watch branch in project settings.")
            }
            else -> throw Exception("GitHub returned HTTP ${response.status.value} for '$repo@$branch'.")
        }
        return response.body<GitRefResponse>().gitObject.sha
    }

    suspend fun fetchFileContent(repo: String, branch: String, filePath: String, token: String): String {

        val response = client.get("https://api.github.com/repos/$repo/contents/$filePath?ref=$branch") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github.v3.raw")
        }
        return when {
            response.status.isSuccess() -> response.bodyAsText()
            // 404 = file doesn't exist yet; start from empty. Any other error must throw so the pipeline fails cleanly.
            response.status == io.ktor.http.HttpStatusCode.NotFound -> ""
            else -> throw Exception(
                "GitHub file fetch failed for '$filePath' in $repo@$branch: HTTP ${response.status.value} ${response.status.description}"
            )
        }
    }

    /**
     * Expands {timestamp}, {date}, and {branch} tokens in a branch name pattern.
     * Returns the default pattern if [pattern] is null or blank.
     */
    private fun expandBranchPattern(pattern: String?, baseBranch: String, timestamp: Long): String {
        val base = pattern?.takeIf { it.isNotBlank() } ?: "syncling/translations-{timestamp}"
        val date = java.time.LocalDate.now().toString()
        val safeBranch = baseBranch.replace(Regex("[^a-zA-Z0-9_\\-]"), "-").trimEnd('-')
        return base
            .replace("{timestamp}", timestamp.toString())
            .replace("{date}", date)
            .replace("{branch}", safeBranch)
    }

    suspend fun createBranchAndPr(
        repo: String,
        baseBranch: String,
        files: Map<String, String>,
        commitMessage: String,
        prTitle: String,
        prBody: String,
        token: String,
        branchPattern: String? = null
    ): PrSummary {
        val timestamp = System.currentTimeMillis()
        var newBranchName = expandBranchPattern(branchPattern, baseBranch, timestamp)

        val refResponse: GitRefResponse = client.get("https://api.github.com/repos/$repo/git/ref/heads/$baseBranch") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }.body()
        val baseSha = refResponse.gitObject.sha

        var createRefResponse = client.post("https://api.github.com/repos/$repo/git/refs") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(mapOf("ref" to "refs/heads/$newBranchName", "sha" to baseSha))
        }

        // GitHub returns 422 if the branch name already exists; retry with +1 timestamp suffix.
        if (createRefResponse.status == io.ktor.http.HttpStatusCode.UnprocessableEntity) {
            newBranchName = expandBranchPattern(branchPattern, baseBranch, timestamp + 1)
            createRefResponse = client.post("https://api.github.com/repos/$repo/git/refs") {
                gitHubAuth(token)
                header(HttpHeaders.Accept, "application/vnd.github+json")
                contentType(ContentType.Application.Json)
                setBody(mapOf("ref" to "refs/heads/$newBranchName", "sha" to baseSha))
            }
        }

        if (!createRefResponse.status.isSuccess()) {
            throw Exception("Failed to create branch $newBranchName. Status: ${createRefResponse.status}")
        }

        // Upload all blobs in parallel — each POST /git/blobs is independent.
        val treeItems = coroutineScope {
            files.map { (path, content) ->
                async {
                    val blobResp: BlobResponse = client.post("https://api.github.com/repos/$repo/git/blobs") {
                        gitHubAuth(token)
                        header(HttpHeaders.Accept, "application/vnd.github+json")
                        contentType(ContentType.Application.Json)
                        setBody(BlobRequest(content = content))
                    }.body()
                    TreeItem(path = path, mode = "100644", type = "blob", sha = blobResp.sha)
                }
            }.awaitAll()
        }

        val treeResp: TreeResponse = client.post("https://api.github.com/repos/$repo/git/trees") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(TreeRequest(base_tree = baseSha, tree = treeItems))
        }.body()

        val commitResp: CommitResponse = client.post("https://api.github.com/repos/$repo/git/commits") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(CommitRequest(message = commitMessage, tree = treeResp.sha, parents = listOf(baseSha)))
        }.body()

        client.patch("https://api.github.com/repos/$repo/git/refs/heads/$newBranchName") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(RefUpdateRequest(sha = commitResp.sha))
        }

        val prResp: PullRequestResponse = client.post("https://api.github.com/repos/$repo/pulls") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(PullRequestPayload(title = prTitle, head = newBranchName, base = baseBranch, body = prBody))
        }.body()

        return PrSummary("success", prResp.html_url, newBranchName)
    }

    /**
     * Pushes a new commit to an existing branch. Returns false if the branch no longer exists
     * (caller should fall back to creating a new PR). Throws on other GitHub API errors.
     */
    suspend fun addCommitToBranch(
        repo: String,
        branchName: String,
        files: Map<String, String>,
        commitMessage: String,
        token: String
    ): Boolean {
        // 1. Get current tip of the branch
        val refResponse = client.get("https://api.github.com/repos/$repo/git/ref/heads/$branchName") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (refResponse.status == io.ktor.http.HttpStatusCode.NotFound) return false
        if (!refResponse.status.isSuccess()) throw Exception("GitHub ref fetch failed: ${refResponse.status}")
        val tipSha = refResponse.body<GitRefResponse>().gitObject.sha

        // 2. Upload blobs in parallel
        val treeItems = coroutineScope {
            files.map { (path, content) ->
                async {
                    val blobResp: BlobResponse = client.post("https://api.github.com/repos/$repo/git/blobs") {
                        gitHubAuth(token)
                        header(HttpHeaders.Accept, "application/vnd.github+json")
                        contentType(ContentType.Application.Json)
                        setBody(BlobRequest(content = content))
                    }.body()
                    TreeItem(path = path, mode = "100644", type = "blob", sha = blobResp.sha)
                }
            }.awaitAll()
        }

        // 3. Create tree on top of current tip
        val treeResp: TreeResponse = client.post("https://api.github.com/repos/$repo/git/trees") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(TreeRequest(base_tree = tipSha, tree = treeItems))
        }.body()

        // 4. Create commit
        val commitResp: CommitResponse = client.post("https://api.github.com/repos/$repo/git/commits") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(CommitRequest(message = commitMessage, tree = treeResp.sha, parents = listOf(tipSha)))
        }.body()

        // 5. Advance the branch ref
        val patchResponse = client.patch("https://api.github.com/repos/$repo/git/refs/heads/$branchName") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(RefUpdateRequest(sha = commitResp.sha))
        }
        if (!patchResponse.status.isSuccess()) throw Exception("Failed to update branch ref: ${patchResponse.status}")
        return true
    }

    suspend fun createWebhook(repo: String, token: String) {
        if (token == "dummy-token") {
            log.info("Mocking GitHub webhook creation for repo: {}", repo)
            return
        }

        val webhookSecret = getSecretValue("github-webhook-secret").ifBlank { "dev_secret" }
        val webhookUrl = "https://syncling.space/webhook/github"

        val req = WebhookCreateRequest(
            name = "web",
            active = true,
            events = listOf("push"),
            config = WebhookConfig(
                url = webhookUrl,
                content_type = "json",
                secret = webhookSecret
            )
        )

        val response = client.post("https://api.github.com/repos/$repo/hooks") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(req)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            if (errorBody.contains("already exists") || errorBody.contains("Hook already exists")) {
                log.info("Webhook already exists for repo {}", repo)
            } else {
                log.warn("Failed to create webhook for {}: {} {}", repo, response.status, errorBody)
                throw Exception("GitHub webhook creation failed: ${response.status}")
            }
        } else {
            log.info("Successfully created push webhook for repo {}", repo)
        }
    }

    // Returns true if an existing stale webhook was replaced, false if already correct or freshly created.
    suspend fun ensureWebhook(repo: String, token: String): Boolean {
        if (token == "dummy-token") return false

        val correctUrl = "https://syncling.space/webhook/github"
        val webhookSecret = getSecretValue("github-webhook-secret").ifBlank { "dev_secret" }

        val existing = runCatching {
            client.get("https://api.github.com/repos/$repo/hooks") {
                gitHubAuth(token)
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }.body<List<WebhookListItem>>()
        }.getOrElse {
            log.warn("ensureWebhook: could not list hooks for {}: {}", repo, it.message)
            return false
        }

        val stale = existing.filter { it.config.url != correctUrl }
        val correct = existing.any { it.config.url == correctUrl }

        if (correct && stale.isEmpty()) return false

        for (hook in stale) {
            runCatching {
                client.delete("https://api.github.com/repos/$repo/hooks/${hook.id}") {
                    gitHubAuth(token)
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                }
            }.onFailure { log.warn("ensureWebhook: failed to delete stale hook {} on {}: {}", hook.id, repo, it.message) }
        }

        if (!correct) {
            val req = WebhookCreateRequest(
                name = "web", active = true, events = listOf("push"),
                config = WebhookConfig(url = correctUrl, content_type = "json", secret = webhookSecret)
            )
            client.post("https://api.github.com/repos/$repo/hooks") {
                gitHubAuth(token)
                header(HttpHeaders.Accept, "application/vnd.github+json")
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        }

        log.info("ensureWebhook: replaced {} stale hook(s) on {}", stale.size, repo)
        return true
    }

    fun close() { client.close() }
}
