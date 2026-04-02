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
            
            // 2. Total Value (Top-Right)
            if (isPrivacyEnabled) {
                tvAssetPrice.text = "**** $currency"
            } else {
                tvAssetPrice.text = totalValueInCurrency.formatCurrency(currency)
            }
            
            // Calculate Total Profit/Loss
            val totalCost = asset.amount.multiply(asset.averageBuyPrice)
            val profitLoss = totalValueInCurrency.subtract(totalCost)
            
            val profitPerc = if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                profitLoss.divide(totalCost, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
            } else BigDecimal.ZERO

            // Color logic based on profit/loss
            val sign = profitLoss.signum()
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

            // 3. Profit/Loss Percentage (Bottom-Left)
            tvAssetChangePerc.text = String.format("%%%+.2f", profitPerc)
            tvAssetChangePerc.setTextColor(color)

            // 4. Profit/Loss Amount (Bottom-Right)
            if (isPrivacyEnabled) {
                tvAssetChangeAbs.text = "****"
            } else {
                tvAssetChangeAbs.text = String.format("%s%s", if (sign > 0) "+" else "", profitLoss.formatCurrency(currency))
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
