package com.nexora.git.core.platform

import com.nexora.git.core.network.GitHubApiConfig
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class GitHubUrlResolver @Inject constructor() {

    fun resolve(
        pathOrUrl: String,
        query: Map<String, String?>,
    ): HttpUrl {
        val base = GitHubApiConfig.REST_BASE_URL.toHttpUrl()

        val resolved = if (pathOrUrl.startsWith("https://")) {
            pathOrUrl.toHttpUrl().also(::requireOfficialApiHost)
        } else {
            val normalized = pathOrUrl.removePrefix("/")
            base.newBuilder()
                .addEncodedPathSegments(normalized)
                .build()
        }

        return resolved.newBuilder().apply {
            query.forEach { (name, value) ->
                if (value != null) {
                    setQueryParameter(name, value)
                }
            }
        }.build()
    }

    private fun requireOfficialApiHost(url: HttpUrl) {
        require(url.isHttps && url.host == "api.github.com") {
            "Only https://api.github.com URLs are allowed"
        }
    }
}
