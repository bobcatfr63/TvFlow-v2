package com.tvflow.app.data.remote

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tvflow.app.data.local.MediaCategoryEntity
import com.tvflow.app.data.local.TVFlowDatabase
import com.tvflow.app.data.local.VodItemEntity
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Tests HTTP failure + Room retention with an isolated in-memory database and synthetic data. */
@RunWith(AndroidJUnit4::class)
class XtreamCacheRetentionDeviceTest {
    @Test fun truncatedRefreshKeepsExistingMoviesUntilSuccessfulRetry() { runBlocking {
        val original = jsonMovies(3, "Original")
        val updated = jsonMovies(5, "Updated")
        val server = ServerSocket(0, 3, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 15000
        val executor = Executors.newSingleThreadExecutor()
        val served = executor.submit {
            for (attempt in 0..2) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    consumeRequest(socket)
                    val body = if (attempt == 0) original else updated
                    val out = socket.getOutputStream()
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    if (attempt == 1) out.write(body, 0, body.size / 3) else out.write(body)
                    out.flush()
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TVFlowDatabase::class.java).build()
        val profile = "isolated-cache-retention"
        try {
            val base = "http://127.0.0.1:${server.localPort}/"
            val api = Retrofit.Builder().baseUrl(base)
                .client(OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
                .addConverterFactory(GsonConverterFactory.create()).build()
                .create(XtreamApi::class.java)
            val url = base + "player_api.php"
            suspend fun persist(items: List<VodStreamDto>) {
                database.dao().replaceVodCatalog(profile,
                    listOf(MediaCategoryEntity(profile, "synthetic", "Films", "VOD", 0)),
                    items.map { VodItemEntity(profile, it.streamId, it.name, "synthetic",
                        null, null, null, containerExtension = "mp4") })
            }
            persist(api.getVodStreams(url, "demo", "demo"))
            assertEquals(3, database.dao().countVodItems(profile))
            assertEquals("Original 1", database.dao().getAllVod(profile).first().name)

            // Failed HTTP fetch must not trigger a destructive replacement.
            val failure = runCatching { persist(api.getVodStreams(url, "demo", "demo")) }
            assertTrue("A truncated body must fail", failure.isFailure)
            assertEquals(3, database.dao().countVodItems(profile))
            assertEquals("Original 1", database.dao().getAllVod(profile).first().name)

            persist(api.getVodStreams(url, "demo", "demo"))
            assertEquals(5, database.dao().countVodItems(profile))
            assertEquals("Updated 1", database.dao().getAllVod(profile).first().name)
            served.get(5, TimeUnit.SECONDS)
        } finally {
            database.close()
            server.close()
            executor.shutdownNow()
        }
    } }

    private fun jsonMovies(count: Int, label: String): ByteArray = buildString {
        append('[')
        for (id in 1..count) {
            if (id > 1) append(',')
            append("{\"stream_id\":").append(id)
                .append(",\"name\":\"").append(label).append(' ').append(id)
                .append("\",\"category_id\":\"synthetic\"}")
        }
        append(']')
    }.toByteArray(Charsets.UTF_8)

    private fun consumeRequest(socket: Socket) {
        val input = socket.getInputStream()
        var matched = 0
        while (matched < 4) {
            val next = input.read()
            require(next >= 0) { "HTTP request ended prematurely" }
            matched = if (next == "\r\n\r\n"[matched].code) matched + 1
                else if (next == '\r'.code) 1 else 0
        }
    }
}
