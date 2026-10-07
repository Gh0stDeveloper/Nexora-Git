package com.nexora.git.core.actions

import android.content.Context
import android.os.Environment
import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.network.GitHubApiConfig
import com.nexora.git.core.platform.GitHubApiErrorMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Singleton
class GitHubActionsBinaryClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val authSessionRepository: AuthSessionRepository,
    private val errorMapper: GitHubApiErrorMapper,
) {
    private val redirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val downloadClient = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getJobLog(
        owner: String,
        repository: String,
        jobId: Long,
    ): AppResult<GitHubActionsLog> =
        withAuthorizedDownload(
            path = "/repos/" + owner + "/" + repository +
                "/actions/jobs/" + jobId + "/logs",
        ) { response ->
            val body = response.body
                ?: return@withAuthorizedDownload AppResult.Failure(
                    AppError.Parsing("GitHub returned an empty job log."),
                )

            val stream = body.byteStream()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            var truncated = false

            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                val remaining = MAX_LOG_PREVIEW_BYTES - total
                if (remaining <= 0) {
                    truncated = true
                    break
                }
                val writeCount = minOf(read, remaining)
                output.write(buffer, 0, writeCount)
                total += writeCount
                if (writeCount < read) {
                    truncated = true
                    break
                }
            }

            if (!truncated && total >= MAX_LOG_PREVIEW_BYTES) {
                truncated = stream.read() >= 0
            }

            AppResult.Success(
                GitHubActionsLog(
                    text = output.toByteArray()
                        .toString(Charsets.UTF_8),
                    truncated = truncated,
                ),
            )
        }

    suspend fun downloadArtifact(
        owner: String,
        repository: String,
        artifact: GitHubActionsArtifact,
    ): AppResult<GitHubArtifactDownload> {
        if (artifact.expired) {
            return AppResult.Failure(
                AppError.Validation("This GitHub Actions artifact has expired."),
            )
        }

        return withAuthorizedDownload(
            path = "/repos/" + owner + "/" + repository +
                "/actions/artifacts/" + artifact.id + "/zip",
        ) { response ->
            val body = response.body
                ?: return@withAuthorizedDownload AppResult.Failure(
                    AppError.Parsing("GitHub returned an empty artifact."),
                )

            val declared = body.contentLength()
            if (declared > MAX_ARTIFACT_BYTES) {
                return@withAuthorizedDownload AppResult.Failure(
                    AppError.Validation(
                        "Artifact exceeds the 2 GiB Nexora Git download safety limit.",
                    ),
                )
            }

            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir,
                "NexoraGit/actions-artifacts",
            )
            if (!directory.exists() && !directory.mkdirs()) {
                return@withAuthorizedDownload AppResult.Failure(
                    AppError.Unknown(
                        IOException("Unable to create artifact directory."),
                    ),
                )
            }

            val safeName = artifact.name
                .replace(Regex("[^A-Za-z0-9._-]+"), "-")
                .trim('-', '.')
                .take(80)
                .ifBlank { "artifact" }

            val target = File(
                directory,
                artifact.id.toString() + "-" + safeName + ".zip",
            )
            val partial = File(target.absolutePath + ".part")
            partial.delete()

            try {
                var total = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_ARTIFACT_BYTES) {
                                throw ArtifactTooLargeException()
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }

                if (target.exists() && !target.delete()) {
                    throw IOException("Unable to replace existing artifact.")
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }

                AppResult.Success(
                    GitHubArtifactDownload(
                        filePath = target.absolutePath,
                        fileName = target.name,
                        sizeInBytes = target.length(),
                    ),
                )
            } catch (_: ArtifactTooLargeException) {
                partial.delete()
                AppResult.Failure(
                    AppError.Validation(
                        "Artifact exceeds the 2 GiB Nexora Git download safety limit.",
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
    }

    private suspend fun <T> withAuthorizedDownload(
        path: String,
        transform: (Response) -> AppResult<T>,
    ): AppResult<T> {
        val accountId = authSessionRepository.getActiveAccountId()
            ?: return AppResult.Failure(
                AppError.Authentication("No active GitHub account"),
            )

        val token = when (
            val result =
                authSessionRepository.getValidAccessToken(accountId)
        ) {
            is AuthResult.Success -> result.value
            is AuthResult.Failure ->
                return AppResult.Failure(
                    AppError.Authentication(result.detail),
                )
        }

        return download(
            path = path,
            token = token,
            accountId = accountId,
            retryAuthentication = true,
            transform = transform,
        )
    }

    private suspend fun <T> download(
        path: String,
        token: String,
        accountId: Long,
        retryAuthentication: Boolean,
        transform: (Response) -> AppResult<T>,
    ): AppResult<T> = withContext(Dispatchers.IO) {
        try {
            val url = (
                GitHubApiConfig.REST_BASE_URL.trimEnd('/') +
                    "/" + path.trimStart('/')
                ).toHttpUrlOrNull()
                ?: return@withContext AppResult.Failure(
                    AppError.Validation("Invalid GitHub download URL."),
                )

            val initial = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", GitHubApiConfig.DEFAULT_ACCEPT)
                .build()

            redirectClient.newCall(initial).execute().use { response ->
                if (response.code == 401 && retryAuthentication) {
                    return@withContext when (
                        val refreshed =
                            authSessionRepository
                                .forceRefreshAccessToken(accountId)
                    ) {
                        is AuthResult.Success -> download(
                            path = path,
                            token = refreshed.value,
                            accountId = accountId,
                            retryAuthentication = false,
                            transform = transform,
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
                                "GitHub download redirect is missing or invalid.",
                            ),
                        )
                    if (!location.isHttps) {
                        return@withContext AppResult.Failure(
                            AppError.Validation(
                                "Refusing a non-HTTPS GitHub download redirect.",
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
                        .use { downloaded ->
                            if (!downloaded.isSuccessful) {
                                AppResult.Failure(
                                    errorMapper.fromHttp(
                                        statusCode =
                                            downloaded.code,
                                        headers =
                                            downloaded.headers,
                                        body = null,
                                    ),
                                )
                            } else {
                                transform(downloaded)
                            }
                        }
                }

                if (!response.isSuccessful) {
                    val errorBody = response.body
                        ?.string()
                        ?.take(32_768)
                    return@withContext AppResult.Failure(
                        errorMapper.fromHttp(
                            statusCode = response.code,
                            headers = response.headers,
                            body = errorBody,
                        ),
                    )
                }

                transform(response)
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

    private class ArtifactTooLargeException : IOException()

    companion object {
        private const val MAX_LOG_PREVIEW_BYTES =
            2 * 1024 * 1024
        private const val MAX_ARTIFACT_BYTES =
            2L * 1024L * 1024L * 1024L
    }
}
