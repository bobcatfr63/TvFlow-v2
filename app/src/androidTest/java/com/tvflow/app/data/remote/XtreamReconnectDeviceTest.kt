package com.tvflow.app.data.remote

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Isolated device HTTP failure/recovery test. No real IPTV account or app profile is used. */
@RunWith(AndroidJUnit4::class)
class XtreamReconnectDeviceTest {
    @Test fun interruptedLargeDownloadRecoversOnNextRequest() { runBlocking {
        val json = buildString {
            append('[')
            for (id in 1..100_000) {
                if (id > 1) append(',')
                append("{\"stream_id\":").append(id).append(",\"name\":\"Synthetic ").append(id).append("\"}")
            }
            append(']')
        }.toByteArray(Charsets.UTF_8)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val serverJob = executor.submit {
            repeat(2) { attempt ->
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    consumeRequest(socket)
                    val out = socket.getOutputStream()
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${json.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    if (attempt == 0) {
                        out.write(json, 0, json.size / 3)
                    } else {
                        out.write(json)
                    }
                    out.flush()
                }
            }
        }
        try {
            val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
            val baseUrl = "http://127.0.0.1:${server.localPort}/"
            val api = Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(XtreamApi::class.java)
            val endpoint = baseUrl + "player_api.php"
            val failed = runCatching { api.getVodStreams(endpoint, "demo", "demo") }
            assertTrue("Truncated HTTP body must fail, never be interpreted as empty catalog", failed.isFailure)
            val mem = Debug.MemoryInfo()
            var maxPssKiB = 0
            val sampler = launch(Dispatchers.Default) {
                while (true) {
                    Debug.getMemoryInfo(mem)
                    maxPssKiB = maxOf(maxPssKiB, mem.totalPss)
                    delay(20)
                }
            }
            val started = SystemClock.elapsedRealtime()
            val items = try { api.getVodStreams(endpoint, "demo", "demo") }
                finally { sampler.cancelAndJoin() }
            val elapsed = SystemClock.elapsedRealtime() - started
            assertEquals(100_000, items.size)
            assertEquals(100_000, items.last().streamId)
            Log.i("CatalogReconnect", "truncated-response-rejected=true recovered=100000 elapsedMs=$elapsed sampledPssKiB=$maxPssKiB")
            serverJob.get(5, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    } }

    private fun consumeRequest(socket: Socket) {
        val input = socket.getInputStream()
        var matching = 0
        while (matching < 4) {
            val c = input.read()
            require(c != -1) { "Unexpected end of HTTP request" }
            matching = if (c == "\r\n\r\n"[matching].code) matching + 1
                else if (c == '\r'.code) 1 else 0
        }
    }
}
