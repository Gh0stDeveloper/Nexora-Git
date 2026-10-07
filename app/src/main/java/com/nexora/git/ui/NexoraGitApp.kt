package com.nexora.git.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexora.git.R
import com.nexora.git.feature.activity.ActivityScreen
import com.nexora.git.feature.actions.ActionsScreen
import com.nexora.git.feature.actions.WorkflowRunDetailScreen
import com.nexora.git.feature.advanced.AdvancedGitHubScreen
import com.nexora.git.feature.auth.AuthUiState
import com.nexora.git.feature.auth.AuthViewModel
import com.nexora.git.feature.auth.LoginScreen
import com.nexora.git.feature.explore.ExploreScreen
import com.nexora.git.feature.editor.MobileEditorScreen
import com.nexora.git.feature.home.HomeScreen
import com.nexora.git.feature.onboarding.OnboardingAction
import com.nexora.git.feature.onboarding.OnboardingScreen
import com.nexora.git.feature.onboarding.OnboardingViewModel
import com.nexora.git.feature.files.CodeBrowserScreen
import com.nexora.git.feature.git.GitWorkspaceScreen
import com.nexora.git.feature.issues.IssueDetailScreen
import com.nexora.git.feature.issues.IssuesScreen
import com.nexora.git.feature.profile.ProfileScreen
import com.nexora.git.feature.pulls.PullRequestDetailScreen
import com.nexora.git.feature.pulls.PullRequestsScreen
import com.nexora.git.feature.repositories.RepositoriesScreen
import com.nexora.git.feature.repositories.RepositoryEntryAction
import com.nexora.git.feature.repositories.RepositoryDetailScreen
import com.nexora.git.feature.releases.ReleaseDetailScreen
import com.nexora.git.feature.releases.ReleasesScreen
import com.nexora.git.feature.settings.SettingsScreen
import com.nexora.git.ui.navigation.NexoraDestination

@Composable
fun NexoraGitApp(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.authorizationUri) {
        val uri = state.authorizationUri ?: return@LaunchedEffect

        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)

        authViewModel.authorizationUriConsumed()
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = authViewModel::clearError,
            confirmButton = {
                TextButton(onClick = authViewModel::clearError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            title = {
                Text(stringResource(R.string.auth_dialog_title))
            },
            text = {
                Text(message)
            },
        )
    }

    when {
        state.loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        !state.signedIn -> {
            LoginScreen(
                configured = state.configured,
                operationInProgress = state.operationInProgress,
                onSignIn = authViewModel::startSignIn,
                modifier = modifier,
            )
        }

        !onboardingState.loaded -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        !onboardingState.completed -> {
            OnboardingScreen(
                modifier = modifier,
                onAction = onboardingViewModel::complete,
            )
        }

        else -> {
            AuthenticatedNexoraGitApp(
                state = state,
                modifier = modifier,
                initialOnboardingAction = onboardingState.pendingAction,
                onInitialActionConsumed = onboardingViewModel::consumePendingAction,
                onSwitchAccount = authViewModel::switchAccount,
                onAddAccount = authViewModel::startSignIn,
                onSignOut = authViewModel::signOutActiveAccount,
            )
        }
    }
}

@Composable
private fun AuthenticatedNexoraGitApp(
    state: AuthUiState,
    initialOnboardingAction: OnboardingAction?,
    onInitialActionConsumed: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAccount = requireNotNull(state.activeAccount)
    val navController = rememberNavController()
    var repositoryEntryAction by remember {
        mutableStateOf(
            when (initialOnboardingAction) {
                OnboardingAction.CLONE -> RepositoryEntryAction.CLONE
                OnboardingAction.IMPORT -> RepositoryEntryAction.IMPORT
                OnboardingAction.CREATE -> RepositoryEntryAction.CREATE
                else -> RepositoryEntryAction.NONE
            },
        )
    }
    val startDestination = remember(initialOnboardingAction) {
        if (initialOnboardingAction == null) {
            NexoraDestination.HOME.route
        } else {
            NexoraDestination.REPOSITORIES.route
        }
    }
    val destinations = NexoraDestination.entries.filter { it.showInBottomBar }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (destinations.any { it.route == currentRoute }) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = {
                                Text(stringResource(destination.labelRes))
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(NexoraDestination.HOME.route) {
                HomeScreen(
                    contentPadding = paddingValues,
                    activeLogin = activeAccount.login,
                    onOpenRepositories = {
                        navController.navigate(
                            NexoraDestination.REPOSITORIES.route,
                        )
                    },
                    onOpenWorkspace = { workspaceId ->
                        navController.navigate("git/" + workspaceId)
                    },
                    onOpenRepository = { owner, name ->
                        navController.navigate(
                            "repository/" + owner + "/" + name,
                        )
                    },
                    onClone = {
                        repositoryEntryAction = RepositoryEntryAction.CLONE
                        navController.navigate(NexoraDestination.REPOSITORIES.route)
                    },
                    onImport = {
                        repositoryEntryAction = RepositoryEntryAction.IMPORT
                        navController.navigate(NexoraDestination.REPOSITORIES.route)
                    },
                    onCreate = {
                        repositoryEntryAction = RepositoryEntryAction.CREATE
                        navController.navigate(NexoraDestination.REPOSITORIES.route)
                    },
                    onOpenExplore = {
                        navController.navigate(
                            NexoraDestination.EXPLORE.route,
                        )
                    },
                    onOpenActivity = {
                        navController.navigate(
                            NexoraDestination.ACTIVITY.route,
                        )
                    },
                )
            }
            composable(NexoraDestination.EXPLORE.route) {
                ExploreScreen(
                    contentPadding = paddingValues,
                    onOpenRepository = { owner, name ->
                        navController.navigate(
                            "repository/" + owner + "/" + name,
                        )
                    },
                )
            }
            composable(NexoraDestination.REPOSITORIES.route) {
                RepositoriesScreen(
                    contentPadding = paddingValues,
                    initialAction = repositoryEntryAction,
                    onInitialActionConsumed = {
                        repositoryEntryAction = RepositoryEntryAction.NONE
                        onInitialActionConsumed()
                    },
                    onOpenRepository = { owner, name ->
                        navController.navigate(
                            "repository/" + owner + "/" + name,
                        )
                    },
                    onBrowseWorkspace = { workspaceId ->
                        navController.navigate(
                            "code/" + workspaceId,
                        )
                    },
                    onOpenGitWorkspace = { workspaceId ->
                        navController.navigate(
                            "git/" + workspaceId,
                        )
                    },
                )
            }

            composable(
                route = "git/{workspaceId}",
                arguments = listOf(
                    navArgument("workspaceId") {
                        type = NavType.StringType
                    },
                ),
            ) { gitEntry ->
                val workspaceId = gitEntry.arguments
                    ?.getString("workspaceId")
                    .orEmpty()

                GitWorkspaceScreen(
                    contentPadding = paddingValues,
                    activeAccount = activeAccount,
                    onBack = {
                        navController.popBackStack()
                    },
                    onEditFile = { relativePath ->
                        navController.navigate(
                            "editor/" +
                                workspaceId +
                                "?path=" +
                                Uri.encode(relativePath),
                        )
                    },
                )
            }

            composable(
                route = "code/{workspaceId}",
                arguments = listOf(
                    navArgument("workspaceId") {
                        type = NavType.StringType
                    },
                ),
            ) { codeEntry ->
                val workspaceId = codeEntry.arguments
                    ?.getString("workspaceId")
                    .orEmpty()

                CodeBrowserScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                    onEdit = { relativePath ->
                        navController.navigate(
                            "editor/" +
                                workspaceId +
                                "?path=" +
                                Uri.encode(relativePath),
                        )
                    },
                )
            }

            composable(
                route = "editor/{workspaceId}?path={path}",
                arguments = listOf(
                    navArgument("workspaceId") {
                        type = NavType.StringType
                    },
                    navArgument("path") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                MobileEditorScreen(
                    contentPadding = paddingValues,
                    activeAccount = activeAccount,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = "repository/{owner}/{name}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                ),
            ) {
                RepositoryDetailScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                    onOpenIssues = { owner, name ->
                        navController.navigate(
                            "issues/" + owner + "/" + name,
                        )
                    },
                    onOpenPullRequests = { owner, name ->
                        navController.navigate(
                            "pulls/" + owner + "/" + name,
                        )
                    },
                    onOpenActions = { owner, name ->
                        navController.navigate(
                            "actions/" + owner + "/" + name,
                        )
                    },
                    onOpenAdvancedGitHub = { owner, name ->
                        navController.navigate(
                            "advanced/" + owner + "/" + name,
                        )
                    },
                    onOpenReleases = { owner, name ->
                        navController.navigate(
                            "releases/" + owner + "/" + name,
                        )
                    },
                )
            }

            composable(
                route = "advanced/{owner}/{name}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                ),
            ) {
                AdvancedGitHubScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = "releases/{owner}/{name}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val owner = entry.arguments
                    ?.getString("owner")
                    .orEmpty()
                val name = entry.arguments
                    ?.getString("name")
                    .orEmpty()

                ReleasesScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                    onOpenRelease = { releaseId ->
                        navController.navigate(
                            "release/" + owner + "/" +
                                name + "/" + releaseId,
                        )
                    },
                )
            }

            composable(
                route = "release/{owner}/{name}/{releaseId}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                    navArgument("releaseId") {
                        type = NavType.LongType
                    },
                ),
            ) {
                ReleaseDetailScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = "actions/{owner}/{name}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val owner = entry.arguments
                    ?.getString("owner")
                    .orEmpty()
                val name = entry.arguments
                    ?.getString("name")
                    .orEmpty()

                ActionsScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                    onOpenRun = { runId ->
                        navController.navigate(
                            "actions-run/" + owner + "/" +
                                name + "/" + runId,
                        )
                    },
                )
            }

            composable(
                route = "actions-run/{owner}/{name}/{runId}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                    navArgument("runId") {
                        type = NavType.LongType
                    },
                ),
            ) {
                WorkflowRunDetailScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = "pulls/{owner}/{name}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val owner = entry.arguments
                    ?.getString("owner")
                    .orEmpty()
                val name = entry.arguments
                    ?.getString("name")
                    .orEmpty()

                PullRequestsScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                    onOpenPullRequest = { number ->
                        navController.navigate(
                            "pull/" + owner + "/" +
                                name + "/" + number,
                        )
                    },
                )
            }

            composable(
                route = "pull/{owner}/{name}/{number}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                    navArgument("number") {
                        type = NavType.IntType
                    },
                ),
            ) {
                PullRequestDetailScreen(
                    contentPadding = paddingValues,
                    activeLogin = activeAccount.login,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = "issues/{owner}/{name}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val owner = entry.arguments
                    ?.getString("owner")
                    .orEmpty()
                val name = entry.arguments
                    ?.getString("name")
                    .orEmpty()

                IssuesScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                    onOpenIssue = { number ->
                        navController.navigate(
                            "issue/" + owner + "/" +
                                name + "/" + number,
                        )
                    },
                )
            }

            composable(
                route = "issue/{owner}/{name}/{number}",
                arguments = listOf(
                    navArgument("owner") {
                        type = NavType.StringType
                    },
                    navArgument("name") {
                        type = NavType.StringType
                    },
                    navArgument("number") {
                        type = NavType.IntType
                    },
                ),
            ) {
                IssueDetailScreen(
                    contentPadding = paddingValues,
                    activeLogin = activeAccount.login,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(NexoraDestination.ACTIVITY.route) {
                ActivityScreen(
                    contentPadding = paddingValues,
                    activeLogin = activeAccount.login,
                    onOpenRepository = { owner, name ->
                        navController.navigate(
                            "repository/" + owner + "/" + name,
                        )
                    },
                )
            }
            composable(NexoraDestination.PROFILE.route) {
                ProfileScreen(
                    contentPadding = paddingValues,
                    activeAccount = activeAccount,
                    accounts = state.accounts,
                    operationInProgress = state.operationInProgress,
                    onSwitchAccount = onSwitchAccount,
                    onAddAccount = onAddAccount,
                    onSignOut = onSignOut,
                    onOpenRepository = { owner, name ->
                        navController.navigate(
                            "repository/" + owner + "/" + name,
                        )
                    },
                    onOpenSettings = {
                        navController.navigate(
                            NexoraDestination.SETTINGS.route,
                        )
                    },
                )
            }
            composable(NexoraDestination.SETTINGS.route) {
                SettingsScreen(
                    contentPadding = paddingValues,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
