package com.tvflow.app.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tvflow.app.BuildConfig
import com.tvflow.app.data.TVFLOW_RELEASE_MANIFEST_URL
import com.tvflow.app.MainActivity
import com.tvflow.app.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UpdateNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(TVFLOW_RELEASE_MANIFEST_URL)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val json = JSONObject(response.body?.string().orEmpty())
                val remoteCode = json.optLong("versionCode", 0L)
                val remoteName = json.optString("version", "").trim()

                if (remoteCode <= BuildConfig.VERSION_CODE.toLong() || remoteName.isBlank()) return@use

                val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getLong(KEY_LAST_NOTIFIED_CODE, -1L) == remoteCode) return@use

                createNotificationChannel(applicationContext)
                if (showUpdateNotification(applicationContext, remoteName, json)) {
                    prefs.edit().putLong(KEY_LAST_NOTIFIED_CODE, remoteCode).apply()
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private fun showUpdateNotification(context: Context, versionName: String, json: JSONObject): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            7401,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val note = firstReleaseNote(json)
        val text = note?.let { "TVFlow $versionName • $it" }
            ?: "TVFlow $versionName est disponible."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Mise à jour TVFlow disponible")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun firstReleaseNote(json: JSONObject): String? {
        val changelog = json.optJSONArray("changelog")
        if (changelog != null && changelog.length() > 0) {
            val entry = changelog.optJSONObject(0)
            return entry?.optString("title")?.trim()?.takeIf { it.isNotBlank() }
                ?: entry?.optString("subject")?.trim()?.takeIf { it.isNotBlank() }
        }
        return json.optJSONArray("releaseNotes")
            ?.optString(0)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val EXTRA_OPEN_UPDATE = "tvflow_open_update"
        private const val CHANNEL_ID = "tvflow_updates"
        private const val CHANNEL_NAME = "Mises à jour TVFlow"
        private const val NOTIFICATION_ID = 7401
        private const val PREFS = "tvflow_update_notifications"
        private const val KEY_LAST_NOTIFIED_CODE = "last_notified_version_code"
        private const val PERIODIC_WORK = "tvflow_update_check_periodic"
        private const val IMMEDIATE_WORK = "tvflow_update_check_immediate"

        fun schedule(context: Context) {
            createNotificationChannel(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodic = PeriodicWorkRequestBuilder<UpdateNotificationWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )

            val immediate = OneTimeWorkRequestBuilder<UpdateNotificationWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                immediate
            )
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avertit lorsqu'une nouvelle version de TVFlow est disponible."
            }
            manager.createNotificationChannel(channel)
        }
    }
}
