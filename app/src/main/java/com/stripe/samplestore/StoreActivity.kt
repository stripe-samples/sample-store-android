package com.stripe.samplestore

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stripe.android.CustomerSession
import com.stripe.android.PaymentConfiguration
import com.stripe.samplestore.databinding.StoreActivityBinding
import com.stripe.samplestore.service.SampleStoreEphemeralKeyProvider

class StoreActivity : AppCompatActivity() {

    private val viewBinding: StoreActivityBinding by lazy {
        StoreActivityBinding.inflate(layoutInflater)
    }

    private val settings: Settings by lazy {
        Settings(applicationContext)
    }

    private val storeAdapter: StoreAdapter by lazy {
        StoreAdapter(this, priceMultiplier) { hasItems ->
            if (hasItems) {
                viewBinding.fab.show()
            } else {
                viewBinding.fab.hide()
            }
        }
    }

    private val ephemeralKeyProvider: SampleStoreEphemeralKeyProvider by lazy {
        SampleStoreEphemeralKeyProvider(applicationContext, settings.stripeAccountId)
    }

    private val priceMultiplier: Float
        get() {
            return try {
                packageManager
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    .metaData
                    .getFloat("com.stripe.samplestore.price_multiplier")
            } catch (e: PackageManager.NameNotFoundException) {
                1.0f
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        PaymentConfiguration.init(this, settings.publishableKey)

        viewBinding.fab.hide()
        setSupportActionBar(findViewById(R.id.my_toolbar))

        viewBinding.storeItems.also {
            it.layoutManager = LinearLayoutManager(this)
            it.addItemDecoration(ItemDivider(this, R.drawable.item_divider))
            it.adapter = storeAdapter
        }

        viewBinding.fab.setOnClickListener {
            storeAdapter.launchPurchaseActivityWithCart()
        }
        setupCustomerSession(settings.stripeAccountId)
    }

    override fun onDestroy() {
        ephemeralKeyProvider.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val extras = data?.extras
        if (requestCode == PURCHASE_REQUEST && resultCode == Activity.RESULT_OK && extras != null) {
            val price = extras.getLong(EXTRA_PRICE_PAID, -1L)
            if (price != -1L) {
                displayPurchase(price)
            } else {
                displaySetupComplete()
            }
            storeAdapter.clearItemSelections()
        }
    }

    private fun displayPurchase(price: Long) {
        showSuccessDialog(
            R.string.purchase_successful,
            getString(R.string.total_price, StoreUtils.getPriceString(price, null))
        )
    }

    private fun displaySetupComplete() {
        showSuccessDialog(
            R.string.setup_successful
        )
    }

    private fun showSuccessDialog(@StringRes titleRes: Int, message: String? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun setupCustomerSession(stripeAccountId: String?) {
        // CustomerSession only needs to be initialized once per app.
        CustomerSession.initCustomerSession(this, ephemeralKeyProvider, stripeAccountId)
    }

    companion object {
        internal const val PURCHASE_REQUEST = 37

        private const val EXTRA_PRICE_PAID = "EXTRA_PRICE_PAID"

        fun createPurchaseCompleteIntent(amount: Long): Intent {
            return Intent()
                .putExtra(EXTRA_PRICE_PAID, amount)
        }
    }
}
