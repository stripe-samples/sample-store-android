package com.stripe.samplestore

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.Currency

@Parcelize
data class StoreCart constructor(
    val currency: Currency,
    val storeLineItems: List<StoreLineItem>
) : Parcelable {

    internal val totalPrice: Long
        get() {
            return storeLineItems.map { it.totalPrice }.sum()
        }

    internal val products: List<String>
        get() {
            return storeLineItems.flatMap { lineItem ->
                (0 until lineItem.quantity).map {lineItem.description }
            }
        }
}
