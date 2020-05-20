package com.stripe.samplestore.service

import android.content.Context
import androidx.annotation.Size
import com.stripe.android.EphemeralKeyProvider
import com.stripe.android.EphemeralKeyUpdateListener
import com.stripe.samplestore.BackendApiFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SampleStoreEphemeralKeyProvider @JvmOverloads constructor(
    context: Context,
    private val stripeAccountId: String? = null
) : EphemeralKeyProvider {

    private val workScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val backendApi: BackendApi = BackendApiFactory(context).create()

    override fun createEphemeralKey(
        @Size(min = 4) apiVersion: String,
        keyUpdateListener: EphemeralKeyUpdateListener
    ) {
        val params = hashMapOf("api_version" to apiVersion)
        stripeAccountId?.let {
            params["stripe_account"] = it
        }

        workScope.launch {
            val response =
                kotlin.runCatching {
                    backendApi
                        .createEphemeralKey(params)
                        .string()
                }

            withContext(Dispatchers.Main) {
                response.fold(
                    onSuccess = {
                        keyUpdateListener.onKeyUpdate(it)
                    },
                    onFailure = {
                        keyUpdateListener
                            .onKeyUpdateFailure(0, it.message.orEmpty())
                    }
                )
            }
        }
    }
}
