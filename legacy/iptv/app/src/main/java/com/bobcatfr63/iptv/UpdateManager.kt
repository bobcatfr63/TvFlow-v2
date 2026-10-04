package com.bobcatfr63.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit


data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String = ""
)

class UpdateManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val releaseApi =
        "https://api.github.com/repos/bobcatfr63/iptv/releases/latest"

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(releaseApi)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Bobcat-IPTV/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IllegalStateException("Vérification de mise à jour impossible (HTTP ${response.code})")

            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val patch = Regex("(\\d+)(?!.*\\d)").find(tag)?.value?.toIntOrNull() ?: return@withContext null
            val versionCode = 1_000_000 + patch
            val versionName = tag.removePrefix("v").ifBlank { versionCode.toString() }
            val notes = body

            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null

            val asset = root["assets"]?.jsonArray
                ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                ?.firstOrNull {
                    val name = it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    name.equals("Bobcat IPTV.apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext null

            val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext null

            AppUpdate(versionCode, versionName, url, notes)
        }
    }

    suspend fun downloadAndInstall(update: AppUpdate): InstallResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return@withContext InstallResult.PERMISSION_REQUIRED
        }

        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("User-Agent", "Bobcat-IPTV/${BuildConfig.VERSION_NAME}")
            .build()
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "Bobcat IPTV-${update.versionCode}.apk")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Téléchargement impossible (HTTP ${response.code})")
            response.body?.byteStream()?.use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("APK de mise à jour vide")
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        InstallResult.INSTALLER_OPENED
    }
}

enum class InstallResult { INSTALLER_OPENED, PERMISSION_REQUIRED }
