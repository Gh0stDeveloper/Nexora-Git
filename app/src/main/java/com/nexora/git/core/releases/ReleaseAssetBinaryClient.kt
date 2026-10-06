package com.nexora.git.core.releases

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.network.GitHubApiConfig
import com.nexora.git.core.platform.GitHubApiErrorMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

@Singleton
class ReleaseAssetBinaryClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val authSessionRepository: AuthSessionRepository,
    private val errorMapper: GitHubApiErrorMapper,
    private val parser: ReleasesJsonParser,
) {
    private val redirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val downloadClient = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadAsset(
        owner: String,
        repository: String,
        asset: ReleaseAsset,
    ): AppResult<ReleaseAssetDownload> =
        withAccessToken { token, accountId ->
            downloadAssetAuthorized(
                owner = owner,
                repository = repository,
                asset = asset,
                token = token,
                accountId = accountId,
                retryAuthentication = true,
            )
        }

    suspend fun uploadAsset(
        owner: String,
        repository: String,
        releaseId: Long,
        contentUri: String,
    ): AppResult<ReleaseAsset> =
        withAccessToken { token, accountId ->
            uploadAssetAuthorized(
                owner = owner,
                repository = repository,
                releaseId = releaseId,
                contentUri = contentUri,
                token = token,
                accountId = accountId,
                retryAuthentication = true,
            )
        }

    private suspend fun downloadAssetAuthorized(
        owner: String,
        repository: String,
        asset: ReleaseAsset,
        token: String,
        accountId: Long,
        retryAuthentication: Boolean,
    ): AppResult<ReleaseAssetDownload> =
        withContext(Dispatchers.IO) {
            try {
                val initial = Request.Builder()
                    .url(
                        GitHubApiConfig.REST_BASE_URL.trimEnd('/') +
                            "/repos/" + owner + "/" + repository +
                            "/releases/assets/" + asset.id,
                    )
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/octet-stream")
                    .get()
                    .build()

                redirectClient.newCall(initial).execute().use { response ->
                    if (response.code == 401 && retryAuthentication) {
                        return@withContext when (
                            val refreshed =
                                authSessionRepository
                                    .forceRefreshAccessToken(accountId)
                        ) {
                            is AuthResult.Success ->
                                downloadAssetAuthorized(
                                    owner = owner,
                                    repository = repository,
                                    asset = asset,
                                    token = refreshed.value,
                                    accountId = accountId,
                                    retryAuthentication = false,
                                )

                            is AuthResult.Failure ->
                                AppResult.Failure(
                                    AppError.Authentication(
                                        refreshed.detail,
                                    ),
                                )
                        }
                    }

                    if (response.code in 300..399) {
                        val location = response.header("Location")
                            ?.toHttpUrlOrNull()
                            ?: return@withContext AppResult.Failure(
                                AppError.Parsing(
                                    "GitHub asset redirect is missing or invalid.",
                                ),
                            )

                        if (!location.isHttps) {
                            return@withContext AppResult.Failure(
                                AppError.Validation(
                                    "Refusing a non-HTTPS release asset redirect.",
                                ),
                            )
                        }

                        val external = Request.Builder()
                            .url(location)
                            .header(
                                "User-Agent",
                                GitHubApiConfig.USER_AGENT,
                            )
                            .get()
                            .build()

                        return@withContext downloadClient
                            .newCall(external)
                            .execute()
                            .use { redirected ->
                                if (!redirected.isSuccessful) {
                                    AppResult.Failure(
                                        errorMapper.fromHttp(
                                            statusCode =
                                                redirected.code,
                                            headers =
                                                redirected.headers,
                                            body = null,
                                        ),
                                    )
                                } else {
                                    saveDownload(
                                        asset = asset,
                                        response = redirected,
                                    )
                                }
                            }
                    }

                    if (!response.isSuccessful) {
                        val body = response.body
                            ?.string()
                            ?.take(32_768)
                        return@withContext AppResult.Failure(
                            errorMapper.fromHttp(
                                statusCode = response.code,
                                headers = response.headers,
                                body = body,
                            ),
                        )
                    }

                    saveDownload(asset, response)
                }
            } catch (error: IOException) {
                AppResult.Failure(
                    errorMapper.fromException(error),
                )
            } catch (error: Exception) {
                AppResult.Failure(
                    errorMapper.fromException(error),
                )
            }
        }

    private fun saveDownload(
        asset: ReleaseAsset,
        response: okhttp3.Response,
    ): AppResult<ReleaseAssetDownload> {
        val body = response.body
            ?: return AppResult.Failure(
                AppError.Parsing(
                    "GitHub returned an empty release asset.",
                ),
            )

        val declared = body.contentLength()
        if (declared > MAX_ASSET_BYTES) {
            return AppResult.Failure(
                AppError.Validation(
                    "Release asset exceeds the 2 GiB Nexora Git safety limit.",
                ),
            )
        }

        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir,
            "NexoraGit/release-assets",
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return AppResult.Failure(
                AppError.Unknown(
                    IOException("Unable to create release asset directory."),
                ),
            )
        }

        val safeName = sanitizeFileName(asset.name)
        val target = File(
            directory,
            asset.id.toString() + "-" + safeName,
        )
        val partial = File(target.absolutePath + ".part")
        partial.delete()

        return try {
            var total = 0L
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_ASSET_BYTES) {
                            throw AssetTooLargeException()
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (target.exists() && !target.delete()) {
                throw IOException(
                    "Unable to replace existing release asset.",
                )
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }

            AppResult.Success(
                ReleaseAssetDownload(
                    filePath = target.absolutePath,
                    fileName = target.name,
                    sizeInBytes = target.length(),
                ),
            )
        } catch (_: AssetTooLargeException) {
            partial.delete()
            AppResult.Failure(
                AppError.Validation(
                    "Release asset exceeds the 2 GiB Nexora Git safety limit.",
                ),
            )
        } catch (error: IOException) {
            partial.delete()
            if (
                error.message
                    ?.contains(
                        "No space left",
                        ignoreCase = true,
                    ) == true
            ) {
                AppResult.Failure(AppError.DiskFull)
            } else {
                AppResult.Failure(
                    AppError.Unknown(error),
                )
            }
        }
    }

    private suspend fun uploadAssetAuthorized(
        owner: String,
        repository: String,
        releaseId: Long,
        contentUri: String,
        token: String,
        accountId: Long,
        retryAuthentication: Boolean,
    ): AppResult<ReleaseAsset> =
        withContext(Dispatchers.IO) {
            val uri = runCatching {
                Uri.parse(contentUri)
            }.getOrElse {
                return@withContext AppResult.Failure(
                    AppError.Validation(
                        "Selected release asset URI is invalid.",
                    ),
                )
            }

            val metadata = queryMetadata(uri)
                ?: return@withContext AppResult.Failure(
                    AppError.Validation(
                        "Unable to read the selected release asset.",
                    ),
                )

            if (
                metadata.size != null &&
                metadata.size > MAX_ASSET_BYTES
            ) {
                return@withContext AppResult.Failure(
                    AppError.Validation(
                        "Release asset exceeds the 2 GiB Nexora Git safety limit.",
                    ),
                )
            }

            val temp = try {
                File.createTempFile(
                    "release-asset-",
                    ".upload",
                    context.cacheDir,
                )
            } catch (error: IOException) {
                return@withContext if (
                    error.message
                        ?.contains(
                            "No space left",
                            ignoreCase = true,
                        ) == true
                ) {
                    AppResult.Failure(AppError.DiskFull)
                } else {
                    AppResult.Failure(
                        AppError.Unknown(error),
                    )
                }
            }

            try {
                var total = 0L
                val stream = context.contentResolver
                    .openInputStream(uri)
                    ?: return@withContext AppResult.Failure(
                        AppError.Validation(
                            "Unable to open the selected release asset.",
                        ),
                    )

                stream.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_ASSET_BYTES) {
                                throw AssetTooLargeException()
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }

                val mediaType = metadata.mimeType
                    ?.toMediaTypeOrNull()
                    ?: "application/octet-stream"
                        .toMediaTypeOrNull()

                val uploadUrl = "https://uploads.github.com"
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("repos")
                    .addPathSegment(owner)
                    .addPathSegment(repository)
                    .addPathSegment("releases")
                    .addPathSegment(releaseId.toString())
                    .addPathSegment("assets")
                    .addQueryParameter(
                        "name",
                        metadata.fileName,
                    )
                    .build()

                val request = Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", "Bearer " + token)
                    .header(
                        "Accept",
                        GitHubApiConfig.DEFAULT_ACCEPT,
                    )
                    .header(
                        "X-GitHub-Api-Version",
                        GitHubApiConfig.REST_API_VERSION,
                    )
                    .post(
                        temp.asRequestBody(mediaType),
                    )
                    .build()

                redirectClient.newCall(request).execute().use { response ->
                    if (response.code == 401 && retryAuthentication) {
                        return@withContext when (
                            val refreshed =
                                authSessionRepository
                                    .forceRefreshAccessToken(accountId)
                        ) {
                            is AuthResult.Success ->
                                uploadAssetAuthorized(
                                    owner = owner,
                                    repository = repository,
                                    releaseId = releaseId,
                                    contentUri = contentUri,
                                    token = refreshed.value,
                                    accountId = accountId,
                                    retryAuthentication = false,
                                )

                            is AuthResult.Failure ->
                                AppResult.Failure(
                                    AppError.Authentication(
                                        refreshed.detail,
                                    ),
                                )
                        }
                    }

                    val responseBody = response.body
                        ?.string()

                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(
                            errorMapper.fromHttp(
                                statusCode = response.code,
                                headers = response.headers,
                                body = responseBody,
                            ),
                        )
                    }

                    runCatching {
                        parser.asset(responseBody)
                    }.fold(
                        onSuccess = {
                            AppResult.Success(it)
                        },
                        onFailure = {
                            AppResult.Failure(
                                AppError.Parsing(
                                    "Invalid GitHub release asset response.",
                                    it,
                                ),
                            )
                        },
                    )
                }
            } catch (_: AssetTooLargeException) {
                AppResult.Failure(
                    AppError.Validation(
                        "Release asset exceeds the 2 GiB Nexora Git safety limit.",
                    ),
                )
            } catch (error: IOException) {
                if (
                    error.message
                        ?.contains(
                            "No space left",
                            ignoreCase = true,
                        ) == true
                ) {
                    AppResult.Failure(AppError.DiskFull)
                } else {
                    AppResult.Failure(
                        errorMapper.fromException(error),
                    )
                }
            } catch (error: Exception) {
                AppResult.Failure(
                    errorMapper.fromException(error),
                )
            } finally {
                temp.delete()
            }
        }

    private suspend fun <T> withAccessToken(
        block: suspend (
            token: String,
            accountId: Long,
        ) -> AppResult<T>,
    ): AppResult<T> {
        val accountId =
            authSessionRepository.getActiveAccountId()
                ?: return AppResult.Failure(
                    AppError.Authentication(
                        "No active GitHub account",
                    ),
                )

        return when (
            val result =
                authSessionRepository
                    .getValidAccessToken(accountId)
        ) {
            is AuthResult.Success ->
                block(result.value, accountId)

            is AuthResult.Failure ->
                AppResult.Failure(
                    AppError.Authentication(
                        result.detail,
                    ),
                )
        }
    }

    private fun queryMetadata(
        uri: Uri,
    ): AssetMetadata? {
        var fileName: String? = null
        var size: Long? = null

        context.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME,
                )
                val sizeIndex = cursor.getColumnIndex(
                    OpenableColumns.SIZE,
                )
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        val resolvedName = fileName
            ?.takeIf(String::isNotBlank)
            ?.let(::sanitizeFileName)
            ?: "release-asset.bin"

        return AssetMetadata(
            fileName = resolvedName,
            size = size,
            mimeType =
                context.contentResolver.getType(uri),
        )
    }

    private fun sanitizeFileName(
        value: String,
    ): String =
        value.replace(
            Regex("[\\/:*?\"<>|\\p{Cntrl}]+"),
            "-",
        ).trim().take(120)
            .ifBlank { "release-asset.bin" }

    private data class AssetMetadata(
        val fileName: String,
        val size: Long?,
        val mimeType: String?,
    )

    private class AssetTooLargeException : IOException()

    companion object {
        private const val MAX_ASSET_BYTES =
            2L * 1024L * 1024L * 1024L
    }
}
