package com.mioo.dao.data.api

import com.mioo.dao.data.model.GithubRelease
import retrofit2.http.GET

interface GithubApiService {
    @GET("repos/XZto502/Mioo_dao/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}
