package com.tvflow.app.ui.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudEnrollmentDeviceTest {
    @Test fun revokedTokenFallsBackToLocalSearch() = runBlocking {
        assumeTrue("Requires previously activated then revoked test token", InstrumentationRegistry.getArguments().getString("tvflowRevoked") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AiGatewayConfig(context)
        assertEquals(TvFlowCloudEnrollment.INTENT_ENDPOINT, config.endpoint)
        val result = GroqIntentClient(context, AiUsageCounter(context)).rewrite("une comedie francaise")
        assertNull("Revoked session must not reach Groq", result)
    }

    @Test fun inviteActivatesAndRequestsCloud() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val invite = InstrumentationRegistry.getArguments().getString("tvflowInvite")
        assumeTrue("Requires a one-time TVFlow test invitation", !invite.isNullOrBlank())
        val context = instrumentation.targetContext
        val enrollment = TvFlowCloudEnrollment(context)
        assertTrue("Registration failed", enrollment.activate(requireNotNull(invite)))
        val config = AiGatewayConfig(context)
        assertEquals(TvFlowCloudEnrollment.INTENT_ENDPOINT, config.endpoint)
        assertTrue(config.token().length >= 32)
        val result = GroqIntentClient(context, AiUsageCounter(context)).rewrite("une comedie francaise")
        assertNotNull("Groq response absent", result)
    }
}
