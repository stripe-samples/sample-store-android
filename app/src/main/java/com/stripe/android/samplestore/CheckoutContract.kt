package com.stripe.android.samplestore

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize

internal class CheckoutContract : ActivityResultContract<CheckoutContract.Args, CheckoutContract.Result>() {

    override fun createIntent(context: Context, input: Args): Intent {
        return Intent(context, PaymentActivity::class.java)
            .putExtra(EXTRA_ARGS, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        return requireNotNull(intent?.getParcelableExtra(EXTRA_RESULT))
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
