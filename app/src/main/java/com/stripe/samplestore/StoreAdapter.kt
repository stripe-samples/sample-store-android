package com.stripe.samplestore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.stripe.android.model.Customer
import com.stripe.samplestore.databinding.StoreItemBinding
import java.util.Currency

internal class StoreAdapter internal constructor(
    private val activity: StoreActivity,
    private val checkoutResultContract: ActivityResultLauncher<CheckoutContract.Args>,
    private val itemsChangedCallback: (Boolean) -> Unit = {}
) : RecyclerView.Adapter<StoreAdapter.ViewHolder>() {
    internal var customer: Customer? = null
    internal var isGooglePayReady: Boolean = false

    private val currency: Currency = Currency.getInstance(Settings.CURRENCY)

    private var totalOrdered: Int = 0

    private val productQuantities: MutableMap<Product, Int>

    init {
        setHasStableIds(true)

        productQuantities = Product.values()
            .map {
                it to 0
            }
            .toMap()
            .toMutableMap()
    }

    private fun adjustItemQuantity(view: View, index: Int, increase: Boolean) {
        val product = Product.values()[index]
        val currentQuantity = getProductQuantity(product)
        if (increase) {
            productQuantities[product] = currentQuantity + 1
            totalOrdered++
            itemsChangedCallback(totalOrdered > 0)
        } else if (currentQuantity > 0) {
            productQuantities[product] = currentQuantity - 1
            totalOrdered--
            itemsChangedCallback(totalOrdered > 0)
        }

        view.announceForAccessibility(
            activity.getString(
                R.string.adjust_cart_product,
                getProductQuantity(product),
                Product.values()[index]
            )
        )

        notifyItemChanged(index)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = Product.values()[position]
        holder.bind(
            product = product,
            quantity = getProductQuantity(product)
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
        val cart = StoreCart(
            currency,
            productQuantities
                .filterValues { it > 0 }
                .map { (product, quantity) ->
                    StoreLineItem(
                        product = product,
                        quantity = quantity
                    )
                }
        )

        checkoutResultContract.launch(
            CheckoutContract.Args(
                cart = cart,
                customerId = requireNotNull(customer?.id),
                isGooglePayReady = isGooglePayReady
            )
        )
    }

    internal fun clearItemSelections() {
        productQuantities.forEach {
            productQuantities[it.key] = 0
        }
        notifyDataSetChanged()
        itemsChangedCallback(false)
    }

    private fun getProductQuantity(product: Product): Int = productQuantities[product] ?: 0

    internal class ViewHolder(
        internal val viewBinding: StoreItemBinding,
        private val currency: Currency
    ) : RecyclerView.ViewHolder(viewBinding.root) {
        fun bind(
            product: Product,
            quantity: Int
        ) {
            viewBinding.label.text = product.emoji
            val res = itemView.context.resources
            viewBinding.buttonAdd.contentDescription = res.getString(R.string.add_item, product.emoji)
            viewBinding.buttonRemove.contentDescription = res.getString(R.string.remove_item, product.emoji)

            val displayPrice = StoreUtils.getPriceString(
                product.price.toLong(),
                currency
            )
            viewBinding.price.text = displayPrice

            viewBinding.quantity.text = quantity.toString()

            viewBinding.itemContainer.contentDescription = res.getString(
                R.string.product_description,
                product.emoji,
                quantity,
                displayPrice
            )
        }
    }
}
