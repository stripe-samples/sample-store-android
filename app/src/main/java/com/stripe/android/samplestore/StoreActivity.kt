package com.stripe.android.samplestore

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stripe.android.GooglePayJsonFactory
import com.stripe.android.samplestore.databinding.StoreActivityBinding

class StoreActivity : AppCompatActivity() {
    private val viewModel: StoreViewModel by viewModels()

    private val viewBinding: StoreActivityBinding by lazy {
        StoreActivityBinding.inflate(layoutInflater)
    }

    private val paymentsClient: PaymentsClient by lazy {
        PaymentsClientFactory(this).create()
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
        StoreAdapter(this, checkoutResultLauncher) { hasItems ->
            if (hasItems) {
                viewBinding.fab.show()
            } else {
                viewBinding.fab.hide()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        viewBinding.fab.isEnabled = false
        viewBinding.progressBar.visibility = View.VISIBLE
        viewModel.retrieveCustomer().observe(
            this,
            { result ->
                viewBinding.progressBar.visibility = View.INVISIBLE
                viewBinding.fab.isEnabled = result.isSuccess

                result.onSuccess {
                    storeAdapter.customer = it
                }
            }
        )

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
