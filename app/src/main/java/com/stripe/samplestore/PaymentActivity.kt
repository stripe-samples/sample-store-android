package com.stripe.samplestore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stripe.android.*
import com.stripe.android.model.*
import com.stripe.android.view.BillingAddressFields
import com.stripe.samplestore.databinding.CartItemBinding
import com.stripe.samplestore.databinding.PaymentActivityBinding
import com.stripe.samplestore.service.BackendApi
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.util.*

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

        viewBinding.buttonAddShippingInfo.setOnClickListener {
            paymentSession.presentShippingFlow()
        }

        viewBinding.buttonAddPaymentMethod.setOnClickListener {
            paymentSession.presentPaymentMethodSelection()
        }

        viewBinding.buttonConfirmPayment.setOnClickListener {
            retrieveCurrentCustomer()
                .doOnSubscribe { startLoading() }
                .flatMapCompletable { customer -> capturePayment(customer.id!!) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { stopLoading() },
                    {
                        stopLoading()
                        when(it) {
                            is CustomerRetrievalException -> {
                                // TODO: we can handle specific exception here
                            }
                        }
                        displayError(it.message)
                    }
                ).let { compositeDisposable.add(it) }
        }

        viewBinding.buttonConfirmSetup.setOnClickListener {
            retrieveCurrentCustomer()
                .doOnSubscribe { startLoading() }
                .flatMapCompletable { customer -> createSetupIntent(customer.id!!) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { stopLoading() },
                    {
                        stopLoading()
                        displayError(it.message)
                    }
                ).let { compositeDisposable.add(it) }
        }
    }

    private fun retrieveCurrentCustomer(): Single<Customer> = Single.create { emitter ->
        val customerSession = CustomerSession.getInstance()
        customerSession.retrieveCurrentCustomer(object : CustomerSession.CustomerRetrievalListener {
            override fun onCustomerRetrieved(customer: Customer) {
                emitter.onSuccess(customer)
            }

            override fun onError(errorCode: Int, errorMessage: String, stripeError: StripeError?) {
                emitter.onError(CustomerRetrievalException(errorCode, errorMessage, stripeError))
            }
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
                    processStripeIntentCompletable(
                        result.intent,
                        isAfterConfirmation = true
                    ).subscribe(
                        { stopLoading() },
                        {
                            stopLoading()
                            displayError(it.message)
                        }
                    ).let { compositeDisposable.add(it) }
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
                        processStripeIntentCompletable(
                            result.intent,
                            isAfterConfirmation = true
                        ).subscribe(
                            { stopLoading() },
                            {
                                stopLoading()
                                displayError(it.message)
                            }
                        ).let {compositeDisposable.add(it)}
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
    ): HashMap<String, Any> {
        val params = HashMap<String, Any>()
        params["amount"] = data.cartTotal.toString()
        params["payment_method_id"] = requireNotNull(data.paymentMethod?.id)
        params["payment_method_types"] = Settings.ALLOWED_PAYMENT_METHOD_TYPES.map { it.code }
        params["currency"] = Settings.CURRENCY
        params["country"] = Settings.COUNTRY
        params["customer_id"] = customerId
        data.shippingInformation?.let {
            params["shipping"] = it.toParamMap()
        }
        params["return_url"] = "stripe://payment-auth-return"
        stripeAccountId?.let {
            params["stripe_account"] = it
        }

        params["products"] = storeCart.lineItems.flatMap { lineItem ->
            mutableListOf<String>().also { products ->
                repeat(lineItem.quantity) {
                    products.add(lineItem.description)
                }
            }
        }

        return params
    }

    private fun createSetupIntentParams(
        data: PaymentSessionData,
        customerId: String,
        stripeAccountId: String?
    ): HashMap<String, Any> {
        val params = HashMap<String, Any>()
        params["payment_method_id"] = requireNotNull(data.paymentMethod?.id)
        params["payment_method_types"] = Settings.ALLOWED_PAYMENT_METHOD_TYPES.map { it.code }
        params["customer_id"] = customerId
        params["return_url"] = "stripe://payment-auth-return"
        params["currency"] = Settings.CURRENCY
        params["country"] = Settings.COUNTRY
        stripeAccountId?.let {
            params["stripe_account"] = it
        }
        return params
    }

    private fun capturePayment(customerId: String): Completable =
        checkPaymentSessionData()
            .observeOn(Schedulers.io()) // network call
            .flatMap { sessionData ->
                service.createPaymentIntent(
                    createCapturePaymentParams(
                        sessionData,
                        customerId,
                        settings.stripeAccountId
                    )
                )
            }
            .flatMapCompletable { responseBody ->
                onStripeIntentClientSecretResponseCompletable(responseBody.string())
            }

    private fun checkPaymentSessionData(): Single<PaymentSessionData> =
        if (paymentSessionData != null) {
            Single.just(paymentSessionData)
        } else {
            Single.error(RuntimeException("PaymentSession is not initialized"))
        }

    private fun createSetupIntent(customerId: String): Completable {
        return checkPaymentSessionData()
            .observeOn(Schedulers.io())
            .flatMapCompletable { paymentSessionData ->
                if (paymentSessionData.paymentMethod == null) {
                    Completable.error(RuntimeException("No payment method selected"))
                } else {

                    val stripeResponse = service.createSetupIntent(
                        createSetupIntentParams(
                            paymentSessionData,
                            customerId,
                            settings.stripeAccountId
                        )
                    )
                    stripeResponse.flatMapCompletable {
                        onStripeIntentClientSecretResponseCompletable(
                            it.toString()
                        )
                    }
                }
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

    private fun processStripeIntentCompletable(
        stripeIntent: StripeIntent,
        isAfterConfirmation: Boolean = false
    ): Completable {

        return when {
            stripeIntent.requiresAction() -> Completable.fromAction {
                stripe.handleNextActionForPayment(this, stripeIntent.clientSecret!!)
            }
            stripeIntent.requiresConfirmation() ->
                confirmStripeIntent(stripeIntent.id!!, settings.stripeAccountId)

            stripeIntent.status == StripeIntent.Status.Succeeded -> {
                when (stripeIntent) {
                    is PaymentIntent -> finishPayment()

                    is SetupIntent -> Completable.fromAction {
                        finishSetup()
                    }
                    else -> Completable.error(RuntimeException("Fuck off"))
                }
            }
            stripeIntent.status == StripeIntent.Status.RequiresPaymentMethod -> {
                if (isAfterConfirmation) {
                    Completable.fromAction {
                        initPaymentSession()
                        viewBinding.buttonAddPaymentMethod.text =
                            getString(R.string.add_payment_method)
                        viewBinding.buttonAddShippingInfo.text =
                            getString(R.string.add_shipping_details)
                    }
                } else {
                    when (stripeIntent) {
                        is PaymentIntent -> Completable.fromAction {
                            stripe.confirmPayment(
                                this,
                                ConfirmPaymentIntentParams.createWithPaymentMethodId(
                                    paymentMethodId = paymentSessionData?.paymentMethod?.id.orEmpty(),
                                    clientSecret = requireNotNull(stripeIntent.clientSecret)
                                )
                            )
                        }

                        is SetupIntent -> Completable.fromAction {
                            stripe.confirmSetupIntent(
                                this,
                                ConfirmSetupIntentParams.create(
                                    paymentMethodId = paymentSessionData?.paymentMethod?.id.orEmpty(),
                                    clientSecret = requireNotNull(stripeIntent.clientSecret)
                                )
                            )
                        }
                        else -> Completable.error(RuntimeException("Unhandled stripeIntent: $stripeIntent"))
                    }
                }
            }
            else -> Completable.error(RuntimeException("Unhandled Payment Intent Status: ${stripeIntent.status}"))
        }
    }

    private fun confirmStripeIntent(stripeIntentId: String, stripeAccountId: String?): Completable {
        val params = HashMap<String, Any>()
        params["payment_intent_id"] = stripeIntentId
        if (stripeAccountId != null) {
            params["stripe_account"] = stripeAccountId
        }

        return service.confirmPaymentIntent(params)
            .flatMapCompletable { onStripeIntentClientSecretResponseCompletable(it.toString()) }
    }

    private fun onStripeIntentClientSecretResponseCompletable(responseContents: String): Completable {
        val response = JSONObject(responseContents)
        return if (response.has("success")) {
            val success = response.getBoolean("success")
            if (success) {
                finishPayment()
            } else {
                Completable.fromAction {
                    displayError("Payment failed")
                }
            }
        } else {
            val clientSecret = response.getString("secret")

            retrieveStripeIntent(clientSecret)
                .flatMapCompletable {
                    processStripeIntentCompletable(
                        it,
                        isAfterConfirmation = false
                    )
                }
        }
    }

    private fun retrieveStripeIntent(clientSecret: String): Single<StripeIntent> {
        return when {
            clientSecret.startsWith("pi_") ->
                Single.just(stripe.retrievePaymentIntentSynchronous(clientSecret)!!)
            clientSecret.startsWith("seti_") ->
                Single.just(stripe.retrieveSetupIntentSynchronous(clientSecret)!!)
            else -> Single.error(IllegalArgumentException("Invalid client_secret: $clientSecret"))
        }
    }

    private fun finishPayment(): Completable = Completable.fromAction {
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

        viewBinding.buttonConfirmPayment.isEnabled =
            java.lang.Boolean.TRUE == viewBinding.buttonConfirmPayment.tag
        viewBinding.buttonConfirmSetup.isEnabled =
            java.lang.Boolean.TRUE == viewBinding.buttonConfirmSetup.tag
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
