package com.tvflow.app.data.backup

import org.junit.Assert.*
import org.junit.Test

class PortableBackupCryptoTest {
    private val password = "Mot de passe de migration fort".toCharArray()

    @Test fun roundTrip() {
        val original = """{"favorites":[5,10],"profile":"iptv"}""".toByteArray()
        val first = PortableBackupCrypto.encrypt(original, password)
        val second = PortableBackupCrypto.encrypt(original, password)
        assertNotEquals(first, second)
        assertArrayEquals(original, PortableBackupCrypto.decrypt(first, password))
    }

    @Test fun invalidPasswordAndCorruption() {
        val value = PortableBackupCrypto.encrypt("secret".toByteArray(), password)
        assertFalse(value.contains("secret"))
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(value, "erreur de mot de passe".toCharArray())
        }
        val last = value.last()
        val tampered = value.dropLast(1) + (if (last == 'A') 'B' else 'A')
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(tampered, password)
        }
    }

    @Test fun invalidInput() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.encrypt(byteArrayOf(1), "short".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt("garbage", password)
        }
    }
}
