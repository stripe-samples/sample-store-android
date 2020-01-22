package com.stripe.samplestore

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Currency

class StoreAdapter internal constructor(
    activity: StoreActivity,
    private val priceMultiplier: Float
) : RecyclerView.Adapter<StoreAdapter.ViewHolder>() {

    // Storing an activity here only so we can launch for result
    private val activity: Activity
    private val currency: Currency

    private val quantityOrdered: IntArray
    private var totalOrdered: Int = 0
    private val totalItemsChangedListener: TotalItemsChangedListener

    class ViewHolder(
        pollingLayout: View,
        private val currency: Currency,
        adapter: StoreAdapter
    ) : RecyclerView.ViewHolder(pollingLayout) {
        private val emojiTextView: TextView = pollingLayout.findViewById(R.id.tv_emoji)
        private val priceTextView: TextView = pollingLayout.findViewById(R.id.tv_price)
        private val quantityTextView: TextView = pollingLayout.findViewById(R.id.tv_quantity)
        private val addButton: ImageButton = pollingLayout.findViewById(R.id.tv_plus)
        private val removeButton: ImageButton = pollingLayout.findViewById(R.id.tv_minus)

        private var mPosition: Int = 0

        init {
            addButton.setOnClickListener { adapter.bumpItemQuantity(mPosition, true) }
            removeButton.setOnClickListener { adapter.bumpItemQuantity(mPosition, false) }
        }

        fun setHidden(hidden: Boolean) {
            val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            emojiTextView.visibility = visibility
            priceTextView.visibility = visibility
            quantityTextView.visibility = visibility
            addButton.visibility = visibility
            removeButton.visibility = visibility
        }

        fun setEmoji(emoji: String) {
            emojiTextView.text = emoji
        }

        fun setPrice(price: Int) {
            priceTextView.text = StoreUtils.getPriceString(price.toLong(), currency)
        }

        fun setQuantity(quantity: Int) {
            quantityTextView.text = quantity.toString()
        }

        fun setPosition(position: Int) {
            mPosition = position
        }
    }

    init {
        this.activity = activity
        totalItemsChangedListener = activity
        // Note: our sample backend assumes USD as currency. This code would be
        // otherwise functional if you switched that assumption on the backend and passed
        // currency code as a parameter.
        currency = Currency.getInstance(Settings.CURRENCY)
        quantityOrdered = IntArray(Products.values().size)
    }

    private fun bumpItemQuantity(index: Int, increase: Boolean) {
        if (index >= 0 && index < quantityOrdered.size) {
            if (increase) {
                quantityOrdered[index]++
                totalOrdered++
                totalItemsChangedListener.onTotalItemsChanged(totalOrdered)
            } else if (quantityOrdered[index] > 0) {
                quantityOrdered[index]--
                totalOrdered--
                totalItemsChangedListener.onTotalItemsChanged(totalOrdered)
            }
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == Products.values().size) {
            holder.setHidden(true)
        } else {
            holder.setHidden(false)
            holder.setEmoji(Products.values()[position].emoji)
            holder.setPrice(getPrice(position))
            holder.setQuantity(quantityOrdered[position])
            holder.position = position
        }
    }

    override fun getItemCount(): Int {
        return Products.values().size + 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val pollingView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_item, parent, false)

        return ViewHolder(pollingView, currency, this)
    }

    internal fun launchPurchaseActivityWithCart() {
        val storeLineItems = quantityOrdered.indices.mapNotNull { i ->
            if (quantityOrdered[i] > 0) {
                StoreLineItem(
                    Products.values()[i].emoji,
                    quantityOrdered[i],
                    getPrice(i).toLong()
                )
            } else {
                null
            }
        }
        val cart = StoreCart(currency, storeLineItems)

        activity.startActivityForResult(
            PaymentActivity.createIntent(activity, cart),
            StoreActivity.PURCHASE_REQUEST)
    }

    internal fun clearItemSelections() {
        for (i in quantityOrdered.indices) {
            quantityOrdered[i] = 0
        }
        notifyDataSetChanged()
        totalItemsChangedListener.onTotalItemsChanged(0)
    }

    private fun getPrice(position: Int): Int {
        return (Products.values()[position].price * priceMultiplier).toInt()
    }

    interface TotalItemsChangedListener {
        fun onTotalItemsChanged(totalItems: Int)
    }

    private enum class Products(internal val emoji: String, internal val price: Int) {
        Shirt("👕", 2000),
        Pants("👖", 4000),
        Dress("👗", 3000),
        MansShoe("👞", 700),
        AthleticShoe("👟", 2000),
        HighHeeledShoe("👠", 1000),
        WomansSandal("👡", 2000),
        WomansBoots("👢", 2500),
        WomansHat("👒", 800),
        Bikini("👙", 3000),
        Lipstick("💄", 2000),
        TopHat("🎩", 5000),
        Purse("👛", 5500),
        Handbag("👜", 6000),
        Sunglasses("🕶", 2000),
        WomansClothes("👚", 2500)
    }
}
