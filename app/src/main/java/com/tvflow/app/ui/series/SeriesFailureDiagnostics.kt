package com.tvflow.app.ui.series

import com.google.gson.JsonParseException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * Categorize series-load failures without exposing exception messages or credential-bearing URLs.
 * This is diagnostic metadata, not proof that the server caused the failure.
 */
internal fun safeSeriesFailureCategory(failure: Throwable): String {
    val chain = generateSequence(failure) { it.cause }.take(8).toList()
    for (cause in chain) {
        when (cause) {
            is HttpException -> return "http_${cause.code()}"
            is SocketTimeoutException -> return "timeout"
            is UnknownHostException -> return "dns"
            is ConnectException -> return "connect"
            is JsonParseException -> return "json"
        }
    }
    return if (chain.any { it is IOException }) "io" else "unexpected"
}
