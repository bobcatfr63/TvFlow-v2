package com.tvflow.app.ui.search

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Polls only after a request has been submitted; never blocks TVFlow startup. */
class TvFlowCloudActivationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("tvflow_cloud_request", Context.MODE_PRIVATE)
        val code = prefs.getString("request_code", null)
        if (code == null || !code.matches(Regex("[a-f0-9]{36}"))) return@withContext Result.success()
        if (prefs.getString("status", "") == "rejected") return@withContext Result.success()
        if (AiGatewayConfig(applicationContext).token().isNotBlank()) {
            prefs.edit().putString("status", "active").apply()
            return@withContext Result.success()
        }
        try {
            val body = JSONObject().put("request_code", code).toString()
            val req = Request.Builder()
                .url("https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-request-status")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val status = http.newCall(req).execute().use { response ->
                if (response.code == 404) return@withContext Result.success()
                if (!response.isSuccessful) return@withContext Result.retry()
                JSONObject(response.body?.string() ?: "{}").optString("status")
            }
            when (status) {
                "approved" -> {
                    prefs.edit().putString("status", "approved").apply()
                    if (TvFlowCloudEnrollment(applicationContext).activate(code)) {
                        prefs.edit().putString("status", "active").remove("request_code").apply()
                        Result.success()
                    } else Result.retry()
                }
                "rejected" -> {
                    prefs.edit().putString("status", "rejected").remove("request_code").apply()
                    Result.success()
                }
                "pending" -> Result.success()
                else -> Result.retry()
            }
        } catch (_: Exception) { Result.retry() }
    }
}
