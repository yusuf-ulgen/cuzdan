package com.example.cuzdan.ui.markets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.R
import com.example.cuzdan.databinding.ItemMarketPriceBinding
import com.example.cuzdan.data.local.entity.MarketAsset
import com.example.cuzdan.util.calculateProfitLossPercentage
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class MarketAdapter(
    private var items: List<MarketAsset> = emptyList(),
    private val onItemClick: (MarketAsset) -> Unit
) : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

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
            
            val formattedPrice = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 4
            }.format(item.currentPrice)
            
            val symbol = when(item.currency) {
                "USD" -> "$"
                "EUR" -> "€"
                else -> "₺"
            }
            textMarketPrice.text = if (item.currency == "USD") "$symbol$formattedPrice" else "$formattedPrice $symbol"
            
            val sign = item.dailyChangePercentage.setScale(2, java.math.RoundingMode.HALF_UP).signum()
            textMarketChange.text = String.format("%%%+.2f", item.dailyChangePercentage)
            textMarketChange.setTextColor(root.context.getColor(
                when {
                    sign > 0 -> R.color.accent_green
                    sign < 0 -> R.color.accent_red
                    else -> R.color.white
                }
            ))

            root.setOnClickListener {
                onItemClick.invoke(item)
            }
        }
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<MarketAsset>) {
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
