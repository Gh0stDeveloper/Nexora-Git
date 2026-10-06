package com.nexora.git.core.actions

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import com.nexora.git.core.platform.GitHubRestResponse
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class GitHubActionsGatewayImpl @Inject constructor(
    private val platform: GitHubPlatformClient,
    private val binary: GitHubActionsBinaryClient,
) : GitHubActionsGateway {

    private val parser = GitHubActionsJsonParser()

    override suspend fun listWorkflows(
        owner: String,
        repository: String,
    ): AppResult<List<GitHubWorkflow>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/actions/workflows",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 20_000L,
            ),
            parse = parser::workflows,
            message = "Invalid GitHub Actions workflows response.",
        )
    }

    override suspend fun listRuns(
        owner: String,
        repository: String,
        workflowId: Long?,
        status: WorkflowRunStatusFilter,
    ): AppResult<List<GitHubWorkflowRun>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (workflowId != null && workflowId <= 0L) {
            return validation("Workflow id is invalid.")
        }

        val base = if (workflowId == null) {
            repoPath(ids) + "/actions/runs"
        } else {
            repoPath(ids) +
                "/actions/workflows/" +
                workflowId +
                "/runs"
        }

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = base,
                query = buildMap {
                    put("per_page", "100")
                    status.wireValue?.let {
                        put("status", it)
                    }
                },
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 10_000L,
            ),
            parse = parser::runs,
            message = "Invalid GitHub Actions runs response.",
        )
    }

    override suspend fun getRun(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<GitHubWorkflowRun> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (runId <= 0L) return validation("Workflow run id is invalid.")

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/actions/runs/" +
                            runId,
                    cachePolicy =
                        GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 5_000L,
                ),
            ),
            parser::run,
            "Invalid GitHub Actions run response.",
        )
    }

    override suspend fun listJobs(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<List<GitHubActionsJob>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (runId <= 0L) return validation("Workflow run id is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl =
                    repoPath(ids) +
                        "/actions/runs/" +
                        runId +
                        "/jobs",
                query = mapOf(
                    "filter" to "all",
                    "per_page" to "100",
                ),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 5_000L,
            ),
            parse = parser::jobs,
            message = "Invalid GitHub Actions jobs response.",
        )
    }

    override suspend fun listArtifacts(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<List<GitHubActionsArtifact>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (runId <= 0L) return validation("Workflow run id is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl =
                    repoPath(ids) +
                        "/actions/runs/" +
                        runId +
                        "/artifacts",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 10_000L,
            ),
            parse = parser::artifacts,
            message = "Invalid GitHub Actions artifacts response.",
        )
    }

    override suspend fun dispatch(
        owner: String,
        repository: String,
        workflowId: Long,
        request: WorkflowDispatchRequest,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (workflowId <= 0L) return validation("Workflow id is invalid.")

        val ref = request.ref.trim()
        if (!REF.matches(ref)) {
            return validation("Workflow dispatch ref is invalid.")
        }
        if (
            request.inputs.any {
                !INPUT_NAME.matches(it.key) ||
                    it.value.length > MAX_INPUT_VALUE
            }
        ) {
            return validation("Workflow dispatch inputs are invalid.")
        }

        val payload = JSONObject()
            .put("ref", ref)
        if (request.inputs.isNotEmpty()) {
            payload.put(
                "inputs",
                JSONObject(request.inputs),
            )
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/actions/workflows/" +
                            workflowId +
                            "/dispatches",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun cancelRun(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<Unit> =
        runAction(
            owner = owner,
            repository = repository,
            runId = runId,
            suffix = "/cancel",
        )

    override suspend fun rerun(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<Unit> =
        runAction(
            owner = owner,
            repository = repository,
            runId = runId,
            suffix = "/rerun",
        )

    override suspend fun rerunFailedJobs(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<Unit> =
        runAction(
            owner = owner,
            repository = repository,
            runId = runId,
            suffix = "/rerun-failed-jobs",
        )

    override suspend fun getJobLog(
        owner: String,
        repository: String,
        jobId: Long,
    ): AppResult<GitHubActionsLog> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (jobId <= 0L) return validation("Job id is invalid.")

        return binary.getJobLog(
            owner = ids.first,
            repository = ids.second,
            jobId = jobId,
        )
    }

    override suspend fun downloadArtifact(
        owner: String,
        repository: String,
        artifact: GitHubActionsArtifact,
    ): AppResult<GitHubArtifactDownload> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (artifact.id <= 0L) {
            return validation("Artifact id is invalid.")
        }

        return binary.downloadArtifact(
            owner = ids.first,
            repository = ids.second,
            artifact = artifact,
        )
    }

    private suspend fun runAction(
        owner: String,
        repository: String,
        runId: Long,
        suffix: String,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (runId <= 0L) return validation("Workflow run id is invalid.")

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/actions/runs/" +
                            runId +
                            suffix,
                    method = GitHubHttpMethod.POST,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    private suspend fun <T> paged(
        first: GitHubRestRequest,
        parse: (String?) -> List<T>,
        message: String,
    ): AppResult<List<T>> {
        val values = mutableListOf<T>()
        val seen = linkedSetOf<String>()
        var request = first

        repeat(MAX_PAGES) {
            when (val response = platform.rest.execute(request)) {
                is AppResult.Failure -> return response
                is AppResult.Success -> {
                    val page = runCatching {
                        parse(response.value.body)
                    }.getOrElse {
                        return AppResult.Failure(
                            AppError.Parsing(message, it),
                        )
                    }
                    values += page

                    val next =
                        response.value.pagination.nextUrl
                            ?: return AppResult.Success(values)
                    if (!seen.add(next)) {
                        return validation(
                            "Repeated GitHub pagination URL.",
                        )
                    }
                    request = GitHubRestRequest(
                        pathOrUrl = next,
                        cachePolicy =
                            GitHubCachePolicy.NETWORK_FIRST,
                        cacheTtlMillis =
                            first.cacheTtlMillis,
                    )
                }
            }
        }

        return validation(
            "GitHub Actions pagination exceeded the safety limit.",
        )
    }

    private fun <T> parse(
        result: AppResult<GitHubRestResponse>,
        parser: (String?) -> T,
        message: String,
    ): AppResult<T> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                runCatching {
                    parser(result.value.body)
                }.fold(
                    onSuccess = {
                        AppResult.Success(it)
                    },
                    onFailure = {
                        AppResult.Failure(
                            AppError.Parsing(message, it),
                        )
                    },
                )
        }

    private fun unit(
        result: AppResult<GitHubRestResponse>,
    ): AppResult<Unit> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(Unit)
        }

    private fun validate(
        owner: String,
        repository: String,
    ): Pair<String, String>? {
        val safeOwner = owner.trim()
        val safeRepository = repository.trim()
        if (
            !OWNER.matches(safeOwner) ||
            !REPOSITORY.matches(safeRepository)
        ) {
            return null
        }
        return safeOwner to safeRepository
    }

    private fun repoPath(
        ids: Pair<String, String>,
    ): String =
        "/repos/" + ids.first + "/" + ids.second

    private fun validation(
        message: String,
    ): AppResult.Failure =
        AppResult.Failure(
            AppError.Validation(message),
        )

    companion object {
        private val OWNER =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
        private val REF =
            Regex("^[A-Za-z0-9._/:-]{1,255}$")
        private val INPUT_NAME =
            Regex("^[A-Za-z_][A-Za-z0-9_-]{0,99}$")
        private const val MAX_INPUT_VALUE = 65_535
        private const val MAX_PAGES = 20
    }
}
