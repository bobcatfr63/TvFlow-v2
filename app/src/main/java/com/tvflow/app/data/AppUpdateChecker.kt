package com.tvflow.app.data

import android.os.Build
import com.tvflow.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val publishedAt: String?,
    val downloadUrl: String,
    val artifactSha256: String,
    val artifactSize: Long,
    val signatureFingerprint: String?
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun check(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_MANIFEST_URL)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseReleaseManifest(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun parseReleaseManifest(body: String): AppUpdateInfo? {
        val json = JSONObject(body)
        val remoteCode = json.optLong("versionCode", 0L)
        val remoteName = json.optString("version", "")
        if (remoteName.isBlank() || !isRemoteVersionNewer(remoteName, remoteCode)) return null

        val artifact = selectArtifactForDevice(json)
        val downloadUrl = artifact?.optString("downloadUrl", "")
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("downloadUrl", "")
        val sha256 = artifact?.optString("artifactSha256", "")
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("artifactSha256", "")
        val size = artifact?.optLong("artifactSize", 0L)
            ?.takeIf { it > 0L }
            ?: json.optLong("artifactSize", 0L)
        if (!downloadUrl.startsWith("https://") || sha256.length != 64 || size <= 0L) return null
        return AppUpdateInfo(
            versionName = remoteName,
            versionCode = remoteCode,
            publishedAt = json.optString("publishedAt").takeIf { it.isNotBlank() },
            downloadUrl = downloadUrl,
            artifactSha256 = sha256.lowercase(),
            artifactSize = size,
            signatureFingerprint = json.optString("signatureFingerprint")
                .takeIf { it.isNotBlank() }
                ?.lowercase()
        )
    }

    private fun selectArtifactForDevice(json: JSONObject): JSONObject? {
        val artifacts = json.optJSONArray("artifacts") ?: return null
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        for (abi in supportedAbis) {
            for (index in 0 until artifacts.length()) {
                val candidate = artifacts.optJSONObject(index) ?: continue
                if (candidate.optString("abi") == abi) return candidate
            }
        }
        return null
    }

    companion object {
        private const val RELEASE_MANIFEST_URL =
            "https://github.com/bobcatfr63/IPTV-Releases/releases/download/tvflow-latest/release.json"
    }
}

internal fun isRemoteVersionNewer(remoteName: String, remoteCode: Long): Boolean {
    val localCode = BuildConfig.VERSION_CODE.toLong()
    if (localCode > 0L) {
        return remoteCode > localCode
    }

    val remoteParts = remoteName.split('.').mapNotNull { it.toIntOrNull() }
    val localParts = BuildConfig.VERSION_NAME.split('.').mapNotNull { it.toIntOrNull() }
    if (remoteParts.size < 3 || localParts.size < 3) return false

    val maxSize = maxOf(remoteParts.size, localParts.size)
    for (index in 0 until maxSize) {
        val remote = remoteParts.getOrElse(index) { 0 }
        val local = localParts.getOrElse(index) { 0 }
        if (remote != local) return remote > local
    }
    return false
}
