package ch.informatx.nfinke.myapplication

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.android.billingclient.api.BillingClient
import BillingManager
import android.util.Log
import android.view.View
import android.widget.Button
import com.android.billingclient.api.Purchase

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), View.OnClickListener, BillingManager.BillingUpdatesListener {

    companion object {
        val TAG = "MainActivity"
    }

    val REMOVE_ADS_PERMANENTLY_SKU_ID = "remove_ads"
    val MONTH_SUB_SKU_ID = "monthly_sub"
    val YEAR_SUB_SKU_ID = "yearly_sub"

    var sub_monthly: Button? = null
    var sub_yearly: Button? = null

    var billingManager: BillingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        sub_monthly = findViewById(R.id.monthly_sub_button)
        sub_yearly = findViewById(R.id.yearly_sub_button)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        sub_monthly?.setOnClickListener(this)
        sub_yearly?.setOnClickListener(this)

        billingManager = BillingManager(this, this)

    }

    fun startPurchaseFlow(skuId : String, @BillingClient.SkuType skuType: String) {
        billingManager?.launchPurchaseFlow(skuId, skuType)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.monthly_sub_button -> startPurchaseFlow(MONTH_SUB_SKU_ID, BillingClient.SkuType.SUBS)
            R.id.yearly_sub_button -> startPurchaseFlow(YEAR_SUB_SKU_ID, BillingClient.SkuType.SUBS)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPurchaseUpdated(purchases: List<Purchase>, responseCode: Int) {
        Log.i(TAG, "onPurchaseUpdated, responseCode = $responseCode, size of purchases = ${purchases.size}")
    }

    override fun onQueryPurchasesFinished(purchases: List<Purchase>) {
        Log.i(TAG, "onQueryPurchasesFinished, size of verified Purchases = ${purchases.size}")
    }

    override fun onConsumeFinished(token: Int, responseCode: String) {
        Log.i(TAG, "onConsumePurchase Successful : BillingResponseCode = $responseCode, token = $token")
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.destroyBillingClient()
    }
}
