package com.stripe.android.samplestore

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Currency

@Parcelize
internal data class StoreCart(
    val currency: Currency,
    val lineItems: List<StoreLineItem>
) : Parcelable {

    internal val totalPrice: Long
        get() = lineItems.sumBy { it.totalPrice.toInt() }.toLong()
}
