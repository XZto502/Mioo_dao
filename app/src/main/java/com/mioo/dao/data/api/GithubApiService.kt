package com.mioo.dao.data.api

import com.mioo.dao.data.model.GithubRelease
import retrofit2.http.GET
import retrofit2.http.Headers

interface GithubApiService {
    @Headers(
        "User-Agent: MiooDao-Client",
        "Accept: application/vnd.github+json"
    )
    @GET("repos/XZto502/Mioo_dao/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}
