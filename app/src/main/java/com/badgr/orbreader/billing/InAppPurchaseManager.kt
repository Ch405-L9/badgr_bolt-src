package com.badgr.orbreader.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.PendingPurchasesParams
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InAppPurchaseManager(
    private val context: Context,
    private val onPurchaseSuccess: () -> Unit,
    private val onPurchaseError: (String) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val SKU_PRO_MONTHLY  = "badgr_bolt_pro_monthly"
        const val SKU_PRO_LIFETIME = "badgr_bolt_pro_lifetime"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    /** The SKU that granted Pro — null when not Pro. */
    private val _activeSku = MutableStateFlow<String?>(null)
    val activeSku: StateFlow<String?> = _activeSku

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (billingClient.isReady) {
            _isConnected.value = true
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    scope.launch { queryExistingPurchases() }
                } else {
                    _isConnected.value = false
                    onPurchaseError("Billing setup failed: ${result.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                scope.launch {
                    delay(3000)
                    connect()
                }
            }
        })
    }

    fun disconnect() {
        scope.cancel()
        billingClient.endConnection()
    }

    suspend fun queryExistingPurchases() {
        var foundActive = false

        val subResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        if (subResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val active = subResult.purchasesList.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (active.isNotEmpty()) { foundActive = true; handlePurchaseList(active) }
        }

        val inappResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        if (inappResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val active = inappResult.purchasesList.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (active.isNotEmpty()) { foundActive = true; handlePurchaseList(active) }
        }

        // Both queries succeeded and found no active purchases — subscription may have expired.
        if (!foundActive) {
            withContext(Dispatchers.Main) {
                _isPro.value = false
                _activeSku.value = null
            }
        }
    }

    fun launchSubscriptionFlow(activity: Activity) {
        launchFlow(activity, SKU_PRO_MONTHLY, BillingClient.ProductType.SUBS)
    }

    fun launchLifetimeFlow(activity: Activity) {
        launchFlow(activity, SKU_PRO_LIFETIME, BillingClient.ProductType.INAPP)
    }

    private fun launchFlow(activity: Activity, productId: String, productType: String) {
        if (!billingClient.isReady) {
            onPurchaseError("Billing client not ready. Please try again.")
            return
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )
        scope.launch {
            val result = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()
            )
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK
                || result.productDetailsList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    onPurchaseError("Product not found: $productId")
                }
                return@launch
            }
            val productDetails = result.productDetailsList!!.first()
            val productDetailsParamsList = if (productType == BillingClient.ProductType.SUBS) {
                val offerToken = productDetails.subscriptionOfferDetails
                    ?.firstOrNull()?.offerToken ?: run {
                    withContext(Dispatchers.Main) {
                        onPurchaseError("No subscription offer found.")
                    }
                    return@launch
                }
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            } else {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
            withContext(Dispatchers.Main) {
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { scope.launch { handlePurchaseList(it) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> { /* no-op */ }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // User owns this product — re-query to restore entitlement instead of showing an error.
                scope.launch { queryExistingPurchases() }
            }
            else -> {
                onPurchaseError("Purchase failed: ${result.debugMessage}")
            }
        }
    }

    /**
     * 2.3.4: Entitlement is granted only after verified acknowledgement.
     *
     * - Already acknowledged purchases: grant entitlement immediately (safe —
     *   Google already confirmed these on a prior session).
     * - Unacknowledged purchases: acknowledge first; grant entitlement only on
     *   BillingResponseCode.OK. If acknowledgement fails, entitlement is withheld
     *   and the error is surfaced — Google will refund after 3 days if never acked.
     */
    private suspend fun handlePurchaseList(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue

            if (purchase.isAcknowledged) {
                // Already verified by Google on a prior session — safe to grant immediately.
                withContext(Dispatchers.Main) {
                    _isPro.value = true
                    _activeSku.value = purchase.products.firstOrNull()
                    onPurchaseSuccess()
                }
            } else {
                // Must acknowledge before granting entitlement.
                val ackOk = acknowledgePurchase(purchase)
                if (ackOk) {
                    withContext(Dispatchers.Main) {
                        _isPro.value = true
                        _activeSku.value = purchase.products.firstOrNull()
                        onPurchaseSuccess()
                    }
                }
                // If ackOk == false, onPurchaseError was already called inside
                // acknowledgePurchase — entitlement is intentionally withheld.
            }
        }
    }

    /**
     * Returns true if acknowledgement succeeded, false otherwise.
     * Surfaces error via onPurchaseError on failure.
     */
    private suspend fun acknowledgePurchase(purchase: Purchase): Boolean {
        val result = billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        )
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            true
        } else {
            withContext(Dispatchers.Main) {
                onPurchaseError("Acknowledgement failed: ${result.debugMessage}")
            }
            false
        }
    }
}
