package com.stripe.android.samplestore

import android.app.Application
import android.os.StrictMode

import com.facebook.stetho.Stetho
import com.stripe.android.CustomerSession
import com.stripe.android.PaymentConfiguration
import com.stripe.android.samplestore.service.SampleStoreEphemeralKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SampleStoreApplication : Application() {

    override fun onCreate() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )

        super.onCreate()

        val settings = Settings(this)
        PaymentConfiguration.init(
            this,
            publishableKey = settings.publishableKey,
            stripeAccountId = settings.stripeAccountId
        )

        CoroutineScope(Dispatchers.IO).launch {
            Stetho.initializeWithDefaults(this@SampleStoreApplication)
        }

        CustomerSession.initCustomerSession(
            this,
            SampleStoreEphemeralKeyProvider(this, settings.stripeAccountId),
            shouldPrefetchEphemeralKey = false
        )
    }
}
