package com.example.cuzdan.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.databinding.ItemAssetBinding
import com.example.cuzdan.util.formatCurrency
import java.math.BigDecimal

class WalletAssetAdapter(
    private var assets: List<Asset> = emptyList(),
    private val isPrivacyEnabled: Boolean = false,
    private val currency: String = "TL",
    private val categoryTotal: java.math.BigDecimal? = null
) : RecyclerView.Adapter<WalletAssetAdapter.ViewHolder>() {

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
            val totalValueInCurrency = asset.amount.multiply(asset.currentPrice)
            
            // Calculate percentage if categoryTotal is provided
            val weightText = if (categoryTotal != null && categoryTotal > java.math.BigDecimal.ZERO) {
                val weight = totalValueInCurrency.multiply(java.math.BigDecimal("100"))
                    .divide(categoryTotal, 0, java.math.RoundingMode.HALF_UP)
                "%$weight "
            } else ""

            tvAssetName.text = "$weightText${asset.name}"
            tvAssetSymbol.text = asset.symbol
            
            // Note: Simplification - we assume viewmodel handles actual currency conversion of prices if needed
            // But here we format with the chosen symbol.
            val totalValue = asset.amount.multiply(asset.currentPrice)
            
            if (isPrivacyEnabled) {
                tvAssetPrice.text = "**** $currency"
                tvAssetPrice.setTextColor(holder.itemView.context.getColor(com.example.cuzdan.R.color.white))
            } else {
                tvAssetPrice.text = totalValue.formatCurrency(currency)
                tvAssetPrice.setTextColor(holder.itemView.context.getColor(com.example.cuzdan.R.color.white))
            }
            
            val profitLoss = totalValue.subtract(asset.amount.multiply(asset.averageBuyPrice))
            val isProfit = profitLoss >= BigDecimal.ZERO
            
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
