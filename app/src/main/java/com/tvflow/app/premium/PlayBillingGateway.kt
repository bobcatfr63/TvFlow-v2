package com.tvflow.app.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Purchases are not entitlements. Tokens must first be verified through a trusted backend. */
class PlayBillingGateway(context: Context) : PurchasesUpdatedListener {
    data class State(
        val connected: Boolean = false,
        val displayedPrice: String? = null,
        val purchasedTokenAwaitingVerification: Boolean = false,
        val message: String = "Connexion à Google Play…"
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState
    private var product: ProductDetails? = null
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value = mutableState.value.copy(connected = true)
                    refresh()
                } else {
                    mutableState.value = State(message = "Google Play indisponible (${result.responseCode}).")
                }
            }
            override fun onBillingServiceDisconnected() {
                mutableState.value = State(message = "Connexion Google Play interrompue.")
            }
        })
    }

    fun refresh() {
        if (!client.isReady) { connect(); return }
        val request = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumPlan.productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()
        client.queryProductDetailsAsync(request) { result, products ->
            product = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                products.productDetailsList.firstOrNull()
            } else null
            val offer = product?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            mutableState.value = mutableState.value.copy(
                displayedPrice = offer?.formattedPrice,
                message = if (offer != null) "Produit Google Play trouvé." else "Produit Premium indisponible dans Google Play Console."
            )
        }
        restore()
    }

    fun restore() {
        if (!client.isReady) { connect(); return }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(message = "Restauration indisponible.")
                return@queryPurchasesAsync
            }
            val eligible = purchases.any { purchase ->
                purchase.products.contains(PremiumPlan.productId) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            mutableState.value = mutableState.value.copy(
                purchasedTokenAwaitingVerification = eligible,
                message = if (eligible) "Achat trouvé. Vérification sécurisée requise avant activation."
                    else "Aucun achat Premium trouvé sur ce compte Google Play."
            )
        }
    }

    /** This flow must remain disabled until the server-side verifier is operational. */
    fun startPurchase(activity: Activity, serverVerifierReady: Boolean): Boolean {
        if (!serverVerifierReady || !client.isReady) return false
        val details = product ?: return false
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return false
        val offerToken = offer.offerToken ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) restore()
        else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            mutableState.value = mutableState.value.copy(message = "Google Play : ${result.debugMessage}")
        }
    }

    fun close() = client.endConnection()
}