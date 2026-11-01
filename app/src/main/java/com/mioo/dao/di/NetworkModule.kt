package com.mioo.dao.di

import android.content.Context
import com.mioo.dao.BuildConfig
import com.mioo.dao.data.api.CookieInterceptor
import com.mioo.dao.data.api.CompatibilityDns
import com.mioo.dao.data.api.ForumWriteInterceptor
import com.mioo.dao.data.repository.SettingsRepository
import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.api.XdPlainStringConverterFactory
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
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
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
        settingsRepository: SettingsRepository,
        @ApplicationContext appContext: Context
    ): OkHttpClient {
        val compatible = { settingsRepository.settings.value.networkCompatibilityEnabled }
        // 独立客户端：解析服务不会收到饼干、帖子内容，也不递归使用自身解析。
        val resolverClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .cache(Cache(File(appContext.cacheDir, "secure_dns"), 2L * 1024 * 1024))
            .build()
        fun resolver(url: String, vararg bootstrap: String): Dns = DnsOverHttps.Builder()
            .client(resolverClient)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(false)
            .build()
        val dns = CompatibilityDns(compatible, listOf(
            resolver("https://dns.alidns.com/dns-query", "223.5.5.5", "223.6.6.6"),
            resolver("https://doh.pub/dns-query", "1.12.12.12", "120.53.53.53")
        ))
        val pool = okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES)
        var previousMode = compatible()
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor { chain ->
                synchronized(pool) {
                    val mode = compatible()
                    if (mode != previousMode) {
                        pool.evictAll()
                        previousMode = mode
                    }
                }
                chain.proceed(chain.request())
            }
            .addInterceptor(cookieInterceptor)
            .addInterceptor(ForumWriteInterceptor(compatible))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Reuse TCP connections for API traffic
            .connectionPool(pool)
            .retryOnConnectionFailure(true)

        // 50 MB HTTP response cache to reduce redundant API round-trips
        val cacheDir = File(appContext.cacheDir, "okhttp_cache")
        builder.cache(Cache(cacheDir, 50L * 1024 * 1024))

        // BODY logging serializes every JSON payload and image upload. Release stays NONE
        // so userhash cookies and upload bodies never hit logcat.
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
            } else {
                okhttp3.logging.HttpLoggingInterceptor.Level.NONE
            }
        }
        builder.addInterceptor(loggingInterceptor)

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
            .addConverterFactory(XdPlainStringConverterFactory(MoshiConverterFactory.create(moshi)))
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
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

        val githubRetrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(cleanClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return githubRetrofit.create(GithubApiService::class.java)
    }
}
