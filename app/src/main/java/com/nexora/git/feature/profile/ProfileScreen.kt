package com.nexora.git.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.R
import com.nexora.git.core.auth.AuthAccountSummary
import com.nexora.git.core.social.GitHubOrganizationSummary
import com.nexora.git.core.social.GitHubSocialUser
import com.nexora.git.core.social.GitHubUserProfile
import com.nexora.git.core.social.StarredRepository
import com.nexora.git.core.social.UpdateGitHubProfileRequest

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    activeAccount: AuthAccountSummary,
    accounts: List<AuthAccountSummary>,
    operationInProgress: Boolean,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onOpenRepository: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(activeAccount.accountId) {
        viewModel.refresh()
    }

    var showEdit by rememberSaveable {
        mutableStateOf(false)
    }

    ProfileContent(
        state = state,
        contentPadding = contentPadding,
        activeAccount = activeAccount,
        accounts = accounts,
        authOperationInProgress = operationInProgress,
        onRefresh = viewModel::refresh,
        onSetSection = viewModel::setSection,
        onEditProfile = {
            showEdit = true
        },
        onSwitchAccount = onSwitchAccount,
        onAddAccount = onAddAccount,
        onSignOut = onSignOut,
        onOpenRepository = onOpenRepository,
        onOpenSettings = onOpenSettings,
        onUnstar = viewModel::unstar,
        onSetFollowing = viewModel::setFollowing,
    )

    if (showEdit) {
        state.profile?.let { profile ->
            EditProfileDialog(
                profile = profile,
                busy = state.operationInProgress,
                onDismiss = {
                    showEdit = false
                },
                onSave = {
                    showEdit = false
                    viewModel.updateProfile(it)
                },
            )
        }
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            },
            title = { Text("GitHub account") },
            text = { Text(message) },
        )
    }

    state.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccess,
            confirmButton = {
                TextButton(onClick = viewModel::dismissSuccess) {
                    Text("Done")
                }
            },
            title = { Text("Nexora Git") },
            text = { Text(message) },
        )
    }
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    contentPadding: PaddingValues,
    activeAccount: AuthAccountSummary,
    accounts: List<AuthAccountSummary>,
    authOperationInProgress: Boolean,
    onRefresh: () -> Unit,
    onSetSection: (ProfileSection) -> Unit,
    onEditProfile: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onOpenRepository: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onUnstar: (StarredRepository) -> Unit,
    onSetFollowing: (GitHubSocialUser, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "@" + activeAccount.login,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    enabled = !state.loading,
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh profile",
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileSection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = {
                            onSetSection(section)
                        },
                        label = {
                            Text(
                                when (section) {
                                    ProfileSection.PROFILE -> "Profile"
                                    ProfileSection.ORGANIZATIONS -> "Organizations"
                                    ProfileSection.STARRED -> "Starred"
                                    ProfileSection.FOLLOWERS -> "Followers"
                                    ProfileSection.FOLLOWING -> "Following"
                                },
                            )
                        },
                    )
                }
            }
        }

        if (state.loading && state.profile == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        when (state.section) {
            ProfileSection.PROFILE -> {
                state.profile?.let { profile ->
                    item {
                        ProfileSummaryCard(
                            profile = profile,
                            busy = state.operationInProgress,
                            onEdit = onEditProfile,
                        )
                    }
                }

                item {
                    AccountManagementCard(
                        activeAccount = activeAccount,
                        accounts = accounts,
                        busy =
                            authOperationInProgress ||
                                state.operationInProgress,
                        onSwitchAccount = onSwitchAccount,
                        onAddAccount = onAddAccount,
                        onSignOut = onSignOut,
                    )
                }
            }

            ProfileSection.ORGANIZATIONS -> {
                if (!state.loading && state.organizations.isEmpty()) {
                    item {
                        ProfileEmptyCard(
                            "No organizations are visible to this account.",
                        )
                    }
                } else {
                    items(
                        items = state.organizations,
                        key = { "org-" + it.id },
                    ) { organization ->
                        OrganizationCard(organization)
                    }
                }
            }

            ProfileSection.STARRED -> {
                if (!state.loading && state.starredRepositories.isEmpty()) {
                    item {
                        ProfileEmptyCard(
                            "No starred repositories.",
                        )
                    }
                } else {
                    items(
                        items = state.starredRepositories,
                        key = { "star-" + it.id },
                    ) { repository ->
                        StarredRepositoryCard(
                            repository = repository,
                            busy = state.operationInProgress,
                            onOpen = {
                                onOpenRepository(
                                    repository.ownerLogin,
                                    repository.name,
                                )
                            },
                            onUnstar = {
                                onUnstar(repository)
                            },
                        )
                    }
                }
            }

            ProfileSection.FOLLOWERS -> {
                if (!state.loading && state.followers.isEmpty()) {
                    item {
                        ProfileEmptyCard(
                            "No followers are visible.",
                        )
                    }
                } else {
                    items(
                        items = state.followers,
                        key = { "follower-" + it.id },
                    ) { user ->
                        SocialUserCard(
                            user = user,
                            following =
                                user.login.lowercase() in
                                    state.followingLogins,
                            busy = state.operationInProgress,
                            onToggle = { follow ->
                                onSetFollowing(
                                    user,
                                    follow,
                                )
                            },
                        )
                    }
                }
            }

            ProfileSection.FOLLOWING -> {
                if (!state.loading && state.following.isEmpty()) {
                    item {
                        ProfileEmptyCard(
                            "This account is not following anyone.",
                        )
                    }
                } else {
                    items(
                        items = state.following,
                        key = { "following-" + it.id },
                    ) { user ->
                        SocialUserCard(
                            user = user,
                            following = true,
                            busy = state.operationInProgress,
                            onToggle = { follow ->
                                onSetFollowing(
                                    user,
                                    follow,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    profile: GitHubUserProfile,
    busy: Boolean,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = profile.name
                    ?.takeIf(String::isNotBlank)
                    ?: profile.login,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "@" + profile.login,
                color = MaterialTheme.colorScheme.primary,
            )

            profile.bio?.takeIf(String::isNotBlank)?.let {
                Text(it)
            }

            val details = listOfNotNull(
                profile.company
                    ?.takeIf(String::isNotBlank)
                    ?.let { "Company: " + it },
                profile.location
                    ?.takeIf(String::isNotBlank)
                    ?.let { "Location: " + it },
                profile.blog
                    ?.takeIf(String::isNotBlank)
                    ?.let { "Website: " + it },
                profile.email
                    ?.takeIf(String::isNotBlank)
                    ?.let { "Email: " + it },
                profile.twitterUsername
                    ?.takeIf(String::isNotBlank)
                    ?.let { "X: @" + it },
            )

            details.forEach {
                Text(
                    text = it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfileMetric(
                    profile.publicRepos,
                    "repos",
                )
                ProfileMetric(
                    profile.followers,
                    "followers",
                )
                ProfileMetric(
                    profile.following,
                    "following",
                )
                ProfileMetric(
                    profile.publicGists,
                    "gists",
                )
            }

            OutlinedButton(
                enabled = !busy,
                onClick = onEdit,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                )
                Text("Edit GitHub profile")
            }
        }
    }
}

@Composable
private fun ProfileMetric(
    value: Int,
    label: String,
) {
    Text(
        text = value.toString() + " " + label,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun AccountManagementCard(
    activeAccount: AuthAccountSummary,
    accounts: List<AuthAccountSummary>,
    busy: Boolean,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.titleMedium,
            )

            accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled =
                                !busy &&
                                    account.accountId !=
                                        activeAccount.accountId,
                            onClick = {
                                onSwitchAccount(
                                    account.accountId,
                                )
                            },
                        )
                        .padding(vertical = 8.dp),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            account.name
                                ?: account.login,
                        )
                        Text(
                            text = "@" + account.login,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            style =
                                MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (
                        account.accountId ==
                            activeAccount.accountId
                    ) {
                        Text(
                            text = "Active",
                            color =
                                MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = onAddAccount,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                )
                Text("Add GitHub account")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = onSignOut,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                )
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun OrganizationCard(
    organization: GitHubOrganizationSummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = organization.login,
                style = MaterialTheme.typography.titleMedium,
            )
            organization.description
                ?.takeIf(String::isNotBlank)
                ?.let {
                    Text(
                        text = it,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}

@Composable
private fun StarredRepositoryCard(
    repository: StarredRepository,
    busy: Boolean,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = repository.fullName,
                style = MaterialTheme.typography.titleMedium,
            )

            repository.description
                ?.takeIf(String::isNotBlank)
                ?.let {
                    Text(
                        text = it,
                        maxLines = 3,
                        overflow =
                            TextOverflow.Ellipsis,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            Text(
                text = buildString {
                    repository.language?.let {
                        append(it)
                        append(" · ")
                    }
                    append(repository.stars)
                    append(" stars · ")
                    append(repository.forks)
                    append(" forks")
                },
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                enabled = !busy,
                onClick = onUnstar,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                )
                Text("Unstar")
            }
        }
    }
}

@Composable
private fun SocialUserCard(
    user: GitHubSocialUser,
    following: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "@" + user.login,
                    style = MaterialTheme.typography.titleMedium,
                )
                user.type?.let {
                    Text(
                        text = it,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    onToggle(!following)
                },
            ) {
                Text(
                    if (following) {
                        "Unfollow"
                    } else {
                        "Follow"
                    },
                )
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: GitHubUserProfile,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateGitHubProfileRequest) -> Unit,
) {
    var name by rememberSaveable(profile.id) {
        mutableStateOf(profile.name.orEmpty())
    }
    var bio by rememberSaveable(profile.id) {
        mutableStateOf(profile.bio.orEmpty())
    }
    var company by rememberSaveable(profile.id) {
        mutableStateOf(profile.company.orEmpty())
    }
    var location by rememberSaveable(profile.id) {
        mutableStateOf(profile.location.orEmpty())
    }
    var blog by rememberSaveable(profile.id) {
        mutableStateOf(profile.blog.orEmpty())
    }
    var twitter by rememberSaveable(profile.id) {
        mutableStateOf(
            profile.twitterUsername.orEmpty(),
        )
    }
    var hireable by rememberSaveable(profile.id) {
        mutableStateOf(profile.hireable == true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    onSave(
                        UpdateGitHubProfileRequest(
                            name = name,
                            bio = bio,
                            company = company,
                            location = location,
                            blog = blog,
                            twitterUsername =
                                twitter,
                            hireable = hireable,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("Edit GitHub profile")
        },
        text = {
            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = bio,
                        onValueChange = { bio = it },
                        minLines = 3,
                        maxLines = 5,
                        label = { Text("Bio") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Company") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = blog,
                        onValueChange = { blog = it },
                        label = { Text("Website") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = twitter,
                        onValueChange = { twitter = it },
                        label = {
                            Text("X / Twitter username")
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        Text("Available for hire")
                        Switch(
                            checked = hireable,
                            onCheckedChange = {
                                hireable = it
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ProfileEmptyCard(
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
