package com.mioo.dao.data.api

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

class CompatibilityNetworkTest {
    private fun dns(lookup: (String) -> List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String) = lookup.invoke(hostname)
    }

    private val first = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
    private val second = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

    @Test fun disabledModeAndOtherHostsNeverUseSecureResolvers() {
        var enabled = false
        var secureCalls = 0
        val dns = CompatibilityDns({ enabled }, listOf(dns { secureCalls++; listOf(second) }),
            dns { listOf(first) })
        assertEquals(listOf(first), dns.lookup(CompatibilityDns.API_HOST))
        enabled = true
        assertEquals(listOf(first), dns.lookup("image.nmb.best"))
        assertEquals(0, secureCalls)
    }

    @Test fun independentResolversProvideAlternativeRoutesWithoutDuplicates() {
        val dns = CompatibilityDns({ true }, listOf(dns { listOf(first) },
            dns { listOf(second, first) }), dns { error("system DNS must not be used") })
        assertEquals(listOf(first, second), dns.lookup(CompatibilityDns.API_HOST))
    }

    @Test fun unavailableResolversFallBackToSystem() {
        val dns = CompatibilityDns({ true }, listOf(dns { throw UnknownHostException() }, dns { emptyList() }),
            dns { listOf(first) })
        assertEquals(listOf(first), dns.lookup(CompatibilityDns.API_HOST))
    }

    @Test fun cacheExpiresAndDisablingBypassesIt() {
        var clock = 0L
        var enabled = true
        var calls = 0
        val dns = CompatibilityDns({ enabled }, listOf(dns { calls++; listOf(second) }),
            dns { listOf(first) }, { clock })
        dns.lookup(CompatibilityDns.API_HOST)
        dns.lookup(CompatibilityDns.API_HOST)
        assertEquals(1, calls)
        clock = 60_001
        dns.lookup(CompatibilityDns.API_HOST)
        assertEquals(2, calls)
        enabled = false
        assertEquals(listOf(first), dns.lookup(CompatibilityDns.API_HOST))
    }

    @Test fun disconnectedResponseDoesNotResubmitThePost() {
        val server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .dns(dns { listOf(InetAddress.getByName("127.0.0.1")) })
            .addInterceptor(ForumWriteInterceptor { false })
            .build()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("must not be reached"))
            val url = server.url("/Home/Forum/doPostThread.html").newBuilder()
                .host(CompatibilityDns.API_HOST).build()
            try {
                client.newCall(Request.Builder().url(url).post("content=test".toRequestBody()).build())
                    .execute().use { fail("Expected connection failure") }
            } catch (_: IOException) { }
            assertEquals(1, server.requestCount)
            assertEquals("content=test", server.takeRequest().body.readUtf8())
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.shutdown()
        }
    }
}
