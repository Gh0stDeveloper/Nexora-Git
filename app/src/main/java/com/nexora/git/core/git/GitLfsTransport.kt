package com.nexora.git.core.git

import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GitLfsTransferSummary(
    val requested: Int,
    val transferred: Int,
    val alreadyPresent: Int,
)

@Singleton
class GitLfsTransport @Inject constructor(
    private val client: OkHttpClient,
    private val credentialProvider: GitCredentialProvider,
    private val manager: GitLfsManager,
) {
    suspend fun downloadMissing(
        repositoryPath: String,
        remoteUrl: String,
    ): GitLfsTransferSummary {
        val state = manager.inspect(repositoryPath)
        if (state.pointers.isEmpty()) {
            return GitLfsTransferSummary(0, 0, 0)
        }

        val root = canonicalRepository(repositoryPath)
        val specs = state.pointers.map { pointer ->
            ObjectSpec(
                oid = pointer.oidSha256.lowercase(),
                size = pointer.sizeBytes,
                worktreePath = pointer.path,
            )
        }

        var transferred = 0
        var alreadyPresent = 0

        specs.chunked(BATCH_SIZE).forEach { chunk ->
            val missing = chunk.filter { spec ->
                val cached = cacheFile(root, spec.oid)
                val valid = cached.isFile &&
                    cached.length() == spec.size &&
                    sha256(cached) == spec.oid
                if (valid) {
                    materialize(root, spec, cached)
                    alreadyPresent += 1
                }
                !valid
            }

            if (missing.isEmpty()) return@forEach

            val response = batch(
                operation = "download",
                remoteUrl = remoteUrl,
                objects = missing,
            )

            response.forEach { item ->
                val spec = missing.firstOrNull {
                    it.oid == item.oid
                } ?: error("Unexpected Git LFS object ${item.oid}.")

                val action = item.action
                    ?: error(
                        "Git LFS server did not provide a download action " +
                            "for ${item.oid}.",
                    )

                val cached = cacheFile(root, spec.oid)
                cached.parentFile?.mkdirs()
                val temporary = File(
                    cached.parentFile,
                    cached.name + ".download",
                )

                val request = requestForAction(
                    action = action,
                    method = "GET",
                    body = null,
                )
                client.newCall(request).execute().use { http ->
                    require(http.isSuccessful) {
                        "Git LFS download failed with HTTP ${http.code}."
                    }
                    val body = requireNotNull(http.body) {
                        "Git LFS download returned an empty body."
                    }
                    temporary.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                require(
                    temporary.length() == spec.size &&
                        sha256(temporary) == spec.oid,
                ) {
                    temporary.delete()
                    "Downloaded Git LFS object failed SHA-256 verification."
                }

                replaceFile(temporary, cached)
                materialize(root, spec, cached)
                transferred += 1
            }
        }

        return GitLfsTransferSummary(
            requested = specs.size,
            transferred = transferred,
            alreadyPresent = alreadyPresent,
        )
    }

    suspend fun uploadPending(
        repositoryPath: String,
        remoteUrl: String,
    ): GitLfsTransferSummary {
        val root = canonicalRepository(repositoryPath)
        val objects = localObjects(root)
        if (objects.isEmpty()) {
            return GitLfsTransferSummary(0, 0, 0)
        }

        var transferred = 0
        var alreadyPresent = 0

        objects.chunked(BATCH_SIZE).forEach { chunk ->
            val response = batch(
                operation = "upload",
                remoteUrl = remoteUrl,
                objects = chunk,
            )

            response.forEach { item ->
                val spec = chunk.firstOrNull {
                    it.oid == item.oid
                } ?: error("Unexpected Git LFS object ${item.oid}.")

                val action = item.action
                if (action == null) {
                    alreadyPresent += 1
                    return@forEach
                }

                val source = requireNotNull(spec.cacheFile) {
                    "Local Git LFS object is unavailable."
                }
                require(
                    source.length() == spec.size &&
                        sha256(source) == spec.oid,
                ) {
                    "Local Git LFS object ${spec.oid} failed verification."
                }

                val upload = requestForAction(
                    action = action,
                    method = "PUT",
                    body = source.asRequestBody(OCTET_STREAM),
                )
                client.newCall(upload).execute().use { http ->
                    require(http.isSuccessful) {
                        "Git LFS upload failed with HTTP ${http.code}."
                    }
                }

                item.verifyAction?.let { verify ->
                    val body = JSONObject()
                        .put("oid", spec.oid)
                        .put("size", spec.size)
                        .toString()
                        .toRequestBody(LFS_MEDIA_TYPE)
                    val verifyRequest = requestForAction(
                        action = verify,
                        method = "POST",
                        body = body,
                    )
                    client.newCall(verifyRequest).execute().use { http ->
                        require(http.isSuccessful) {
                            "Git LFS verification failed with HTTP ${http.code}."
                        }
                    }
                }

                transferred += 1
            }
        }

        return GitLfsTransferSummary(
            requested = objects.size,
            transferred = transferred,
            alreadyPresent = alreadyPresent,
        )
    }

    private suspend fun batch(
        operation: String,
        remoteUrl: String,
        objects: List<ObjectSpec>,
    ): List<BatchObject> {
        val endpoint = lfsBatchEndpoint(remoteUrl)
        val credentials = credentialProvider
            .credentialsFor(remoteUrl)
            ?: error(
                "Active GitHub credentials are required for Git LFS transfer.",
            )
        require(credentials.password.isNotBlank()) {
            "Active GitHub credentials are required for Git LFS transfer."
        }

        val objectArray = JSONArray()
        objects.forEach { spec ->
            objectArray.put(
                JSONObject()
                    .put("oid", spec.oid)
                    .put("size", spec.size),
            )
        }

        val payload = JSONObject()
            .put("operation", operation)
            .put("transfers", JSONArray().put("basic"))
            .put("objects", objectArray)
            .toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", LFS_MEDIA)
            .header("Content-Type", LFS_MEDIA)
            .header(
                "Authorization",
                Credentials.basic(
                    credentials.username.ifBlank {
                        "x-access-token"
                    },
                    credentials.password,
                ),
            )
            .post(payload.toRequestBody(LFS_MEDIA_TYPE))
            .build()

        val responseText = client.newCall(request).execute().use { http ->
            require(http.isSuccessful) {
                "Git LFS batch request failed with HTTP ${http.code}."
            }
            requireNotNull(http.body) {
                "Git LFS batch request returned an empty body."
            }.string()
        }

        val response = JSONObject(responseText)
        val transfer = response.optString("transfer")
        require(transfer.isBlank() || transfer == "basic") {
            "Git LFS server selected unsupported transfer adapter '$transfer'."
        }

        val array = response.optJSONArray("objects") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                item.optJSONObject("error")?.let { problem ->
                    error(
                        "Git LFS object ${item.optString("oid")} failed: " +
                            problem.optString(
                                "message",
                                "server error",
                            ),
                    )
                }

                val actions = item.optJSONObject("actions")
                val primary = actions?.optJSONObject(operation)
                val verify = actions?.optJSONObject("verify")

                add(
                    BatchObject(
                        oid = item.getString("oid").lowercase(),
                        action = primary?.let(::parseAction),
                        verifyAction = verify?.let(::parseAction),
                    ),
                )
            }
        }
    }

    private fun parseAction(
        json: JSONObject,
    ): LfsAction {
        val headers = buildMap {
            val headerObject = json.optJSONObject("header")
            if (headerObject != null) {
                val keys = headerObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, headerObject.getString(key))
                }
            }
        }

        return LfsAction(
            href = json.getString("href"),
            headers = headers,
        )
    }

    private fun requestForAction(
        action: LfsAction,
        method: String,
        body: okhttp3.RequestBody?,
    ): Request {
        val uri = URI(action.href)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Git LFS transfer action must use HTTPS."
        }

        val builder = Request.Builder().url(action.href)
        action.headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        return when (method) {
            "GET" -> builder.get().build()
            "PUT" -> builder.put(
                requireNotNull(body),
            ).build()
            "POST" -> builder.post(
                requireNotNull(body),
            ).build()
            else -> error("Unsupported Git LFS HTTP method.")
        }
    }

    private fun lfsBatchEndpoint(
        remoteUrl: String,
    ): String {
        val uri = URI(remoteUrl)
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true),
        ) {
            "Git LFS transfer currently supports authenticated GitHub HTTPS remotes."
        }

        val path = uri.path
            .trim('/')
            .removeSuffix(".git")
        val segments = path.split('/').filter(String::isNotBlank)
        require(segments.size == 2) {
            "GitHub remote must identify an owner and repository."
        }
        require(
            segments.none {
                it == "." || it == ".."
            },
        ) {
            "Invalid GitHub repository path."
        }

        return "https://github.com/" +
            segments[0] + "/" +
            segments[1] +
            ".git/info/lfs/objects/batch"
    }

    private fun localObjects(
        root: File,
    ): List<ObjectSpec> {
        val objectRoot = File(root, ".git/lfs/objects")
        if (!objectRoot.isDirectory) return emptyList()

        val result = mutableListOf<ObjectSpec>()
        objectRoot.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val oid = file.name.lowercase()
            if (!OID_REGEX.matches(oid)) return@forEach
            require(result.size < MAX_LOCAL_OBJECTS) {
                "Too many local LFS objects for one mobile transfer."
            }
            result += ObjectSpec(
                oid = oid,
                size = file.length(),
                cacheFile = file,
            )
        }
        return result
    }

    private fun cacheFile(
        root: File,
        oid: String,
    ): File {
        require(OID_REGEX.matches(oid)) {
            "Invalid Git LFS object ID."
        }
        return File(
            root,
            ".git/lfs/objects/" +
                oid.substring(0, 2) + "/" +
                oid.substring(2, 4) + "/" +
                oid,
        )
    }

    private fun materialize(
        root: File,
        spec: ObjectSpec,
        source: File,
    ) {
        val relative = requireNotNull(spec.worktreePath) {
            "Git LFS worktree path is missing."
        }
        val target = File(root, relative).canonicalFile
        val rootPrefix = root.canonicalPath + File.separator
        require(
            target.path == root.canonicalPath ||
                target.path.startsWith(rootPrefix),
        ) {
            "Git LFS pointer path escapes the workspace."
        }

        target.parentFile?.mkdirs()
        val temporary = File(
            target.parentFile,
            target.name + ".lfs.tmp",
        )
        source.inputStream().use { input ->
            temporary.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        replaceFile(temporary, target)
    }

    private fun replaceFile(
        temporary: File,
        target: File,
    ) {
        if (target.exists() && !target.delete()) {
            temporary.delete()
            error("Unable to replace Git LFS file ${target.name}.")
        }
        if (!temporary.renameTo(target)) {
            temporary.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) {
                    digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    private fun canonicalRepository(
        repositoryPath: String,
    ): File = File(repositoryPath).canonicalFile.also { root ->
        require(File(root, ".git").isDirectory) {
            "Git repository metadata is missing."
        }
    }

    private data class ObjectSpec(
        val oid: String,
        val size: Long,
        val worktreePath: String? = null,
        val cacheFile: File? = null,
    )

    private data class LfsAction(
        val href: String,
        val headers: Map<String, String>,
    )

    private data class BatchObject(
        val oid: String,
        val action: LfsAction?,
        val verifyAction: LfsAction?,
    )

    companion object {
        private const val LFS_MEDIA =
            "application/vnd.git-lfs+json"
        private val LFS_MEDIA_TYPE = LFS_MEDIA.toMediaType()
        private val OCTET_STREAM =
            "application/octet-stream".toMediaType()
        private val OID_REGEX = Regex("[0-9a-f]{64}")
        private const val BATCH_SIZE = 50
        private const val MAX_LOCAL_OBJECTS = 2_000
    }
}
