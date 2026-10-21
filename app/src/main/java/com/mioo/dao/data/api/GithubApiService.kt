package com.mioo.dao.data.api

import com.mioo.dao.data.model.GithubRelease
import retrofit2.http.GET
import retrofit2.http.Url

interface GithubApiService {
    @GET
    suspend fun getLatestRelease(@Url url: String): GithubRelease
}
