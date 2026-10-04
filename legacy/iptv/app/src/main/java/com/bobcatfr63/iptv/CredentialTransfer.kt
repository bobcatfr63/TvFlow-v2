package com.bobcatfr63.iptv

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CredentialTransfer {
    fun encode(credentials: Credentials): String {
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val clear = JSONObject().put("v", 1).put("server", credentials.server)
            .put("username", credentials.username).put("password", credentials.password).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        val payload = listOf(key, iv, cipher.doFinal(clear)).joinToString(".") {
            Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
        return Uri.Builder().scheme("bobcatiptv").authority("import").appendQueryParameter("data", payload).build().toString()
    }

    fun decode(uri: Uri): Credentials? = runCatching {
        val parts = uri.getQueryParameter("data")!!.split('.')
        val decode = { value: String -> Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val key = decode(parts[0]); val iv = decode(parts[1]); val encrypted = decode(parts[2])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        val root = JSONObject(String(cipher.doFinal(encrypted)))
        Credentials(root.getString("server"), root.getString("username"), root.getString("password"))
    }.getOrNull()
}
