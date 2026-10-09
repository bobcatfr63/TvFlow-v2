package com.tvflow.app.premium

/** This catalog is descriptive. Google Play Console owns the actual localized price. */
object PremiumPlan {
    const val productId = "tvflow_premium_lifetime"
    const val introductoryPriceLabel = "4,99 €"
    const val isSubscription = false
    val benefits = listOf(
        "Thèmes exclusifs et personnalisation avancée",
        "Expérience TVFlow CINEMA enrichie",
        "Profils famille et enfants multiples (à venir)",
        "Synchronisation téléphone et TV (à venir)"
    )
}

enum class PremiumEntitlement { FREE, PREMIUM }

/** Never infer an entitlement from a local preference or an unverified purchase. */
fun verifiedPremiumEntitlement(purchaseVerifiedByTrustedBackend: Boolean): PremiumEntitlement =
    if (purchaseVerifiedByTrustedBackend) PremiumEntitlement.PREMIUM else PremiumEntitlement.FREE