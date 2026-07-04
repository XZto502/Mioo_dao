package com.mioo.dao.data.api

import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface XdApiService {

    @GET("getCDNPath")
    suspend fun getCDNPath(): ResponseBody

    @GET("getForumList")
    suspend fun getForumList(): List<ForumGroup>

    @GET("getTimelineList")
    suspend fun getTimelineList(): List<ForumGroup>

    @GET("showf")
    suspend fun showf(
        @Query("id") id: String,
        @Query("page") page: Int
    ): List<Thread>

    @GET("timeline")
    suspend fun timeline(
        @Query("id") id: String,
        @Query("page") page: Int
    ): List<Thread>

    @GET("thread")
    suspend fun thread(
        @Query("id") id: String,
        @Query("page") page: Int
    ): Thread

    @GET("po")
    suspend fun po(
        @Query("id") id: String,
        @Query("page") page: Int
    ): Thread

    @GET("feed")
    suspend fun feed(
        @Query("uuid") uuid: String?,
        @Query("page") page: Int
    ): List<Thread>

    @GET("addFeed")
    suspend fun addFeed(
        @Query("uuid") uuid: String?,
        @Query("tid") tid: String
    ): ResponseBody

    @GET("delFeed")
    suspend fun delFeed(
        @Query("uuid") uuid: String?,
        @Query("tid") tid: String
    ): ResponseBody

    @GET("ref")
    suspend fun ref(
        @Query("id") id: String
    ): ResponseBody

    @Multipart
    @POST("/Home/Forum/doPostThread.html")
    suspend fun doPostThread(
        @Part("fid") fid: RequestBody,
        @Part("title") title: RequestBody?,
        @Part("name") name: RequestBody?,
        @Part("email") email: RequestBody?,
        @Part("content") content: RequestBody,
        @Part image: MultipartBody.Part?
    ): ResponseBody

    @Multipart
    @POST("/Home/Forum/doReplyThread.html")
    suspend fun doReplyThread(
        @Part("modo") modo: RequestBody?, // Sometimes used in forms
        @Part("title") title: RequestBody?,
        @Part("name") name: RequestBody?,
        @Part("email") email: RequestBody?,
        @Part("content") content: RequestBody,
        @Part("resto") parent: RequestBody, // The thread ID to reply to
        @Part image: MultipartBody.Part?
    ): ResponseBody

    @FormUrlEncoded
    @POST("/Home/Forum/doReplyThread.html")
    suspend fun doReplyThreadText(
        @Field("resto") resto: String,
        @Field("content") content: String,
        @Field("name") name: String?,
        @Field("title") title: String?,
        @Field("email") email: String?
    ): ResponseBody
}
