package com.tvflow.app.ui.search

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tvflow.app.BuildConfig
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-owned proxy credential. Groq secrets are never accepted by the app. */
class AiGatewayConfig(context: Context) {
    private val prefs = context.getSharedPreferences("ai_gateway", Context.MODE_PRIVATE)
    val endpoint: String get() = prefs.getString("endpoint", null) ?: BuildConfig.TVFLOW_AI_ENDPOINT
    fun token(): String = runCatching {
        val packed = Base64.decode(prefs.getString("token", "") ?: "", Base64.NO_WRAP)
        if (packed.size < 29) return@runCatching ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
    }.getOrDefault("")
    fun save(endpoint: String, token: String) {
        require(validEndpoint(endpoint, BuildConfig.DEBUG))
        require(token.length in 32..256 && !token.startsWith("gsk_") && token.none { it.isWhitespace() })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("endpoint", endpoint)
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    fun clear() { prefs.edit().clear().apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tvflow_ai_proxy", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tvflow_ai_proxy",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        fun validEndpoint(value: String, debug: Boolean): Boolean = runCatching {
            val uri = URI(value)
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                !uri.host.isNullOrBlank() && uri.port in -1..65535 && (uri.path == "/v1/intent" || uri.path == "/functions/v1/tvflow-ai/intent") &&
                (uri.scheme == "https" || (debug && uri.scheme == "http" && uri.host == "127.0.0.1"))
        }.getOrDefault(false)
    }
}
