package com.tvflow.app.ui.login

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.io.BufferedReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class LocalPairingServer(
    private val context: Context,
    private val onM3uSubmitted: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null
    private val token = UUID.randomUUID().toString().replace("-", "")

    fun start(): String? {
        if (running.get()) return pairingUrl()
        val host = localIpv4Address() ?: return null
        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        serverSocket = socket
        running.set(true)
        worker = Thread {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { connection ->
                        connection.soTimeout = 5_000
                        handleClient(connection.getInputStream().bufferedReader(), connection.getOutputStream())
                    }
                }
            }
        }.apply {
            name = "TVFlow-Pairing"
            isDaemon = true
            start()
        }
        return "http://$host:${socket.localPort}/?token=$token"
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker = null
    }
    private fun pairingUrl(): String? {
        val socket = serverSocket ?: return null
        val host = localIpv4Address() ?: return null
        return "http://$host:${socket.localPort}/?token=$token"
    }

    private fun handleClient(reader: BufferedReader, output: OutputStream) {
        val requestLine = reader.readLine().orEmpty()
        val headers = buildMap<String, String> {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val separator = line.indexOf(':')
                if (separator > 0) put(
                    line.substring(0, separator).trim().lowercase(),
                    line.substring(separator + 1).trim()
                )
            }
        }
        when {
            requestLine.startsWith("GET ") -> serveForm(output, requestLine)
            requestLine.startsWith("POST /submit") -> {
                val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 8_192) ?: 0
                val body = CharArray(length)
                var read = 0
                while (read < length) {
                    val count = reader.read(body, read, length - read)
                    if (count <= 0) break
                    read += count
                }
                val form = parseForm(String(body, 0, read))
                val submittedToken = form["token"].orEmpty()
                val m3u = form["m3u"].orEmpty().trim()
                if (submittedToken != token || m3u.length !in 8..4_096) {
                    respond(output, 400, "Requête invalide")
                } else {
                    onM3uSubmitted(m3u)
                    respond(output, 200, successPage(), "text/html; charset=utf-8")
                }
            }
            else -> respond(output, 404, "Introuvable")
        }
    }

    private fun serveForm(output: OutputStream, requestLine: String) {
        val validToken = requestLine.substringAfter("token=", "").substringBefore(' ') == token
        if (!validToken) {
            respond(output, 403, "Lien expiré")
            return
        }
        respond(output, 200, formPage(), "text/html; charset=utf-8")
    }
    private fun formPage(): String = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>TVFlow</title>
        <style>
        body{font-family:system-ui;background:#101216;color:#fff;margin:0;padding:24px}
        main{max-width:680px;margin:auto}textarea,button{width:100%;box-sizing:border-box}
        textarea{min-height:160px;padding:14px;border-radius:12px;font-size:16px}
        button{margin-top:14px;padding:16px;border:0;border-radius:12px;font-size:18px}
        </style></head><body><main>
        <h1>TVFlow</h1><p>Colle ton lien Xtream/M3U ci-dessous.</p>
        <form method="post" action="/submit">
        <input type="hidden" name="token" value="$token">
        <textarea name="m3u" autofocus required></textarea>
        <button type="submit">Envoyer à la TV</button>
        </form></main></body></html>
    """.trimIndent()

    private fun successPage(): String = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>TVFlow</title></head><body style="font-family:system-ui;padding:24px">
        <h1>Connexion envoyée ✓</h1><p>Tu peux revenir sur la TV.</p>
        </body></html>
    """.trimIndent()
    private fun parseForm(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index < 0) null else {
                decode(pair.substring(0, index)) to decode(pair.substring(index + 1))
            }
        }.toMap()

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun respond(
        output: OutputStream,
        status: Int,
        body: String,
        contentType: String = "text/plain; charset=utf-8"
    ) {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: $contentType\r\n" +
            "Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(payload)
        output.flush()
    }
    private fun localIpv4Address(): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return null
        val properties = manager.getLinkProperties(network) ?: return null
        return properties.linkAddresses
            .asSequence()
            .map(LinkAddress::getAddress)
            .filterIsInstance<Inet4Address>()
            .firstOrNull { address -> !address.isLoopbackAddress && address.isSiteLocalAddress }
            ?.hostAddress
    }
}
