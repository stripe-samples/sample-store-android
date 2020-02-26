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

    // Note: our sample backend assumes USD as currency. This code would be
    // otherwise functional if you switched that assumption on the backend and passed
    // currency code as a parameter.
    private val cart: IntArray = IntArray(Product.values().size)

    init {
        setHasStableIds(true)
    }

    private fun adjustItemQuantity(view: View, index: Int, increase: Boolean) {
        if (increase) {
            cart[index]++
            totalOrdered++
            itemsChangedCallback(totalOrdered > 0)
        } else if (cart[index] > 0) {
            cart[index]--
            totalOrdered--
            itemsChangedCallback(totalOrdered > 0)
        }

        view.announceForAccessibility(
            activity.getString(
                R.string.adjust_cart_product,
                cart[index],
                Product.values()[index]
            )
        )

        notifyItemChanged(index)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            productName = Product.values()[position].emoji,
            price = getPrice(position),
            quantity = cart[position]
        )
        holder.addButton.setOnClickListener {
            adjustItemQuantity(it, holder.adapterPosition, true)
        }
        holder.removeButton.setOnClickListener {
            adjustItemQuantity(it, holder.adapterPosition, false)
        }
    }

    override fun getItemId(position: Int): Long {
        return Product.values()[position].ordinal.toLong()
    }

    override fun getItemCount(): Int {
        return Product.values().size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val pollingView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_item, parent, false)

        return ViewHolder(pollingView, currency)
    }

    internal fun launchPurchaseActivityWithCart() {
        val cart = StoreCart(currency)
        for (i in this.cart.indices) {
            if (this.cart[i] > 0) {
                cart.addStoreLineItem(
                    Product.values()[i].emoji,
                    this.cart[i],
                    getPrice(i).toLong()
                )
            }
        }

        activity.startActivityForResult(
            PaymentActivity.createIntent(activity, cart),
            StoreActivity.PURCHASE_REQUEST)
    }

    internal fun clearItemSelections() {
        cart.forEachIndexed { index, _ ->
            cart[index] = 0
        }
        notifyDataSetChanged()
        itemsChangedCallback(false)
    }

    private fun getPrice(position: Int): Int {
        return (Product.values()[position].price * priceMultiplier).toInt()
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

    private enum class Product(
        internal val emoji: String,
        internal val price: Int
    ) {
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
