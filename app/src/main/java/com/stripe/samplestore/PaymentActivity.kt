package com.stripe.samplestore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.Size
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
import com.stripe.android.model.Customer
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.SetupIntent
import com.stripe.android.model.ShippingInformation
import com.stripe.android.model.ShippingMethod
import com.stripe.android.model.StripeIntent
import com.stripe.android.view.BillingAddressFields
import com.stripe.samplestore.service.BackendApi
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_payment.add_payment_method
import kotlinx.android.synthetic.main.activity_payment.add_shipping_info
import kotlinx.android.synthetic.main.activity_payment.btn_confirm_payment
import kotlinx.android.synthetic.main.activity_payment.btn_setup_intent
import kotlinx.android.synthetic.main.activity_payment.cart_items
import kotlinx.android.synthetic.main.activity_payment.progress_bar
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Currency
import java.util.HashMap
import java.util.Locale

class PaymentActivity : AppCompatActivity() {

    private val settings: Settings by lazy {
        Settings(applicationContext)
    }
    private val paymentConfiguration: PaymentConfiguration by lazy {
        PaymentConfiguration.getInstance(this)
    }

    private val compositeDisposable = CompositeDisposable()

    private val stripe: Stripe by lazy {
        if (settings.stripeAccountId != null) {
            Stripe(this, paymentConfiguration.publishableKey, settings.stripeAccountId)
        } else {
            Stripe(this, paymentConfiguration.publishableKey)
        }
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
        setContentView(R.layout.activity_payment)

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
        compositeDisposable.add(RxView.clicks(add_shipping_info)
            .subscribe { paymentSession.presentShippingFlow() })
        compositeDisposable.add(RxView.clicks(add_payment_method)
            .subscribe { paymentSession.presentPaymentMethodSelection() })

        val customerSession = CustomerSession.getInstance()
        compositeDisposable.add(RxView.clicks(btn_confirm_payment)
            .subscribe {
                customerSession.retrieveCurrentCustomer(
                    PaymentIntentCustomerRetrievalListener(this@PaymentActivity)
                )
            })
        compositeDisposable.addAll(RxView.clicks(btn_setup_intent)
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
                    processStripeIntent(result.intent)
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
                        processStripeIntent(result.intent)
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
        btn_confirm_payment.text = getString(
            R.string.pay_label,
            StoreUtils.getPriceString(cartTotal, null)
        )
    }

    private fun updateCartItems(
        totalPrice: Int,
        shippingCost: Int = 0
    ) {
        cart_items.removeAllViewsInLayout()

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

        val totalView = layoutInflater
            .inflate(R.layout.cart_item, cart_items, false)
        setupTotalPriceView(
            totalView,
            currencySymbol,
            totalPrice
        )
        cart_items.addView(totalView)
    }

    private fun addLineItems(currencySymbol: String, items: List<StoreLineItem>) {
        items.forEach { item ->
            val view = layoutInflater.inflate(
                R.layout.cart_item, cart_items, false
            )
            val displayPrice = getDisplayPrice(currencySymbol, item.totalPrice.toInt())
            val cartItem = view.findViewById<View>(R.id.cart_item)
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
            fillOutCartItemView(item, view, currencySymbol)
            cart_items.addView(view)
        }
    }

    private fun setupTotalPriceView(
        view: View,
        currencySymbol: String,
        cartTotal: Int
    ) {
        val itemViews = getItemViews(view)
        itemViews[0].text = getString(R.string.checkout_total_cost_label)
        itemViews[3].text = getDisplayPrice(currencySymbol, cartTotal)
    }

    private fun fillOutCartItemView(item: StoreLineItem, view: View, currencySymbol: String) {
        val itemViews = getItemViews(view)

        itemViews[0].text = item.description

        if (item.isProduct) {
            val quantityPriceString = "X " + item.quantity + " @"
            itemViews[1].text = quantityPriceString
            // unit price
            itemViews[2].text = getDisplayPrice(currencySymbol, item.unitPrice.toInt())
        }

        // total price
        itemViews[3].text = getDisplayPrice(currencySymbol, item.totalPrice.toInt())
    }

    @Size(value = 4)
    private fun getItemViews(view: View): List<TextView> {
        val labelView = view.findViewById<TextView>(R.id.tv_cart_emoji)
        val quantityView = view.findViewById<TextView>(R.id.tv_cart_quantity)
        val unitPriceView = view.findViewById<TextView>(R.id.tv_cart_unit_price)
        val totalPriceView = view.findViewById<TextView>(R.id.tv_cart_total_price)
        return listOf(labelView, quantityView, unitPriceView, totalPriceView)
    }

    private fun createCapturePaymentParams(
        data: PaymentSessionData,
        customerId: String,
        stripeAccountId: String?
    ): HashMap<String, Any> {
        val params = HashMap<String, Any>()
        params["amount"] = data.cartTotal.toString()
        params["payment_method"] = data.paymentMethod!!.id!!
        params["payment_method_types"] = Settings.ALLOWED_PAYMENT_METHOD_TYPES.map { it.code }
        params["currency"] = Settings.CURRENCY
        params["customer_id"] = customerId
        if (data.shippingInformation != null) {
            params["shipping"] = data.shippingInformation!!.toParamMap()
        }
        params["return_url"] = "stripe://payment-auth-return"
        if (stripeAccountId != null) {
            params["stripe_account"] = stripeAccountId
        }
        return params
    }

    private fun createSetupIntentParams(
        data: PaymentSessionData,
        customerId: String,
        stripeAccountId: String?
    ): HashMap<String, Any> {
        val params = HashMap<String, Any>()
        params["payment_method"] = data.paymentMethod!!.id!!
        params["payment_method_types"] = Settings.ALLOWED_PAYMENT_METHOD_TYPES.map { it.code }
        params["customer_id"] = customerId
        params["return_url"] = "stripe://payment-auth-return"
        params["currency"] = Settings.CURRENCY
        if (stripeAccountId != null) {
            params["stripe_account"] = stripeAccountId
        }
        return params
    }

    private fun capturePayment(customerId: String) {
        paymentSessionData?.let {
            if (it.paymentMethod == null) {
                displayError("No payment method selected")
                return
            }

            val stripeResponse = service.capturePayment(
                createCapturePaymentParams(it, customerId, settings.stripeAccountId)
            )
            compositeDisposable.add(stripeResponse
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { startLoading() }
                .doFinally { stopLoading() }
                .subscribe(
                    { onStripeIntentClientSecretResponse(it) },
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
            )
            compositeDisposable.add(stripeResponse
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { startLoading() }
                .doFinally { stopLoading() }
                .subscribe(
                    { onStripeIntentClientSecretResponse(it) },
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

    private fun processStripeIntent(stripeIntent: StripeIntent) {
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
            // reset payment method and shipping if authentication fails
            initPaymentSession()
            add_payment_method.text = getString(R.string.add_payment_method)
            add_shipping_info.text = getString(R.string.add_shipping_details)
        } else {
            displayError(
                "Unhandled Payment Intent Status: " + stripeIntent.status.toString()
            )
        }
    }

    private fun confirmStripeIntent(stripeIntentId: String, stripeAccountId: String?) {
        val params = HashMap<String, Any>()
        params["payment_intent_id"] = stripeIntentId
        if (stripeAccountId != null) {
            params["stripe_account"] = stripeAccountId
        }

        compositeDisposable.add(service.confirmPayment(params)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { startLoading() }
            .doFinally { stopLoading() }
            .subscribe(
                { onStripeIntentClientSecretResponse(it) },
                { throwable -> displayError(throwable.localizedMessage) }
            ))
    }

    @Throws(IOException::class, JSONException::class)
    private fun onStripeIntentClientSecretResponse(responseBody: ResponseBody) {
        val clientSecret = JSONObject(responseBody.string()).getString("secret")
        compositeDisposable.add(
            Observable
                .fromCallable { retrieveStripeIntent(clientSecret) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { startLoading() }
                .doFinally { stopLoading() }
                .subscribe { processStripeIntent(it) }
        )
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
        progress_bar.visibility = View.VISIBLE
        add_payment_method.isEnabled = false
        add_shipping_info.isEnabled = false

        btn_confirm_payment.tag = btn_confirm_payment.isEnabled
        btn_confirm_payment.isEnabled = false

        btn_setup_intent.tag = btn_setup_intent.isEnabled
        btn_setup_intent.isEnabled = false
    }

    private fun stopLoading() {
        progress_bar.visibility = View.INVISIBLE
        add_payment_method.isEnabled = true
        add_shipping_info.isEnabled = true

        btn_confirm_payment.isEnabled = java.lang.Boolean.TRUE == btn_confirm_payment.tag
        btn_setup_intent.isEnabled = java.lang.Boolean.TRUE == btn_setup_intent.tag
    }

    private fun getPaymentMethodDescription(paymentMethod: PaymentMethod): String {
        return when (paymentMethod.type) {
            PaymentMethod.Type.Card -> {
                paymentMethod.card?.let {
                    "${getDisplayName(it.brand)}-${it.last4}"
                } ?: ""
            }
            PaymentMethod.Type.Fpx -> {
                paymentMethod.fpx?.let {
                    "${getDisplayName(it.bank)} (FPX)"
                } ?: ""
            }
            else -> ""
        }
    }

    private fun getDisplayName(name: String?): String {
        return (name ?: "")
            .split("_")
            .joinToString(separator = " ") { it.capitalize() }
    }

    private fun onPaymentSessionDataChanged(data: PaymentSessionData) {
        paymentSessionData = data

        data.shippingMethod?.let { shippingMethod ->
            add_shipping_info.text = shippingMethod.label
            shippingCosts = shippingMethod.amount
        }

        paymentSession.setCartTotal(totalPrice)
        updateCartItems(totalPrice.toInt(), shippingCosts.toInt())
        updateConfirmPaymentButton(totalPrice)

        data.paymentMethod?.let { paymentMethod ->
            add_payment_method.text = getPaymentMethodDescription(paymentMethod)
        }

        if (data.isPaymentReadyToCharge) {
            btn_confirm_payment.isEnabled = true
            btn_setup_intent.isEnabled = true
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
            val activity = listenerActivity ?: return

            activity.displayError(errorMessage)
        }

        override fun onPaymentSessionDataChanged(data: PaymentSessionData) {
            val activity = listenerActivity ?: return
            activity.onPaymentSessionDataChanged(data)
        }
    }

    private class PaymentIntentCustomerRetrievalListener constructor(
        activity: PaymentActivity
    ) : CustomerSession.ActivityCustomerRetrievalListener<PaymentActivity>(activity) {

        override fun onCustomerRetrieved(customer: Customer) {
            val activity = activity ?: return

            customer.id?.let { activity.capturePayment(it) }
        }

        override fun onError(errorCode: Int, errorMessage: String, stripeError: StripeError?) {
            val activity = activity ?: return

            activity.displayError("Error getting payment method:. $errorMessage")
        }
    }

    private class SetupIntentCustomerRetrievalListener constructor(
        activity: PaymentActivity
    ) : CustomerSession.ActivityCustomerRetrievalListener<PaymentActivity>(activity) {

        override fun onCustomerRetrieved(customer: Customer) {
            val activity = activity ?: return
            customer.id?.let { activity.createSetupIntent(it) }
        }

        override fun onError(errorCode: Int, errorMessage: String, stripeError: StripeError?) {
            val activity = activity ?: return
            activity.displayError("Error getting payment method:. $errorMessage")
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
