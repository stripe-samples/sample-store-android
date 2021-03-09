package com.stripe.android.samplestore

import android.app.Application
import android.net.TrafficStats
import android.os.StrictMode
import com.stripe.android.CustomerSession
import com.stripe.android.PaymentConfiguration
import com.stripe.android.samplestore.service.SampleStoreEphemeralKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SampleStoreApplication : Application() {

    override fun onCreate() {
        TrafficStats.setThreadStatsTag(TAG)

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
                .build()
        )

        super.onCreate()

        val settings = Settings(this)

        CoroutineScope(Dispatchers.IO).launch {
            PaymentConfiguration.init(
                this@SampleStoreApplication,
                publishableKey = settings.publishableKey,
                stripeAccountId = settings.stripeAccountId
            )
        }

        CustomerSession.initCustomerSession(
            this,
            SampleStoreEphemeralKeyProvider(this, settings.stripeAccountId),
            shouldPrefetchEphemeralKey = false
        )
    }

    private companion object {
        private const val TAG = 99999
    }
}
