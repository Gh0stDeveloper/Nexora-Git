package com.nexora.git.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexora.git.feature.activity.ActivityScreen
import com.nexora.git.feature.explore.ExploreScreen
import com.nexora.git.feature.home.HomeScreen
import com.nexora.git.feature.profile.ProfileScreen
import com.nexora.git.feature.repositories.RepositoriesScreen
import com.nexora.git.ui.navigation.NexoraDestination

@Composable
fun NexoraGitApp(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val destinations = NexoraDestination.entries
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
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
                RepositoriesScreen(contentPadding = paddingValues)
            }
            composable(NexoraDestination.ACTIVITY.route) {
                ActivityScreen(contentPadding = paddingValues)
            }
            composable(NexoraDestination.PROFILE.route) {
                ProfileScreen(contentPadding = paddingValues)
            }
        }
    }
}
