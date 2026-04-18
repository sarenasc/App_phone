package com.comparador.supermercados.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.comparador.supermercados.R
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.databinding.ItemProductBinding
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class ProductAdapter : ListAdapter<Product, ProductAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root) {

        private val currencyFormat = NumberFormat.getNumberInstance(Locale("es", "CL"))

        fun bind(product: Product) {
            b.tvName.text = product.name

            b.tvPrice.text = "$${currencyFormat.format(product.price.roundToInt())}"

            if (product.brand != null) {
                b.tvBrand.text = product.brand
                b.tvBrand.visibility = View.VISIBLE
            } else {
                b.tvBrand.visibility = View.GONE
            }

            val original = product.originalPrice
            if (original != null && original > product.price) {
                b.tvOriginalPrice.text = "$${currencyFormat.format(original.roundToInt())}"
                b.tvOriginalPrice.paintFlags = b.tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.tvOriginalPrice.visibility = View.VISIBLE

                val pct = ((original - product.price) / original * 100).roundToInt()
                b.tvDiscount.text = "-$pct%"
                b.tvDiscount.visibility = View.VISIBLE
            } else {
                b.tvOriginalPrice.visibility = View.GONE
                b.tvDiscount.visibility = View.GONE
            }

            b.ivProduct.load(product.imageUrl) {
                placeholder(R.drawable.ic_product_placeholder)
                error(R.drawable.ic_product_placeholder)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(a: Product, b: Product) =
            a.name == b.name && a.supermarket == b.supermarket
        override fun areContentsTheSame(a: Product, b: Product) = a == b
    }
}
