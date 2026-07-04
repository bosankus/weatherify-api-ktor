package com.syncling.services

import com.androidplay.core.secrets.getSecretValue
import com.syncling.domain.FigmaCandidateStatus
import com.syncling.domain.FigmaFramePreview
import com.syncling.domain.FigmaNodeBinding
import com.syncling.domain.FigmaStringCandidate
import com.syncling.domain.Project
import com.syncling.repository.FigmaCandidateRepository
import com.syncling.repository.FigmaNodeBindingRepository
import com.syncling.repository.FigmaPreviewRepository
import com.syncling.repository.TranslationEmbeddingRepository
import com.syncling.repository.TranslationRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.UUID

/** One text node as extracted by the Figma plugin. */
data class FigmaPushNode(
    val nodeId: String,
    val nodeName: String,
    val text: String,
    val pageName: String? = null,
    val frameName: String? = null,
    /** Nearest frame node id — links the node to its shared frame screenshot. */
    val frameId: String? = null,
    /** True when the Figma text box has a fixed width — translations that expand may overflow it. */
    val fixedWidth: Boolean = false,
)

/** Designer-facing heads-up returned with the push response — never blocks the push. */
data class FigmaLengthWarning(
    val nodeId: String,
    val text: String,
    val message: String,
)

/** One frame screenshot uploaded alongside a push (already base64-decoded by the route). */
data class FigmaFrameUpload(val frameId: String, val png: ByteArray)

data class FigmaIngestSummary(
    /** Candidates now waiting in the inbox (new + refreshed). */
    val staged: Int,
    /** Of those, nodes already bound to a repo key — approval updates the key in place. */
    val updates: Int,
    /** Of those, texts that exactly match an existing source string (reuse suggested). */
    val duplicates: Int,
    /** Nodes skipped because the bound key already carries this exact text. */
    val skippedUnchanged: Int,
    /** Nodes skipped because the dev previously rejected this exact text. */
    val skippedRejected: Int,
    /** Fixed-width texts likely to overflow once translated — designer feedback, non-blocking. */
    val warnings: List<FigmaLengthWarning> = emptyList(),
    /** Set when auto-approve turned this push straight into a PR. */
    val autoPrUrl: String? = null,
)

data class FigmaApproveResult(
    val prUrl: String,
    val branchName: String,
    val keysAdded: List<String>,
    val keysUpdated: List<String>,
)

/** A candidate whose key collides with an existing source key that carries different text. */
class FigmaKeyConflictException(val conflicts: Map<String, String>) :
    Exception("Key conflict for: ${conflicts.keys.joinToString()}")

private val KEY_PATTERN = Regex("^[a-z][a-z0-9_]{0,79}$")

fun isValidStringKey(key: String): Boolean = KEY_PATTERN.matches(key)

/**
 * Ingests strings pushed from the Figma plugin into the per-project inbox and turns
 * approved candidates into a PR against the project's watch branch. The merged PR
 * then triggers the regular translation pipeline via the existing push webhook —
 * this service deliberately ends where that pipeline begins.
 */
class FigmaSyncService(
    private val candidateRepository: FigmaCandidateRepository,
    private val bindingRepository: FigmaNodeBindingRepository,
    private val translationRepository: TranslationRepository,
    private val gitHubService: GitHubService,
    /** Optional Phase-2 collaborators — features degrade gracefully when absent (tests, minimal hosts). */
    private val previewRepository: FigmaPreviewRepository? = null,
    private val embeddingService: EmbeddingService? = null,
    private val embeddingRepository: TranslationEmbeddingRepository? = null,
    private val notificationService: InAppNotificationService? = null,
) {
    private val log = LoggerFactory.getLogger(FigmaSyncService::class.java)
    private val geminiApiKey: String = getSecretValue("gemini-api-key")
    private val json = Json { ignoreUnknownKeys = true }
    private val semaphore = Semaphore(4)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun ingest(
        project: Project,
        submittedByUserId: String,
        fileKey: String,
        nodes: List<FigmaPushNode>,
        previews: List<FigmaFrameUpload> = emptyList(),
        /** Auto-approve mode: turn every pending candidate into a PR right away. Needs [githubToken]. */
        autoApprove: Boolean = false,
        githubToken: String? = null,
    ): FigmaIngestSummary {
        val cleaned = nodes
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() && it.nodeId.isNotBlank() }
            .distinctBy { it.nodeId }
        if (cleaned.isEmpty()) return FigmaIngestSummary(0, 0, 0, 0, 0)

        val nodeIds = cleaned.map { it.nodeId }
        val existingSources = translationRepository.getStringKeysAndTexts(project.id)
        val textToExistingKey = existingSources.entries.associate { (k, v) -> v.trim() to k }
        val bindings = bindingRepository.findForNodes(project.id, fileKey, nodeIds)
            .associateBy { it.figmaNodeId }
        val rejectedTexts = candidateRepository.findRejectedTexts(project.id, fileKey, nodeIds)

        var skippedUnchanged = 0
        var skippedRejected = 0
        val toStage = mutableListOf<FigmaPushNode>()
        for (node in cleaned) {
            val binding = bindings[node.nodeId]
            when {
                // Bound node whose copy hasn't changed since the last approved sync — nothing to do.
                binding != null && (binding.lastText == node.text || existingSources[binding.stringKey]?.trim() == node.text) ->
                    skippedUnchanged++
                rejectedTexts[node.nodeId]?.contains(node.text) == true ->
                    skippedRejected++
                else -> toStage.add(node)
            }
        }
        if (toStage.isEmpty()) return FigmaIngestSummary(0, 0, 0, skippedUnchanged, skippedRejected)

        // Suggest keys only for unbound nodes — bound nodes keep their repo key.
        val unbound = toStage.filter { bindings[it.nodeId] == null }
        val suggestions = suggestKeys(project, unbound)
        // Semantic near-duplicates for unbound nodes with no exact text match — a softer
        // "did you mean to reuse checkout_title?" hint powered by the fuzzy-TM embeddings.
        val fuzzyMatches = findSimilarKeys(
            project.id,
            unbound.filter { textToExistingKey[it.text] == null },
            textToExistingKey,
        )
        val reservedKeys = existingSources.keys.toMutableSet()

        var updates = 0
        var duplicates = 0
        val now = Clock.System.now()
        for (node in toStage) {
            val boundKey = bindings[node.nodeId]?.stringKey
            val duplicateOfKey = if (boundKey == null) textToExistingKey[node.text] else null
            val fuzzy = if (boundKey == null && duplicateOfKey == null) fuzzyMatches[node.nodeId] else null
            val suggestedKey = boundKey ?: uniqueKey(
                suggestions[node.nodeId] ?: fallbackKey(node),
                reservedKeys,
            ).also { reservedKeys.add(it) }
            if (boundKey != null) updates++
            if (duplicateOfKey != null) duplicates++

            candidateRepository.upsertPending(
                FigmaStringCandidate(
                    id = UUID.randomUUID().toString(),
                    projectId = project.id,
                    figmaFileKey = fileKey,
                    figmaNodeId = node.nodeId,
                    nodeName = node.nodeName,
                    pageName = node.pageName,
                    frameName = node.frameName,
                    figmaFrameId = node.frameId,
                    sourceText = node.text,
                    suggestedKey = suggestedKey,
                    boundKey = boundKey,
                    duplicateOfKey = duplicateOfKey,
                    similarToKey = fuzzy?.first,
                    similarityScore = fuzzy?.second,
                    submittedByUserId = submittedByUserId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        storeFramePreviews(project.id, fileKey, previews, toStage, now)
        val warnings = expansionWarnings(toStage, project.targets.map { it.code })

        val autoPrUrl = if (autoApprove && githubToken != null) autoApproveAll(project, githubToken) else null
        if (autoPrUrl != null) {
            notificationService?.notifyFigmaAutoPr(
                userId = project.ownerId,
                projectName = project.name,
                projectId = project.id,
                count = toStage.size,
                prUrl = autoPrUrl,
            )
        } else {
            notificationService?.notifyFigmaStrings(
                userId = project.ownerId,
                projectName = project.name,
                projectId = project.id,
                count = toStage.size,
            )
        }
        log.info(
            "Figma ingest: project={} file={} staged={} updates={} duplicates={} unchanged={} rejected={} autoPr={}",
            project.id, fileKey, toStage.size, updates, duplicates, skippedUnchanged, skippedRejected, autoPrUrl != null,
        )
        return FigmaIngestSummary(
            toStage.size, updates, duplicates, skippedUnchanged, skippedRejected,
            warnings = warnings, autoPrUrl = autoPrUrl,
        )
    }

    /**
     * Auto-approve: sweep every pending candidate (not just this push) into one PR so the
     * inbox stays empty. Any failure — key conflicts, GitHub down — leaves the candidates
     * in the inbox and falls back to the normal "awaiting review" notification.
     */
    private suspend fun autoApproveAll(project: Project, githubToken: String): String? {
        val targetFile = project.sourceFilePaths.firstOrNull() ?: return null
        val pending = candidateRepository.listForProject(project.id, FigmaCandidateStatus.PENDING, 200, 0)
        if (pending.isEmpty()) return null
        return runCatching { approve(project, pending.map { it.id }, targetFile, githubToken).prUrl }
            .onFailure { e ->
                if (e is CancellationException) throw e
                log.warn("Figma auto-approve fell back to inbox: project={} reason={}", project.id, e.message)
            }
            .getOrNull()
    }

    /** Bound keys whose repo copy no longer matches what Figma last synced — the design file is stale. */
    suspend fun detectDrift(project: Project): List<com.syncling.domain.FigmaDriftItem> {
        val bindings = bindingRepository.listForProject(project.id)
        if (bindings.isEmpty()) return emptyList()
        val sources = translationRepository.getStringKeysAndTexts(project.id)
        return computeDrift(bindings, sources)
    }

    /** Keeps only screenshots of frames that actually produced staged candidates. Best-effort. */
    private suspend fun storeFramePreviews(
        projectId: String,
        fileKey: String,
        previews: List<FigmaFrameUpload>,
        staged: List<FigmaPushNode>,
        now: kotlinx.datetime.Instant,
    ) {
        val repo = previewRepository ?: return
        if (previews.isEmpty()) return
        val stagedFrameIds = staged.mapNotNullTo(mutableSetOf()) { it.frameId }
        for (preview in previews.distinctBy { it.frameId }) {
            if (preview.frameId !in stagedFrameIds || preview.png.isEmpty()) continue
            runCatching {
                repo.upsert(
                    FigmaFramePreview(
                        projectId = projectId,
                        figmaFileKey = fileKey,
                        figmaFrameId = preview.frameId,
                        png = preview.png,
                        updatedAt = now,
                    ),
                )
            }.onFailure { log.warn("Figma preview store failed for frame {}: {}", preview.frameId, it.message) }
        }
    }

    /**
     * nodeId → (existing key, similarity) for pushed texts that are semantically close to an
     * existing source string. Rides the fuzzy-TM embedding rows; returns empty when the project
     * has no embeddings yet or the embedding backends aren't wired.
     */
    private suspend fun findSimilarKeys(
        projectId: String,
        nodes: List<FigmaPushNode>,
        textToExistingKey: Map<String, String>,
    ): Map<String, Pair<String, Float>> {
        val service = embeddingService ?: return emptyMap()
        val repository = embeddingRepository ?: return emptyMap()
        if (nodes.isEmpty() || textToExistingKey.isEmpty()) return emptyMap()

        val rows = runCatching { repository.listForProject(projectId) }
            .getOrElse { e ->
                log.warn("Figma fuzzy lookup — embedding rows unavailable: {}", e.message)
                return emptyMap()
            }
        // Only rows whose source text still exists in the source file can be reused as keys.
        val candidates = rows.mapNotNull { row ->
            textToExistingKey[row.sourceText.trim()]?.let { key -> Triple(key, row.embedding, norm(row.embedding)) }
        }
        if (candidates.isEmpty()) return emptyMap()

        val vectors = runCatching { service.embedBatch(nodes.map { it.text }) }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                log.warn("Figma fuzzy lookup — embedding call failed: {}", e.message)
                return emptyMap()
            }

        val result = mutableMapOf<String, Pair<String, Float>>()
        for ((idx, vec) in vectors.withIndex()) {
            if (vec == null) continue
            val qNorm = norm(vec)
            if (qNorm == 0f) continue
            var bestKey: String? = null
            var bestSim = FUZZY_SIMILARITY_THRESHOLD
            for ((key, emb, embNorm) in candidates) {
                if (embNorm == 0f || emb.size != vec.size) continue
                var dot = 0.0
                for (i in vec.indices) dot += vec[i] * emb[i]
                val sim = (dot / (qNorm * embNorm)).toFloat()
                if (sim >= bestSim) {
                    bestSim = sim
                    bestKey = key
                }
            }
            bestKey?.let { result[nodes[idx].nodeId] = it to bestSim }
        }
        return result
    }

    private fun norm(v: FloatArray): Float {
        var s = 0.0
        for (x in v) s += x * x
        return kotlin.math.sqrt(s).toFloat()
    }

    /**
     * Merges the approved candidates into [targetFile] on the watch branch and opens a PR.
     * Throws [FigmaKeyConflictException] when a *new* key already exists in the source file
     * with different text — the dev must rename or reject that candidate first.
     */
    suspend fun approve(
        project: Project,
        candidateIds: List<String>,
        targetFile: String,
        githubToken: String,
    ): FigmaApproveResult {
        val candidates = candidateRepository.findByIds(candidateIds)
            .filter { it.projectId == project.id && it.status == FigmaCandidateStatus.PENDING }
        require(candidates.isNotEmpty()) { "No pending candidates to approve" }

        val invalidKeys = candidates.filterNot { isValidStringKey(it.effectiveKey) }
        if (invalidKeys.isNotEmpty()) {
            throw FigmaKeyConflictException(
                invalidKeys.associate { it.effectiveKey to "Invalid key — use lowercase letters, digits, and underscores" },
            )
        }
        val batchDupes = candidates.groupBy { it.effectiveKey }.filterValues { it.size > 1 }
        if (batchDupes.isNotEmpty()) {
            throw FigmaKeyConflictException(batchDupes.mapValues { "Two approved strings resolve to the same key" })
        }

        val existingSources = translationRepository.getStringKeysAndTexts(project.id)
        val conflicts = candidates
            .filter { it.boundKey == null }
            .filter { existingSources[it.effectiveKey]?.trim()?.let { t -> t != it.sourceText } == true }
            .associate { it.effectiveKey to "Key already exists with different text — rename or reject" }
        if (conflicts.isNotEmpty()) throw FigmaKeyConflictException(conflicts)

        val entries = candidates.associate { it.effectiveKey to it.sourceText }
        val currentContent = gitHubService.fetchFileContent(
            repo = project.githubRepo, branch = project.watchBranch,
            filePath = targetFile, token = githubToken,
        )
        val merged = mergeIntoSourceFile(targetFile, currentContent, entries)

        val added = candidates.filter { it.boundKey == null }.map { it.effectiveKey }
        val updated = candidates.mapNotNull { it.boundKey }
        val pr = gitHubService.createBranchAndPr(
            repo = project.githubRepo,
            baseBranch = project.watchBranch,
            files = mapOf(targetFile to merged),
            commitMessage = "feat(i18n): sync ${candidates.size} string(s) from Figma",
            prTitle = "Add strings from Figma (${candidates.size})",
            prBody = buildPrBody(candidates, added, updated),
            token = githubToken,
            branchPattern = "syncling/figma-{timestamp}",
        )

        candidateRepository.markPrOpened(candidates.map { it.id }, pr.prUrl)
        val now = Clock.System.now()
        bindingRepository.upsertAll(
            candidates.map {
                FigmaNodeBinding(
                    projectId = project.id,
                    figmaFileKey = it.figmaFileKey,
                    figmaNodeId = it.figmaNodeId,
                    stringKey = it.effectiveKey,
                    lastText = it.sourceText,
                    updatedAt = now,
                )
            },
        )
        log.info("Figma approve: project={} pr={} added={} updated={}", project.id, pr.prUrl, added.size, updated.size)
        return FigmaApproveResult(prUrl = pr.prUrl, branchName = pr.branchName, keysAdded = added, keysUpdated = updated)
    }

    suspend fun reject(projectId: String, candidateIds: List<String>): Int =
        candidateRepository.markRejected(candidateIds, projectId)

    // ── Key suggestion ────────────────────────────────────────────────────────

    private suspend fun suggestKeys(project: Project, nodes: List<FigmaPushNode>): Map<String, String> {
        if (nodes.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, String>()
        for (chunk in nodes.chunked(KEY_BATCH_SIZE)) {
            val batch = semaphore.withPermit {
                runCatching { callGeminiKeyBatch(project, chunk) }.getOrElse { e ->
                    if (e is CancellationException) throw e
                    log.warn("Figma key suggestion failed for {} node(s) — using slug fallback: {}", chunk.size, e.message)
                    emptyMap()
                }
            }
            results.putAll(batch)
        }
        return results
    }

    private suspend fun callGeminiKeyBatch(project: Project, nodes: List<FigmaPushNode>): Map<String, String> {
        val inputJson = buildJsonObject {
            for (node in nodes) {
                put(node.nodeId, buildJsonObject {
                    put("text", node.text.take(300))
                    put("layer", node.nodeName.take(100))
                    put("screen", (node.frameName ?: node.pageName ?: "").take(100))
                })
            }
        }.toString()

        val payload = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(KEY_SYSTEM_PROMPT))),
            contents = listOf(GeminiContent(listOf(GeminiPart(inputJson)))),
            generationConfig = GenerationConfig(
                temperature = 0.0,
                thinkingConfig = ThinkingConfig(0),
                responseMimeType = "application/json",
            ),
        )
        val response = client.post("$GEMINI_ENDPOINT?key=$geminiApiKey") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) throw Exception("Gemini key batch HTTP ${response.status}")
        val raw = response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty Gemini key batch response")

        val parsed = json.parseToJsonElement(raw).jsonObject
        return nodes.mapNotNull { node ->
            parsed[node.nodeId]?.jsonPrimitive?.contentOrNull
                ?.let { normalizeKey(it) }
                ?.takeIf { isValidStringKey(it) }
                ?.let { node.nodeId to it }
        }.toMap()
    }

    fun close() {
        client.close()
    }

    companion object {
        private const val KEY_BATCH_SIZE = 40

        /** Cosine floor for suggesting an existing key as a semantic near-duplicate. */
        const val FUZZY_SIMILARITY_THRESHOLD = 0.90f

        /**
         * Typical length expansion of English UI copy per target language (rough industry
         * averages). Only languages that grow ≥20% are listed — the warning is a nudge,
         * not a measurement.
         */
        val EXPANSION_FACTORS: Map<String, Float> = mapOf(
            "de" to 1.35f, "fi" to 1.30f, "hu" to 1.30f, "el" to 1.30f,
            "fr" to 1.25f, "es" to 1.25f, "pt" to 1.25f, "pl" to 1.25f,
            "it" to 1.20f, "ru" to 1.20f, "nl" to 1.20f, "tr" to 1.20f,
        )

        /** Fixed-width texts too short to meaningfully overflow are not worth warning about. */
        private const val MIN_WARN_TEXT_LENGTH = 12

        /**
         * Warns about fixed-width text boxes whose copy will likely grow past the box once
         * translated into the project's target languages. Pure — testable without the service.
         */
        fun expansionWarnings(nodes: List<FigmaPushNode>, targetLanguageCodes: List<String>): List<FigmaLengthWarning> {
            val worst = targetLanguageCodes
                .mapNotNull { code ->
                    val lang = code.substringBefore('-').substringBefore('_').lowercase()
                    EXPANSION_FACTORS[lang]?.let { lang to it }
                }
                .maxByOrNull { it.second }
                ?: return emptyList()
            val (lang, factor) = worst
            val pct = ((factor - 1f) * 100).toInt()
            return nodes
                .filter { it.fixedWidth && it.text.length >= MIN_WARN_TEXT_LENGTH }
                .map {
                    FigmaLengthWarning(
                        nodeId = it.nodeId,
                        text = it.text,
                        message = "Fixed-width text box — ${lang.uppercase()} copy typically runs ~$pct% longer and may overflow.",
                    )
                }
        }

        /** Compares last-synced Figma copy against the current source file. Pure — testable without repos. */
        fun computeDrift(
            bindings: List<FigmaNodeBinding>,
            currentSources: Map<String, String>,
        ): List<com.syncling.domain.FigmaDriftItem> =
            bindings.mapNotNull { b ->
                val repoText = currentSources[b.stringKey]?.trim() ?: return@mapNotNull null
                if (repoText == b.lastText.trim()) return@mapNotNull null
                com.syncling.domain.FigmaDriftItem(
                    stringKey = b.stringKey,
                    figmaFileKey = b.figmaFileKey,
                    figmaNodeId = b.figmaNodeId,
                    figmaText = b.lastText,
                    repoText = repoText,
                )
            }.sortedBy { it.stringKey }

        private val KEY_SYSTEM_PROMPT = """
            You are an Android/iOS localization engineer. For each Figma text node, produce a resource string key.

            Input: a JSON object where each key is a node id mapping to {"text": "<UI copy>", "layer": "<Figma layer name>", "screen": "<frame or page name>"}.
            Output: a JSON object mapping the SAME node ids to a single snake_case key string.

            Rules for keys:
            - Lowercase letters, digits, and underscores only; must start with a letter; at most 60 characters.
            - Prefix with a short screen context when available: "checkout_", "settings_", "onboarding_".
            - Reflect the UI role: "_title", "_subtitle", "_cta", "_hint", "_error" suffixes where obvious.
            - Derive from the meaning of the text, never transliterate it verbatim beyond a few words.
            - Include every input node id in the output — no omissions, no extra keys, no markdown.
        """.trimIndent()

        /** Slug fallback when Gemini is unavailable: layer name first, then the copy itself. */
        fun fallbackKey(node: FigmaPushNode): String {
            val base = node.nodeName.ifBlank { node.text }
            val slug = normalizeKey(base)
            return if (isValidStringKey(slug)) slug else "figma_string"
        }

        fun normalizeKey(raw: String): String {
            val slug = raw.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(60)
                .trimEnd('_')
            return when {
                slug.isEmpty() -> ""
                slug.first().isLetter() -> slug
                else -> "s_$slug".take(60).trimEnd('_')
            }
        }

        /** Appends _2, _3, … until [key] no longer collides with [reserved]. */
        fun uniqueKey(key: String, reserved: Set<String>): String {
            if (key !in reserved) return key
            var i = 2
            while ("${key}_$i" in reserved) i++
            return "${key}_$i"
        }

        /** Dispatches on file extension the same way the export endpoint does. */
        fun mergeIntoSourceFile(path: String, currentContent: String, entries: Map<String, String>): String =
            when (path.substringAfterLast('.', "xml").lowercase()) {
                "strings" -> StringParserService.mergeIosStrings(currentContent, entries)
                "json", "arb" -> StringParserService.mergeJsonStrings(currentContent, entries)
                else -> StringParserService.mergeAndroidXml(currentContent, entries)
            }

        private fun buildPrBody(
            candidates: List<FigmaStringCandidate>,
            added: List<String>,
            updated: List<String>,
        ): String = buildString {
            appendLine("Strings synced from Figma via Syncling.")
            appendLine()
            if (added.isNotEmpty()) {
                appendLine("**New keys (${added.size})**")
                for (c in candidates.filter { it.boundKey == null }) {
                    appendLine("- `${c.effectiveKey}` — ${c.sourceText.take(80)}")
                }
                appendLine()
            }
            if (updated.isNotEmpty()) {
                appendLine("**Updated copy (${updated.size})**")
                for (c in candidates.filter { it.boundKey != null }) {
                    appendLine("- `${c.effectiveKey}` — ${c.sourceText.take(80)}")
                }
                appendLine()
            }
            val fileKey = candidates.firstOrNull()?.figmaFileKey
            if (fileKey != null) appendLine("Source file: https://www.figma.com/design/$fileKey")
            appendLine()
            appendLine("_Merging this PR triggers the Syncling translation pipeline for all configured languages._")
        }
    }
}
