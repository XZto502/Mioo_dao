package com.mioo.dao.data.api

import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.util.concurrent.TimeUnit

/** 已提交的 POST 不可重放；建立连接前仍可让 OkHttp 尝试其他解析地址。 */
class ForumWriteInterceptor(private val compatibilityEnabled: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != CompatibilityDns.API_HOST || original.method != "POST") {
            return chain.proceed(original)
        }
        val body = original.body ?: return chain.proceed(original)
        val request = original.newBuilder().method(original.method, object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
        }).build()
        val transport = if (compatibilityEnabled()) {
            chain.withWriteTimeout(60, TimeUnit.SECONDS).withReadTimeout(30, TimeUnit.SECONDS)
        } else chain
        return transport.proceed(request)
    }
}
