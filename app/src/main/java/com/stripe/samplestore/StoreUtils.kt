package com.stripe.samplestore

import java.text.DecimalFormat
import java.util.Currency
import kotlin.math.pow

/**
 * Class for utility functions.
 */
internal object StoreUtils {

    private const val CURRENCY_SIGN = '\u00A4'

    fun getPriceString(price: Long, currency: Currency?): String {
        val displayCurrency = currency ?: Currency.getInstance(Settings.CURRENCY)

        val fractionDigits = displayCurrency.defaultFractionDigits
        val totalLength = price.toString().length
        val builder = StringBuilder().append(CURRENCY_SIGN)

        if (fractionDigits == 0) {
            for (i in 0 until totalLength) {
                builder.append('#')
            }
            val noDecimalCurrencyFormat = DecimalFormat(builder.toString())
            noDecimalCurrencyFormat.currency = displayCurrency
            return noDecimalCurrencyFormat.format(price)
        }

        val beforeDecimal = totalLength - fractionDigits
        repeat(beforeDecimal) {
            builder.append('#')
        }
        // So we display "$0.55" instead of "$.55"
        if (totalLength <= fractionDigits) {
            builder.append('0')
        }
        builder.append('.')
        repeat(fractionDigits) {
            builder.append('0')
        }
        val modBreak = 10.0.pow(fractionDigits.toDouble())
        val decimalPrice = price / modBreak

        return DecimalFormat(builder.toString()).let {
            it.currency = displayCurrency
            it.format(decimalPrice)
        }
    }
}
