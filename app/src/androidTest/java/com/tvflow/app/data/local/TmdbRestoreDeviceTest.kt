package com.tvflow.app.data.local

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tvflow.app.data.metadata.TmdbMetadataClient
import com.tvflow.app.domain.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TmdbRestoreDeviceTest {
    @Test fun restoreTokenUsingDataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.tvflow.app.next") { "Restore is restricted to Next" }
        val file = java.io.File(context.filesDir, "TVFlow-restore.tvfb")
        try {
            val token = org.json.JSONObject(file.readText(Charsets.UTF_8)).getString("tmdbToken").trim()
            require(token.length in 16..4096 && token.none { it.isISOControl() })
            val store = SettingsStore(context)
            val preferencesBefore = store.playerPreferences.first()
            store.setTmdbToken(token)
            assertTrue("Credential restored through SettingsStore", store.currentTmdbToken() == token)
            assertEquals("Player preferences must survive restoration", preferencesBefore, store.playerPreferences.first())
            val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
            val metadata = TmdbMetadataClient(client, store, context)
            assertEquals("Restored credential must authenticate", 200, metadata.validateCredential(token))
            for ((type, title, year) in listOf(
                Triple(MediaType.VOD, "Inception", 2010),
                Triple(MediaType.SERIES, "Breaking Bad", 2008)
            )) {
                val result = metadata.search(type, title, year)
                val url = result?.backdropUrl
                assertTrue("Landscape artwork required for $title", url?.startsWith("https://image.tmdb.org/t/p/w1280/") == true)
                client.newCall(Request.Builder().url(requireNotNull(url)).build()).execute().use { response ->
                    assertTrue("Artwork HTTP success for $title", response.isSuccessful)
                    val bytes = requireNotNull(response.body).bytes()
                    val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
                    assertTrue("Artwork must decode as landscape for $title",
                        dimensions.outWidth > dimensions.outHeight && dimensions.outHeight > 0)
                }
            }
        } finally {
            file.delete()
        }
    }
}
