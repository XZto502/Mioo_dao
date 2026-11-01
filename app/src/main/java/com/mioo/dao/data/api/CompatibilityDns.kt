package com.mioo.dao.data.api

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/** 仅改变官方 API 的解析结果；请求域名、SNI 和证书验证均保持原样。 */
class CompatibilityDns(
    private val enabled: () -> Boolean,
    private val secureResolvers: List<Dns>,
    private val system: Dns = Dns.SYSTEM,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) : Dns {
    private var cached: Pair<Long, List<InetAddress>>? = null

    @Synchronized
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname != API_HOST || !enabled()) return system.lookup(hostname)
        cached?.let { if (now() - it.first < 60_000) return it.second }
        val resolved = mutableListOf<InetAddress>()
        for (resolver in secureResolvers) {
            try {
                resolved += resolver.lookup(hostname)
            } catch (_: UnknownHostException) {
                // 一个加密解析服务不可达时尝试另一个，最后回退系统解析。
            }
        }
        if (resolved.isNotEmpty()) {
            val addresses = resolved.distinct()
            cached = now() to addresses
            return addresses
        }
        return system.lookup(hostname)
    }

    companion object { const val API_HOST = "api.nmb.best" }
}
