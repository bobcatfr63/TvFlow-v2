package com.tvflow.app.ui.search

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class GroqIntentDeviceTest {
    private fun context(): Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("instrumentation_$name", mode)
    }
    private val token = "device-test-" + "x".repeat(40)
    private fun withServer(code: Int = 200, body: String = """{"normalized_query":"film action"}""", stallMs: Long = 0, stallFirstMs: Long = 0,
                           test: (Context, AtomicInteger) -> Unit) {
        val server = ServerSocket(0)
        val calls = AtomicInteger()
        val context = context()
        val config = AiGatewayConfig(context)
        config.save("http://127.0.0.1:${server.localPort}/v1/intent", token)
        val worker = thread(isDaemon = true) {
            runCatching {
                while (!server.isClosed) {
                    val connection = server.accept()
                    runCatching { connection.use { socket ->
                    socket.soTimeout = 2000
                    val input = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers += line
                    }
                    calls.incrementAndGet()
                    if (stallMs > 0) Thread.sleep(stallMs)
                    if (stallFirstMs > 0 && calls.get() == 1) Thread.sleep(stallFirstMs)
                    val authorized = headers.any { it.equals("Authorization: Bearer $token", true) }
                    val status = if (authorized) code else 401
                    val data = body.toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(data); flush()
                    }
                    } }
                }
            }
        }
        try { test(context, calls) } finally {
            server.close()
            worker.join(3000)
            config.clear()
        }
    }
    @Test fun authenticatedRewriteAndKeystoreStorage() = withServer { context, calls ->
        assertEquals(token, AiGatewayConfig(context).token())
        assertFalse(context.getSharedPreferences("ai_gateway", 0).getString("token", "")!!.contains(token))
        runBlocking { assertEquals("film action", GroqIntentClient(context, AiUsageCounter(context)).rewrite("Je veux un film action")) }
        assertEquals(1, calls.get())
    }
    @Test fun unavailableGatewayFallsBackAndCoolsDown() = withServer(code = 503) { context, calls ->
        val client = GroqIntentClient(context, AiUsageCounter(context))
        runBlocking {
            assertNull(client.rewrite("Je cherche un film français"))
            assertNull(client.rewrite("Une série de science fiction"))
        }
        assertEquals(1, calls.get())
    }
    @Test fun malformedResponseFallsBack() = withServer(body = "not-json") { context, _ ->
        runBlocking { assertNull(GroqIntentClient(context, AiUsageCounter(context)).rewrite("Un thriller français")) }
    }
    @Test fun oversizedResponseFallsBack() = withServer(body = "x".repeat(5000)) { context, _ ->
        runBlocking { assertNull(GroqIntentClient(context, AiUsageCounter(context)).rewrite("Un thriller français")) }
    }
    @Test fun stalledNetworkRespectsTotalDeadline() = withServer(stallMs = 6000) { context, _ ->
        val elapsed = measureTimeMillis {
            runBlocking { assertNull(GroqIntentClient(context, AiUsageCounter(context)).rewrite("Une série policière")) }
        }
        assertTrue("HTTP call must time out within its deadline", elapsed in 4500..6500)
    }
    @Test fun cancelledQueryDoesNotDisableNextQuery() = withServer(stallFirstMs = 1000) { context, calls ->
        runBlocking {
            val client = GroqIntentClient(context, AiUsageCounter(context))
            val oldQuery = launch { client.rewrite("Un film action precedent") }
            withTimeout(4000) { while (calls.get() == 0) delay(10) }
            oldQuery.cancelAndJoin()
            delay(100)
            assertEquals("film action", client.rewrite("Un nouveau film action"))
            assertEquals(2, calls.get())
        }
    }
    @Test fun absentConfigurationIsImmediate() {
        val context = context()
        AiGatewayConfig(context).clear()
        val elapsed = measureTimeMillis {
            runBlocking { assertNull(GroqIntentClient(context, AiUsageCounter(context)).rewrite("Une série policière")) }
        }
        assertTrue("Disabled gateway should not wait for network", elapsed < 500)
    }
    @Test fun groqSecretRejectedByAndroid() {
        try {
            AiGatewayConfig(context()).save("https://example.org/v1/intent", "gsk_" + "x".repeat(40))
            fail("Groq secret must never be stored on Android")
        } catch (_: IllegalArgumentException) { }
    }
}
