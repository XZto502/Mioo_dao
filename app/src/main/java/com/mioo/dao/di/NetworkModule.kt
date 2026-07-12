package com.mioo.dao.di

import android.content.Context
import com.mioo.dao.BuildConfig
import com.mioo.dao.data.api.CookieInterceptor
import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.api.GithubApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.nmb.best/api/"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieInterceptor: CookieInterceptor,
        @ApplicationContext appContext: Context
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(cookieInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Reuse TCP connections aggressively for API + image CDN traffic
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)

        // 50 MB HTTP response cache to reduce redundant API round-trips
        val cacheDir = File(appContext.cacheDir, "okhttp_cache")
        builder.cache(Cache(cacheDir, 50L * 1024 * 1024))

        // Only log request/response bodies in debug builds
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideXdApiService(retrofit: Retrofit): XdApiService {
        return retrofit.create(XdApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGithubApiService(
        moshi: Moshi
    ): GithubApiService {
        val cleanClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val githubRetrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(cleanClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return githubRetrofit.create(GithubApiService::class.java)
    }
}
