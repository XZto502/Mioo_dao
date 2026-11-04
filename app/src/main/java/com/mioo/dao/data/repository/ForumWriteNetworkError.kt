package com.mioo.dao.data.repository

import java.net.SocketTimeoutException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun forumWriteNetworkError(error: Exception): String = when (error) {
    is UnknownHostException -> "域名解析失败，可在设置中尝试开启网络兼容模式。正文和图片已保留。"
    is SSLException -> "安全连接失败，可在设置中尝试开启网络兼容模式；请检查手机时间。正文和图片已保留。"
    is SocketTimeoutException, is SocketException ->
        "网络超时或连接中断，提交结果暂不确定。正文和图片已保留，请先查看是否已发出，再决定重试。可在设置中开启网络兼容模式。"
    else -> error.localizedMessage ?: "发送失败，正文和图片已保留"
}
