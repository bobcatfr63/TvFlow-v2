package com.tvflow.app.ui.search

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live test. Token is provisioned through adb stdin, never in APK or instrumentation args. */
@RunWith(AndroidJUnit4::class)
class GroqLiveDeviceTest {
    @Test fun configurePrivateHttpsForPhone() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val secretFile = target.getFileStreamPath("groq_test_token")
        assertTrue("Temporary private test token is missing", secretFile.isFile)
        val token = try { secretFile.readText().trim() } finally { secretFile.delete() }
        val address = "https://desktop-8vd83bl.tail89b08b.ts.net:8443/v1/intent"
        val config = AiGatewayConfig(target)
        config.save(address, token)
        assertTrue("Private HTTPS endpoint not retained", config.endpoint == address)
        assertTrue("Encrypted credential cannot be decrypted", config.token() == token)
        assertTrue("Credential stored in plaintext", !target.getSharedPreferences("ai_gateway", 0)
            .getString("token", "")!!.contains(token))
    }
    @Test fun realGroqOverPrivateHttpsWithoutAdbReverse() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AiGatewayConfig(target)
        assertTrue("Private HTTPS endpoint not configured", config.endpoint ==
            "https://desktop-8vd83bl.tail89b08b.ts.net:8443/v1/intent")
        assertTrue("Secure proxy credential not configured", config.token().length >= 32)
        val rewritten = runBlocking {
            GroqIntentClient(target, AiUsageCounter(target)).rewrite("une comedie francaise")
        }
        assertNotNull("Private HTTPS Groq connection failed on physical phone", rewritten)
    }
    @Test fun realGroqRoundTrip() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val secretFile = target.getFileStreamPath("groq_test_token")
        assertTrue("Temporary private test token is missing", secretFile.isFile)
        val token = try { secretFile.readText().trim() } finally { secretFile.delete() }
        // Provision real app preferences only on disposable, dedicated test emulators.
        val config = AiGatewayConfig(target)
        run {
            assertTrue("Debug endpoint rejected: buildDebug=${com.tvflow.app.BuildConfig.DEBUG}",
                AiGatewayConfig.validEndpoint("http://127.0.0.1:18765/v1/intent", com.tvflow.app.BuildConfig.DEBUG))
            assertTrue("Private proxy token invalid length", token.length in 32..256)
            config.save("http://127.0.0.1:18765/v1/intent", token)
            val rewrite = runBlocking { GroqIntentClient(target, AiUsageCounter(target)).rewrite("une comedie francaise") }
            assertNotNull("Groq authentication/network/live API failed", rewrite)
            assertTrue(rewrite!!.contains("comed", ignoreCase = true) || rewrite.contains("comé", ignoreCase = true))
        }
    }
}
