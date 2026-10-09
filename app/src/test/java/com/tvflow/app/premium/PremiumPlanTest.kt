package com.tvflow.app.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PremiumPlanTest {
    @Test fun premiumIsLifetimeOneTimeProduct() {
        assertEquals("tvflow_premium_lifetime", PremiumPlan.productId)
        assertFalse(PremiumPlan.isSubscription)
    }

    @Test fun neverGrantPremiumWithoutTrustedVerification() {
        assertEquals(PremiumEntitlement.FREE, verifiedPremiumEntitlement(false))
        assertEquals(PremiumEntitlement.PREMIUM, verifiedPremiumEntitlement(true))
    }
}