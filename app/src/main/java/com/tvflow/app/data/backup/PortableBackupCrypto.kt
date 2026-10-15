package com.tvflow.app.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted portable backup. Does not depend on device Android Keystore. */
object PortableBackupCrypto {
    private const val HEADER = "TVFLOW-BACKUP-V1:"
    private const val MAX_SIZE = 16 * 1024 * 1024
    private val rng = SecureRandom()

    fun encrypt(data: ByteArray, password: CharArray): String {
        require(password.size >= 12) { "Mot de passe : 12 caractères minimum" }
        require(data.size in 1..MAX_SIZE) { "Sauvegarde trop volumineuse ou vide" }
        val salt = ByteArray(16).also(rng::nextBytes)
        val nonce = ByteArray(12).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(HEADER.toByteArray(Charsets.UTF_8))
        return HEADER + Base64.getEncoder().encodeToString(salt + nonce + cipher.doFinal(data))
    }

    fun decrypt(text: String, password: CharArray): ByteArray {
        require(text.startsWith(HEADER)) { "Format inconnu" }
        val body = text.removePrefix(HEADER)
        require(body.length <= (MAX_SIZE + 64) * 2) { "Sauvegarde trop volumineuse" }
        val bytes = try { Base64.getDecoder().decode(body) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("Sauvegarde corrompue", e) }
        require(bytes.size > 44) { "Sauvegarde tronquée" }
        val salt = bytes.copyOfRange(0, 16)
        val nonce = bytes.copyOfRange(16, 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(HEADER.toByteArray(Charsets.UTF_8))
        return try { cipher.doFinal(bytes.copyOfRange(28, bytes.size)) }
            catch (e: AEADBadTagException) { throw IllegalArgumentException("Mot de passe incorrect ou sauvegarde altérée", e) }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }
}
