package com.stripe.android.samplestore

import android.content.Context
import android.content.pm.PackageManager

/**
 * See [Configuring the example app](https://github.com/stripe/stripe-android#building-the-example-project)
 * for instructions on how to configure the app before running it.
 */
class Settings(context: Context) {
    private val appContext = context.applicationContext
    private val backendMetadata = getMetadata(METADATA_KEY_BACKEND_URL_KEY)
    private val publishableKeyMetadata = getMetadata(METADATA_KEY_PUBLISHABLE_KEY)
    private val stripeAccountIdMetadata = getMetadata(METADATA_KEY_STRIPE_ACCOUNT_ID)

    val backendUrl: String
        get() {
            return backendMetadata ?: BASE_URL
        }

    val publishableKey: String
        get() {
            return publishableKeyMetadata ?: PUBLISHABLE_KEY
        }

    val stripeAccountId: String?
        get() {
            return stripeAccountIdMetadata ?: STRIPE_ACCOUNT_ID
        }

    private fun getMetadata(key: String): String? {
        return appContext.packageManager
            .getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
            .metaData
            .getString(key)
            .takeIf { it?.isNotBlank() == true }
    }

    internal companion object {
        /**
         * Three-letter ISO [currency code](https://stripe.com/docs/api/payment_intents/object#payment_intent_object-currency),
         * in lowercase. Must be a [supported currency](https://stripe.com/docs/currencies).
         */
        const val CURRENCY = "USD"

        const val COUNTRY = "us"

        /**
         * Set to the base URL of your test backend. If you are using
         * [example-mobile-backend](https://github.com/stripe/example-mobile-backend),
         * the URL will be something like `https://hidden-beach-12345.herokuapp.com/`.
         */
        private const val BASE_URL = "put your base url here"

        /**
         * Set to publishable key from https://dashboard.stripe.com/test/apikeys
         */
        private const val PUBLISHABLE_KEY = "pk_test_your_key_goes_here"

        /**
         * Optionally, set to a Connect Account id to use for API requests to test Connect
         *
         * See https://dashboard.stripe.com/test/connect/accounts/overview
         */
        private val STRIPE_ACCOUNT_ID: String? = null

        private const val METADATA_KEY_BACKEND_URL_KEY =
            "com.stripe.example.metadata.backend_url"
        private const val METADATA_KEY_PUBLISHABLE_KEY =
            "com.stripe.example.metadata.publishable_key"
        private const val METADATA_KEY_STRIPE_ACCOUNT_ID =
            "com.stripe.example.metadata.stripe_account_id"
    }
}
