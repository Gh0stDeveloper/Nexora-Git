package com.nexora.git.marketing

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nexora.git.core.actions.GitHubWorkflow
import com.nexora.git.core.actions.GitHubWorkflowRun
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.files.BrowserFileKind
import com.nexora.git.core.git.GitBranch
import com.nexora.git.core.git.GitDivergence
import com.nexora.git.core.git.GitPullStrategy
import com.nexora.git.core.pulls.PullRequestCheckRun
import com.nexora.git.core.pulls.PullRequestRef
import com.nexora.git.core.pulls.PullRequestSummary
import com.nexora.git.core.pulls.PullRequestUser
import com.nexora.git.core.repository.RepositoryDetails
import com.nexora.git.core.repository.RepositoryPermissions
import com.nexora.git.core.repository.RepositorySubscriptionState
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.repository.RepositoryViewerState
import com.nexora.git.core.settings.AppThemeMode
import com.nexora.git.core.storage.WorkspaceSyncState
import com.nexora.git.feature.actions.ActionsContent
import com.nexora.git.feature.actions.ActionsUiState
import com.nexora.git.feature.editor.MobileEditorContent
import com.nexora.git.feature.editor.MobileEditorUiState
import com.nexora.git.feature.git.GitWorkspaceUiState
import com.nexora.git.feature.git.PullCard
import com.nexora.git.feature.git.PushCard
import com.nexora.git.feature.git.RepositorySummaryCard
import com.nexora.git.feature.home.HomeContent
import com.nexora.git.feature.home.HomeUiState
import com.nexora.git.feature.home.HomeWorkspaceSummary
import com.nexora.git.feature.pulls.PullRequestDetailContent
import com.nexora.git.feature.pulls.PullRequestDetailUiState
import com.nexora.git.feature.repositories.RepositoryDetailContent
import com.nexora.git.feature.repositories.RepositoryDetailUiState
import com.nexora.git.ui.theme.NexoraGitTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

class MarketingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repository = RepositorySummary(
        id = 1,
        nodeId = "R_nexora",
        name = "Nexora-Git",
        fullName = "Gh0stDeveloper/Nexora-Git",
        ownerLogin = "Gh0stDeveloper",
        ownerAvatarUrl = null,
        description = "Advanced open-source Git and GitHub workspace for Android.",
        privateRepository = false,
        fork = false,
        archived = false,
        visibility = "public",
        language = "Kotlin",
        defaultBranch = "main",
        cloneUrl = "https://github.com/Gh0stDeveloper/Nexora-Git.git",
        htmlUrl = "https://github.com/Gh0stDeveloper/Nexora-Git",
        stars = 128,
        forks = 14,
        openIssues = 7,
        sizeKb = 4096,
        updatedAt = "2026-10-07T20:00:00Z",
        pushedAt = "2026-10-07T20:00:00Z",
        permissions = RepositoryPermissions(
            admin = true,
            push = true,
        ),
    )

    private val details = RepositoryDetails(
        summary = repository,
        homepage = "https://nexora.example",
        subscribers = 42,
        hasIssues = true,
        hasWiki = true,
        hasProjects = true,
        hasPages = true,
        deleteBranchOnMerge = true,
        allowMergeCommit = true,
        allowSquashMerge = true,
        allowRebaseMerge = true,
    )

    private val gitState = GitWorkspaceUiState(
        workspaceName = "Nexora-Git",
        branch = "feature/mobile-workspace",
        branches = listOf(
            GitBranch(
                name = "feature/mobile-workspace",
                remote = false,
                head = true,
                upstream = "origin/feature/mobile-workspace",
            ),
            GitBranch(
                name = "origin/feature/mobile-workspace",
                remote = true,
                head = false,
                upstream = "",
            ),
        ),
        divergence = GitDivergence(
            localRef = "feature/mobile-workspace",
            upstreamRef = "origin/feature/mobile-workspace",
            localOid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            upstreamOid = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            ahead = 2,
            behind = 1,
        ),
        loading = false,
    )

    @Test
    fun captureHomeLight() = capture("home-light", AppThemeMode.LIGHT) {
        HomeContent(
            state = homeState(),
            contentPadding = PaddingValues(0.dp),
            activeLogin = "Gh0stDeveloper",
            onOpenRepositories = {},
            onOpenExplore = {},
            onOpenActivity = {},
            onOpenWorkspace = {},
            onOpenRepository = { _, _ -> },
            onClone = {},
            onImport = {},
            onCreate = {},
        )
    }

    @Test
    fun captureHomeDark() = capture("home-dark", AppThemeMode.DARK) {
        HomeContent(
            state = homeState(),
            contentPadding = PaddingValues(0.dp),
            activeLogin = "Gh0stDeveloper",
            onOpenRepositories = {},
            onOpenExplore = {},
            onOpenActivity = {},
            onOpenWorkspace = {},
            onOpenRepository = { _, _ -> },
            onClone = {},
            onImport = {},
            onCreate = {},
        )
    }

    @Test
    fun captureHomeAmoled() = capture("home-amoled", AppThemeMode.AMOLED) {
        HomeContent(
            state = homeState(),
            contentPadding = PaddingValues(0.dp),
            activeLogin = "Gh0stDeveloper",
            onOpenRepositories = {},
            onOpenExplore = {},
            onOpenActivity = {},
            onOpenWorkspace = {},
            onOpenRepository = { _, _ -> },
            onClone = {},
            onImport = {},
            onCreate = {},
        )
    }

    @Test
    fun captureRepositoryDetail() = capture(
        "repository-detail",
        AppThemeMode.DARK,
    ) {
        RepositoryDetailContent(
            state = RepositoryDetailUiState(
                loading = false,
                details = details,
                viewerState = RepositoryViewerState(
                    starred = true,
                    subscription = RepositorySubscriptionState.SUBSCRIBED,
                    canSubscribe = true,
                ),
            ),
            contentPadding = PaddingValues(0.dp),
            onBack = {},
            onRefresh = {},
            onToggleStar = {},
            onToggleWatch = {},
            onFork = {},
            onClone = {},
            onOpenSettings = {},
            onOpenIssues = { _, _ -> },
            onOpenPullRequests = { _, _ -> },
            onOpenActions = { _, _ -> },
            onOpenReleases = { _, _ -> },
            onOpenAdvancedGitHub = { _, _ -> },
        )
    }

    @Test
    fun captureEditor() = capture("editor", AppThemeMode.DARK) {
        MobileEditorContent(
            state = MobileEditorUiState(
                workspaceName = "Nexora-Git",
                file = BrowserFile(
                    name = "RepositoryWorkspaceCoordinator.kt",
                    relativePath =
                        "app/src/main/java/com/nexora/git/core/repository/RepositoryWorkspaceCoordinator.kt",
                    absolutePath = "/workspace/RepositoryWorkspaceCoordinator.kt",
                    sizeBytes = 1480,
                    lastModifiedEpochMillis = 1,
                    mimeType = "text/plain",
                    kind = BrowserFileKind.TEXT,
                    language = "Kotlin",
                    text = "suspend fun cloneRepository() {\n    // Native Git workspace\n}\n",
                    truncated = false,
                    lineCount = 3,
                ),
                value = TextFieldValue(
                    text = "suspend fun cloneRepository() {\n    // Native Git workspace\n}\n",
                    selection = TextRange(12),
                ),
                loading = false,
                dirty = true,
                canUndo = true,
                gitAvailable = true,
                indentStyle = EditorIndentStyle.SPACES_4,
            ),
            contentPadding = PaddingValues(0.dp),
            onBack = {},
            onValueChange = {},
            onUndo = {},
            onRedo = {},
            onToggleSearch = {},
            onSearchQueryChange = {},
            onReplacementChange = {},
            onMatchCaseChange = {},
            onNextMatch = {},
            onPreviousMatch = {},
            onReplaceCurrent = {},
            onReplaceAll = {},
            onInsertIndent = {},
            onIndentStyleChange = {},
            onToggleIntelligence = {},
            onSelectSymbol = {},
            onFormat = {},
            onSave = {},
            onDiff = {},
            onCommit = {},
        )
    }

    @Test
    fun captureGitWorkbench() = capture("git-workbench", AppThemeMode.DARK) {
        Column(modifier = Modifier.fillMaxSize()) {
            RepositorySummaryCard(gitState)
            PullCard(
                remote = "origin",
                strategy = GitPullStrategy.MERGE,
                busy = false,
                conflictCount = 0,
                rebaseInProgress = false,
                onStrategyChange = {},
                onPull = {},
            )
            PushCard(
                state = gitState,
                remote = "origin",
                pushTarget = "feature/mobile-workspace",
                onPushTargetChange = {},
                onPush = {},
                onForceWithLease = {},
            )
        }
    }

    @Test
    fun capturePullRequest() = capture("pull-request", AppThemeMode.DARK) {
        val pull = PullRequestSummary(
            id = 12,
            nodeId = "PR_12",
            number = 35,
            title = "Phase S P1 product quality",
            body = "Settings, localization, durable work and performance hardening.",
            state = "open",
            draft = false,
            locked = false,
            merged = false,
            mergeable = true,
            mergeableState = "clean",
            author = PullRequestUser("Gh0stDeveloper", null),
            head = PullRequestRef(
                "Gh0stDeveloper:phase-s",
                "phase-s",
                "aaaaaaa",
                "Gh0stDeveloper/Nexora-Git",
            ),
            base = PullRequestRef(
                "Gh0stDeveloper:main",
                "main",
                "bbbbbbb",
                "Gh0stDeveloper/Nexora-Git",
            ),
            comments = 4,
            reviewComments = 2,
            commits = 24,
            additions = 820,
            deletions = 140,
            changedFiles = 18,
            createdAt = "2026-10-07T20:00:00Z",
            updatedAt = "2026-10-07T20:30:00Z",
            closedAt = null,
            mergedAt = null,
            htmlUrl = "https://github.com/Gh0stDeveloper/Nexora-Git/pull/35",
        )
        PullRequestDetailContent(
            state = PullRequestDetailUiState(
                owner = "Gh0stDeveloper",
                repository = "Nexora-Git",
                number = 35,
                pullRequest = pull,
                repositoryDetails = details,
                checks = listOf(
                    PullRequestCheckRun(
                        id = 10,
                        name = "Android CI",
                        status = "completed",
                        conclusion = "success",
                        detailsUrl = null,
                        startedAt = null,
                        completedAt = null,
                    ),
                    PullRequestCheckRun(
                        id = 11,
                        name = "CodeQL",
                        status = "completed",
                        conclusion = "success",
                        detailsUrl = null,
                        startedAt = null,
                        completedAt = null,
                    ),
                ),
                loading = false,
            ),
            activeLogin = "Gh0stDeveloper",
            contentPadding = PaddingValues(0.dp),
            onBack = {},
            onRefresh = {},
            onUpdate = {},
            onSetState = {},
            onSetDraft = {},
            onSubmitReview = { _, _ -> },
            onCreateReviewComment = {},
            onUpdateReviewComment = { _, _ -> },
            onDeleteReviewComment = {},
            onMerge = {},
        )
    }

    @Test
    fun captureActions() = capture("actions", AppThemeMode.DARK) {
        ActionsContent(
            state = ActionsUiState(
                owner = "Gh0stDeveloper",
                repository = "Nexora-Git",
                workflows = listOf(
                    GitHubWorkflow(
                        id = 10,
                        nodeId = "W_android",
                        name = "Android CI",
                        path = ".github/workflows/android-ci.yml",
                        state = "active",
                        createdAt = null,
                        updatedAt = null,
                        htmlUrl = null,
                        badgeUrl = null,
                    ),
                    GitHubWorkflow(
                        id = 11,
                        nodeId = "W_codeql",
                        name = "CodeQL",
                        path = ".github/workflows/codeql.yml",
                        state = "active",
                        createdAt = null,
                        updatedAt = null,
                        htmlUrl = null,
                        badgeUrl = null,
                    ),
                ),
                runs = listOf(
                    GitHubWorkflowRun(
                        id = 20,
                        nodeId = "RUN_20",
                        name = "Android CI",
                        displayTitle = "Build, lint and unit test",
                        event = "push",
                        status = "completed",
                        conclusion = "success",
                        workflowId = 10,
                        runNumber = 655,
                        runAttempt = 1,
                        headBranch = "main",
                        headSha = "1330fd6d16e2",
                        htmlUrl = null,
                        createdAt = null,
                        updatedAt = null,
                        runStartedAt = null,
                        actorLogin = "Gh0stDeveloper",
                    ),
                ),
                loading = false,
            ),
            contentPadding = PaddingValues(0.dp),
            onBack = {},
            onRefresh = {},
            onSelectWorkflow = {},
            onSetStatus = {},
            onOpenRun = {},
            onDispatch = {},
        )
    }

    private fun homeState(): HomeUiState =
        HomeUiState(
            loading = false,
            recentWorkspaces = listOf(
                HomeWorkspaceSummary(
                    id = "workspace-1",
                    name = "Nexora-Git",
                    branch = "main",
                    changedPaths = 3,
                    conflictedPaths = 0,
                    ahead = 2,
                    behind = 0,
                    syncState = WorkspaceSyncState.READY,
                    lastOpenedAtEpochMillis = 1,
                ),
                HomeWorkspaceSummary(
                    id = "workspace-2",
                    name = "GhostNexoraBot",
                    branch = "feat/android-native-lite-base",
                    changedPaths = 0,
                    conflictedPaths = 0,
                    ahead = 0,
                    behind = 1,
                    syncState = WorkspaceSyncState.READY,
                    lastOpenedAtEpochMillis = 1,
                ),
            ),
            recentRepositories = listOf(repository),
            workspaceCount = 6,
            repositoryCount = 14,
        )

    private fun capture(
        name: String,
        mode: AppThemeMode,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            NexoraGitTheme(mode = mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()

        val bitmap = composeRule
            .onRoot(useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
        val directory = File(
            context.getExternalFilesDir(null),
            "screenshots",
        ).apply { mkdirs() }
        val output = File(directory, "$name.png")
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        check(output.length() > 0L)

        // Android Gradle instrumentation may uninstall the debug target after tests.
        // Stage genuine captures outside app-specific storage before test teardown.
        val staging = "/sdcard/Download/NexoraGitMarketing"
        val target = "$staging/$name.png"
        val command = "mkdir -p '$staging' && cp '${output.absolutePath}' '$target' " +
            "&& test -s '$target' && echo CAPTURE_EXPORTED"
        val response = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { reader -> reader.readText() }
        check("CAPTURE_EXPORTED" in response) {
            "Failed to stage screenshot $name: $response"
        }
    }
}
