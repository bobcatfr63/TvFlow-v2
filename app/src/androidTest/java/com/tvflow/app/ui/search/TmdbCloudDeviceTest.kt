package com.tvflow.app.ui.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbCloudDeviceTest {
    @Test fun retrieveFrenchMovieMetadata() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val invitation = args.getString("tvflowInvite")
        assumeTrue("Requires one-time private invite", !invitation.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Invite activation failed", TvFlowCloudEnrollment(context).activate(requireNotNull(invitation)))
        val token = AiGatewayConfig(context).token()
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-metadata")
            .header("Authorization", "Bearer $token")
            .post("{\"type\":\"movie\",\"query\":\"Inception\"}".toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            assertEquals("TMDB cloud error: ${body.take(200)}", 200, response.code)
            val items = JSONObject(body).getJSONArray("results")
            assertTrue("No movies returned", items.length() > 0)
            assertTrue("Missing French metadata", items.getJSONObject(0).has("overview"))
        }
    }
}
