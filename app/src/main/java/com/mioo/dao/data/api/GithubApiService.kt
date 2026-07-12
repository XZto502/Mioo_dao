package com.mioo.dao.data.api

import com.mioo.dao.data.model.GithubRelease
import retrofit2.http.GET

interface GithubApiService {
    @GET("https://ghproxy.net/https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json")
    suspend fun getLatestRelease(): GithubRelease
}
