package com.nexora.git.core.explore

import com.nexora.git.core.repository.RepositoryJsonParser
import com.nexora.git.core.repository.RepositorySummary
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class ExploreJsonParser @Inject constructor(
    private val repositoryParser: RepositoryJsonParser,
) {

    fun repositories(body: String?): List<RepositorySummary> {
        val items = root(body).optJSONArray("items")
            ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                add(
                    repositoryParser.summary(
                        items.getJSONObject(index),
                    ),
                )
            }
        }
    }

    fun users(body: String?): List<ExploreUser> {
        val items = root(body).optJSONArray("items")
            ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    ExploreUser(
                        id = item.getLong("id"),
                        login = item.getString("login"),
                        avatarUrl = item.nullableString("avatar_url"),
                        htmlUrl = item.getString("html_url"),
                        type = item.nullableString("type"),
                        score = item.optDouble("score", 0.0),
                    ),
                )
            }
        }
    }

    fun code(body: String?): List<ExploreCodeResult> {
        val items = root(body).optJSONArray("items")
            ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val repository = item.getJSONObject("repository")
                val owner = repository.optJSONObject("owner")

                add(
                    ExploreCodeResult(
                        name = item.getString("name"),
                        path = item.getString("path"),
                        sha = item.optString("sha"),
                        htmlUrl = item.getString("html_url"),
                        repositoryFullName =
                            repository.getString("full_name"),
                        repositoryOwner =
                            owner?.optString("login").orEmpty(),
                        repositoryName =
                            repository.getString("name"),
                    ),
                )
            }
        }
    }

    private fun root(body: String?): JSONObject =
        JSONObject(
            requireNotNull(body) {
                "GitHub search response body is empty"
            },
        )

    private fun JSONObject.nullableString(
        key: String,
    ): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
            .takeIf { it.isNotBlank() && it != "null" }
    }
}
