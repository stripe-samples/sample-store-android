package com.stripe.samplestore

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stripe.android.GooglePayJsonFactory
import com.stripe.samplestore.databinding.StoreActivityBinding

class StoreActivity : AppCompatActivity() {
    private val viewModel: StoreViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(application)
        )[StoreViewModel::class.java]
    }

    private val viewBinding: StoreActivityBinding by lazy {
        StoreActivityBinding.inflate(layoutInflater)
    }

    private val paymentsClient: PaymentsClient by lazy {
        Wallet.getPaymentsClient(this,
            Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                .build())
    }
    private val googlePayJsonFactory: GooglePayJsonFactory by lazy {
        GooglePayJsonFactory(this)
    }

    private val checkoutResultLauncher = registerForActivityResult(
        CheckoutContract()
    ) { result ->
        when (result) {
            is CheckoutContract.Result.PaymentIntent -> {
                displayPurchase(result.amount)
            }
            is CheckoutContract.Result.SetupIntent -> {
                displaySetupComplete()
            }
        }

        storeAdapter.clearItemSelections()
    }

    private val storeAdapter: StoreAdapter by lazy {
        StoreAdapter(this, priceMultiplier, checkoutResultLauncher) { hasItems ->
            if (hasItems) {
                viewBinding.fab.show()
            } else {
                viewBinding.fab.hide()
            }
        }
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

        viewBinding.fab.isEnabled = false
        viewBinding.progressBar.visibility = View.VISIBLE
        viewModel.retrieveCustomer().observe(this, Observer { result ->
            viewBinding.progressBar.visibility = View.INVISIBLE
            viewBinding.fab.isEnabled = result.isSuccess

            result.onSuccess {
                storeAdapter.customer = it
            }
        })

        isGooglePayReady()

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

    /**
     * Check that Google Pay is available and ready
     */
    private fun isGooglePayReady() {
        val request = IsReadyToPayRequest.fromJson(
            googlePayJsonFactory.createIsReadyToPayRequest().toString()
        )

        paymentsClient.isReadyToPay(request)
            .addOnCompleteListener { task ->
                storeAdapter.isGooglePayReady = runCatching {
                    task.isSuccessful
                }.getOrDefault(false)
            }
    }
}
