package com.tvflow.app.ui.series

import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SeriesFailureDiagnosticsTest {
    @Test fun classifiesHttpStatusWithoutMessage() {
        val exception = HttpException(Response.error<String>(503, "down".toResponseBody()))
        assertEquals("http_503", safeSeriesFailureCategory(exception))
    }

    @Test fun classifiesNestedNetworkAndJsonFailures() {
        assertEquals("timeout", safeSeriesFailureCategory(RuntimeException("secret-url", SocketTimeoutException("secret-url"))))
        assertEquals("dns", safeSeriesFailureCategory(UnknownHostException("secret-url")))
        assertEquals("json", safeSeriesFailureCategory(JsonSyntaxException("secret-url")))
        assertEquals("io", safeSeriesFailureCategory(IOException("secret-url")))
    }

    @Test fun neverReturnsExceptionMessage() {
        val result = safeSeriesFailureCategory(IllegalArgumentException("https://example.invalid/secret"))
        assertEquals("unexpected", result)
        assertFalse(result.contains("secret"))
    }
}
