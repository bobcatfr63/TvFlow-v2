package com.tvflow.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbRestoreDeviceTest {
    @Test fun restoreTokenUsingDataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.filesDir, "TVFlow-restore.tvfb")
        val token = org.json.JSONObject(file.readText(Charsets.UTF_8)).getString("tmdbToken").trim()
        require(token.length in 16..4096 && '\n' !in token)
        try {
            val store = SettingsStore(context)
            store.setTmdbToken(token)
            assertTrue(store.currentTmdbToken() == token)
        } finally {
            file.delete()
        }
    }
}
