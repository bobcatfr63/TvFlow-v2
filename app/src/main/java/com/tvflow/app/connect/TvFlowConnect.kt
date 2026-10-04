package com.tvflow.app.connect

import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

data class PlaybackTransfer(
    val kind: String,
    val mediaId: String,
    val title: String,
    val categoryId: String? = null,
    val extension: String? = null,
    val seriesId: Int? = null,
    val positionMs: Long = 0L
)

data class TvFlowTv(val name: String, val address: String)

object TvFlowConnect {
    private const val TAG = "TVFlowConnect"
    private const val DISCOVERY_PORT = 45891
    private const val COMMAND_PORT = 45892
    private const val DISCOVERY_MAGIC = "TVFLOW_DISCOVER_V1"
    private const val RESPONSE_PREFIX = "TVFLOW_TV_V1|"
    private const val COMMAND_MAGIC = "TVFLOW_PLAY_V1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _pendingTransfer = MutableStateFlow<PlaybackTransfer?>(null)
    val pendingTransfer: StateFlow<PlaybackTransfer?> = _pendingTransfer

    @Volatile private var receiverStarted = false

    fun startReceiverIfTv(context: Context) {
        val television = context.isTelevisionUi()
        Log.i(TAG, "Receiver check: tv=$television model=${Build.MANUFACTURER} ${Build.MODEL}")
        if (!television || receiverStarted) return
        synchronized(this) {
            if (receiverStarted) return
            receiverStarted = true
            Log.i(TAG, "Starting TVFlow receiver on UDP $DISCOVERY_PORT / TCP $COMMAND_PORT")
            scope.launch { discoveryLoop() }
            scope.launch { commandLoop(context.applicationContext) }
        }
    }

    fun consume(transfer: PlaybackTransfer) {
        if (_pendingTransfer.value == transfer) _pendingTransfer.value = null
    }

    suspend fun discoverTvs(context: Context, timeoutMs: Long = 1_500L): List<TvFlowTv> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, TvFlowTv>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 220
            val payload = DISCOVERY_MAGIC.toByteArray(Charsets.UTF_8)
            val targets = context.broadcastAddresses()
            repeat(2) {
                targets.forEach { target ->
                    runCatching { socket.send(DatagramPacket(payload, payload.size, target, DISCOVERY_PORT)) }
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(512)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    if (!response.address.isSiteLocalAddress) continue
                    val text = String(response.data, 0, response.length, Charsets.UTF_8)
                    if (!text.startsWith(RESPONSE_PREFIX)) continue
                    val address = response.address.hostAddress.orEmpty()
                    val name = text.removePrefix(RESPONSE_PREFIX).ifBlank { "TVFlow TV" }
                    found[address] = TvFlowTv(name, address)
                } catch (_: SocketTimeoutException) {
                }
            }
        }
        found.values.toList()
    }

    suspend fun send(tv: TvFlowTv, transfer: PlaybackTransfer): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(tv.address, COMMAND_PORT), 1_500)
                socket.soTimeout = 1_500
                PrintWriter(socket.getOutputStream(), true).use { it.println(transfer.toJson().toString()) }
                BufferedReader(InputStreamReader(socket.getInputStream())).use { it.readLine() == "OK" }
            }
        }.getOrDefault(false)
    }

    private suspend fun discoveryLoop() {
        while (currentCoroutineContext().isActive) {
            val result = runCatching {
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(DISCOVERY_PORT))
                    val buffer = ByteArray(512)
                    while (currentCoroutineContext().isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        if (!packet.address.isSiteLocalAddress) continue
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (text != DISCOVERY_MAGIC) continue
                        val replyText = RESPONSE_PREFIX + Build.MANUFACTURER + " " + Build.MODEL
                        val reply = replyText.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                    }
                }
            }
            result.exceptionOrNull()?.let {
                Log.w(TAG, "Discovery listener stopped", it)
                delay(1_000L)
            }
        }
    }

    private suspend fun commandLoop(context: Context) {
        while (currentCoroutineContext().isActive) {
            val result = runCatching {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(COMMAND_PORT))
                    while (currentCoroutineContext().isActive) {
                        val client = runCatching { server.accept() }.getOrNull() ?: continue
                        runCatching { handleClient(context, client) }
                    }
                }
            }
            result.exceptionOrNull()?.let { Log.w(TAG, "Command listener stopped", it) }
            if (currentCoroutineContext().isActive) delay(1_000L)
        }
    }

    private fun handleClient(context: Context, socket: Socket) {
        socket.use { client ->
            if (!client.inetAddress.isSiteLocalAddress) return
            client.soTimeout = 1_500
            val raw = BufferedReader(InputStreamReader(client.getInputStream())).readLine()
            val transfer = runCatching { parseTransfer(raw) }.getOrNull()
            val writer = PrintWriter(client.getOutputStream(), true)
            if (transfer != null) {
                Log.i(TAG, "Received ${transfer.kind}:${transfer.mediaId} from ${client.inetAddress.hostAddress}")
                _pendingTransfer.value = transfer
                bringTvFlowToFront(context)
                writer.println("OK")
            } else {
                writer.println("ERROR")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun bringTvFlowToFront(context: Context) {
        runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "$TAG:handoff"
            ).apply {
                setReferenceCounted(false)
                acquire(5_000L)
            }
        }.onFailure { Log.w(TAG, "Unable to wake TV for handoff", it) }

        runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return@runCatching
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            context.startActivity(launchIntent)
        }.onFailure { Log.w(TAG, "Unable to bring TVFlow to foreground", it) }
    }

    private fun PlaybackTransfer.toJson(): JSONObject = JSONObject()
        .put("magic", COMMAND_MAGIC)
        .put("kind", kind)
        .put("mediaId", mediaId)
        .put("title", title)
        .put("categoryId", categoryId)
        .put("extension", extension)
        .put("seriesId", seriesId)
        .put("positionMs", positionMs.coerceAtLeast(0L))

    private fun parseTransfer(raw: String?): PlaybackTransfer? {
        if (raw.isNullOrBlank()) return null
        val json = JSONObject(raw)
        if (json.optString("magic") != COMMAND_MAGIC) return null
        val kind = json.optString("kind")
        val mediaId = json.optString("mediaId")
        if (kind !in setOf("live", "vod", "episode") || mediaId.isBlank()) return null
        return PlaybackTransfer(
            kind = kind,
            mediaId = mediaId,
            title = json.optString("title").ifBlank { "TVFlow" },
            categoryId = json.optString("categoryId").takeIf { it.isNotBlank() },
            extension = json.optString("extension").takeIf { it.isNotBlank() },
            seriesId = json.optInt("seriesId", -1).takeIf { it >= 0 },
            positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L)
        )
    }
}

private fun Context.broadcastAddresses(): Set<InetAddress> {
    val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
    val manager = getSystemService(ConnectivityManager::class.java)
    val properties = manager.getLinkProperties(manager.activeNetwork) ?: return addresses
    properties.linkAddresses.forEach { link ->
        val address = link.address
        val prefix = link.prefixLength
        if (address !is Inet4Address || prefix !in 0..31) return@forEach
        val raw = address.address
        var ip = 0
        raw.forEach { byte -> ip = (ip shl 8) or (byte.toInt() and 0xff) }
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val broadcast = ip or mask.inv()
        val bytes = byteArrayOf(
            (broadcast ushr 24).toByte(),
            (broadcast ushr 16).toByte(),
            (broadcast ushr 8).toByte(),
            broadcast.toByte()
        )
        addresses += InetAddress.getByAddress(bytes)
    }
    return addresses
}

private fun Context.isTelevisionUi(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
