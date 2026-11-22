package com.mioo.dao.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class NetworkDiagnosticsTest {

    private fun baseEnv(
        hasActiveNetwork: Boolean = true,
        allowThirdParty: Boolean = true,
        compatRequested: Boolean = false,
        compatEffective: Boolean = false,
        hasSpeakingCookie: Boolean = true
    ) = NetworkEnvironmentSnapshot(
        hasActiveNetwork = hasActiveNetwork,
        transportLabel = if (hasActiveNetwork) "Wi-Fi" else "未连接",
        allowThirdParty = allowThirdParty,
        networkCompatibilityRequested = compatRequested,
        networkCompatibilityEffective = compatEffective,
        hasSpeakingCookie = hasSpeakingCookie,
        hasAuthCookie = false
    )

    @Test
    fun classifyFailureMapsTransportStagesAccurately() {
        assertEquals(BlockedStage.DNS, classifyFailure(UnknownHostException("host"), ProbeStage.DNS).first)
        assertEquals(BlockedStage.TLS, classifyFailure(SSLHandshakeException("handshake"), ProbeStage.TLS).first)
        assertEquals(BlockedStage.TCP, classifyFailure(ConnectException("refused"), ProbeStage.TCP).first)
        assertEquals(BlockedStage.DNS, classifyFailure(SocketTimeoutException("timeout"), ProbeStage.DNS).first)
        assertEquals(BlockedStage.TCP, classifyFailure(SocketTimeoutException("timeout"), ProbeStage.TCP).first)
        assertEquals(BlockedStage.TLS, classifyFailure(SocketTimeoutException("timeout"), ProbeStage.TLS).first)
        assertEquals(BlockedStage.HTTP, classifyFailure(SocketTimeoutException("timeout"), ProbeStage.HTTP_RESPONSE).first)
        assertEquals(BlockedStage.TLS, classifyFailure(EOFException("eof"), ProbeStage.TLS).first)
        assertEquals(BlockedStage.HTTP, classifyFailure(EOFException("eof"), ProbeStage.BODY).first)
    }

    @Test
    fun classifyForumListBodyDistinguishesOkSlowAndBlocked() {
        val ok = classifyForumListBody(200, """[{"id":1,"name":"综合"}]""", 120L)
        assertEquals(ProbeStatus.OK, ok.status)

        val slow = classifyForumListBody(200, """[{"id":1,"name":"综合"}]""", 1800L)
        assertEquals(ProbeStatus.SLOW, slow.status)

        val htmlBlocked = classifyForumListBody(200, "<html>challenge</html>", 200L)
        assertEquals(ProbeStatus.BLOCKED, htmlBlocked.status)
        assertEquals(BlockedStage.RESPONSE, htmlBlocked.blockedStage)

        val cf403 = classifyForumListBody(403, "forbidden", 150L)
        assertEquals(ProbeStatus.BLOCKED, cf403.status)
        assertEquals(BlockedStage.HTTP, cf403.blockedStage)
    }

    @Test
    fun classifyTimelineBodyExtractsSampleImageAndDetectsAuthErrors() {
        val ok = classifyTimelineBody(
            httpCode = 200,
            body = """[{"id":123,"img":"2026-11-22/abc","ext":".jpg"}]""",
            totalMs = 180L,
            hasSpeakingCookie = true
        )
        assertEquals(ProbeStatus.OK, ok.status)
        assertEquals("/thumb/2026-11-22/abc.jpg", ok.sampleImagePath)

        val quotedNoCookie = classifyTimelineBody(
            httpCode = 200,
            body = "\"必须登录饼干后才能访问\"",
            totalMs = 90L,
            hasSpeakingCookie = false
        )
        assertEquals(ProbeStatus.WARN, quotedNoCookie.status)
        assertEquals(BlockedStage.AUTH, quotedNoCookie.blockedStage)
        assertNull(quotedNoCookie.sampleImagePath)

        val envelopeBadCookie = classifyTimelineBody(
            httpCode = 200,
            body = """{"success":false,"error":"饼干已失效"}""",
            totalMs = 90L,
            hasSpeakingCookie = true
        )
        assertEquals(ProbeStatus.WARN, envelopeBadCookie.status)
        assertEquals(BlockedStage.AUTH, envelopeBadCookie.blockedStage)
    }

    @Test
    fun classifyImageCdnAndWebHostResponsesHandleExpectedCodes() {
        assertEquals(ProbeStatus.OK, classifyImageCdnResponse(200, usedSampleImage = true, totalMs = 100L).status)
        assertEquals(ProbeStatus.OK, classifyImageCdnResponse(404, usedSampleImage = true, totalMs = 100L).status)
        assertEquals(ProbeStatus.BLOCKED, classifyImageCdnResponse(502, usedSampleImage = true, totalMs = 100L).status)

        assertEquals(ProbeStatus.OK, classifyImageCdnResponse(403, usedSampleImage = false, totalMs = 100L).status)
        assertEquals(ProbeStatus.BLOCKED, classifyImageCdnResponse(503, usedSampleImage = false, totalMs = 100L).status)

        assertEquals(ProbeStatus.OK, classifyWebHostResponse(302, totalMs = 80L).status)
        assertEquals(ProbeStatus.BLOCKED, classifyWebHostResponse(403, totalMs = 80L).status)
    }

    @Test
    fun summarizeDiagnosticsSuggestsCompatibilityModeOnlyWhenPermittedAndRelevant() {
        fun res(id: ProbeId, status: ProbeStatus, stage: BlockedStage = BlockedStage.NONE) =
            DiagnosticProbeResult(id = id, status = status, blockedStage = stage)

        val apiBlockedPermitted = summarizeDiagnostics(
            baseEnv(allowThirdParty = true, compatRequested = false),
            listOf(
                res(ProbeId.DNS_API, ProbeStatus.BLOCKED, BlockedStage.DNS),
                res(ProbeId.API_FORUM_LIST, ProbeStatus.BLOCKED, BlockedStage.DNS),
                res(ProbeId.API_CONTENT_AUTH, ProbeStatus.BLOCKED, BlockedStage.DNS),
                res(ProbeId.IMAGE_CDN, ProbeStatus.OK),
                res(ProbeId.WEB_PRIMARY, ProbeStatus.OK),
                res(ProbeId.WEB_ALT, ProbeStatus.OK)
            )
        )
        assertEquals(ProbeStatus.BLOCKED, apiBlockedPermitted.severity)
        assertTrue(apiBlockedPermitted.suggestEnableCompatibilityMode)

        val apiBlockedNoThirdParty = summarizeDiagnostics(
            baseEnv(allowThirdParty = false, compatRequested = false),
            listOf(
                res(ProbeId.DNS_API, ProbeStatus.OK),
                res(ProbeId.API_FORUM_LIST, ProbeStatus.BLOCKED, BlockedStage.TCP),
                res(ProbeId.API_CONTENT_AUTH, ProbeStatus.BLOCKED, BlockedStage.TCP),
                res(ProbeId.IMAGE_CDN, ProbeStatus.OK),
                res(ProbeId.WEB_PRIMARY, ProbeStatus.OK),
                res(ProbeId.WEB_ALT, ProbeStatus.OK)
            )
        )
        assertFalse(apiBlockedNoThirdParty.suggestEnableCompatibilityMode)

        val imageOnlyBlocked = summarizeDiagnostics(
            baseEnv(),
            listOf(
                res(ProbeId.DNS_API, ProbeStatus.OK),
                res(ProbeId.API_FORUM_LIST, ProbeStatus.OK),
                res(ProbeId.API_CONTENT_AUTH, ProbeStatus.OK),
                res(ProbeId.IMAGE_CDN, ProbeStatus.BLOCKED, BlockedStage.TLS),
                res(ProbeId.WEB_PRIMARY, ProbeStatus.OK),
                res(ProbeId.WEB_ALT, ProbeStatus.OK)
            )
        )
        assertEquals(ProbeStatus.BLOCKED, imageOnlyBlocked.severity)
        assertTrue(imageOnlyBlocked.title.contains("图床"))
        assertFalse(imageOnlyBlocked.suggestEnableCompatibilityMode)
    }

    @Test
    fun formatDiagnosticReportNeverLeaksSecrets() {
        val secretHash = "SECRET_USERHASH_DO_NOT_LEAK_998877"
        val env = baseEnv(hasSpeakingCookie = true)
        val results = listOf(
            DiagnosticProbeResult(
                id = ProbeId.API_FORUM_LIST,
                status = ProbeStatus.OK,
                timings = StageTimings(dnsMs = 12, tcpMs = 18, tlsMs = 25, ttfbMs = 30, totalMs = 90),
                httpCode = 200,
                protocol = "h2",
                tlsVersion = "TLSv1.3",
                resolvedIps = listOf("104.21.1.2"),
                headline = "通畅"
            )
        )
        val summary = summarizeDiagnostics(env, results)
        val report = formatDiagnosticReport(env, results, summary, "1.1.7", "15", "2026-11-22 12:00:00")

        assertTrue(report.contains("核心 API 接口"))
        assertTrue(report.contains("已配置"))
        assertFalse(report.contains(secretHash))
    }

    @Test
    fun runnerExecutesProbesEndToEndAndIsolatesCookiesToApiOnly() = runBlocking {
        val apiServer = MockWebServer()
        val imageServer = MockWebServer()
        val webServer = MockWebServer()

        val recordedHeaders = mutableListOf<Pair<String, String?>>()
        apiServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedHeaders += (request.path ?: "") to request.getHeader("Cookie")
                return when {
                    request.path?.startsWith("/api/getForumList") == true ->
                        MockResponse().setResponseCode(200).setBody("""[{"id":4,"name":"综合版1"}]""")
                    request.path?.startsWith("/api/timeline") == true ->
                        MockResponse().setResponseCode(200)
                            .setBody("""[{"id":1001,"img":"2026-11-22/sample1","ext":".jpg"}]""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        imageServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedHeaders += ("IMAGE:${request.path}") to request.getHeader("Cookie")
                return if (request.path == "/thumb/2026-11-22/sample1.jpg") {
                    MockResponse().setResponseCode(200).setBody("jpg-bytes")
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
        webServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedHeaders += ("WEB:${request.path}") to request.getHeader("Cookie")
                return MockResponse().setResponseCode(503)
            }
        }

        apiServer.start()
        imageServer.start()
        webServer.start()

        try {
            val runner = XIslandNetworkDiagnosticsRunner(
                cookieHeaderProvider = { "userhash=test_secret_hash_123" },
                endpoints = DiagnosticEndpoints(
                    apiDnsHost = "localhost",
                    apiBaseUrl = apiServer.url("/api/").toString(),
                    imageBaseUrl = imageServer.url("/").toString().trimEnd('/'),
                    webPrimaryUrl = webServer.url("/").toString(),
                    webAltUrl = webServer.url("/alt").toString()
                ),
                environmentProvider = { baseEnv(hasSpeakingCookie = true) },
                systemDns = object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        listOf(InetAddress.getByName("127.0.0.1"))
                }
            )

            val (_, results) = runner.runAll { }
            val byId = results.associateBy { it.id }

            assertEquals(ProbeStatus.OK, byId[ProbeId.DNS_API]?.status)
            assertEquals(ProbeStatus.OK, byId[ProbeId.API_FORUM_LIST]?.status)
            assertEquals(ProbeStatus.OK, byId[ProbeId.API_CONTENT_AUTH]?.status)
            assertEquals(ProbeStatus.OK, byId[ProbeId.IMAGE_CDN]?.status)
            assertEquals(ProbeStatus.BLOCKED, byId[ProbeId.WEB_PRIMARY]?.status)
            assertEquals(BlockedStage.HTTP, byId[ProbeId.WEB_PRIMARY]?.blockedStage)
            assertEquals(ProbeStatus.BLOCKED, byId[ProbeId.WEB_ALT]?.status)

            val apiTimelineCookie = recordedHeaders.firstOrNull { it.first.startsWith("/api/timeline") }?.second
            assertEquals("userhash=test_secret_hash_123", apiTimelineCookie)
            assertTrue(recordedHeaders.filter { it.first.startsWith("IMAGE:") }.all { it.second == null })
            assertTrue(recordedHeaders.filter { it.first.startsWith("WEB:") }.all { it.second == null })
            assertTrue(recordedHeaders.any { it.first == "IMAGE:/thumb/2026-11-22/sample1.jpg" })

            // Verify socket disconnect failure classification on single-probe retest
            webServer.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }
            val (_, single) = runner.runSingle(ProbeId.WEB_PRIMARY)
            assertEquals(ProbeStatus.BLOCKED, single.status)
            assertEquals(BlockedStage.HTTP, single.blockedStage)
        } finally {
            apiServer.shutdown()
            imageServer.shutdown()
            webServer.shutdown()
        }
    }
}
