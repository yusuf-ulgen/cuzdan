package com.example.cuzdan.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.databinding.ItemAssetBinding
import com.example.cuzdan.util.HapticManager
import com.example.cuzdan.util.formatCurrency
import android.view.View
import java.math.BigDecimal

class WalletAssetAdapter(
    private var assets: List<Asset> = emptyList(),
    private val isPrivacyEnabled: Boolean = false,
    private val currency: String = "TL",
    private val categoryTotal: java.math.BigDecimal? = null,
    private val onItemClick: (Asset, View, View) -> Unit = { _, _, _ -> }
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
            
            // 1. Asset Name (Top-Left)
            tvAssetName.text = asset.name
            
            // Set individual asset icon
            val iconRes = com.example.cuzdan.util.AssetUtils.getAssetIcon(asset.symbol, asset.assetType)
            ivAssetIcon.setImageResource(iconRes)
            
            // 2. Total Value (Top-Right)
            if (isPrivacyEnabled) {
                tvAssetPrice.text = "**** $currency"
            } else {
                tvAssetPrice.text = totalValueInCurrency.formatCurrency(currency)
            }
            
            // Calculate DAILY Profit/Loss (what the asset did today)
            // prevPrice = currentPrice / (1 + dailyChangePercentage/100)
            val dailyPerc = asset.dailyChangePercentage
            val denom = BigDecimal.ONE.add(dailyPerc.divide(BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP))
            val prevPrice = if (denom.compareTo(BigDecimal.ZERO) != 0) {
                asset.currentPrice.divide(denom, 12, java.math.RoundingMode.HALF_UP)
            } else asset.currentPrice
            val dailyPriceChange = asset.currentPrice.subtract(prevPrice)
            val dailyCashChange = asset.amount.multiply(dailyPriceChange)

            // Color logic based on daily change
            val sign = dailyCashChange.signum()
            val colorRes = when {
                sign > 0 -> com.example.cuzdan.R.color.accent_green
                sign < 0 -> com.example.cuzdan.R.color.accent_red
                else -> {
                    val typedValue = android.util.TypedValue()
                    holder.itemView.context.theme.resolveAttribute(com.example.cuzdan.R.attr.textSecondary, typedValue, true)
                    typedValue.resourceId
                }
            }
            val color = if (colorRes != 0) holder.itemView.context.getColor(colorRes) else 0xFF888888.toInt()

            // 3. Daily Change Percentage (Bottom-Left) — what the stock did today
            tvAssetChangePerc.text = String.format("%%%+.2f", dailyPerc)
            tvAssetChangePerc.setTextColor(color)

            // 4. Daily Change Amount (Bottom-Right) — cash P/L for today
            if (isPrivacyEnabled) {
                tvAssetChangeAbs.text = "****"
            } else {
                tvAssetChangeAbs.text = String.format("%s%s", if (sign > 0) "+" else "", dailyCashChange.formatCurrency(currency))
            }
            tvAssetChangeAbs.setTextColor(color)
            
            root.setOnClickListener {
                HapticManager.tap(it)
                onItemClick(asset, viewIconBg, tvAssetName)
            }
        }
    }

    override fun getItemCount() = assets.size

    fun updateAssets(newAssets: List<Asset>) {
        assets = newAssets
        notifyDataSetChanged()
    }
}
