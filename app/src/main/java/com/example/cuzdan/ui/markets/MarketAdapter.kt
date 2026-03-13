package com.example.cuzdan.ui.markets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.R
import com.example.cuzdan.databinding.ItemMarketPriceBinding
import java.text.NumberFormat
import java.util.Locale

class MarketAdapter(
    private var items: List<MarketPrice> = emptyList()
) : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    class ViewHolder(val binding: ItemMarketPriceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMarketPriceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            textMarketName.text = item.name
            textMarketSymbol.text = item.symbol
            textMarketPrice.text = String.format("%,.2f", item.currentPrice)
            
            val isPositive = item.dailyChangePerc >= java.math.BigDecimal.ZERO
            textMarketChange.text = String.format("%%%+.2f", item.dailyChangePerc)
            textMarketChange.setTextColor(root.context.getColor(
                if (isPositive) R.color.accent_green else R.color.accent_red
            ))
        }
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<MarketPrice>) {
        items = newItems
        notifyDataSetChanged()
    }
}
