package com.bobcatfr63.iptv

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object CrashReporter {
    private const val PREFS = "bobcat_crash_diagnostics"
    private const val KEY_LAST = "last_crash"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(
                appContext,
                "CRASH ${thread.name}",
                throwable
            )
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, label: String, throwable: Throwable? = null, details: String? = null) {
        runCatching {
            val report = buildString {
                append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()))
                append('\n')
                append(label)
                if (!details.isNullOrBlank()) {
                    append('\n')
                    append(details.take(3000))
                }
                if (throwable != null) {
                    append('\n')
                    append(throwable::class.java.name)
                    throwable.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                    append('\n')
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    append(sw.toString().take(9000))
                }
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = prefs.getString(KEY_LAST, null)
            val combined = if (previous.isNullOrBlank()) report else "$previous\n\n--- TENTATIVE SUIVANTE ---\n$report"
            prefs.edit().putString(KEY_LAST, sanitize(combined).takeLast(24_000)).commit()
        }
    }

    fun recordWithVlcLog(context: Context, label: String, details: String? = null) {
        Thread {
            val log: String = runCatching {
                val process = ProcessBuilder(
                    "logcat", "-d", "-v", "time", "-t", "350",
                    "--pid=${android.os.Process.myPid()}"
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                output.lineSequence()
                    .filter { line ->
                        listOf("vlc", "libvlc", "mediacodec", "decoder", "demux", "http", "matroska")
                            .any { line.contains(it, ignoreCase = true) }
                    }
                    .toList()
                    .takeLast(140)
                    .joinToString("\n")
                    .ifBlank { "Aucune ligne VLC disponible dans logcat." }
            }.getOrElse { "Lecture du journal VLC impossible : ${it.javaClass.simpleName}" }
            record(context, label, details = listOfNotNull(details, "JOURNAL INTERNE VLC:\n$log").joinToString("\n"))
        }.start()
    }

    fun consume(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_LAST, null)
        if (value != null) prefs.edit().remove(KEY_LAST).apply()
        return value?.let(::sanitize)
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("/(live|movie|series)/[^/\\s]+/[^/\\s]+/"), "/$1/***/***/")
        .replace(Regex("([?&](?:username|password)=)[^&\\s]+", RegexOption.IGNORE_CASE), "$1***")
}
