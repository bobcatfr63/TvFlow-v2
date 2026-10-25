package com.tvflow.app.ui.settings

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBusyOperationTest {
    @Test fun cancellationClearsBusyWithoutDisplayingSuccess() = runBlocking {
        val busy = MutableStateFlow(false)
        val message = MutableStateFlow<String?>(null)
        val job = launch {
            runSettingsBusyOperation(busy, message, "Terminé") {
                delay(60_000)
            }
        }
        while (!busy.value) yield()
        assertTrue(busy.value)
        job.cancelAndJoin()
        assertFalse(busy.value)
        assertNull(message.value)
    }

    @Test fun failureClearsBusyAndShowsError() = runBlocking {
        val busy = MutableStateFlow(false)
        val message = MutableStateFlow<String?>(null)
        runSettingsBusyOperation(busy, message, "Terminé") {
            throw IllegalStateException("Serveur indisponible")
        }
        assertFalse(busy.value)
        assertEquals("Serveur indisponible", message.value)
    }

    @Test fun successClearsBusyAndShowsSuccess() = runBlocking {
        val busy = MutableStateFlow(false)
        val message = MutableStateFlow<String?>(null)
        runSettingsBusyOperation(busy, message, "Profil activé") {}
        assertFalse(busy.value)
        assertEquals("Profil activé", message.value)
    }
}
