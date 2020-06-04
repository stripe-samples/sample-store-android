package com.stripe.android.samplestore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import com.stripe.android.ApiResultCallback
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.SetupIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import okhttp3.ResponseBody
import org.json.JSONObject

internal class PaymentViewModel(application: Application) : AndroidViewModel(application) {
    private val backendApi = BackendApiFactory(application).create()
    private val stripe = StripeFactory(application).create()
    private val coroutineContext = Dispatchers.IO + SupervisorJob()

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
    ) = liveData<Result<JSONObject>>(coroutineContext) {
        emit(
            runCatching {
                JSONObject(backendMethod().string())
            }
        )
    }

    fun retrievePaymentIntent(clientSecret: String): LiveData<Result<PaymentIntent>> {
        val liveData = MutableLiveData<Result<PaymentIntent>>()
        stripe.retrievePaymentIntent(
            clientSecret,
            callback = object : ApiResultCallback<PaymentIntent> {
                override fun onError(e: Exception) {
                    liveData.value = Result.failure(e)
                }

                override fun onSuccess(result: PaymentIntent) {
                    liveData.value = Result.success(result)
                }
            }
        )
        return liveData
    }

    fun retrieveSetupIntent(clientSecret: String): LiveData<Result<SetupIntent>> {
        val liveData = MutableLiveData<Result<SetupIntent>>()
        stripe.retrieveSetupIntent(
            clientSecret,
            callback = object : ApiResultCallback<SetupIntent> {
                override fun onError(e: Exception) {
                    liveData.value = Result.failure(e)
                }

                override fun onSuccess(result: SetupIntent) {
                    liveData.value = Result.success(result)
                }
            }
        )
        return liveData
    }

    fun createPaymentMethod(params: PaymentMethodCreateParams): LiveData<Result<PaymentMethod>> {
        val liveData = MutableLiveData<Result<PaymentMethod>>()
        stripe.createPaymentMethod(
            params,
            callback = object : ApiResultCallback<PaymentMethod> {
                override fun onSuccess(result: PaymentMethod) {
                    liveData.value = Result.success(result)
                }

                override fun onError(e: Exception) {
                    liveData.value = Result.failure(e)
                }
            }
        )
        return liveData
    }

    override fun onCleared() {
        super.onCleared()
        coroutineContext.cancelChildren()
    }
}
