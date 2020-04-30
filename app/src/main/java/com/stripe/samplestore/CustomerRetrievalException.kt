package com.stripe.samplestore

import com.stripe.android.StripeError

data class CustomerRetrievalException(
    val errorCode: Int,
    val errorMessage: String,
    val stripeError: StripeError?
) : Throwable(errorMessage)
