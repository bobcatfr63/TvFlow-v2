package com.tvflow.app.data.remote

import android.os.Debug
import android.os.SystemClock
import android.util.Log
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

/** Synthetic HTTP -> Retrofit -> Room pipeline, isolated from persistent TVFlow data. */
@RunWith(AndroidJUnit4::class)
class XtreamToRoomLargeDeviceTest {
    @Test fun downloadAndPersist100kFilmsWithoutTouchingProfiles() { runBlocking {
        val count = 100_000
        val payload = buildString {
            append('[')
            for (id in 1..count) {
                if (id != 1) append(',')
                append("{\"stream_id\":").append(id)
                    .append(",\"name\":\"Film ").append(id).append("\",\"category_id\":\"synthetic\"}")
            }
            append(']')
        }.toByteArray(Charsets.UTF_8)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val task = executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                consumeRequest(socket)
                val output = socket.getOutputStream()
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n").toByteArray())
                output.write(payload)
                output.flush()
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TVFlowDatabase::class.java).build()
        val profile = "isolated-network-room-stress"
        val mem = Debug.MemoryInfo()
        var maxPssKiB = 0
        val sampler = launch(Dispatchers.Default) {
            while (true) {
                Debug.getMemoryInfo(mem)
                maxPssKiB = maxOf(maxPssKiB, mem.totalPss)
                delay(25)
            }
        }
        try {
            val url = "http://127.0.0.1:${server.localPort}/"
            val api = Retrofit.Builder().baseUrl(url)
                .client(OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS).build())
                .addConverterFactory(GsonConverterFactory.create()).build()
                .create(XtreamApi::class.java)
            val start = SystemClock.elapsedRealtime()
            val items = api.getVodStreams(url + "player_api.php", "demo", "demo")
            val downloadedAt = SystemClock.elapsedRealtime()
            assertEquals(count, items.size)
            db.dao().replaceVodCatalog(profile,
                listOf(MediaCategoryEntity(profile, "synthetic", "Movies", "VOD", 0)),
                items.map { dto ->
                    VodItemEntity(profile, dto.streamId, dto.name, dto.categoryId.orEmpty(),
                        null, null, null, containerExtension = "mp4")
                })
            val savedAt = SystemClock.elapsedRealtime()
            assertEquals(count, db.dao().countVodItems(profile))
            assertEquals(count, db.dao().getAllVod(profile).last().streamId)
            task.get(5, TimeUnit.SECONDS)
            Log.i("CatalogPipeline", "count=$count httpParseMs=${downloadedAt-start} roomMs=${savedAt-downloadedAt} sampledPssKiB=$maxPssKiB")
            assertTrue("Memory sampling should run", maxPssKiB > 0)
        } finally {
            sampler.cancelAndJoin()
            db.close()
            server.close()
            executor.shutdownNow()
        }
    } }

    private fun consumeRequest(socket: Socket) {
        val input = socket.getInputStream()
        var matched = 0
        while (matched < 4) {
            val value = input.read()
            require(value >= 0) { "HTTP request ended prematurely" }
            matched = if (value == "\r\n\r\n"[matched].code) matched + 1
                else if (value == '\r'.code) 1 else 0
        }
    }
}
