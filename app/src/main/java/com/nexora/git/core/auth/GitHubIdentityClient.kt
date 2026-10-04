package com.nexora.git.core.auth

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class GitHubIdentityClient @Inject constructor() {

    private val client = OkHttpClient()

    suspend fun getIdentity(accessToken: String): GitHubIdentity =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $accessToken")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("User-Agent", "Nexora-Git-Android")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw IOException(
                        "GitHub identity request failed with HTTP " + response.code,
                    )
                }

                val json = JSONObject(body)
                val id = json.optLong("id", -1L)
                val login = json.optString("login")

                if (id <= 0L || login.isBlank()) {
                    throw IOException("GitHub identity response is incomplete")
                }

                GitHubIdentity(
                    id = id,
                    login = login,
                    name = json.optString("name").takeIf { it.isNotBlank() },
                    avatarUrl = json.optString("avatar_url")
                        .takeIf { it.isNotBlank() },
                )
            }
        }
}
