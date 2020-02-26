package com.stripe.samplestore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.stripe.samplestore.databinding.StoreItemBinding
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
        holder.viewBinding.buttonAdd.setOnClickListener {
            adjustItemQuantity(it, holder.adapterPosition, true)
        }
        holder.viewBinding.buttonRemove.setOnClickListener {
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
        return ViewHolder(
            StoreItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            currency
        )
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
        internal val viewBinding: StoreItemBinding,
        private val currency: Currency
    ) : RecyclerView.ViewHolder(viewBinding.root) {
        fun bind(
            productName: String,
            price: Int,
            quantity: Int
        ) {
            viewBinding.label.text = productName
            val res = itemView.context.resources
            viewBinding.buttonAdd.contentDescription = res.getString(R.string.add_item, productName)
            viewBinding.buttonRemove.contentDescription = res.getString(R.string.remove_item, productName)

            val displayPrice = StoreUtils.getPriceString(price.toLong(), currency)
            viewBinding.price.text = displayPrice

            viewBinding.quantity.text = quantity.toString()

            viewBinding.itemContainer.contentDescription = res.getString(
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
