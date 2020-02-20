package com.stripe.samplestore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Currency

internal class StoreAdapter internal constructor(
    private val activity: StoreActivity,
    private val priceMultiplier: Float,
    private val itemsChangedCallback: (Boolean) -> Unit = {}
) : RecyclerView.Adapter<StoreAdapter.ViewHolder>() {

    private val currency: Currency = Currency.getInstance(Settings.CURRENCY)

    private var totalOrdered: Int = 0

    private val products: List<String> = EMOJI_PRODUCTS.map {
        StoreUtils.getEmojiByUnicode(it)
    }

    // Note: our sample backend assumes USD as currency. This code would be
    // otherwise functional if you switched that assumption on the backend and passed
    // currency code as a parameter.
    private val quantityOrdered: IntArray = IntArray(products.size)

    init {
        setHasStableIds(true)
    }

    private fun adjustItemQuantity(view: View, index: Int, increase: Boolean) {
        if (increase) {
            quantityOrdered[index]++
            totalOrdered++
            itemsChangedCallback(totalOrdered > 0)
        } else if (quantityOrdered[index] > 0) {
            quantityOrdered[index]--
            totalOrdered--
            itemsChangedCallback(totalOrdered > 0)
        }

        view.announceForAccessibility(
            activity.getString(
                R.string.adjust_cart_product,
                quantityOrdered[index],
                products[index]
            )
        )

        notifyItemChanged(index)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            productName = products[position],
            price = getPrice(position),
            quantity = quantityOrdered[position]
        )
        holder.addButton.setOnClickListener {
            adjustItemQuantity(it, holder.adapterPosition, true)
        }
        holder.removeButton.setOnClickListener {
            adjustItemQuantity(it, holder.adapterPosition, false)
        }
    }

    override fun getItemId(position: Int): Long {
        return products[position].hashCode().toLong()
    }

    override fun getItemCount(): Int {
        return products.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val pollingView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_item, parent, false)

        return ViewHolder(pollingView, currency)
    }

    internal fun launchPurchaseActivityWithCart() {
        val cart = StoreCart(currency)
        for (i in quantityOrdered.indices) {
            if (quantityOrdered[i] > 0) {
                cart.addStoreLineItem(
                    products[i],
                    quantityOrdered[i],
                    getPrice(i).toLong()
                )
            }
        }

        activity.startActivityForResult(
            PaymentActivity.createIntent(activity, cart),
            StoreActivity.PURCHASE_REQUEST)
    }

    internal fun clearItemSelections() {
        quantityOrdered.forEachIndexed { index, _ ->
            quantityOrdered[index] = 0
        }
        notifyDataSetChanged()
        itemsChangedCallback(false)
    }

    private fun getPrice(position: Int): Int {
        return (EMOJI_PRICES[position] * priceMultiplier).toInt()
    }

    internal class ViewHolder(
        itemView: View,
        private val currency: Currency
    ) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.item)
        private val emojiTextView: TextView = itemView.findViewById(R.id.tv_emoji)
        private val priceTextView: TextView = itemView.findViewById(R.id.tv_price)
        private val quantityTextView: TextView = itemView.findViewById(R.id.tv_quantity)
        internal val addButton: ImageButton = itemView.findViewById(R.id.btn_add)
        internal val removeButton: ImageButton = itemView.findViewById(R.id.btn_remove)

        fun bind(
            productName: String,
            price: Int,
            quantity: Int
        ) {
            emojiTextView.text = productName
            val res = itemView.context.resources
            addButton.contentDescription = res.getString(R.string.add_item, productName)
            removeButton.contentDescription = res.getString(R.string.remove_item, productName)

            val displayPrice = StoreUtils.getPriceString(price.toLong(), currency)
            priceTextView.text = displayPrice

            quantityTextView.text = quantity.toString()

            container.contentDescription = res.getString(
                R.string.product_description,
                productName,
                quantity,
                displayPrice
            )
        }
    }

    private companion object {
        private val EMOJI_PRODUCTS = intArrayOf(
            0x1F455, 0x1F456, 0x1F457, 0x1F458, 0x1F459, 0x1F45A, 0x1F45B,
            0x1F45C, 0x1F45D, 0x1F45E, 0x1F45F, 0x1F460, 0x1F461, 0x1F462
        )

        private val EMOJI_PRICES = intArrayOf(
            2000, 4000, 3000, 700, 600, 1000, 2000,
            2500, 800, 3000, 2000, 5000, 5500, 6000
        )
    }
}
