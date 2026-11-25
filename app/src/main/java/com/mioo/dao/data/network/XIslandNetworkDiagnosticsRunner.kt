package com.mioo.dao.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mioo.dao.data.api.CompatibilityDns
import com.mioo.dao.data.api.CookieInterceptor
import com.mioo.dao.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class DiagnosticEndpoints(
    val apiDnsHost: String = CompatibilityDns.API_HOST,
    val apiBaseUrl: String = "https://api.nmb.best/api/",
    val imageBaseUrl: String = "https://image.nmb.best",
    val webPrimaryUrl: String = "https://www.nmbxd1.com/",
    val webAltUrl: String = "https://www.nmbxd.com/"
)

@Singleton
class XIslandNetworkDiagnosticsRunner internal constructor(
    private val cookieHeaderProvider: () -> String?,
    private val endpoints: DiagnosticEndpoints,
    private val environmentProvider: () -> NetworkEnvironmentSnapshot,
    private val systemDns: Dns = Dns.SYSTEM,
    private val compatResolversFactory: ((OkHttpClient) -> List<Pair<String, Dns>>)? = null
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ) : this(
        cookieHeaderProvider = { buildApiCookieHeader(settingsRepository) },
        endpoints = DiagnosticEndpoints(),
        environmentProvider = { readAndroidEnvironment(context, settingsRepository) },
        systemDns = Dns.SYSTEM,
        compatResolversFactory = null
    )

    fun inspectEnvironment(): NetworkEnvironmentSnapshot = environmentProvider()

    @Volatile
    private var lastSampleImagePath: String? = null

    suspend fun runAll(
        onProgress: suspend (DiagnosticProbeResult) -> Unit
    ): Pair<NetworkEnvironmentSnapshot, List<DiagnosticProbeResult>> = withContext(Dispatchers.IO) {
        val env = inspectEnvironment()
        if (!env.hasActiveNetwork) {
            val offlineResults = ProbeId.entries.map { id ->
                DiagnosticProbeResult(
                    id = id,
                    status = ProbeStatus.BLOCKED,
                    blockedStage = BlockedStage.NO_NETWORK,
                    headline = "当前设备无网络连接",
                    detail = "请检查 Wi-Fi 或移动数据连接"
                ).also { onProgress(it) }
            }
            return@withContext env to offlineResults
        }

        val emitMutex = Mutex()
        val resultsMap = LinkedHashMap<ProbeId, DiagnosticProbeResult>(ProbeId.entries.size)
        suspend fun record(result: DiagnosticProbeResult) {
            emitMutex.withLock {
                resultsMap[result.id] = result
                onProgress(result)
            }
        }

        val resources = createRunResources(env)
        try {
            coroutineScope {
                val dnsJob = launch {
                    record(executeDnsProbe(env, resources.compatResolvers))
                }
                val forumJob = launch {
                    record(executeForumListProbe(resources.baseClient, resources.apiDns))
                }
                val timelineAndImageJob = launch {
                    val (timelineResult, sampleImagePath) = executeTimelineProbe(
                        resources.baseClient,
                        resources.apiDns,
                        env
                    )
                    if (sampleImagePath != null) lastSampleImagePath = sampleImagePath
                    record(timelineResult)
                    val imageResult = executeImageCdnProbe(
                        resources.baseClient,
                        sampleImagePath ?: lastSampleImagePath
                    )
                    record(imageResult)
                }
                val webPrimaryJob = launch {
                    record(executeWebProbe(ProbeId.WEB_PRIMARY, endpoints.webPrimaryUrl, resources.baseClient))
                }
                val webAltJob = launch {
                    record(executeWebProbe(ProbeId.WEB_ALT, endpoints.webAltUrl, resources.baseClient))
                }

                dnsJob.join()
                forumJob.join()
                timelineAndImageJob.join()
                webPrimaryJob.join()
                webAltJob.join()
            }
        } finally {
            resources.close()
        }

        val ordered = ProbeId.entries.map { id ->
            resultsMap[id] ?: DiagnosticProbeResult(id = id, status = ProbeStatus.IDLE)
        }
        env to ordered
    }

    suspend fun runSingle(
        probeId: ProbeId,
        sampleImagePath: String? = null
    ): Pair<NetworkEnvironmentSnapshot, DiagnosticProbeResult> = withContext(Dispatchers.IO) {
        val env = inspectEnvironment()
        if (!env.hasActiveNetwork) {
            return@withContext env to DiagnosticProbeResult(
                id = probeId,
                status = ProbeStatus.BLOCKED,
                blockedStage = BlockedStage.NO_NETWORK,
                headline = "当前设备无网络连接",
                detail = "请检查 Wi-Fi 或移动数据连接"
            )
        }

        val resources = createRunResources(env)
        try {
            val result = when (probeId) {
                ProbeId.DNS_API -> executeDnsProbe(env, resources.compatResolvers)
                ProbeId.API_FORUM_LIST -> executeForumListProbe(resources.baseClient, resources.apiDns)
                ProbeId.API_CONTENT_AUTH -> {
                    val (res, sample) = executeTimelineProbe(resources.baseClient, resources.apiDns, env)
                    if (sample != null) lastSampleImagePath = sample
                    res
                }
                ProbeId.IMAGE_CDN -> executeImageCdnProbe(resources.baseClient, sampleImagePath ?: lastSampleImagePath)
                ProbeId.WEB_PRIMARY -> executeWebProbe(ProbeId.WEB_PRIMARY, endpoints.webPrimaryUrl, resources.baseClient)
                ProbeId.WEB_ALT -> executeWebProbe(ProbeId.WEB_ALT, endpoints.webAltUrl, resources.baseClient)
            }
            env to result
        } finally {
            resources.close()
        }
    }

    private class RunResources(
        val baseClient: OkHttpClient,
        val resolverClient: OkHttpClient?,
        val compatResolvers: List<Pair<String, Dns>>,
        val apiDns: Dns
    ) {
        fun close() {
            runCatching {
                baseClient.connectionPool.evictAll()
                baseClient.dispatcher.executorService.shutdown()
            }
            resolverClient?.let { rc ->
                runCatching {
                    rc.connectionPool.evictAll()
                    rc.dispatcher.executorService.shutdown()
                }
            }
        }
    }

    private fun createRunResources(env: NetworkEnvironmentSnapshot): RunResources {
        val baseClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .cache(null)
            .build()

        if (!env.networkCompatibilityEffective) {
            return RunResources(
                baseClient = baseClient,
                resolverClient = null,
                compatResolvers = emptyList(),
                apiDns = systemDns
            )
        }

        val resolverClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .cache(null)
            .build()

        val resolvers = compatResolversFactory?.invoke(resolverClient) ?: listOf(
            "阿里 DoH" to DnsOverHttps.Builder()
                .client(resolverClient)
                .url("https://dns.alidns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("223.5.5.5"),
                    InetAddress.getByName("223.6.6.6")
                )
                .includeIPv6(false)
                .build(),
            "腾讯 DoH" to DnsOverHttps.Builder()
                .client(resolverClient)
                .url("https://doh.pub/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.12.12.12"),
                    InetAddress.getByName("120.53.53.53")
                )
                .includeIPv6(false)
                .build()
        )
        val compatDns = CompatibilityDns(
            enabled = { true },
            secureResolvers = resolvers.map { it.second },
            system = systemDns
        )
        return RunResources(
            baseClient = baseClient,
            resolverClient = resolverClient,
            compatResolvers = resolvers,
            apiDns = compatDns
        )
    }

    private suspend fun executeDnsProbe(
        env: NetworkEnvironmentSnapshot,
        compatResolvers: List<Pair<String, Dns>>
    ): DiagnosticProbeResult {
        val startNs = System.nanoTime()
        return if (env.networkCompatibilityEffective && compatResolvers.isNotEmpty()) {
            val resolvedIps = LinkedHashSet<String>()
            val succeededNames = mutableListOf<String>()
            for ((name, resolver) in compatResolvers) {
                val lookup = withTimeoutOrNull(4_500L) {
                    runCatching { resolver.lookup(endpoints.apiDnsHost) }.getOrNull()
                }
                if (!lookup.isNullOrEmpty()) {
                    succeededNames += name
                    lookup.mapNotNull { it.hostAddress }.forEach { resolvedIps += it }
                }
            }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            if (resolvedIps.isNotEmpty()) {
                val slow = elapsedMs >= DIAGNOSTIC_SLOW_THRESHOLD_MS
                DiagnosticProbeResult(
                    id = ProbeId.DNS_API,
                    status = if (slow) ProbeStatus.SLOW else ProbeStatus.OK,
                    timings = StageTimings(dnsMs = elapsedMs, totalMs = elapsedMs),
                    resolvedIps = resolvedIps.take(4),
                    headline = if (slow) "加密解析可用（偏慢）" else "加密解析正常",
                    detail = "已通过 ${succeededNames.joinToString(" / ")} 解析到 ${resolvedIps.size} 个地址"
                )
            } else {
                val sysStartNs = System.nanoTime()
                val sysLookup = withTimeoutOrNull(4_500L) {
                    runCatching { systemDns.lookup(endpoints.apiDnsHost) }.getOrNull()
                }
                val totalMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                val sysMs = ((System.nanoTime() - sysStartNs) / 1_000_000L).coerceAtLeast(1L)
                if (!sysLookup.isNullOrEmpty()) {
                    DiagnosticProbeResult(
                        id = ProbeId.DNS_API,
                        status = ProbeStatus.WARN,
                        blockedStage = BlockedStage.DNS,
                        timings = StageTimings(dnsMs = sysMs, totalMs = totalMs),
                        resolvedIps = sysLookup.mapNotNull { it.hostAddress }.distinct().take(4),
                        headline = "加密 DNS 失败，已回退系统 DNS",
                        detail = "DoH 解析不可达，系统 DNS 返回 ${sysLookup.size} 个地址"
                    )
                } else {
                    DiagnosticProbeResult(
                        id = ProbeId.DNS_API,
                        status = ProbeStatus.BLOCKED,
                        blockedStage = BlockedStage.DNS,
                        timings = StageTimings(dnsMs = totalMs, totalMs = totalMs),
                        headline = "DNS 解析全部失败",
                        detail = "加密 DNS 与系统 DNS 均无法解析 ${endpoints.apiDnsHost}"
                    )
                }
            }
        } else {
            val lookupResult = withTimeoutOrNull(5_000L) {
                runCatching { systemDns.lookup(endpoints.apiDnsHost) }
            }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            when {
                lookupResult == null -> DiagnosticProbeResult(
                    id = ProbeId.DNS_API,
                    status = ProbeStatus.BLOCKED,
                    blockedStage = BlockedStage.DNS,
                    timings = StageTimings(dnsMs = elapsedMs, totalMs = elapsedMs),
                    headline = "系统 DNS 解析超时",
                    detail = "超过 5000 ms 未返回结果"
                )
                lookupResult.isFailure -> DiagnosticProbeResult(
                    id = ProbeId.DNS_API,
                    status = ProbeStatus.BLOCKED,
                    blockedStage = BlockedStage.DNS,
                    timings = StageTimings(dnsMs = elapsedMs, totalMs = elapsedMs),
                    headline = "系统 DNS 解析失败",
                    detail = "无法解析 ${endpoints.apiDnsHost}"
                )
                else -> {
                    val ips = lookupResult.getOrDefault(emptyList()).mapNotNull { it.hostAddress }.distinct()
                    if (ips.isEmpty()) {
                        DiagnosticProbeResult(
                            id = ProbeId.DNS_API,
                            status = ProbeStatus.BLOCKED,
                            blockedStage = BlockedStage.DNS,
                            timings = StageTimings(dnsMs = elapsedMs, totalMs = elapsedMs),
                            headline = "系统 DNS 返回空地址",
                            detail = "未获取到可用 IP"
                        )
                    } else {
                        val slow = elapsedMs >= DIAGNOSTIC_SLOW_THRESHOLD_MS
                        DiagnosticProbeResult(
                            id = ProbeId.DNS_API,
                            status = if (slow) ProbeStatus.SLOW else ProbeStatus.OK,
                            timings = StageTimings(dnsMs = elapsedMs, totalMs = elapsedMs),
                            resolvedIps = ips.take(4),
                            headline = if (slow) "系统 DNS 可用（偏慢）" else "系统 DNS 解析正常",
                            detail = "返回 ${ips.size} 个地址"
                        )
                    }
                }
            }
        }
    }

    private suspend fun executeForumListProbe(
        baseClient: OkHttpClient,
        apiDns: Dns
    ): DiagnosticProbeResult {
        val listener = DiagnosticEventListener()
        val client = baseClient.newBuilder()
            .dns(apiDns)
            .eventListener(listener)
            .build()
        val request = Request.Builder()
            .url("${endpoints.apiBaseUrl.trimEnd('/')}/getForumList")
            .header("User-Agent", "HavfunClient-Android")
            .header("Connection", "close")
            .header("Cache-Control", "no-cache")
            .get()
            .build()

        return try {
            client.newCall(request).await().use { response ->
                val body = readBoundedUtf8(response.body)
                val timings = listener.snapshotTimings()
                val cls = classifyForumListBody(response.code, body, timings.totalMs)
                DiagnosticProbeResult(
                    id = ProbeId.API_FORUM_LIST,
                    status = cls.status,
                    blockedStage = cls.blockedStage,
                    timings = timings,
                    httpCode = response.code,
                    protocol = listener.protocolName,
                    tlsVersion = listener.tlsVersionName,
                    resolvedIps = listener.resolvedIps,
                    headline = cls.headline,
                    detail = cls.detail
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val (stage, msg) = classifyFailure(e, listener.lastStage)
            DiagnosticProbeResult(
                id = ProbeId.API_FORUM_LIST,
                status = ProbeStatus.BLOCKED,
                blockedStage = stage,
                timings = listener.snapshotTimings(),
                protocol = listener.protocolName,
                tlsVersion = listener.tlsVersionName,
                resolvedIps = listener.resolvedIps,
                headline = msg,
                detail = "阻塞阶段：${stage.label}"
            )
        }
    }

    private suspend fun executeTimelineProbe(
        baseClient: OkHttpClient,
        apiDns: Dns,
        env: NetworkEnvironmentSnapshot
    ): Pair<DiagnosticProbeResult, String?> {
        val listener = DiagnosticEventListener()
        val client = baseClient.newBuilder()
            .dns(apiDns)
            .eventListener(listener)
            .build()
        val requestBuilder = Request.Builder()
            .url("${endpoints.apiBaseUrl.trimEnd('/')}/timeline?id=1&page=1")
            .header("User-Agent", "HavfunClient-Android")
            .header("Connection", "close")
            .header("Cache-Control", "no-cache")
            .get()

        val cookieHeader = cookieHeaderProvider()
        if (!cookieHeader.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        return try {
            client.newCall(requestBuilder.build()).await().use { response ->
                val body = readBoundedUtf8(response.body)
                val timings = listener.snapshotTimings()
                val cls = classifyTimelineBody(
                    httpCode = response.code,
                    body = body,
                    totalMs = timings.totalMs,
                    hasSpeakingCookie = env.hasSpeakingCookie
                )
                val result = DiagnosticProbeResult(
                    id = ProbeId.API_CONTENT_AUTH,
                    status = cls.status,
                    blockedStage = cls.blockedStage,
                    timings = timings,
                    httpCode = response.code,
                    protocol = listener.protocolName,
                    tlsVersion = listener.tlsVersionName,
                    resolvedIps = listener.resolvedIps,
                    headline = cls.headline,
                    detail = cls.detail
                )
                result to cls.sampleImagePath
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val (stage, msg) = classifyFailure(e, listener.lastStage)
            val result = DiagnosticProbeResult(
                id = ProbeId.API_CONTENT_AUTH,
                status = ProbeStatus.BLOCKED,
                blockedStage = stage,
                timings = listener.snapshotTimings(),
                protocol = listener.protocolName,
                tlsVersion = listener.tlsVersionName,
                resolvedIps = listener.resolvedIps,
                headline = msg,
                detail = "阻塞阶段：${stage.label}"
            )
            result to null
        }
    }

    private suspend fun executeImageCdnProbe(
        baseClient: OkHttpClient,
        sampleImagePath: String?
    ): DiagnosticProbeResult {
        val listener = DiagnosticEventListener()
        val client = baseClient.newBuilder()
            .dns(systemDns)
            .eventListener(listener)
            .build()
        val usedSample = !sampleImagePath.isNullOrBlank()
        val targetUrl = if (usedSample) {
            "${endpoints.imageBaseUrl.trimEnd('/')}/${sampleImagePath!!.trimStart('/')}"
        } else {
            "${endpoints.imageBaseUrl.trimEnd('/')}/"
        }
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .header("Connection", "close")
            .header("Cache-Control", "no-cache")
        if (usedSample) {
            requestBuilder.get()
        } else {
            requestBuilder.head()
        }

        return try {
            client.newCall(requestBuilder.build()).await().use { response ->
                if (usedSample) {
                    // Drain only a tiny slice or close immediately after headers/first bytes
                    runCatching { response.body?.source()?.request(4096L) }
                }
                val timings = listener.snapshotTimings()
                val cls = classifyImageCdnResponse(response.code, usedSample, timings.totalMs)
                DiagnosticProbeResult(
                    id = ProbeId.IMAGE_CDN,
                    status = cls.status,
                    blockedStage = cls.blockedStage,
                    timings = timings,
                    httpCode = response.code,
                    protocol = listener.protocolName,
                    tlsVersion = listener.tlsVersionName,
                    resolvedIps = listener.resolvedIps,
                    headline = cls.headline,
                    detail = cls.detail
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val (stage, msg) = classifyFailure(e, listener.lastStage)
            DiagnosticProbeResult(
                id = ProbeId.IMAGE_CDN,
                status = ProbeStatus.BLOCKED,
                blockedStage = stage,
                timings = listener.snapshotTimings(),
                protocol = listener.protocolName,
                tlsVersion = listener.tlsVersionName,
                resolvedIps = listener.resolvedIps,
                headline = msg,
                detail = "阻塞阶段：${stage.label}"
            )
        }
    }

    private suspend fun executeWebProbe(
        probeId: ProbeId,
        url: String,
        baseClient: OkHttpClient
    ): DiagnosticProbeResult {
        val listener = DiagnosticEventListener()
        val client = baseClient.newBuilder()
            .dns(systemDns)
            .eventListener(listener)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .header("Cache-Control", "no-cache")
            .head()
            .build()

        return try {
            client.newCall(request).await().use { response ->
                val timings = listener.snapshotTimings()
                val cls = classifyWebHostResponse(response.code, timings.totalMs)
                DiagnosticProbeResult(
                    id = probeId,
                    status = cls.status,
                    blockedStage = cls.blockedStage,
                    timings = timings,
                    httpCode = response.code,
                    protocol = listener.protocolName,
                    tlsVersion = listener.tlsVersionName,
                    resolvedIps = listener.resolvedIps,
                    headline = cls.headline,
                    detail = cls.detail
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val (stage, msg) = classifyFailure(e, listener.lastStage)
            DiagnosticProbeResult(
                id = probeId,
                status = ProbeStatus.BLOCKED,
                blockedStage = stage,
                timings = listener.snapshotTimings(),
                protocol = listener.protocolName,
                tlsVersion = listener.tlsVersionName,
                resolvedIps = listener.resolvedIps,
                headline = msg,
                detail = "阻塞阶段：${stage.label}"
            )
        }
    }

    private fun readBoundedUtf8(body: ResponseBody?, maxBytes: Long = 262_144L): String {
        if (body == null) return ""
        val source = body.source()
        source.request(maxBytes)
        val count = minOf(source.buffer.size, maxBytes)
        return source.buffer.clone().readString(count, Charsets.UTF_8)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }
}

private fun buildApiCookieHeader(settingsRepository: SettingsRepository): String? {
    val settings = settingsRepository.settings.value
    val activeCookie = settings.cookiesList.getOrNull(settings.selectedCookieIndex)
        ?.let { CookieInterceptor.extractHash(it) }
        ?: settings.cookie.takeIf { it.isNotBlank() }?.let { CookieInterceptor.extractHash(it) }
    val authCookie = CookieInterceptor.extractHash(settings.authCookie.takeIf { it.isNotBlank() })
    val parts = ArrayList<String>(2)
    if (!activeCookie.isNullOrBlank()) parts += "userhash=$activeCookie"
    if (!authCookie.isNullOrBlank()) parts += "auth=$authCookie"
    return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

private fun readAndroidEnvironment(
    context: Context,
    settingsRepository: SettingsRepository
): NetworkEnvironmentSnapshot {
    var hasActiveNetwork = false
    var transportLabel = "未连接"
    var vpnActive = false
    var proxyConfigured = false

    runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val linkProps = if (network != null) cm.getLinkProperties(network) else null
        hasActiveNetwork = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (caps != null) {
            vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            transportLabel = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
                vpnActive -> "VPN"
                hasActiveNetwork -> "其他网络"
                else -> "未连接"
            }
        }
        proxyConfigured = linkProps?.httpProxy != null ||
            !System.getProperty("http.proxyHost").isNullOrBlank() ||
            !System.getProperty("https.proxyHost").isNullOrBlank()
    }

    val settings = settingsRepository.settings.value
    val allowThirdParty = settingsRepository.thirdPartyConsent.value?.allowThirdPartyServices == true
    val compatRequested = settings.networkCompatibilityEnabled
    val compatEffective = allowThirdParty && compatRequested
    val activeCookie = settings.cookiesList.getOrNull(settings.selectedCookieIndex)
        ?.let { CookieInterceptor.extractHash(it) }
        ?: settings.cookie.takeIf { it.isNotBlank() }?.let { CookieInterceptor.extractHash(it) }
    val authCookie = CookieInterceptor.extractHash(settings.authCookie.takeIf { it.isNotBlank() })

    return NetworkEnvironmentSnapshot(
        hasActiveNetwork = hasActiveNetwork,
        transportLabel = transportLabel,
        vpnActive = vpnActive,
        proxyConfigured = proxyConfigured,
        allowThirdParty = allowThirdParty,
        networkCompatibilityRequested = compatRequested,
        networkCompatibilityEffective = compatEffective,
        hasSpeakingCookie = !activeCookie.isNullOrBlank(),
        hasAuthCookie = !authCookie.isNullOrBlank()
    )
}

private class DiagnosticEventListener : EventListener() {
    @Volatile var lastStage: ProbeStage = ProbeStage.INIT
    @Volatile private var callStartNs: Long? = null
    @Volatile private var dnsStartNs: Long? = null
    @Volatile private var dnsEndNs: Long? = null
    @Volatile private var connectStartNs: Long? = null
    @Volatile private var connectEndNs: Long? = null
    @Volatile private var secureStartNs: Long? = null
    @Volatile private var secureEndNs: Long? = null
    @Volatile private var reqStartNs: Long? = null
    @Volatile private var respHeadersStartNs: Long? = null
    @Volatile private var callEndNs: Long? = null
    @Volatile var protocolName: String? = null
    @Volatile var tlsVersionName: String? = null
    @Volatile var resolvedIps: List<String> = emptyList()

    override fun callStart(call: Call) {
        callStartNs = System.nanoTime()
        lastStage = ProbeStage.INIT
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartNs = System.nanoTime()
        lastStage = ProbeStage.DNS
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsEndNs = System.nanoTime()
        resolvedIps = inetAddressList.mapNotNull { it.hostAddress }.distinct().take(4)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartNs = System.nanoTime()
        lastStage = ProbeStage.TCP
    }

    override fun secureConnectStart(call: Call) {
        secureStartNs = System.nanoTime()
        lastStage = ProbeStage.TLS
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        secureEndNs = System.nanoTime()
        tlsVersionName = handshake?.tlsVersion?.javaName
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        connectEndNs = System.nanoTime()
        protocolName = protocol?.toString()
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        protocolName = connection.protocol().toString()
        tlsVersionName = connection.handshake()?.tlsVersion?.javaName ?: tlsVersionName
    }

    override fun requestHeadersStart(call: Call) {
        reqStartNs = System.nanoTime()
        lastStage = ProbeStage.HTTP_REQUEST
    }

    override fun responseHeadersStart(call: Call) {
        respHeadersStartNs = System.nanoTime()
        lastStage = ProbeStage.HTTP_RESPONSE
    }

    override fun responseBodyStart(call: Call) {
        lastStage = ProbeStage.BODY
    }

    override fun callEnd(call: Call) {
        callEndNs = System.nanoTime()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        callEndNs = System.nanoTime()
    }

    fun snapshotTimings(): StageTimings {
        val now = System.nanoTime()
        val dnsMs = diffMs(dnsStartNs, dnsEndNs)
        val tcpEnd = secureStartNs ?: connectEndNs
        val tcpMs = diffMs(connectStartNs, tcpEnd)
        val tlsMs = diffMs(secureStartNs, secureEndNs)
        val ttfbAnchor = reqStartNs ?: callStartNs
        val ttfbMs = diffMs(ttfbAnchor, respHeadersStartNs)
        val totalMs = diffMs(callStartNs, callEndNs ?: now)
        return StageTimings(
            dnsMs = dnsMs,
            tcpMs = tcpMs,
            tlsMs = tlsMs,
            ttfbMs = ttfbMs,
            totalMs = totalMs
        )
    }

    private fun diffMs(startNs: Long?, endNs: Long?): Long? {
        if (startNs == null || endNs == null) return null
        return ((endNs - startNs) / 1_000_000L).coerceAtLeast(0L)
    }
}
