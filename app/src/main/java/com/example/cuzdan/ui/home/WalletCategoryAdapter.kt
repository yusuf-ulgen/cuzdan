package com.example.cuzdan.ui.home
 
import com.example.cuzdan.data.local.entity.Asset

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.databinding.ItemWalletCategoryBinding
import com.example.cuzdan.util.formatCurrency

class WalletCategoryAdapter(
    private var items: List<WalletCategorySummary> = emptyList(),
    private val onExpandToggle: (WalletCategorySummary) -> Unit,
    private val onAssetClick: (Asset, android.view.View, android.view.View) -> Unit = { _, _, _ -> }
) : RecyclerView.Adapter<WalletCategoryAdapter.ViewHolder>() {

    private var isPrivacyEnabled: Boolean = false
    private var currency: String = "TL"

    class ViewHolder(val binding: ItemWalletCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWalletCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isExpanded = item.isExpanded
        val isNakit = item.type == com.example.cuzdan.data.local.entity.AssetType.NAKIT

        holder.binding.apply {
            textCategoryTitle.text = item.title

            // Nakit: only show title + value, no P/L
            if (isNakit) {
                textCategoryChangePerc.visibility = View.GONE
                textCategoryChangeAbs.visibility = View.GONE
                imageExpandArrow.visibility = View.GONE

                if (isPrivacyEnabled) {
                    textCategoryTotal.text = "**** $currency"
                } else {
                    textCategoryTotal.text = item.totalValue.formatCurrency(currency)
                    val textPrimaryValue = android.util.TypedValue()
                    holder.itemView.context.theme.resolveAttribute(com.example.cuzdan.R.attr.textPrimary, textPrimaryValue, true)
                    textCategoryTotal.setTextColor(textPrimaryValue.data)
                }
                return@apply
            }

            // Normal categories: show P/L
            textCategoryChangePerc.visibility = View.VISIBLE
            textCategoryChangeAbs.visibility = View.VISIBLE
            
            if (isPrivacyEnabled) {
                textCategoryTotal.text = "**** $currency"
                textCategoryChangeAbs.text = "****"
                textCategoryChangePerc.text = "%***"
                textCategoryChangePerc.setTextColor(holder.itemView.context.getColor(com.example.cuzdan.R.color.text_label))
            } else {
                textCategoryTotal.text = item.totalValue.formatCurrency(currency)
                textCategoryChangeAbs.text = item.totalProfitLoss.formatCurrency(currency)
                textCategoryChangePerc.text = String.format("%%%+.2f", item.profitLossPerc)
                
                val sign = item.profitLossPerc.setScale(2, java.math.RoundingMode.HALF_UP).signum()
                val colorResId = when {
                    sign > 0 -> com.example.cuzdan.R.color.accent_green
                    sign < 0 -> com.example.cuzdan.R.color.accent_red
                    else -> 0
                }
                val colorInt = if (colorResId != 0) {
                    holder.itemView.context.getColor(colorResId)
                } else {
                    val typedValue = android.util.TypedValue()
                    holder.itemView.context.theme.resolveAttribute(com.example.cuzdan.R.attr.textPrimary, typedValue, true)
                    typedValue.data
                }
                
                val textPrimaryValue = android.util.TypedValue()
                holder.itemView.context.theme.resolveAttribute(com.example.cuzdan.R.attr.textPrimary, textPrimaryValue, true)
                textCategoryTotal.setTextColor(textPrimaryValue.data)
                textCategoryChangeAbs.setTextColor(colorInt)
                textCategoryChangePerc.setTextColor(colorInt)
            }
            
            imageExpandArrow.visibility = View.VISIBLE
            imageExpandArrow.rotation = if (isExpanded) 90f else 270f
            recyclerChildAssets.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            if (isExpanded) {
                val childAdapter = WalletAssetAdapter(item.assets, isPrivacyEnabled, currency, item.totalValue, onAssetClick)
                recyclerChildAssets.layoutManager = LinearLayoutManager(holder.itemView.context)
                recyclerChildAssets.adapter = childAdapter
            }
            
            root.setOnClickListener {
                onExpandToggle(item)
            }
        }
    }

    override fun getItemCount() = items.size

    fun setItemsWithPrivacy(newItems: List<WalletCategorySummary>, privacyEnabled: Boolean, newCurrency: String = "TL") {
        items = newItems
        isPrivacyEnabled = privacyEnabled
        currency = newCurrency
        notifyDataSetChanged()
    }
}
