package com.tvflow.app.ui.search

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The request code is installation-scoped and never contains IPTV credentials. */
object TvFlowCloudRequest {
    private const val PREFS = "tvflow_cloud_request"
    private const val ENDPOINT = "https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-access-request"
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    fun state(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (AiGatewayConfig(context).token().isNotBlank()) "Accès Cloud actif"
        else when (p.getString("status", "")) {
            "pending" -> "Demande envoyée : en attente d'approbation"
            "approved" -> "Demande approuvée : activation en cours"
            "rejected" -> "Demande refusée"
            else -> "Aucune demande enregistrée"
        }
    }

    suspend fun send(context: Context, email: String, device: String): String = withContext(Dispatchers.IO) {
        val normalized = email.trim()
        if (normalized.length !in 5..254 || !normalized.contains('@')) return@withContext "Adresse e-mail invalide"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("status", "") in listOf("pending", "approved"))
            return@withContext "Une demande est déjà en attente. " + state(context)
        val body = JSONObject().put("email", normalized).put("device_name", device.take(60)).toString()
        val req = Request.Builder().url(ENDPOINT)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        runCatching {
            http.newCall(req).execute().use { response ->
                when (response.code) {
                    202 -> {
                        val raw = response.body?.string()?.takeIf { it.length <= 1024 } ?: return@use "Réponse serveur vide"
                        val code = JSONObject(raw).optString("request_code")
                        if (!code.matches(Regex("[a-f0-9]{36}"))) return@use "Code serveur invalide"
                        if (!prefs.edit().putString("request_code", code).putString("status", "pending").commit())
                            return@use "Impossible de sauvegarder la demande"
                        schedule(context)
                        checkNow(context)
                        "Demande envoyée ! Activation automatique après approbation."
                    }
                    429 -> "Limite atteinte : réessaie demain."
                    400 -> "Vérifie ton adresse e-mail."
                    else -> "Envoi indisponible (HTTP ${response.code}). Réessaie plus tard."
                }
            }
        }.getOrElse { "Connexion indisponible : réessaie plus tard." }
    }

    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<TvFlowCloudActivationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "tvflow-cloud-activation-now", ExistingWorkPolicy.KEEP, request
        )
    }

    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<TvFlowCloudActivationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "tvflow-cloud-activation", ExistingPeriodicWorkPolicy.KEEP, work
        )
    }
}
