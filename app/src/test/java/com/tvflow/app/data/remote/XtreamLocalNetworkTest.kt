package com.tvflow.app.data.remote

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Loopback-only Xtream HTTP scenarios. No real account, server, or device is touched. */
class XtreamLocalNetworkTest {
    private fun withServer(handler: (String) -> Pair<Int, String>, test: suspend (XtreamApi, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/player_api.php") { exchange ->
            val action = exchange.requestURI.rawQuery.orEmpty().substringAfter("action=", "").substringBefore("&")
            val (status, body) = handler(action)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            val api = Retrofit.Builder().baseUrl(url)
                .client(OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(250, TimeUnit.MILLISECONDS).build())
                .addConverterFactory(GsonConverterFactory.create()).build().create(XtreamApi::class.java)
            runBlocking { test(api, url + "player_api.php") }
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test fun vodCanSucceedWhenSeriesEndpointReturns500() = withServer({ action ->
        when (action) {
            "get_vod_streams" -> 200 to """[{"stream_id":7,"name":"Synthetic film","category_id":"c"}]"""
            "get_series" -> 500 to """{"error":"temporary"}"""
            else -> 200 to "[]"
        }
    }) { api, url ->
        kotlinx.coroutines.coroutineScope {
            val films = async { runCatching { api.getVodStreams(url, "demo", "demo") } }
            val series = async { runCatching { api.getSeries(url, "demo", "demo") } }
            assertEquals(7, films.await().getOrThrow().single().streamId)
            assertTrue(series.await().exceptionOrNull() is HttpException)
        }
    }

    @Test fun serverTimeoutFailsWithoutReturningAnEmptyCatalog() = withServer({ action ->
        if (action == "get_vod_streams") Thread.sleep(1200)
        200 to "[]"
    }) { api, url ->
        val result = runCatching { api.getVodStreams(url, "demo", "demo") }
        assertTrue("Timeout should be an error, not a destructive empty result", result.isFailure)
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
    }

    @Test fun legitimateEmptyCatalogDeserializesAsEmptyList() = withServer({ _ -> 200 to "[]" }) { api, url ->
        assertTrue(api.getVodStreams(url, "demo", "demo").isEmpty())
    }

    @Test fun transientHttp500CanRecoverOnNextRequest() {
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        withServer({ action ->
            if (action == "get_vod_streams" && attempts.incrementAndGet() == 1) {
                500 to """{"error":"temporary"}"""
            } else {
                200 to """[{"stream_id":42,"name":"Recovered movie","category_id":"c"}]"""
            }
        }) { api, url ->
            val first = runCatching { api.getVodStreams(url, "demo", "demo") }
            assertTrue(first.exceptionOrNull() is HttpException)
            val recovered = api.getVodStreams(url, "demo", "demo")
            assertEquals(42, recovered.single().streamId)
            assertEquals(2, attempts.get())
        }
    }

    @Test fun cancellationStopsAnInFlightXtreamRequest() {
        val received = java.util.concurrent.CountDownLatch(1)
        withServer({ action ->
            if (action == "get_vod_streams") {
                received.countDown()
                Thread.sleep(1500)
            }
            200 to "[]"
        }) { api, url ->
            val request = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                api.getVodStreams(url, "demo", "demo")
            }
            assertTrue("Request never reached the fixture", received.await(2, TimeUnit.SECONDS))
            kotlinx.coroutines.withTimeout(1500) { request.cancelAndJoin() }
            assertTrue(request.isCancelled)
        }
    }

    @Test fun tenThousandFilmsDeserializeOverLoopback() {
        val json = buildString {
            append('[')
            for (id in 1..10_000) {
                if (id > 1) append(',')
                append("{\"stream_id\":").append(id)
                append(",\"name\":\"Synthetic ").append(id)
                append("\"}")
            }
            append(']')
        }
        withServer({ _ -> 200 to json }) { api, url ->
            val started = System.nanoTime()
            val items = api.getVodStreams(url, "demo", "demo")
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(10_000, items.size)
            assertEquals(10_000, items.last().streamId)
            println("XtreamLocalNetworkTest: 10000 films parsed in ${elapsedMs}ms")
        }
    }
}
