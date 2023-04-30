package com.stripe.android.samplestore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize


internal class CheckoutContract : ActivityResultContract<CheckoutContract.Args, CheckoutContract.Result>() {
    override fun createIntent(context: Context, input: Args): Intent {
        // WARNING: only `Bundle` seems to work with `@Parcelize data class`.
        val bundle = Bundle()
        bundle.putParcelable("data", input)
        val intent = Intent(context, PaymentActivity::class.java)
            .putExtra(EXTRA_ARGS, bundle)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val result = intent?.getParcelableExtra<Result>(EXTRA_RESULT)
        if (result == null) {
            Common.logError(TAG, "parseResult: intent is missing.")
        }
        return result ?: Result.SetupIntent
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
        const val TAG = "CheckoutContract"
        const val EXTRA_ARGS = "extra_args"
        const val EXTRA_RESULT = "result_extra"
    }
}
