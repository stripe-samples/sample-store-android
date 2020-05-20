package com.stripe.android.samplestore

import com.google.common.truth.Truth.assertThat
import java.util.Currency
import kotlin.test.Test

class StoreCartTest {

    @Test
    fun totalPrice_shouldReturnExpectedValue() {
        val cart = StoreCart(
            Currency.getInstance("USD"),
            listOf(
                StoreLineItem(Product.AthleticShoe, 2),
                StoreLineItem(Product.Dress, 1)
            )
        )

        assertThat(cart.totalPrice)
            .isEqualTo(7000L)
    }
}
