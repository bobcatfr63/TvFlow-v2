package com.tvflow.app.domain

import java.net.URI
import java.net.URLDecoder

fun parseXtreamM3uUrl(input: String): XtreamCredentials? {
    val raw = input.trim()
    if (!raw.contains("get.php", ignoreCase = true)) return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme ?: return null
    val authority = uri.rawAuthority ?: return null
    val query = parseQuery(uri.rawQuery)
    val username = query["username"]?.takeIf { it.isNotBlank() } ?: return null
    val password = query["password"]?.takeIf { it.isNotBlank() } ?: return null
    val output = query["output"]?.lowercase()?.takeIf { it in setOf("ts", "m3u8") } ?: "ts"
    val parentPath = uri.path.substringBeforeLast("/get.php", "").trimEnd('/')
    return XtreamCredentials(
        serverUrl = "$scheme://$authority$parentPath",
        username = username,
        password = password,
        outputFormat = output
    )
}

private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split('&').mapNotNull { part ->
        val pair = part.split('=', limit = 2)
        if (pair.isEmpty()) null
        else {
            val key = URLDecoder.decode(pair[0], "UTF-8")
            val value = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            key to value
        }
    }.toMap()
}
