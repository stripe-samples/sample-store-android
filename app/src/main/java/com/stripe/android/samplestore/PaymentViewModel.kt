package com.stripe.android.samplestore

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import com.stripe.android.Stripe
import com.stripe.android.createPaymentMethod
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.retrievePaymentIntent
import com.stripe.android.retrieveSetupIntent
import com.stripe.android.samplestore.service.BackendApi
import okhttp3.ResponseBody
import org.json.JSONObject

internal class PaymentViewModel(
    private val backendApi: BackendApi,
    private val stripe: Stripe
) : ViewModel() {
    fun createPaymentIntent(params: Map<String, Any>): LiveData<Result<JSONObject>> {
        return executeBackendMethod {
            backendApi.createPaymentIntent(params.toMutableMap())
        }
    }

    fun createSetupIntent(params: Map<String, Any>): LiveData<Result<JSONObject>> {
        return executeBackendMethod {
            backendApi.createSetupIntent(params.toMutableMap())
        }
    }

    fun confirmStripeIntent(params: Map<String, Any>): LiveData<Result<JSONObject>> {
        return executeBackendMethod {
            backendApi.confirmPaymentIntent(params.toMutableMap())
        }
    }

    private fun executeBackendMethod(
        backendMethod: suspend () -> ResponseBody
    ) = liveData {
        emit(
            runCatching {
                JSONObject(backendMethod().string())
            }
        )
    }

    fun retrievePaymentIntent(clientSecret: String) = liveData {
        emit(
            runCatching {
                stripe.retrievePaymentIntent(clientSecret)
            }
        )
    }

    fun retrieveSetupIntent(clientSecret: String) = liveData {
        emit(
            runCatching {
                stripe.retrieveSetupIntent(clientSecret)
            }
        )
    }

    fun createPaymentMethod(
        params: PaymentMethodCreateParams
    ) = liveData<Result<PaymentMethod>> {
        runCatching {
            stripe.createPaymentMethod(params)
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaymentViewModel(
                BackendApiFactory(application).create(),
                StripeFactory(application).create()
            ) as T
        }
    }
}
