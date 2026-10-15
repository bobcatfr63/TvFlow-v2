package com.tvflow.app.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PremiumPlanTest {
    @Test fun premiumIsLifetimeOneTimeProduct() {
        assertEquals("tvflow_premium_lifetime", PremiumPlan.productId)
        assertFalse(PremiumPlan.isSubscription)
    }

    @Test fun threeOffersHaveDistinctBillingProducts() {
        assertEquals(listOf(PremiumOfferType.FREE, PremiumOfferType.MONTHLY, PremiumOfferType.LIFETIME), PremiumPlan.offers.map { it.type })
        assertEquals(null, PremiumPlan.offers[0].productId)
        assertEquals("tvflow_premium_monthly", PremiumPlan.offers[1].productId)
        assertEquals(true, PremiumPlan.offers[1].isSubscription)
        assertEquals("tvflow_premium_lifetime", PremiumPlan.offers[2].productId)
        assertFalse(PremiumPlan.offers[2].isSubscription)
    }

    @Test fun neverGrantPremiumWithoutTrustedVerification() {
        assertEquals(PremiumEntitlement.FREE, verifiedPremiumEntitlement(false))
        assertEquals(PremiumEntitlement.PREMIUM, verifiedPremiumEntitlement(true))
    }
}