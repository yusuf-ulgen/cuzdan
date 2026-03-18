package com.example.cuzdan.ui.markets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.R
import com.example.cuzdan.databinding.ItemMarketPriceBinding
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.util.calculateProfitLossPercentage
import java.text.NumberFormat
import java.util.Locale

class MarketAdapter(
    private var items: List<Asset> = emptyList(),
    private val onItemClick: (Asset) -> Unit
) : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

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
            textMarketName.text = getLocalizedAssetName(item.name, root.context)
            textMarketSymbol.text = item.symbol
            textMarketPrice.text = currencyFormat.format(item.currentPrice)
            
            val isPositive = item.dailyChangePercentage >= java.math.BigDecimal.ZERO
            
            textMarketChange.text = String.format("%%%+.2f", item.dailyChangePercentage)
            textMarketChange.setTextColor(root.context.getColor(
                if (isPositive) R.color.accent_green else R.color.accent_red
            ))

            root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<Asset>) {
        items = newItems
        notifyDataSetChanged()
    }
    private fun getLocalizedAssetName(name: String, context: android.content.Context): String {
        return when(name) {
            "Türk Lirası" -> context.getString(R.string.currency_try).replace(" (₺)", "")
            "Amerikan Doları" -> context.getString(R.string.currency_usd).replace(" ($)", "")
            "Euro" -> context.getString(R.string.currency_eur).replace(" (€)", "")
            "İngiliz Sterlini" -> "British Pound" // Add to strings if needed, currently manual for quick fix
            "İsviçre Frangı" -> "Swiss Franc"
            "Japon Yeni" -> "Japanese Yen"
            else -> name
        }
    }
}
