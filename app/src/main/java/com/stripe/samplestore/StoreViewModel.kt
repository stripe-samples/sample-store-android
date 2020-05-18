package com.stripe.samplestore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.stripe.android.CustomerSession
import com.stripe.android.StripeError
import com.stripe.android.model.Customer
import com.stripe.samplestore.service.SampleStoreEphemeralKeyProvider

internal class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settings = Settings(context)
    private val ephemeralKeyProvider = SampleStoreEphemeralKeyProvider(
        context,
        settings.stripeAccountId
    )
    private val customerSession: CustomerSession by lazy { CustomerSession.getInstance() }

    init {
        CustomerSession.initCustomerSession(
            context,
            ephemeralKeyProvider,
            settings.stripeAccountId,
            shouldPrefetchEphemeralKey = false
        )
    }

    fun retrieveCustomer(): LiveData<Result<Customer>> {
        val liveData = MutableLiveData<Result<Customer>>()
        customerSession.retrieveCurrentCustomer(object : CustomerSession.CustomerRetrievalListener {
            override fun onCustomerRetrieved(customer: Customer) {
                liveData.value = Result.success(customer)
            }

            override fun onError(
                errorCode: Int,
                errorMessage: String,
                stripeError: StripeError?
            ) {
                liveData.value = Result.failure(
                    RuntimeException("Could not retrieve Customer. $errorMessage ($errorCode). $stripeError")
                )
            }
        })
        return liveData
    }

    override fun onCleared() {
        super.onCleared()
        ephemeralKeyProvider.destroy()
    }
}
