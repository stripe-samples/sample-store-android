package com.stripe.samplestore.service

import android.content.Context
import androidx.annotation.Size
import com.stripe.android.EphemeralKeyProvider
import com.stripe.android.EphemeralKeyUpdateListener
import com.stripe.samplestore.BackendApiFactory
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException

class SampleStoreEphemeralKeyProvider @JvmOverloads constructor(
    context: Context,
    private val stripeAccountId: String? = null
) : EphemeralKeyProvider {

    private val compositeDisposable: CompositeDisposable = CompositeDisposable()
    private val backendApi: BackendApi = BackendApiFactory(context).create()

    override fun createEphemeralKey(
        @Size(min = 4) apiVersion: String,
        keyUpdateListener: EphemeralKeyUpdateListener
    ) {
        val params = hashMapOf("api_version" to apiVersion)
        stripeAccountId?.let {
            params["stripe_account"] = it
        }

        compositeDisposable.add(backendApi.createEphemeralKey(params)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { response ->
                try {
                    val rawKey = response.string()
                    keyUpdateListener.onKeyUpdate(rawKey)
                } catch (e: IOException) {
                    keyUpdateListener
                        .onKeyUpdateFailure(0, e.message ?: "")
                }
            })
    }

    fun destroy() {
        compositeDisposable.dispose()
    }
}
