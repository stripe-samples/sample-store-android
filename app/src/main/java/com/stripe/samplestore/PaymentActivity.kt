package com.stripe.samplestore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding2.view.RxView
import com.stripe.android.ApiResultCallback
import com.stripe.android.CustomerSession
import com.stripe.android.PayWithGoogleUtils
import com.stripe.android.PaymentAuthConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.PaymentSession
import com.stripe.android.PaymentSessionConfig
import com.stripe.android.PaymentSessionData
import com.stripe.android.SetupIntentResult
import com.stripe.android.Stripe
import com.stripe.android.StripeError
import com.stripe.android.model.Address
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.ConfirmSetupIntentParams
import com.stripe.android.model.Customer
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.SetupIntent
import com.stripe.android.model.ShippingInformation
import com.stripe.android.model.ShippingMethod
import com.stripe.android.model.StripeIntent
import com.stripe.android.view.BillingAddressFields
import com.stripe.samplestore.databinding.CartItemBinding
import com.stripe.samplestore.databinding.PaymentActivityBinding
import com.stripe.samplestore.service.BackendApi
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Currency
import java.util.Locale

class PaymentActivity : AppCompatActivity() {

    private val viewBinding: PaymentActivityBinding by lazy {
        PaymentActivityBinding.inflate(layoutInflater)
    }

    private val settings: Settings by lazy {
        Settings(applicationContext)
    }
    private val paymentConfiguration: PaymentConfiguration by lazy {
        PaymentConfiguration.getInstance(this)
    }

    private val compositeDisposable = CompositeDisposable()

    private val stripe: Stripe by lazy {
        Stripe(this, paymentConfiguration.publishableKey, settings.stripeAccountId)
    }

    private val paymentSession: PaymentSession by lazy {
        PaymentSession(
            this,
            PaymentSessionConfig.Builder()
                .setPrepopulatedShippingInfo(exampleShippingInfo)
                .setShippingInformationValidator(ShippingInfoValidator())
                .setShippingMethodsFactory(ShippingMethodsFactory())
                .setBillingAddressFields(BillingAddressFields.PostalCode)
                .setShouldShowGooglePay(true)
                .build()
        )
    }

    private val service: BackendApi by lazy {
        BackendApiFactory(applicationContext).create()
    }

    private val storeCart: StoreCart by lazy {
        requireNotNull(intent?.extras?.getParcelable<StoreCart>(EXTRA_CART))
    }

    private var shippingCosts = 0L

    private val totalPrice: Long
        get() = storeCart.totalPrice + shippingCosts

    private val exampleShippingInfo: ShippingInformation
        get() {
            val address = Address.Builder()
                .setCity("San Francisco")
                .setCountry("US")
                .setLine1("123 Market St")
                .setLine2("#345")
                .setPostalCode("94107")
                .setState("CA")
                .build()
            return ShippingInformation(address, "Fake Name", "(555) 555-5555")
        }

    private var paymentSessionData: PaymentSessionData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        val selectCustomization = PaymentAuthConfig.Stripe3ds2ButtonCustomization.Builder()
            .setBackgroundColor("#EC4847")
            .setTextColor("#000000")
            .build()
        val uiCustomization =
            PaymentAuthConfig.Stripe3ds2UiCustomization.Builder.createWithAppTheme(this)
                .setButtonCustomization(
                    selectCustomization,
                    PaymentAuthConfig.Stripe3ds2UiCustomization.ButtonType.SELECT
                )
                .build()
        PaymentAuthConfig.init(
            PaymentAuthConfig.Builder()
                .set3ds2Config(
                    PaymentAuthConfig.Stripe3ds2Config.Builder()
                        .setUiCustomization(uiCustomization)
                        .build()
                )
                .build()
        )

        initPaymentSession()

        updateCartItems(totalPrice.toInt())

        updateConfirmPaymentButton(totalPrice)
        compositeDisposable.add(RxView.clicks(viewBinding.buttonAddShippingInfo)
            .subscribe { paymentSession.presentShippingFlow() })
        compositeDisposable.add(RxView.clicks(viewBinding.buttonAddPaymentMethod)
            .subscribe { paymentSession.presentPaymentMethodSelection() })

        val customerSession = CustomerSession.getInstance()
        compositeDisposable.add(RxView.clicks(viewBinding.buttonConfirmPayment)
            .subscribe {
                customerSession.retrieveCurrentCustomer(
                    PaymentIntentCustomerRetrievalListener(this@PaymentActivity)
                )
            })
        compositeDisposable.addAll(RxView.clicks(viewBinding.buttonConfirmSetup)
            .subscribe {
                customerSession.retrieveCurrentCustomer(
                    SetupIntentCustomerRetrievalListener(this@PaymentActivity)
                )
            })
    }

    /*
     * Cleaning up all Rx subscriptions in onDestroy.
     */
    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val isPaymentIntentResult = stripe.onPaymentResult(
            requestCode, data,
            object : ApiResultCallback<PaymentIntentResult> {
                override fun onSuccess(result: PaymentIntentResult) {
                    stopLoading()
                    processStripeIntent(
                        result.intent,
                        isAfterConfirmation = true
                    )
                }

                override fun onError(e: Exception) {
                    stopLoading()
                    displayError(e.message)
                }
            })

        if (isPaymentIntentResult) {
            startLoading()
        } else {
            val isSetupIntentResult = stripe.onSetupResult(requestCode, data,
                object : ApiResultCallback<SetupIntentResult> {
                    override fun onSuccess(result: SetupIntentResult) {
                        stopLoading()
                        processStripeIntent(
                            result.intent,
                            isAfterConfirmation = true
                        )
                    }

                    override fun onError(e: Exception) {
                        stopLoading()
                        displayError(e.message)
                    }
                })
            if (!isSetupIntentResult) {
                paymentSession.handlePaymentData(requestCode, resultCode, data!!)
            }
        }
    }

    private fun updateConfirmPaymentButton(cartTotal: Long) {
        viewBinding.buttonConfirmPayment.text = getString(
            R.string.pay_label,
            StoreUtils.getPriceString(cartTotal, null)
        )
    }

    private fun updateCartItems(
        totalPrice: Int,
        shippingCost: Int = 0
    ) {
        viewBinding.cartItems.removeAllViewsInLayout()

        val currencySymbol = storeCart.currency.getSymbol(Locale.US)

        addLineItems(currencySymbol, storeCart.lineItems)

        addLineItems(
            currencySymbol, listOf(
                StoreLineItem(
                    getString(R.string.checkout_shipping_cost_label),
                    1,
                    shippingCost.toLong(),
                    false
                )
            )
        )

        val totalViewBinding = CartItemBinding.inflate(
            layoutInflater, viewBinding.cartItems, false
        ).also {
            it.label.text = getString(R.string.checkout_total_cost_label)
            it.totalPrice.text = getDisplayPrice(currencySymbol, totalPrice)
        }
        viewBinding.cartItems.addView(totalViewBinding.root)
    }

    private fun addLineItems(currencySymbol: String, items: List<StoreLineItem>) {
        items.forEach { item ->
            val cartItemViewBinding = CartItemBinding.inflate(
                layoutInflater, viewBinding.cartItems, false
            )
            val displayPrice = getDisplayPrice(currencySymbol, item.totalPrice.toInt())
            val cartItem = cartItemViewBinding.cartItem
            cartItem.contentDescription = if (item.isProduct) {
                getString(
                    R.string.cart_item_description,
                    item.quantity,
                    item.description,
                    displayPrice
                )
            } else {
                getString(R.string.shipping_price, displayPrice)
            }
            fillOutCartItemView(item, cartItemViewBinding, currencySymbol)
            viewBinding.cartItems.addView(cartItemViewBinding.root)
        }
    }

    private fun fillOutCartItemView(
        item: StoreLineItem, viewBinding: CartItemBinding, currencySymbol: String
    ) {
        viewBinding.label.text = item.description

        if (item.isProduct) {
            val quantityPriceString = "X " + item.quantity + " @"
            viewBinding.quantity.text = quantityPriceString
            // unit price
            viewBinding.unitPrice.text = getDisplayPrice(currencySymbol, item.unitPrice.toInt())
        }

        // total price
        viewBinding.totalPrice.text = getDisplayPrice(currencySymbol, item.totalPrice.toInt())
    }

    private fun createCapturePaymentParams(
        data: PaymentSessionData,
        customerId: String,
        stripeAccountId: String?
    ): Map<String, Any> {
        return mapOf(
            "payment_method_id" to requireNotNull(data.paymentMethod?.id),
            "country" to Settings.COUNTRY,
            "customer_id" to customerId,
            "products" to storeCart.lineItems.flatMap { lineItem ->
                mutableListOf<String>().also { products ->
                    repeat(lineItem.quantity) {
                        products.add(lineItem.description)
                    }
                }
            }
        ).plus(
            data.shippingInformation?.let {
                mapOf("shipping" to it.toParamMap())
            }.orEmpty()
        ).plus(
            stripeAccountId?.let {
                mapOf("stripe_account" to it)
            }.orEmpty()
        )
    }

    private fun createSetupIntentParams(
        data: PaymentSessionData,
        customerId: String,
        stripeAccountId: String?
    ): Map<String, Any> {
        return mapOf(
            "payment_method_id" to requireNotNull(data.paymentMethod?.id),
            "customer_id" to customerId,
            "country" to Settings.COUNTRY
        ).plus(
            stripeAccountId?.let {
                mapOf("stripe_account" to it)
            }.orEmpty()
        )
    }

    private fun capturePayment(customerId: String) {
        paymentSessionData?.let {
            if (it.paymentMethod == null) {
                displayError("No payment method selected")
                return
            }

            val stripeResponse = service.createPaymentIntent(
                createCapturePaymentParams(it, customerId, settings.stripeAccountId)
                    .toMutableMap()
            )
            compositeDisposable.add(stripeResponse
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { startLoading() }
                .doFinally { stopLoading() }
                .subscribe(
                    { responseBody -> onStripeIntentClientSecretResponse(responseBody.string()) },
                    { throwable -> displayError(throwable.localizedMessage) }
                ))
        }
    }

    private fun createSetupIntent(customerId: String) {
        paymentSessionData?.let {
            if (it.paymentMethod == null) {
                displayError("No payment method selected")
                return
            }

            val stripeResponse = service.createSetupIntent(
                createSetupIntentParams(it, customerId, settings.stripeAccountId)
                    .toMutableMap()
            )
            compositeDisposable.add(stripeResponse
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { startLoading() }
                .doFinally { stopLoading() }
                .subscribe(
                    { responseBody -> onStripeIntentClientSecretResponse(responseBody.string()) },
                    { throwable -> displayError(throwable.localizedMessage) }
                ))
        }
    }

    private fun displayError(errorMessage: String?) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(errorMessage)
            .setNeutralButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun processStripeIntent(
        stripeIntent: StripeIntent,
        isAfterConfirmation: Boolean = false
    ) {
        if (stripeIntent.requiresAction()) {
            stripe.handleNextActionForPayment(this, stripeIntent.clientSecret!!)
        } else if (stripeIntent.requiresConfirmation()) {
            confirmStripeIntent(stripeIntent.id!!, settings.stripeAccountId)
        } else if (stripeIntent.status == StripeIntent.Status.Succeeded) {
            if (stripeIntent is PaymentIntent) {
                finishPayment()
            } else if (stripeIntent is SetupIntent) {
                finishSetup()
            }
        } else if (stripeIntent.status == StripeIntent.Status.RequiresPaymentMethod) {
            if (isAfterConfirmation) {
                // reset payment method and shipping if authentication fails
                initPaymentSession()
                viewBinding.buttonAddPaymentMethod.text = getString(R.string.add_payment_method)
                viewBinding.buttonAddShippingInfo.text = getString(R.string.add_shipping_details)
            } else {
                if (stripeIntent is PaymentIntent) {
                    stripe.confirmPayment(
                        this,
                        ConfirmPaymentIntentParams.createWithPaymentMethodId(
                            paymentMethodId = paymentSessionData?.paymentMethod?.id.orEmpty(),
                            clientSecret = requireNotNull(stripeIntent.clientSecret)
                        )
                    )
                } else if (stripeIntent is SetupIntent)  {
                    stripe.confirmSetupIntent(
                        this,
                        ConfirmSetupIntentParams.create(
                            paymentMethodId = paymentSessionData?.paymentMethod?.id.orEmpty(),
                            clientSecret = requireNotNull(stripeIntent.clientSecret)
                        )
                    )
                }
            }
        } else {
            displayError(
                "Unhandled Payment Intent Status: " + stripeIntent.status.toString()
            )
        }
    }

    private fun confirmStripeIntent(stripeIntentId: String, stripeAccountId: String?) {
        val params = mapOf(
            "payment_intent_id" to stripeIntentId
        ).plus(
            stripeAccountId?.let {
                mapOf("stripe_account" to it)
            }.orEmpty()
        )

        compositeDisposable.add(
            service.confirmPaymentIntent(params.toMutableMap())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { startLoading() }
                .doFinally { stopLoading() }
                .subscribe(
                    { onStripeIntentClientSecretResponse(it.string()) },
                    { throwable -> displayError(throwable.localizedMessage) }
                )
        )
    }

    @Throws(IOException::class, JSONException::class)
    private fun onStripeIntentClientSecretResponse(responseContents: String) {
        val response = JSONObject(responseContents)

        if (response.has("success")) {
            val success = response.getBoolean("success")
            if (success) {
                finishPayment()
            } else {
                displayError("Payment failed")
            }
        } else {
            val clientSecret = response.getString("secret")
            compositeDisposable.add(
                Observable
                    .fromCallable { retrieveStripeIntent(clientSecret) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { startLoading() }
                    .doFinally { stopLoading() }
                    .subscribe(
                        { processStripeIntent(it, isAfterConfirmation = false) },
                        { throwable -> displayError(throwable.localizedMessage) }
                    )
            )
        }
    }

    private fun retrieveStripeIntent(clientSecret: String): StripeIntent {
        return when {
            clientSecret.startsWith("pi_") ->
                stripe.retrievePaymentIntentSynchronous(clientSecret)!!
            clientSecret.startsWith("seti_") ->
                stripe.retrieveSetupIntentSynchronous(clientSecret)!!
            else -> throw IllegalArgumentException("Invalid client_secret: $clientSecret")
        }
    }

    private fun finishPayment() {
        paymentSession.onCompleted()
        val data = StoreActivity.createPurchaseCompleteIntent(
            storeCart.totalPrice + shippingCosts
        )
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun finishSetup() {
        paymentSession.onCompleted()
        setResult(Activity.RESULT_OK, Intent().putExtras(Bundle()))
        finish()
    }

    private fun initPaymentSession() {
        paymentSession.init(PaymentSessionListenerImpl(this))
        paymentSession.setCartTotal(storeCart.totalPrice)
    }

    private fun startLoading() {
        viewBinding.progressBar.visibility = View.VISIBLE
        viewBinding.buttonAddPaymentMethod.isEnabled = false
        viewBinding.buttonAddShippingInfo.isEnabled = false

        viewBinding.buttonConfirmPayment.tag = viewBinding.buttonConfirmPayment.isEnabled
        viewBinding.buttonConfirmPayment.isEnabled = false

        viewBinding.buttonConfirmSetup.tag = viewBinding.buttonConfirmSetup.isEnabled
        viewBinding.buttonConfirmSetup.isEnabled = false
    }

    private fun stopLoading() {
        viewBinding.progressBar.visibility = View.INVISIBLE
        viewBinding.buttonAddPaymentMethod.isEnabled = true
        viewBinding.buttonAddShippingInfo.isEnabled = true

        viewBinding.buttonConfirmPayment.isEnabled = java.lang.Boolean.TRUE == viewBinding.buttonConfirmPayment.tag
        viewBinding.buttonConfirmSetup.isEnabled = java.lang.Boolean.TRUE == viewBinding.buttonConfirmSetup.tag
    }

    private fun getPaymentMethodDescription(paymentMethod: PaymentMethod): String {
        return when (paymentMethod.type) {
            PaymentMethod.Type.Card -> {
                paymentMethod.card?.let {
                    "${getDisplayName(it.brand)}-${it.last4}"
                }.orEmpty()
            }
            PaymentMethod.Type.Fpx -> {
                paymentMethod.fpx?.let {
                    "${getDisplayName(it.bank)} (FPX)"
                }.orEmpty()
            }
            else -> ""
        }
    }

    private fun getDisplayName(name: String?): String {
        return (name.orEmpty())
            .split("_")
            .joinToString(separator = " ") { it.capitalize() }
    }

    private fun onPaymentSessionDataChanged(data: PaymentSessionData) {
        paymentSessionData = data

        data.shippingMethod?.let { shippingMethod ->
            viewBinding.buttonAddShippingInfo.text = shippingMethod.label
            shippingCosts = shippingMethod.amount
        }

        paymentSession.setCartTotal(totalPrice)
        updateCartItems(totalPrice.toInt(), shippingCosts.toInt())
        updateConfirmPaymentButton(totalPrice)

        data.paymentMethod?.let { paymentMethod ->
            viewBinding.buttonAddPaymentMethod.text = getPaymentMethodDescription(paymentMethod)
        }

        if (data.isPaymentReadyToCharge) {
            viewBinding.buttonConfirmPayment.isEnabled = true
            viewBinding.buttonConfirmSetup.isEnabled = true
        }
    }

    private fun getDisplayPrice(currencySymbol: String, price: Int): String {
        return currencySymbol + PayWithGoogleUtils.getPriceString(price, storeCart.currency)
    }

    private class PaymentSessionListenerImpl constructor(
        activity: PaymentActivity
    ) : PaymentSession.ActivityPaymentSessionListener<PaymentActivity>(activity) {

        override fun onCommunicatingStateChanged(isCommunicating: Boolean) {}

        override fun onError(errorCode: Int, errorMessage: String) {
            listenerActivity?.displayError(errorMessage)
        }

        override fun onPaymentSessionDataChanged(data: PaymentSessionData) {
            listenerActivity?.onPaymentSessionDataChanged(data)
        }
    }

    private class PaymentIntentCustomerRetrievalListener constructor(
        activity: PaymentActivity
    ) : CustomerSession.ActivityCustomerRetrievalListener<PaymentActivity>(activity) {

        override fun onCustomerRetrieved(customer: Customer) {
            customer.id?.let { activity?.capturePayment(it) }
        }

        override fun onError(errorCode: Int, errorMessage: String, stripeError: StripeError?) {
            activity?.displayError("Error getting payment method:. $errorMessage")
        }
    }

    private class SetupIntentCustomerRetrievalListener constructor(
        activity: PaymentActivity
    ) : CustomerSession.ActivityCustomerRetrievalListener<PaymentActivity>(activity) {

        override fun onCustomerRetrieved(customer: Customer) {
            customer.id?.let { activity?.createSetupIntent(it) }
        }

        override fun onError(errorCode: Int, errorMessage: String, stripeError: StripeError?) {
            activity?.displayError("Error getting payment method:. $errorMessage")
        }
    }

    private class ShippingInfoValidator : PaymentSessionConfig.ShippingInformationValidator {
        override fun getErrorMessage(shippingInformation: ShippingInformation): String {
            return "A US address is required"
        }

        override fun isValid(shippingInformation: ShippingInformation): Boolean {
            return Locale.US.country == shippingInformation.address?.country
        }
    }

    private class ShippingMethodsFactory : PaymentSessionConfig.ShippingMethodsFactory {
        override fun create(shippingInformation: ShippingInformation): List<ShippingMethod> {
            val isCourierSupported = "94110" == shippingInformation.address?.postalCode
            val currency = Currency.getInstance(Settings.CURRENCY.toUpperCase(Locale.ROOT))
            val courierMethod = if (isCourierSupported) {
                ShippingMethod(
                    label = "1 Hour Courier",
                    identifier = "courier",
                    detail = "Arrives in the next hour",
                    amount = 1099,
                    currency = currency
                )
            } else {
                null
            }
            return listOfNotNull(
                ShippingMethod(
                    label = "UPS Ground",
                    identifier = "ups-ground",
                    detail = "Arrives in 3-5 days",
                    amount = 0,
                    currency = currency
                ),
                ShippingMethod(
                    label = "FedEx",
                    identifier = "fedex",
                    detail = "Arrives tomorrow",
                    amount = 599,
                    currency = currency
                ),
                courierMethod
            )
        }
    }

    companion object {
        private const val EXTRA_CART = "extra_cart"

        fun createIntent(activity: Activity, cart: StoreCart): Intent {
            return Intent(activity, PaymentActivity::class.java)
                .putExtra(EXTRA_CART, cart)
        }
    }
}
