package com.stripe.android.samplestore

import android.app.Activity
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants

class PaymentsClientFactory(
    private val activity: Activity
) {
    fun create(): PaymentsClient {
        return Wallet.getPaymentsClient(
            activity,
            Wallet.WalletOptions.Builder()
                .setEnvironment(
                    when (BuildConfig.BUILD_TYPE) {
                        "release" -> WalletConstants.ENVIRONMENT_PRODUCTION
                        else -> WalletConstants.ENVIRONMENT_TEST
                    }
                )
                .build()
        )
    }
}
