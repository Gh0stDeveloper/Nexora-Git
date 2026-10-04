package com.nexora.git.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

interface GitHubApi {

    @GET("rate_limit")
    suspend fun getRateLimit(): Response<ResponseBody>
}
