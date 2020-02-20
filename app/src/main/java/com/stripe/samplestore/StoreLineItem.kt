package com.stripe.samplestore

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * Represents a single line item for purchase in this store.
 */
@Parcelize
data class StoreLineItem(
    val description: String,
    val quantity: Int,
    val unitPrice: Long,
    val isProduct: Boolean = true
) : Parcelable {
    internal val totalPrice: Long
        get() = unitPrice * quantity
}
