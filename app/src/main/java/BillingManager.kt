import android.app.Activity
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.util.BillingHelper
import java.io.IOException
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

class BillingManager (val activity : Activity, val billingUpdatesListener: BillingUpdatesListener) : PurchasesUpdatedListener {

    private val BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuaIKg6BIeM4IS5wwmTE+nQb/RBN+yVixgYl0mVtY04cGSrRs/Djs5kl3xVWSHsKg6E9nUH0FoOj/vbmojJYn4wdWfTLyab9wW0luTDMd8O+3kPZvzEOm0HwxZtq1afs11U2fKdXQSm8Qw4xgH5nDn22pnDLlxZxohnbedeYDId87/aH1BbzYY+pzCvy7IxcuZ5qifrJBnnSAcTryFGRh1OCX9MDQbnwqOVwE5aKmTJluhAT3hbtZAD+K/HAUqAOKEfsIA+S7RUxTVYu1p6bkD8Y6QHY3WmG4SoQ0Lb2q8xZuXJRVDHwgQfHdLgIfc1hf5NUCFRJ28EHsUvekSoBm+QIDAQAB"
    private val SIGNATURE_ALGORITHM = "SHA1withRSA"
    private val KEY_FACTORY_ALGORITHM = "RSA"

    private val verifiedPurchaseList = ArrayList<Purchase>()

    private val billingClient : BillingClient
    private val TAG = "BillingManager"

    init {
        billingClient = BillingClient.newBuilder(activity).setListener(this).build()
        startServiceConnectionIfNeeded(null)
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated() response: " + responseCode)
    }

    private fun startServiceConnectionIfNeeded(executeOnSuccess: Runnable?) {
        if (billingClient.isReady()) {
            executeOnSuccess?.run()
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponse: Int) {
                    Log.i(TAG, "onBillingSetupFinished() BillingResponse: $billingResponse")
                    if (billingResponse == BillingClient.BillingResponse.OK) {
                        executeOnSuccess?.run()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.i(TAG, "onBillingServiceDisconnected()")
                }
            })
        }
    }

    fun destroyBillingClient() {
        billingClient.endConnection()
    }

    interface BillingUpdatesListener {
        fun onPurchaseUpdated(purchases: List<Purchase>, responseCode: Int)
        fun onConsumeFinished(token: Int, @BillingClient.BillingResponse responseCode: String)
        fun onQueryPurchasesFinished(purchases: List<Purchase>)
    }

    fun launchPurchaseFlow(skuId: String, billingType: String) {
        val billingFlowParams = BillingFlowParams.newBuilder().setType(billingType).setSku(skuId).build()
        val purchaseFlowRunnable = Runnable {
            billingClient.launchBillingFlow(activity,billingFlowParams)
        }
        startServiceConnectionIfNeeded(purchaseFlowRunnable)
    }

    fun queryPurchases() {
        val purchaseQueryRunnable = Runnable {
            verifiedPurchaseList.clear()

            val purchaseResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)

            if (purchaseResult.responseCode == BillingClient.BillingResponse.OK) {
                purchaseResult.purchasesList.addAll(purchaseResult.purchasesList)
            }

            if (areSubscriptionsSupported()){
                val subscriptionResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS)
                if (subscriptionResult.responseCode == BillingClient.BillingResponse.OK) {
                    purchaseResult.purchasesList.addAll(subscriptionResult.purchasesList)
                }
            } else {
                Log.d(TAG, "Subscription are not supported for this client!")
            }

            for (purchase in purchaseResult.purchasesList) {
                handlePurchase(purchase)
            }
            billingUpdatesListener.onQueryPurchasesFinished(verifiedPurchaseList)
        }
        startServiceConnectionIfNeeded(purchaseQueryRunnable)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (isValidSignature(purchase.originalJson, purchase.signature)) {
            verifiedPurchaseList.add(purchase)
        }
    }

    fun areSubscriptionsSupported(): Boolean {
        val responseCode = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        return responseCode == BillingClient.BillingResponse.OK
    }

    fun consumePurchase(purchase: Purchase) {
        val consumePurchaseRunnable = Runnable {
            billingClient.consumeAsync(purchase.purchaseToken, ConsumeResponseListener {
                responseCode, purchaseToken ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    billingUpdatesListener.onConsumeFinished(responseCode, purchaseToken)
                }
            })
        }
        startServiceConnectionIfNeeded(consumePurchaseRunnable)
    }

    private fun isValidSignature(signedData: String, signature: String): Boolean {
        val publicKey = generatePublicKey(BASE_64_ENCODED_PUBLIC_KEY)

        val signatureBytes: ByteArray
        try {
            signatureBytes = Base64.decode(signature, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            BillingHelper.logWarn(TAG, "Base64 decoding failed.")
            return false
        }

        try {
            val signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureAlgorithm.initVerify(publicKey)
            signatureAlgorithm.update(signedData.toByteArray())
            if (!signatureAlgorithm.verify(signatureBytes)) {
                BillingHelper.logWarn(TAG, "Signature verification failed.")
                return false
            }
            return true
        } catch (e: NoSuchAlgorithmException) {
            // "RSA" is guaranteed to be available.
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            BillingHelper.logWarn(TAG, "Invalid key specification.")
        } catch (e: SignatureException) {
            BillingHelper.logWarn(TAG, "Signature exception.")
        }

        return false
    }

    @Throws(IOException::class)
    private fun generatePublicKey(encodedPublicKey: String): PublicKey {
        try {
            val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
        } catch (e: NoSuchAlgorithmException) {
            // "RSA" is guaranteed to be available.
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            val msg = "key specification: $e"
            BillingHelper.logWarn(TAG, msg)
            throw IOException(msg)
        }
    }

}