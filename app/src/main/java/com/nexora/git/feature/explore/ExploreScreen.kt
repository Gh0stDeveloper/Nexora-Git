package com.nexora.git.feature.explore

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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.explore.ExploreCodeResult
import com.nexora.git.core.explore.ExploreSearchSection
import com.nexora.git.core.explore.ExploreUser
import com.nexora.git.core.repository.RepositorySummary

@Composable
fun ExploreScreen(
    contentPadding: PaddingValues,
    onOpenRepository: (String, String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    ExploreContent(
        state = state,
        contentPadding = contentPadding,
        onQueryChange = viewModel::setQuery,
        onSectionChange = viewModel::setSection,
        onSearch = viewModel::search,
        onOpenRepository = onOpenRepository,
        onOpenUser = { user ->
            uriHandler.openUri(user.htmlUrl)
        },
        onOpenCode = { result ->
            uriHandler.openUri(result.htmlUrl)
        },
    )
}

@Composable
internal fun ExploreContent(
    state: ExploreUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSectionChange: (ExploreSearchSection) -> Unit,
    onSearch: () -> Unit,
    onOpenRepository: (String, String) -> Unit,
    onOpenUser: (ExploreUser) -> Unit,
    onOpenCode: (ExploreCodeResult) -> Unit,
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
            Text(
                text = "Explore",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            Text(
                text = "Search GitHub repositories, users and code with your active account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("Search GitHub") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExploreSearchSection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = {
                            onSectionChange(section)
                        },
                        label = {
                            Text(section.label())
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (section) {
                                    ExploreSearchSection.REPOSITORIES ->
                                        Icons.Outlined.Source
                                    ExploreSearchSection.USERS ->
                                        Icons.Outlined.Person
                                    ExploreSearchSection.CODE ->
                                        Icons.Outlined.Code
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.query.isNotBlank() && !state.loading,
                onClick = onSearch,
            ) {
                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                    Text("Search")
                }
            }
        }

        state.errorMessage?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        when (state.section) {
            ExploreSearchSection.REPOSITORIES -> {
                items(
                    items = state.repositories,
                    key = { "repository-" + it.id },
                ) { repository ->
                    RepositorySearchCard(
                        repository = repository,
                        onOpen = {
                            onOpenRepository(
                                repository.ownerLogin,
                                repository.name,
                            )
                        },
                    )
                }
            }

            ExploreSearchSection.USERS -> {
                items(
                    items = state.users,
                    key = { "user-" + it.id },
                ) { user ->
                    UserSearchCard(
                        user = user,
                        onOpen = { onOpenUser(user) },
                    )
                }
            }

            ExploreSearchSection.CODE -> {
                items(
                    items = state.code,
                    key = {
                        it.repositoryFullName +
                            ":" + it.path + ":" + it.sha
                    },
                ) { result ->
                    CodeSearchCard(
                        result = result,
                        onOpen = { onOpenCode(result) },
                    )
                }
            }
        }

        if (
            state.hasSearched &&
            !state.loading &&
            state.errorMessage == null &&
            state.resultCount() == 0
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "No matching results.",
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositorySearchCard(
    repository: RepositorySummary,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        overflow = TextOverflow.Ellipsis,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            Text(
                text = buildString {
                    append(repository.language ?: "Repository")
                    append(" · ")
                    append(repository.stars)
                    append(" stars")
                    if (repository.privateRepository) {
                        append(" · Private")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserSearchCard(
    user: ExploreUser,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
            )
            Column {
                Text(
                    text = "@" + user.login,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = user.type ?: "GitHub user",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeSearchCard(
    result: ExploreCodeResult,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = result.repositoryFullName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = result.path,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = result.sha.take(12),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun ExploreSearchSection.label(): String =
    when (this) {
        ExploreSearchSection.REPOSITORIES -> "Repositories"
        ExploreSearchSection.USERS -> "Users"
        ExploreSearchSection.CODE -> "Code"
    }

private fun ExploreUiState.resultCount(): Int =
    when (section) {
        ExploreSearchSection.REPOSITORIES -> repositories.size
        ExploreSearchSection.USERS -> users.size
        ExploreSearchSection.CODE -> code.size
    }
