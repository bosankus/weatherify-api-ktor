package com.transloom.services

import com.androidplay.core.secrets.getSecretValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

data class PrSummary(val status: String, val prUrl: String)

// GitHub API Models
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

    suspend fun getCommitDiff(repo: String, commitHash: String, token: String): String {
        if (token == "dummy-token") {
            log.info("Mocking GitHub diff fetch (no GITHUB_TOKEN set)")
            return """
                diff --git a/app/src/main/res/values/strings.xml b/app/src/main/res/values/strings.xml
                +    <string name="payment_error">Payment failed. Try again.</string>
                +    <string name="welcome_user">Welcome, %1${'$'}s!</string>
            """.trimIndent()
        }
        
        val response = client.get("https://api.github.com/repos/$repo/commits/$commitHash") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github.v3.diff")
        }
        if (!response.status.isSuccess()) {
            log.warn("Could not fetch diff for commit {}: {}", commitHash.take(7), response.status)
            return ""
        }
        return response.bodyAsText()
    }

    suspend fun fetchFileContent(repo: String, branch: String, filePath: String, token: String): String {

        val response = client.get("https://api.github.com/repos/$repo/contents/$filePath?ref=$branch") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github.v3.raw")
        }
        return when {
            response.status.isSuccess() -> response.bodyAsText()
            // Fix E2: 404 means the file genuinely doesn't exist yet — start from an empty file.
            // Any other failure (401 expired token, 403 no access, 5xx GitHub outage) must throw
            // so the pipeline fails cleanly rather than silently dropping all existing translations.
            response.status == io.ktor.http.HttpStatusCode.NotFound -> ""
            else -> throw Exception(
                "GitHub file fetch failed for '$filePath' in $repo@$branch: HTTP ${response.status.value} ${response.status.description}"
            )
        }
    }

    suspend fun createBranchAndPr(
        repo: String, 
        baseBranch: String, 
        files: Map<String, String>, 
        commitMessage: String,
        prTitle: String,
        prBody: String,
        token: String
    ): PrSummary {
        val timestamp = System.currentTimeMillis()
        var newBranchName = "transloom/translations-$timestamp"

        // 1. Get SHA of base branch
        val refResponse: GitRefResponse = client.get("https://api.github.com/repos/$repo/git/ref/heads/$baseBranch") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }.body()
        val baseSha = refResponse.gitObject.sha

        // 2. Create new branch reference
        var createRefResponse = client.post("https://api.github.com/repos/$repo/git/refs") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(mapOf("ref" to "refs/heads/$newBranchName", "sha" to baseSha))
        }
        
        // E3: If a branch with the same timestamp already exists, GitHub returns 422. Retry once.
        if (createRefResponse.status == io.ktor.http.HttpStatusCode.UnprocessableEntity) {
            newBranchName = "transloom/translations-${timestamp + 1}"
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

        // 3. Create blob for each file
        val treeItems = mutableListOf<TreeItem>()
        for ((path, content) in files) {
            val blobResp: BlobResponse = client.post("https://api.github.com/repos/$repo/git/blobs") {
                gitHubAuth(token)
                header(HttpHeaders.Accept, "application/vnd.github+json")
                contentType(ContentType.Application.Json)
                setBody(BlobRequest(content = content))
            }.body()
            
            treeItems.add(TreeItem(path = path, mode = "100644", type = "blob", sha = blobResp.sha))
        }

        // 4. Create Tree
        val treeResp: TreeResponse = client.post("https://api.github.com/repos/$repo/git/trees") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(TreeRequest(base_tree = baseSha, tree = treeItems))
        }.body()

        // 5. Create Commit
        val commitResp: CommitResponse = client.post("https://api.github.com/repos/$repo/git/commits") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(CommitRequest(message = commitMessage, tree = treeResp.sha, parents = listOf(baseSha)))
        }.body()

        // 6. Update Branch Ref
        client.patch("https://api.github.com/repos/$repo/git/refs/heads/$newBranchName") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(RefUpdateRequest(sha = commitResp.sha))
        }

        // 7. Open Pull Request
        val prResp: PullRequestResponse = client.post("https://api.github.com/repos/$repo/pulls") {
            gitHubAuth(token)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(PullRequestPayload(title = prTitle, head = newBranchName, base = baseBranch, body = prBody))
        }.body()

        return PrSummary("success", prResp.html_url)
    }

    suspend fun createWebhook(repo: String, token: String) {
        if (token == "dummy-token") {
            log.info("Mocking GitHub webhook creation for repo: {}", repo)
            return
        }

        val webhookSecret = getSecretValue("github-webhook-secret").ifBlank { "dev_secret" }
        val webhookUrl = getSecretValue("webhook-url")

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

    fun close() { client.close() }
}
