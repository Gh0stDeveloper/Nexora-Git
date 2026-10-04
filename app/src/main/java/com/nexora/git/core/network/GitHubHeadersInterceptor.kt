package com.nexora.git.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class GitHubHeadersInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", GitHubApiConfig.USER_AGENT)

        if (original.header("Accept") == null) {
            builder.header("Accept", GitHubApiConfig.DEFAULT_ACCEPT)
        }

        if (original.url.host == "api.github.com" &&
            original.url.encodedPath != "/graphql"
        ) {
            builder.header(
                "X-GitHub-Api-Version",
                GitHubApiConfig.REST_API_VERSION,
            )
        }

        return chain.proceed(builder.build())
    }
}
