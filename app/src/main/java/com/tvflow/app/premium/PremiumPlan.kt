package com.tvflow.app.premium

/** This catalog is descriptive. Google Play Console owns the actual localized price. */
object PremiumPlan {
    const val productId = "tvflow_premium_lifetime"
    const val introductoryPriceLabel = "9,99 €"
    const val monthlyPriceLabel = "2,99 €/mois"
    const val isSubscription = false // Legacy lifetime purchase; monthly is a separate subscription.
    const val monthlyProductId = "tvflow_premium_monthly"
    const val monthlyBasePlanId = "monthly"
    val offers = listOf(
        PremiumOffer(PremiumOfferType.FREE, null, false),
        PremiumOffer(PremiumOfferType.MONTHLY, monthlyProductId, true),
        PremiumOffer(PremiumOfferType.LIFETIME, productId, false)
    )
    val benefits = listOf(
        "Thèmes exclusifs et personnalisation avancée",
        "Expérience TVFlow CINEMA enrichie",
        "Profils famille et enfants multiples (à venir)",
        "Synchronisation téléphone et TV (à venir)"
    )
}

enum class PremiumOfferType { FREE, MONTHLY, LIFETIME }

data class PremiumOffer(
    val type: PremiumOfferType,
    val productId: String?,
    val isSubscription: Boolean
)

enum class PremiumEntitlement { FREE, PREMIUM }

/** Never infer an entitlement from a local preference or an unverified purchase. */
fun verifiedPremiumEntitlement(purchaseVerifiedByTrustedBackend: Boolean): PremiumEntitlement =
    if (purchaseVerifiedByTrustedBackend) PremiumEntitlement.PREMIUM else PremiumEntitlement.FREE