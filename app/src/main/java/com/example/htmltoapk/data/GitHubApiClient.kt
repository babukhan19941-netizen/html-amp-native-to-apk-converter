package com.example.htmltoapk.data

import com.example.htmltoapk.data.model.GitHubPushResult
import com.example.htmltoapk.data.model.ProjectFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Pushes a full project (any number of text + binary files) to a new or existing GitHub
 * repository using the low-level Git Data API (blobs -> tree -> commit -> ref update),
 * which lets us commit an entire file tree in a single atomic commit rather than one
 * file at a time via the Contents API.
 */
class GitHubApiClient(private val personalAccessToken: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val apiBase = "https://api.github.com"

    private fun authedRequest(url: String) = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $personalAccessToken")
        .addHeader("Accept", "application/vnd.github+json")
        .addHeader("X-GitHub-Api-Version", "2022-11-28")

    /** GET /user - validates the PAT and returns the authenticated login. */
    fun fetchAuthenticatedUser(): Result<String> = runCatching {
        val req = authedRequest("$apiBase/user").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("GitHub auth failed: HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty()).getString("login")
        }
    }

    private fun repoExists(owner: String, repo: String): Boolean {
        val req = authedRequest("$apiBase/repos/$owner/$repo").get().build()
        client.newCall(req).execute().use { return it.isSuccessful }
    }

    /** POST /user/repos - creates a new repo if one with this name doesn't already exist. */
    private fun createRepoIfNeeded(owner: String, repo: String, private_: Boolean) {
        if (repoExists(owner, repo)) return
        val body = JSONObject().apply {
            put("name", repo)
            put("private", private_)
            put("auto_init", true) // ensures an initial commit/branch exists to build the tree on
        }.toString().toRequestBody(jsonMedia)

        val req = authedRequest("$apiBase/user/repos").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Repo creation failed: HTTP ${resp.code} ${resp.body?.string()}")
        }
    }

    private fun getDefaultBranchSha(owner: String, repo: String, branch: String = "main"): String {
        val req = authedRequest("$apiBase/repos/$owner/$repo/git/refs/heads/$branch").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Could not read ref heads/$branch: HTTP ${resp.code}")
            return JSONObject(resp.body?.string().orEmpty()).getJSONObject("object").getString("sha")
        }
    }

    private fun createBlob(owner: String, repo: String, file: ProjectFile): String {
        val (content, encoding) = if (file.isBinary && file.binaryContent != null) {
            Base64.getEncoder().encodeToString(file.binaryContent) to "base64"
        } else {
            Base64.getEncoder().encodeToString((file.textContent ?: "").toByteArray(Charsets.UTF_8)) to "base64"
        }
        val body = JSONObject().apply {
            put("content", content)
            put("encoding", encoding)
        }.toString().toRequestBody(jsonMedia)

        val req = authedRequest("$apiBase/repos/$owner/$repo/git/blobs").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Blob creation failed for ${file.relativePath}: HTTP ${resp.code}")
            return JSONObject(resp.body?.string().orEmpty()).getString("sha")
        }
    }

    private fun createTree(owner: String, repo: String, baseTreeSha: String, entries: List<Pair<ProjectFile, String>>): String {
        val treeArray = JSONArray()
        entries.forEach { (file, blobSha) ->
            treeArray.put(JSONObject().apply {
                put("path", file.relativePath)
                put("mode", "100644")
                put("type", "blob")
                put("sha", blobSha)
            })
        }
        val body = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeArray)
        }.toString().toRequestBody(jsonMedia)

        val req = authedRequest("$apiBase/repos/$owner/$repo/git/trees").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Tree creation failed: HTTP ${resp.code} ${resp.body?.string()}")
            return JSONObject(resp.body?.string().orEmpty()).getString("sha")
        }
    }

    private fun createCommit(owner: String, repo: String, message: String, treeSha: String, parentSha: String): String {
        val body = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", JSONArray().put(parentSha))
        }.toString().toRequestBody(jsonMedia)

        val req = authedRequest("$apiBase/repos/$owner/$repo/git/commits").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Commit creation failed: HTTP ${resp.code}")
            return JSONObject(resp.body?.string().orEmpty()).getString("sha")
        }
    }

    private fun updateRef(owner: String, repo: String, commitSha: String, branch: String = "main") {
        val body = JSONObject().apply {
            put("sha", commitSha)
            put("force", false)
        }.toString().toRequestBody(jsonMedia)

        val req = authedRequest("$apiBase/repos/$owner/$repo/git/refs/heads/$branch")
            .patch(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Ref update failed: HTTP ${resp.code} ${resp.body?.string()}")
        }
    }

    /**
     * Full push flow: verify token -> ensure repo exists -> blob each file -> build tree
     * on top of the branch's current tree -> commit -> move the branch pointer.
     * Reports progress via [onProgress] (0..1) for a UI progress bar.
     */
    fun pushProject(
        owner: String,
        repo: String,
        files: List<ProjectFile>,
        commitMessage: String = "Push generated Android project",
        makePrivate: Boolean = true,
        branch: String = "main",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): GitHubPushResult {
        return try {
            onProgress(0.02f, "Authenticating with GitHub…")
            fetchAuthenticatedUser().getOrThrow()

            onProgress(0.08f, "Ensuring repository exists…")
            createRepoIfNeeded(owner, repo, makePrivate)

            onProgress(0.15f, "Reading current branch state…")
            val parentCommitSha = getDefaultBranchSha(owner, repo, branch)

            val blobShas = mutableListOf<Pair<ProjectFile, String>>()
            files.forEachIndexed { idx, file ->
                val sha = createBlob(owner, repo, file)
                blobShas += file to sha
                val progress = 0.15f + 0.6f * (idx + 1) / files.size.coerceAtLeast(1)
                onProgress(progress, "Uploaded ${file.relativePath}")
            }

            onProgress(0.8f, "Building file tree…")
            val treeSha = createTree(owner, repo, parentCommitSha, blobShas)

            onProgress(0.9f, "Creating commit…")
            val commitSha = createCommit(owner, repo, commitMessage, treeSha, parentCommitSha)

            onProgress(0.95f, "Updating $branch…")
            updateRef(owner, repo, commitSha, branch)

            onProgress(1f, "Done")
            GitHubPushResult(
                success = true,
                repoUrl = "https://github.com/$owner/$repo",
                actionsUrl = "https://github.com/$owner/$repo/actions",
                message = "Pushed ${files.size} files to $owner/$repo@$branch"
            )
        } catch (t: Throwable) {
            GitHubPushResult(success = false, message = t.message ?: "Unknown error during push")
        }
    }
}
