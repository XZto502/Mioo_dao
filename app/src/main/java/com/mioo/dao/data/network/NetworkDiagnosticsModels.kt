package com.mioo.dao.data.network

import androidx.compose.runtime.Immutable
import com.squareup.moshi.JsonReader
import okhttp3.internal.http2.StreamResetException
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

const val DIAGNOSTIC_SLOW_THRESHOLD_MS = 1500L

enum class ProbeStatus {
    IDLE,
    RUNNING,
    OK,
    SLOW,
    WARN,
    BLOCKED
}

enum class ProbeStage {
    INIT,
    DNS,
    TCP,
    TLS,
    HTTP_REQUEST,
    HTTP_RESPONSE,
    BODY
}

enum class BlockedStage(val label: String) {
    NONE("无"),
    NO_NETWORK("无网络"),
    DNS("DNS 解析"),
    TCP("TCP 连接"),
    TLS("TLS 握手"),
    HTTP("HTTP 通信"),
    AUTH("饼干鉴权"),
    RESPONSE("响应解析")
}

enum class ProbeGroup {
    CORE_API,
    MEDIA_AND_WEB
}

enum class ProbeId(
    val title: String,
    val host: String,
    val endpointHint: String,
    val group: ProbeGroup
) {
    DNS_API("当前 DNS 解析", "api.nmb.best", "系统 DNS / 网络兼容加密 DNS", ProbeGroup.CORE_API),
    API_FORUM_LIST("核心 API 接口", "api.nmb.best", "/api/getForumList", ProbeGroup.CORE_API),
    API_CONTENT_AUTH("内容与饼干鉴权", "api.nmb.best", "/api/timeline?id=1&page=1", ProbeGroup.CORE_API),
    IMAGE_CDN("图床 CDN", "image.nmb.best", "/thumb/ 缩略图链路", ProbeGroup.MEDIA_AND_WEB),
    WEB_PRIMARY("网页主域名", "www.nmbxd1.com", "https://www.nmbxd1.com/", ProbeGroup.MEDIA_AND_WEB),
    WEB_ALT("网页备用域名", "www.nmbxd.com", "https://www.nmbxd.com/", ProbeGroup.MEDIA_AND_WEB)
}

@Immutable
data class StageTimings(
    val dnsMs: Long? = null,
    val tcpMs: Long? = null,
    val tlsMs: Long? = null,
    val ttfbMs: Long? = null,
    val totalMs: Long? = null
)

@Immutable
data class DiagnosticProbeResult(
    val id: ProbeId,
    val status: ProbeStatus = ProbeStatus.IDLE,
    val blockedStage: BlockedStage = BlockedStage.NONE,
    val timings: StageTimings = StageTimings(),
    val httpCode: Int? = null,
    val protocol: String? = null,
    val tlsVersion: String? = null,
    val resolvedIps: List<String> = emptyList(),
    val headline: String = "等待检测",
    val detail: String? = null
)

@Immutable
data class NetworkEnvironmentSnapshot(
    val hasActiveNetwork: Boolean = false,
    val transportLabel: String = "未连接",
    val vpnActive: Boolean = false,
    val proxyConfigured: Boolean = false,
    val allowThirdParty: Boolean = false,
    val networkCompatibilityRequested: Boolean = false,
    val networkCompatibilityEffective: Boolean = false,
    val hasSpeakingCookie: Boolean = false,
    val hasAuthCookie: Boolean = false
)

@Immutable
data class DiagnosticSummary(
    val severity: ProbeStatus,
    val title: String,
    val detail: String,
    val suggestEnableCompatibilityMode: Boolean = false
)

internal data class TimelineClassification(
    val status: ProbeStatus,
    val blockedStage: BlockedStage,
    val headline: String,
    val detail: String?,
    val sampleImagePath: String? = null
)

internal data class HttpClassification(
    val status: ProbeStatus,
    val blockedStage: BlockedStage,
    val headline: String,
    val detail: String?
)

internal fun classifyFailure(throwable: Throwable, lastStage: ProbeStage): Pair<BlockedStage, String> {
    val root = unwrapCause(throwable)
    return when (root) {
        is UnknownHostException -> BlockedStage.DNS to "DNS 解析失败，无法获取服务器地址"
        is SSLException -> BlockedStage.TLS to "TLS 握手失败或证书异常"
        is ConnectException -> BlockedStage.TCP to "TCP 连接被拒绝"
        is SocketTimeoutException, is InterruptedIOException -> when (lastStage) {
            ProbeStage.DNS -> BlockedStage.DNS to "DNS 解析超时"
            ProbeStage.TCP -> BlockedStage.TCP to "TCP 连接超时"
            ProbeStage.TLS -> BlockedStage.TLS to "TLS 握手超时"
            else -> BlockedStage.HTTP to "等待服务器响应超时"
        }
        is SocketException, is EOFException, is StreamResetException -> when (lastStage) {
            ProbeStage.TLS -> BlockedStage.TLS to "TLS 握手时连接被重置"
            ProbeStage.TCP -> BlockedStage.TCP to "TCP 连接被重置"
            else -> BlockedStage.HTTP to "通信连接被重置"
        }
        is IOException -> BlockedStage.HTTP to sanitizeExceptionMessage(root)
        else -> BlockedStage.HTTP to sanitizeExceptionMessage(root)
    }
}

private fun unwrapCause(throwable: Throwable): Throwable {
    var current: Throwable = throwable
    var depth = 0
    while (depth < 6) {
        val next = current.cause ?: break
        if (next === current) break
        if (current is UnknownHostException ||
            current is SSLException ||
            current is ConnectException ||
            current is SocketTimeoutException
        ) {
            return current
        }
        current = next
        depth++
    }
    return current
}

private fun sanitizeExceptionMessage(throwable: Throwable): String {
    val raw = throwable.message?.trim().orEmpty()
    if (raw.isEmpty()) return "网络通信异常"
    val sanitized = raw
        .replace(Regex("(?i)(userhash|auth|cookie)\\s*=\\s*[^;\\s]+"), "$1=***")
        .take(80)
    return "网络异常：$sanitized"
}

internal fun classifyForumListBody(
    httpCode: Int,
    body: String,
    totalMs: Long?
): HttpClassification {
    val trimmed = body.trim().removePrefix("\uFEFF")
    return when {
        httpCode == 200 && trimmed.startsWith("[") -> {
            val validArray = runCatching {
                JsonReader.of(Buffer().writeUtf8(trimmed)).use { reader ->
                    reader.beginArray()
                    true
                }
            }.getOrDefault(false)
            if (validArray) {
                if (totalMs != null && totalMs >= DIAGNOSTIC_SLOW_THRESHOLD_MS) {
                    HttpClassification(ProbeStatus.SLOW, BlockedStage.NONE, "可连通（延迟偏高）", "板块列表 JSON 解析正常")
                } else {
                    HttpClassification(ProbeStatus.OK, BlockedStage.NONE, "通畅", "板块列表响应正常")
                }
            } else {
                HttpClassification(ProbeStatus.BLOCKED, BlockedStage.RESPONSE, "响应解析失败", "返回内容不是有效 JSON 列表")
            }
        }
        httpCode == 403 || httpCode == 429 || httpCode == 503 ->
            HttpClassification(ProbeStatus.BLOCKED, BlockedStage.HTTP, "访问受限/风控 (HTTP $httpCode)", "服务器或防护节点拦截了请求")
        httpCode in 500..599 ->
            HttpClassification(ProbeStatus.BLOCKED, BlockedStage.HTTP, "服务端异常 (HTTP $httpCode)", "X 岛服务端暂不可用")
        httpCode == 200 ->
            HttpClassification(ProbeStatus.BLOCKED, BlockedStage.RESPONSE, "响应异常 (HTTP 200)", "返回了非 JSON 内容（可能被劫持或维护页）")
        else ->
            HttpClassification(ProbeStatus.BLOCKED, BlockedStage.HTTP, "响应异常 (HTTP $httpCode)", null)
    }
}

internal fun classifyTimelineBody(
    httpCode: Int,
    body: String,
    totalMs: Long?,
    hasSpeakingCookie: Boolean
): TimelineClassification {
    val trimmed = body.trim().removePrefix("\uFEFF")
    if (httpCode == 200) {
        val quotedError = parseQuotedJsonString(trimmed)
        if (quotedError != null) {
            return authWarnClassification(quotedError, hasSpeakingCookie)
        }
        if (trimmed.startsWith("{")) {
            val envelopeError = extractJsonErrorEnvelope(trimmed)
            if (envelopeError != null) {
                return authWarnClassification(envelopeError, hasSpeakingCookie)
            }
        }
        if (trimmed.startsWith("[")) {
            val parsed = parseTimelineArrayInfo(trimmed)
            if (parsed.validArray) {
                val sampleImage = parsed.sampleImagePath
                val status = if (totalMs != null && totalMs >= DIAGNOSTIC_SLOW_THRESHOLD_MS) {
                    ProbeStatus.SLOW
                } else {
                    ProbeStatus.OK
                }
                val headline = when {
                    status == ProbeStatus.SLOW && hasSpeakingCookie -> "可连通（延迟偏高）"
                    hasSpeakingCookie -> "通畅（饼干有效）"
                    else -> "通畅"
                }
                return TimelineClassification(
                    status = status,
                    blockedStage = BlockedStage.NONE,
                    headline = headline,
                    detail = if (sampleImage != null) "时间线列表正常，已取样图床路径" else "时间线列表正常",
                    sampleImagePath = sampleImage
                )
            }
        }
        return TimelineClassification(
            status = ProbeStatus.BLOCKED,
            blockedStage = BlockedStage.RESPONSE,
            headline = "返回格式异常",
            detail = "HTTP 200 但未返回有效时间线数据"
        )
    }
    return when {
        httpCode == 401 || httpCode == 403 -> {
            if (!hasSpeakingCookie) {
                TimelineClassification(
                    status = ProbeStatus.WARN,
                    blockedStage = BlockedStage.AUTH,
                    headline = "未配置饼干",
                    detail = "接口返回 HTTP $httpCode，链路可达但需要饼干鉴权"
                )
            } else {
                TimelineClassification(
                    status = ProbeStatus.BLOCKED,
                    blockedStage = BlockedStage.HTTP,
                    headline = "访问受限/风控 (HTTP $httpCode)",
                    detail = "服务器或防护节点拒绝了当前请求"
                )
            }
        }
        httpCode == 429 || httpCode == 503 || httpCode in 500..599 ->
            TimelineClassification(
                status = ProbeStatus.BLOCKED,
                blockedStage = BlockedStage.HTTP,
                headline = if (httpCode in 500..599 && httpCode != 503) "服务端异常 (HTTP $httpCode)" else "访问受限/风控 (HTTP $httpCode)",
                detail = null
            )
        else ->
            TimelineClassification(
                status = ProbeStatus.BLOCKED,
                blockedStage = BlockedStage.HTTP,
                headline = "响应异常 (HTTP $httpCode)",
                detail = null
            )
    }
}

private fun authWarnClassification(message: String, hasSpeakingCookie: Boolean): TimelineClassification {
    return if (!hasSpeakingCookie) {
        TimelineClassification(
            status = ProbeStatus.WARN,
            blockedStage = BlockedStage.AUTH,
            headline = "未配置饼干",
            detail = "接口返回：$message（链路正常，导入饼干后可正常看串）"
        )
    } else {
        TimelineClassification(
            status = ProbeStatus.WARN,
            blockedStage = BlockedStage.AUTH,
            headline = "饼干被拒或失效",
            detail = "接口返回：$message"
        )
    }
}

private data class TimelineArrayInfo(
    val validArray: Boolean,
    val sampleImagePath: String? = null
)

private fun parseQuotedJsonString(trimmed: String): String? {
    if (trimmed.length < 2 || trimmed.length > 512) return null
    if (trimmed.first() != '"' || trimmed.last() != '"') return null
    return runCatching {
        JsonReader.of(Buffer().writeUtf8(trimmed)).use { reader ->
            reader.isLenient = true
            reader.nextString().takeIf { it.isNotBlank() && it.length <= 400 }
        }
    }.getOrNull()
}

private fun extractJsonErrorEnvelope(trimmed: String): String? {
    return runCatching {
        var hasSuccessFalse = false
        var hasId = false
        var errorMsg: String? = null
        var msg: String? = null
        var message: String? = null
        JsonReader.of(Buffer().writeUtf8(trimmed)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "success" -> {
                        if (reader.peek() == JsonReader.Token.BOOLEAN && !reader.nextBoolean()) {
                            hasSuccessFalse = true
                        } else {
                            reader.skipValue()
                        }
                    }
                    "id" -> {
                        hasId = true
                        reader.skipValue()
                    }
                    "error" -> errorMsg = nextNonEmptyStringOrNull(reader)
                    "msg" -> msg = nextNonEmptyStringOrNull(reader)
                    "message" -> message = nextNonEmptyStringOrNull(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        val errorOnly = errorMsg != null && !hasId
        if (hasSuccessFalse || errorOnly) {
            errorMsg ?: msg ?: message ?: "需要饼干鉴权"
        } else {
            null
        }
    }.getOrNull()
}

private fun parseTimelineArrayInfo(trimmed: String): TimelineArrayInfo {
    return runCatching {
        var samplePath: String? = null
        JsonReader.of(Buffer().writeUtf8(trimmed)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                if (samplePath != null || reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                var img: String? = null
                var ext: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "img" -> img = nextNonEmptyStringOrNull(reader)
                        "ext" -> ext = nextNonEmptyStringOrNull(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val cleanImg = img?.trim()?.trimStart('/').orEmpty()
                val cleanExt = ext?.trim().orEmpty()
                if (cleanImg.isNotEmpty() && cleanExt.isNotEmpty()) {
                    val combined = "$cleanImg$cleanExt"
                    if (' ' !in combined && ".." !in combined && !combined.startsWith("http", ignoreCase = true)) {
                        samplePath = "/thumb/$combined"
                    }
                }
            }
            reader.endArray()
        }
        TimelineArrayInfo(validArray = true, sampleImagePath = samplePath)
    }.getOrElse { TimelineArrayInfo(validArray = false) }
}

private fun nextNonEmptyStringOrNull(reader: JsonReader): String? {
    return if (reader.peek() == JsonReader.Token.STRING) {
        reader.nextString().trim().takeIf { it.isNotEmpty() }
    } else {
        reader.skipValue()
        null
    }
}

internal fun classifyImageCdnResponse(
    httpCode: Int,
    usedSampleImage: Boolean,
    totalMs: Long?
): HttpClassification {
    val slow = totalMs != null && totalMs >= DIAGNOSTIC_SLOW_THRESHOLD_MS
    return if (usedSampleImage) {
        when {
            httpCode in 200..299 -> HttpClassification(
                status = if (slow) ProbeStatus.SLOW else ProbeStatus.OK,
                blockedStage = BlockedStage.NONE,
                headline = if (slow) "可连通（延迟偏高）" else "缩略图响应正常",
                detail = "实测缩略图拉取正常"
            )
            httpCode == 404 -> HttpClassification(
                status = if (slow) ProbeStatus.SLOW else ProbeStatus.OK,
                blockedStage = BlockedStage.NONE,
                headline = if (slow) "图床可达（延迟偏高）" else "图床可达（样本缩略图不存在）",
                detail = "CDN 节点正常响应"
            )
            httpCode == 403 || httpCode == 429 || httpCode in 500..599 -> HttpClassification(
                status = ProbeStatus.BLOCKED,
                blockedStage = BlockedStage.HTTP,
                headline = "图床受限或异常 (HTTP $httpCode)",
                detail = null
            )
            else -> HttpClassification(
                status = ProbeStatus.BLOCKED,
                blockedStage = BlockedStage.HTTP,
                headline = "图床响应异常 (HTTP $httpCode)",
                detail = null
            )
        }
    } else {
        when {
            httpCode in 200..404 -> HttpClassification(
                status = if (slow) ProbeStatus.SLOW else ProbeStatus.OK,
                blockedStage = BlockedStage.NONE,
                headline = if (slow) "图床主机可达（延迟偏高）" else "图床主机可达",
                detail = "未取到样本图，根目录返回 HTTP $httpCode 属正常"
            )
            else -> HttpClassification(
                status = ProbeStatus.BLOCKED,
                blockedStage = BlockedStage.HTTP,
                headline = "图床受限或异常 (HTTP $httpCode)",
                detail = null
            )
        }
    }
}

internal fun classifyWebHostResponse(
    httpCode: Int,
    totalMs: Long?
): HttpClassification {
    val slow = totalMs != null && totalMs >= DIAGNOSTIC_SLOW_THRESHOLD_MS
    return when {
        httpCode in 200..399 -> HttpClassification(
            status = if (slow) ProbeStatus.SLOW else ProbeStatus.OK,
            blockedStage = BlockedStage.NONE,
            headline = if (slow) "可连通（延迟偏高） (HTTP $httpCode)" else "通畅 (HTTP $httpCode)",
            detail = null
        )
        httpCode == 403 || httpCode == 429 || httpCode == 503 -> HttpClassification(
            status = ProbeStatus.BLOCKED,
            blockedStage = BlockedStage.HTTP,
            headline = "受限/风控 (HTTP $httpCode)",
            detail = "网页站点或防护节点拦截了请求"
        )
        httpCode in 500..599 -> HttpClassification(
            status = ProbeStatus.BLOCKED,
            blockedStage = BlockedStage.HTTP,
            headline = "服务端异常 (HTTP $httpCode)",
            detail = null
        )
        else -> HttpClassification(
            status = ProbeStatus.BLOCKED,
            blockedStage = BlockedStage.HTTP,
            headline = "响应异常 (HTTP $httpCode)",
            detail = null
        )
    }
}

fun summarizeDiagnostics(
    env: NetworkEnvironmentSnapshot,
    results: List<DiagnosticProbeResult>
): DiagnosticSummary {
    if (!env.hasActiveNetwork) {
        return DiagnosticSummary(
            severity = ProbeStatus.BLOCKED,
            title = "当前设备无可用网络",
            detail = "请检查 Wi-Fi 或移动数据连接后再试。"
        )
    }
    if (results.isEmpty() || results.all { it.status == ProbeStatus.IDLE }) {
        return DiagnosticSummary(
            severity = ProbeStatus.IDLE,
            title = "准备就绪",
            detail = "将依次检测 DNS 解析、API 接口、饼干鉴权、图床 CDN 与网页域名。"
        )
    }
    if (results.any { it.status == ProbeStatus.RUNNING }) {
        val doneCount = results.count { it.status != ProbeStatus.IDLE && it.status != ProbeStatus.RUNNING }
        return DiagnosticSummary(
            severity = ProbeStatus.RUNNING,
            title = "正在检测 X 岛通信…",
            detail = "已完成 $doneCount / ${results.size} 项"
        )
    }

    val byId = results.associateBy { it.id }
    val dns = byId[ProbeId.DNS_API]
    val forumApi = byId[ProbeId.API_FORUM_LIST]
    val authApi = byId[ProbeId.API_CONTENT_AUTH]
    val imageCdn = byId[ProbeId.IMAGE_CDN]
    val webPrimary = byId[ProbeId.WEB_PRIMARY]
    val webAlt = byId[ProbeId.WEB_ALT]

    val apiTransportBlocked = forumApi?.status == ProbeStatus.BLOCKED ||
        (authApi?.status == ProbeStatus.BLOCKED && authApi.blockedStage != BlockedStage.AUTH)
    val canSuggestCompat = apiTransportBlocked && env.allowThirdParty && !env.networkCompatibilityRequested

    val allBlocked = results.all { it.status == ProbeStatus.BLOCKED }
    if (allBlocked) {
        val vpnHint = if (env.vpnActive || env.proxyConfigured) "（当前开启了代理/VPN，可尝试切换或直连对比）" else ""
        return DiagnosticSummary(
            severity = ProbeStatus.BLOCKED,
            title = "X 岛全部链路受阻",
            detail = "所有检测项均未连通，可能是当前网络拦截或无外网权限$vpnHint。",
            suggestEnableCompatibilityMode = canSuggestCompat
        )
    }

    if (apiTransportBlocked) {
        val stage = forumApi?.blockedStage?.takeIf { it != BlockedStage.NONE }
            ?: authApi?.blockedStage?.takeIf { it != BlockedStage.NONE }
            ?: BlockedStage.HTTP
        val compatHint = when {
            canSuggestCompat -> "建议开启下方「网络兼容模式」后重新测试。"
            env.networkCompatibilityEffective -> "网络兼容模式已生效仍受阻，可尝试切换 Wi-Fi / 流量或检查代理。"
            else -> "可尝试切换网络环境（Wi-Fi / 移动数据）后再测。"
        }
        return DiagnosticSummary(
            severity = ProbeStatus.BLOCKED,
            title = "核心 API 通信受阻（${stage.label}）",
            detail = "看串、刷列表与发串会受到影响。$compatHint",
            suggestEnableCompatibilityMode = canSuggestCompat
        )
    }

    if (imageCdn?.status == ProbeStatus.BLOCKED) {
        return DiagnosticSummary(
            severity = ProbeStatus.BLOCKED,
            title = "图床 CDN 受阻，API 正常",
            detail = "文字列表可加载，但附图与缩略图可能无法显示。"
        )
    }

    val webBothBlocked = webPrimary?.status == ProbeStatus.BLOCKED && webAlt?.status == ProbeStatus.BLOCKED
    if (webBothBlocked) {
        return DiagnosticSummary(
            severity = ProbeStatus.WARN,
            title = "网页域名受阻，应用核心接口正常",
            detail = "App 内看串与看图不受影响，但外部网页直链可能无法打开。"
        )
    }

    if (authApi?.status == ProbeStatus.WARN) {
        val detail = if (!env.hasSpeakingCookie) {
            "网络链路正常，但尚未导入发言饼干，部分串详情或发串功能将受限。"
        } else {
            "网络链路正常，但当前选中饼干被接口拒绝，请检查饼干是否有效。"
        }
        return DiagnosticSummary(
            severity = ProbeStatus.WARN,
            title = authApi.headline,
            detail = detail
        )
    }

    if (dns?.status == ProbeStatus.WARN) {
        return DiagnosticSummary(
            severity = ProbeStatus.WARN,
            title = "DNS 解析存在异常或已回退",
            detail = dns.detail ?: "加密 DNS 未完全成功，已回退系统 DNS。"
        )
    }

    if (webPrimary?.status == ProbeStatus.BLOCKED || webAlt?.status == ProbeStatus.BLOCKED) {
        return DiagnosticSummary(
            severity = ProbeStatus.WARN,
            title = "部分网页域名受阻，核心接口正常",
            detail = "App 内刷串与看图正常，仅个别网页域名连通失败。"
        )
    }

    if (results.any { it.status == ProbeStatus.SLOW }) {
        return DiagnosticSummary(
            severity = ProbeStatus.SLOW,
            title = "可连通，但部分链路延迟偏高",
            detail = "各项接口均可访问，部分节点响应超过 ${DIAGNOSTIC_SLOW_THRESHOLD_MS} ms。"
        )
    }

    return DiagnosticSummary(
        severity = ProbeStatus.OK,
        title = "与 X 岛通信畅通",
        detail = "DNS、核心 API、图床 CDN 与网页域名均正常响应。"
    )
}

fun formatDiagnosticReport(
    env: NetworkEnvironmentSnapshot,
    results: List<DiagnosticProbeResult>,
    summary: DiagnosticSummary?,
    appVersion: String,
    androidRelease: String,
    timestampText: String?
): String = buildString {
    appendLine("== 喵岛 X 岛通信诊断报告 ==")
    appendLine("应用版本: v$appVersion (Android $androidRelease)")
    if (!timestampText.isNullOrBlank()) {
        appendLine("检测时间: $timestampText")
    }
    append("网络环境: ${env.transportLabel}")
    if (env.vpnActive) append(" + VPN")
    if (env.proxyConfigured) append(" + 系统代理")
    appendLine()
    val dnsMode = when {
        env.networkCompatibilityEffective -> "网络兼容模式（加密解析 DoH）"
        env.networkCompatibilityRequested && !env.allowThirdParty -> "系统 DNS（兼容模式已勾选但未授权第三方服务）"
        else -> "系统默认 DNS"
    }
    appendLine("解析模式: $dnsMode")
    appendLine("饼干状态: 发言饼干=${if (env.hasSpeakingCookie) "已配置" else "未配置"}，鉴权饼干=${if (env.hasAuthCookie) "已配置" else "未配置"}")
    if (summary != null) {
        appendLine("综合结论: ${summary.title} — ${summary.detail}")
    }
    appendLine()
    appendLine("-- 分项结果 --")
    results.forEach { r ->
        val total = r.timings.totalMs?.let { "${it} ms" } ?: "--"
        appendLine("[${statusLabel(r.status)}] ${r.id.title} (${r.id.host}) | $total | ${r.headline}")
        val stageParts = buildList {
            r.timings.dnsMs?.let { add("DNS ${it}ms") }
            r.timings.tcpMs?.let { add("TCP ${it}ms") }
            r.timings.tlsMs?.let { add("TLS ${it}ms") }
            r.timings.ttfbMs?.let { add("首字节 ${it}ms") }
        }
        val metaParts = buildList {
            r.httpCode?.let { add("HTTP $it") }
            r.protocol?.let { add(it) }
            r.tlsVersion?.let { add(it) }
            if (r.blockedStage != BlockedStage.NONE) add("阻塞=${r.blockedStage.label}")
        }
        if (stageParts.isNotEmpty() || metaParts.isNotEmpty()) {
            appendLine("  耗时/协议: ${(stageParts + metaParts).joinToString(" · ")}")
        }
        if (r.resolvedIps.isNotEmpty()) {
            appendLine("  解析IP: ${r.resolvedIps.joinToString(", ")}")
        }
        if (!r.detail.isNullOrBlank()) {
            appendLine("  说明: ${r.detail}")
        }
    }
}.trimEnd()

private fun statusLabel(status: ProbeStatus): String = when (status) {
    ProbeStatus.IDLE -> "未测"
    ProbeStatus.RUNNING -> "检测中"
    ProbeStatus.OK -> "通畅"
    ProbeStatus.SLOW -> "偏慢"
    ProbeStatus.WARN -> "注意"
    ProbeStatus.BLOCKED -> "受阻"
}
