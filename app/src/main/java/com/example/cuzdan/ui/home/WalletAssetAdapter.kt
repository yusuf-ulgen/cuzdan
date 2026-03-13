package com.example.cuzdan.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.databinding.ItemAssetBinding
import java.text.NumberFormat
import java.util.Locale

class WalletAssetAdapter(
    private var assets: List<Asset> = emptyList()
) : RecyclerView.Adapter<WalletAssetAdapter.ViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    class ViewHolder(val binding: ItemAssetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val asset = assets[position]
        holder.binding.apply {
            tvAssetName.text = asset.name
            tvAssetSymbol.text = asset.symbol
            
            val totalValue = asset.amount.multiply(asset.currentPrice)
            tvAssetPrice.text = currencyFormat.format(totalValue)
            
            val profitLoss = totalValue.subtract(asset.amount.multiply(asset.averageBuyPrice))
            val isProfit = profitLoss >= java.math.BigDecimal.ZERO
            
            tvAssetChange.text = if (isProfit) "▲" else "▼"
            tvAssetChange.setTextColor(if (isProfit) 
                holder.itemView.context.getColor(com.example.cuzdan.R.color.accent_green) 
                else holder.itemView.context.getColor(com.example.cuzdan.R.color.accent_red)
            )
        }
    }

    override fun getItemCount() = assets.size

    fun updateAssets(newAssets: List<Asset>) {
        assets = newAssets
        notifyDataSetChanged()
    }
}
