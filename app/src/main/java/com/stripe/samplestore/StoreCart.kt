package com.stripe.samplestore

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.Currency
import java.util.UUID

@Parcelize
data class StoreCart(
    val currency: Currency,
    val storeLineItems: MutableMap<String, StoreLineItem> = mutableMapOf()
) : Parcelable {
    internal val lineItems: List<StoreLineItem>
        get() = storeLineItems.values.toList()

    internal val totalPrice: Long
        get() = storeLineItems.values.sumBy { it.totalPrice.toInt() }.toLong()

    fun addStoreLineItem(description: String, quantity: Int, unitPrice: Long) {
        storeLineItems[UUID.randomUUID().toString()] = StoreLineItem(
            description, quantity, unitPrice
        )
    }
}
