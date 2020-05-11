package com.stripe.samplestore

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.android.parcel.Parcelize

internal class CheckoutContract : ActivityResultContract<StoreCart, CheckoutContract.Result>() {
    override fun createIntent(context: Context, cart: StoreCart?): Intent {
        return Intent(context, PaymentActivity::class.java)
            .putExtra(EXTRA_CART, cart)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
        return intent?.getParcelableExtra(EXTRA_RESULT)
    }

    sealed class Result : Parcelable {
        @Parcelize
        data class PaymentIntent(val amount: Long) : Result()

        @Parcelize
        object SetupIntent : Result()
    }

    companion object {
        const val EXTRA_CART = "extra_cart"
        const val EXTRA_RESULT = "result_extra"
    }
}
