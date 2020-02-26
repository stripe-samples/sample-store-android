package com.stripe.samplestore

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Custom divider will be used in the list.
 */
internal class ItemDivider(
    context: Context,
    @DrawableRes resId: Int
) : RecyclerView.ItemDecoration() {

    private val divider: Drawable = ContextCompat.getDrawable(context, resId)!!

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val start = parent.paddingStart
        val end = parent.width - parent.paddingEnd

        repeat(parent.childCount) {
            val child = parent.getChildAt(it)

            val params = child.layoutParams as RecyclerView.LayoutParams

            val top = child.bottom + params.bottomMargin
            val bottom = top + divider.intrinsicHeight

            divider.setBounds(start, top, end, bottom)
            divider.draw(c)
        }
    }
}
