package com.stripe.samplestore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.stripe.android.ApiResultCallback
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.SetupIntent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject

internal class PaymentViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val service = BackendApiFactory(context).create()
    private val stripe = StripeFactory(context).create()
    private val compositeDisposable = CompositeDisposable()

    fun createPaymentIntent(params: Map<String, Any>): LiveData<Result<JSONObject>> {
        val liveData = MutableLiveData<Result<JSONObject>>()

        compositeDisposable.add(
            service.createPaymentIntent(params.toMutableMap())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        liveData.value = Result.success(JSONObject(it.string()))
                    },
                    {
                        liveData.value = Result.failure(it)
                    }
                )
        )

        return liveData
    }

    fun createSetupIntent(params: Map<String, Any>): LiveData<Result<JSONObject>> {
        val liveData = MutableLiveData<Result<JSONObject>>()

        compositeDisposable.add(
            service
                .createSetupIntent(params.toMutableMap())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        liveData.value = Result.success(JSONObject(it.string()))
                    },
                    {
                        liveData.value = Result.failure(it)
                    }
                )
        )

        return liveData
    }

    fun confirmStripeIntent(params: Map<String, Any>): LiveData<Result<JSONObject>> {
        val liveData = MutableLiveData<Result<JSONObject>>()
        compositeDisposable.add(
            service.confirmPaymentIntent(params.toMutableMap())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        liveData.value = Result.success(JSONObject(it.string()))
                    },
                    {
                        liveData.value = Result.failure(it)
                    }
                )
        )

        return liveData
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
}
