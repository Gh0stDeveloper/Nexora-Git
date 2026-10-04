package com.nexora.git.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexora.git.feature.activity.ActivityScreen
import com.nexora.git.feature.auth.AuthUiState
import com.nexora.git.feature.auth.AuthViewModel
import com.nexora.git.feature.auth.LoginScreen
import com.nexora.git.feature.explore.ExploreScreen
import com.nexora.git.feature.home.HomeScreen
import com.nexora.git.feature.profile.ProfileScreen
import com.nexora.git.feature.repositories.RepositoriesScreen
import com.nexora.git.feature.repositories.RepositoryDetailScreen
import com.nexora.git.ui.navigation.NexoraDestination

@Composable
fun NexoraGitApp(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
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
                    Text("OK")
                }
            },
            title = {
                Text("GitHub authentication")
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

        else -> {
            AuthenticatedNexoraGitApp(
                state = state,
                modifier = modifier,
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
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAccount = requireNotNull(state.activeAccount)
    val navController = rememberNavController()
    val destinations = NexoraDestination.entries
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
                                    contentDescription = destination.label,
                                )
                            },
                            label = {
                                Text(destination.label)
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NexoraDestination.HOME.route,
        ) {
            composable(NexoraDestination.HOME.route) {
                HomeScreen(contentPadding = paddingValues)
            }
            composable(NexoraDestination.EXPLORE.route) {
                ExploreScreen(contentPadding = paddingValues)
            }
            composable(NexoraDestination.REPOSITORIES.route) {
                RepositoriesScreen(
                    contentPadding = paddingValues,
                    onOpenRepository = { owner, name ->
                        navController.navigate(
                            "repository/" + owner + "/" + name,
                        )
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
                )
            }
            composable(NexoraDestination.ACTIVITY.route) {
                ActivityScreen(contentPadding = paddingValues)
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
                )
            }
        }
    }
}
