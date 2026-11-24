package com.bitwarden.network.util

import retrofit2.HttpException

/**
 * HTTP status of this failure, walking causes so a wrapped [HttpException] still counts.
 */
fun Throwable.httpStatusCode(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpException) return current.code()
        current = current.cause
    }
    return null
}
