package com.example.cuzdan.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.databinding.ItemReportCategoryBinding
import com.example.cuzdan.databinding.ItemAssetBinding
import com.example.cuzdan.util.formatCurrency
import java.math.BigDecimal

data class ReportCategory(
    val name: String,
    val totalValue: BigDecimal,
    val changePerc: BigDecimal,
    val changeAbs: BigDecimal,
    val assets: List<com.example.cuzdan.data.local.entity.Asset>
)

class ReportCategoryAdapter(
    private var items: List<ReportCategory> = emptyList(),
    private var isPrivacyEnabled: Boolean = false,
    private var currency: String = "TL",
    private val onItemClick: (ReportCategory) -> Unit
) : RecyclerView.Adapter<ReportCategoryAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()

    class ViewHolder(val binding: ItemReportCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isExpanded = expandedPositions.contains(position)
        
        holder.binding.apply {
            textCategoryName.text = item.name
            
            if (isPrivacyEnabled) {
                textCategoryValue.text = "**** $currency"
                textCategoryChangePerc.text = "%***"
                textCategoryChangeAbs.text = "****"
                textCategoryChangePerc.setTextColor(holder.itemView.context.getColor(com.example.cuzdan.R.color.text_label))
            } else {
                textCategoryValue.text = item.totalValue.formatCurrency(currency)
                textCategoryChangePerc.text = String.format("%%%+.1f", item.changePerc)
                textCategoryChangeAbs.text = item.changeAbs.formatCurrency(currency)

                val colorAttr = if (item.changeAbs >= BigDecimal.ZERO) {
                    com.example.cuzdan.R.attr.pill_green_text
                } else {
                    com.example.cuzdan.R.attr.pill_red_text
                }
                val bgAttr = if (item.changeAbs >= BigDecimal.ZERO) {
                    com.example.cuzdan.R.attr.pill_green_bg
                } else {
                    com.example.cuzdan.R.attr.pill_red_bg
                }

                val typedValue = android.util.TypedValue()
                holder.itemView.context.theme.resolveAttribute(colorAttr, typedValue, true)
                val colorInt = typedValue.data
                holder.itemView.context.theme.resolveAttribute(bgAttr, typedValue, true)
                val bgInt = typedValue.data

                textCategoryValue.setTextColor(holder.itemView.context.obtainStyledAttributes(intArrayOf(com.example.cuzdan.R.attr.textPrimary)).getColor(0, 0))
                textCategoryChangePerc.setTextColor(colorInt)
                textCategoryChangePerc.setBackgroundColor(bgInt)
                textCategoryChangeAbs.setTextColor(colorInt)
            }
            
            imageExpand.rotation = if (isExpanded) 180f else 0f
            recyclerChildAssets.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            if (isExpanded) {
                val childAdapter = ReportAssetInlineAdapter(item.assets, isPrivacyEnabled, currency)
                recyclerChildAssets.layoutManager = LinearLayoutManager(holder.itemView.context)
                recyclerChildAssets.adapter = childAdapter
            }

            root.setOnClickListener {
                if (isExpanded) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newList: List<ReportCategory>) {
        items = newList
        notifyDataSetChanged()
    }

    fun setPrivacyEnabled(enabled: Boolean) {
        isPrivacyEnabled = enabled
        notifyDataSetChanged()
    }

    fun setCurrency(newCurrency: String) {
        currency = newCurrency
        notifyDataSetChanged()
    }
}

class ReportAssetInlineAdapter(
    private val assets: List<com.example.cuzdan.data.local.entity.Asset>,
    private val isPrivacyEnabled: Boolean,
    private val currency: String
) : RecyclerView.Adapter<ReportAssetInlineAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAssetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val asset = assets[position]
        holder.binding.apply {
            tvAssetName.text = asset.name
            
            val totalValue = asset.amount.multiply(asset.currentPrice)
            val cost = asset.amount.multiply(asset.averageBuyPrice)
            val profitLoss = totalValue.subtract(cost)
            val isProfit = profitLoss >= BigDecimal.ZERO
            
            val profitPerc = if (cost.compareTo(BigDecimal.ZERO) > 0) {
                profitLoss.divide(cost, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
            } else BigDecimal.ZERO

            val color = if (isProfit) com.example.cuzdan.R.color.accent_green else com.example.cuzdan.R.color.accent_red
            val colorInt = holder.itemView.context.getColor(color)

            if (isPrivacyEnabled) {
                tvAssetSymbol.text = "%***"
                tvAssetChange.text = "**** $currency"
                tvAssetPrice.text = "****"
                
                tvAssetSymbol.setTextColor(holder.itemView.context.getColor(com.example.cuzdan.R.color.text_label))
                tvAssetChange.setTextColor(holder.itemView.context.obtainStyledAttributes(intArrayOf(com.example.cuzdan.R.attr.textPrimary)).getColor(0, 0))
                tvAssetPrice.setTextColor(holder.itemView.context.getColor(com.example.cuzdan.R.color.text_label))
            } else {
                tvAssetSymbol.setTextColor(colorInt)
                
                tvAssetChange.text = totalValue.formatCurrency(currency)
                tvAssetChange.setTextColor(holder.itemView.context.obtainStyledAttributes(intArrayOf(com.example.cuzdan.R.attr.textPrimary)).getColor(0, 0))
                
                tvAssetPrice.text = profitLoss.formatCurrency(currency, showSign = true)
                tvAssetPrice.setTextColor(colorInt)
            }
        }
    }

    override fun getItemCount(): Int = assets.size
}
