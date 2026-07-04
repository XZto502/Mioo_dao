package com.mioo.dao.data.model

sealed class XdResponse<out T> {
    data class Success<out T>(val data: T) : XdResponse<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val throwable: Throwable? = null
    ) : XdResponse<Nothing>()
}
