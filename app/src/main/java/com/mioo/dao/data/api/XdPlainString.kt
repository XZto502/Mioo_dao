package com.mioo.dao.data.api

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.Type

/**
 * X岛在串被删、没有饼干、冷却时仍返回 HTTP 200，正文是 JSON 字符串
 * （例如 `"该主题不存在"`），而不是对象或数组。
 */
fun extractQuotedJsonString(raw: String): String? {
    val trimmed = raw.trim().removePrefix("\uFEFF")
    if (trimmed.length < 2 || trimmed.length > 512) return null
    if (trimmed.first() != '"' || trimmed.last() != '"') return null
    return try {
        org.json.JSONArray("[$trimmed]").getString(0).takeIf { it.length <= 400 }
    } catch (_: Exception) {
        null
    }
}

/** 短文本提示。带「成功」或本身是 URL 的字符串留给原解析。 */
fun isXdBusinessPlainString(message: String): Boolean {
    val text = message.trim()
    if (text.isEmpty() || text.length > 400) return false
    if (text.contains("成功")) return false
    if (text.startsWith("http://") || text.startsWith("https://")) return false
    return true
}

/** 让列表接口在 Moshi 之前看到中文提示，而不是 JsonDataException。 */
class XdPlainStringConverterFactory(
    private val delegate: Converter.Factory
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val inner = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter { body ->
            val message = peekBusinessMessage(body)
            if (message != null) {
                body.close()
                throw IOException(message)
            }
            inner.convert(body)
        }
    }

    private fun peekBusinessMessage(body: ResponseBody): String? {
        val source = body.source()
        source.request(512)
        if (!source.exhausted()) return null
        val size = source.buffer.size
        if (size == 0L || size > 512L) return null
        val raw = source.buffer.clone().readUtf8()
        val message = extractQuotedJsonString(raw) ?: return null
        return message.takeIf { isXdBusinessPlainString(it) }
    }
}
