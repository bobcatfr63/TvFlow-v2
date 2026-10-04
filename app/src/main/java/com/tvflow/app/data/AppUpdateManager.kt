package com.tvflow.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tvflow.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val percent: Int) : UpdateDownloadState
    data class Ready(val apkPath: String) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {    suspend fun download(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(Uri.parse(info.downloadUrl).host.equals("github.com", ignoreCase = true)) {
            "Source de mise à jour non autorisée."
        }

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "updates").also { it.mkdirs() }
        directory.mkdirs()

        val target = File(directory, "TVFlow-${info.versionName}-${info.versionCode}.apk")

        directory.listFiles()
            ?.filter { it.isFile && it != target && it.name.startsWith("TVFlow-") }
            ?.forEach { stale -> runCatching { stale.delete() } }

        if (target.isFile && validateDownloadedApk(target, info)) {
            onProgress(100)
            return@withContext target
        }

        // Keep the temporary file as an APK so Android TV can inspect its package metadata before install.
        val partial = File(directory, target.name.removeSuffix(".apk") + ".part.apk")
        partial.delete()

        // Update transport smoke marker: keeps this path covered by real release tests.
        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("Accept", "application/vnd.android.package-archive")
            .header("Cache-Control", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Téléchargement impossible (${response.code})." }
            val body = checkNotNull(response.body) { "APK vide." }
            var written = 0L
            partial.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        val percent = ((written * 100L) / info.artifactSize)
                            .coerceIn(0L, 100L)
                            .toInt()
                        onProgress(percent)
                    }
                }
            }
        }

        check(partial.length() == info.artifactSize) {
            "Taille de l’APK incorrecte."
        }
        check(validateDownloadedApk(partial, info)) {
            "La vérification de sécurité de l’APK a échoué."
        }

        target.delete()
        check(partial.renameTo(target)) { "Impossible de finaliser le téléchargement." }
        onProgress(100)
        target
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    fun unknownSourcesIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installIntent(apkPath: String): Intent {
        val apk = File(apkPath)
        require(apk.isFile) { "APK introuvable." }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun validateDownloadedApk(file: File, info: AppUpdateInfo): Boolean {
        if (!sha256(file).equals(info.artifactSha256, ignoreCase = true)) return false

        val archive = packageArchiveInfo(file) ?: return false
        if (archive.packageName != BuildConfig.APPLICATION_ID) return false
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        if (archiveVersion != info.versionCode) return false
        val remoteSigner = signerFingerprint(archive) ?: return false
        val expectedSigner = info.signatureFingerprint
        if (expectedSigner != null && !remoteSigner.equals(expectedSigner, ignoreCase = true)) return false

        val installed = installedPackageInfo() ?: return false
        val installedSigner = signerFingerprint(installed) ?: return false
        return remoteSigner.equals(installedSigner, ignoreCase = true)
    }

    private fun packageArchiveInfo(file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            )
        }

    private fun installedPackageInfo(): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
                    } else {
                        PackageManager.GET_SIGNATURES
                    }
                )
            }
        }.getOrNull()

    private fun signerFingerprint(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
                ?: @Suppress("DEPRECATION") info.signatures
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return null
        val certificate = signatures.firstOrNull()?.toByteArray() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
