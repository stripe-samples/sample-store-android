package com.stripe.samplestore

import android.os.StrictMode
import androidx.multidex.MultiDexApplication
import com.stripe.android.PaymentConfiguration

class StoreApplication : MultiDexApplication() {

    override fun onCreate() {
        PaymentConfiguration.init(this, Settings(this).publishableKey)

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
    }
}
