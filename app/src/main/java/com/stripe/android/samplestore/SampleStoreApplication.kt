package com.stripe.android.samplestore

import android.os.StrictMode
import androidx.multidex.MultiDexApplication

import com.facebook.stetho.Stetho
import com.stripe.android.CustomerSession
import com.stripe.android.PaymentConfiguration
import com.stripe.android.samplestore.service.SampleStoreEphemeralKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SampleStoreApplication : MultiDexApplication() {

    override fun onCreate() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectAll()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )

        super.onCreate()

        PaymentConfiguration.init(this, Settings(this).publishableKey)

        CoroutineScope(Dispatchers.IO).launch {
            Stetho.initializeWithDefaults(this@SampleStoreApplication)
        }

        val stripeAccountId = Settings(this).stripeAccountId
        CustomerSession.initCustomerSession(
            this,
            SampleStoreEphemeralKeyProvider(this, stripeAccountId),
            stripeAccountId,
            shouldPrefetchEphemeralKey = false
        )
    }
}
