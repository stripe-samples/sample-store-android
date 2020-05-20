package com.stripe.android.samplestore

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.android.parcel.Parcelize

internal class CheckoutContract : ActivityResultContract<CheckoutContract.Args, CheckoutContract.Result>() {
    override fun createIntent(context: Context, args: Args?): Intent {
        return Intent(context, PaymentActivity::class.java)
            .putExtra(EXTRA_ARGS, args)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
        return intent?.getParcelableExtra(EXTRA_RESULT)
    }

    @Parcelize
    data class Args(
        val cart: StoreCart,
        val customerId: String,
        val isGooglePayReady: Boolean
    ) : Parcelable

    sealed class Result : Parcelable {
        @Parcelize
        data class PaymentIntent(val amount: Long) : Result()

        @Parcelize
        object SetupIntent : Result()
    }

    companion object {
        const val EXTRA_ARGS = "extra_args"
        const val EXTRA_RESULT = "result_extra"
    }
}
