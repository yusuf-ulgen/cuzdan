package com.example.cuzdan.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.databinding.ItemWalletCategoryBinding
import com.example.cuzdan.util.formatCurrency

class WalletCategoryAdapter(
    private var items: List<WalletCategorySummary> = emptyList()
) : RecyclerView.Adapter<WalletCategoryAdapter.ViewHolder>() {

    private var isPrivacyEnabled: Boolean = false
    private val expandedPositions = mutableSetOf<Int>()

    class ViewHolder(val binding: ItemWalletCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWalletCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isExpanded = expandedPositions.contains(position)

        holder.binding.apply {
            textCategoryTitle.text = item.title
            
            if (isPrivacyEnabled) {
                textCategoryTotal.text = "**** TL"
                textCategoryChange.text = "**** %***"
            } else {
                textCategoryTotal.text = item.totalValue.formatCurrency()
                textCategoryChange.text = String.format("%s %%%+.2f", 
                    item.totalProfitLoss.formatCurrency(), 
                    item.profitLossPerc
                )
            }
            
            imageExpandArrow.rotation = if (isExpanded) 90f else 270f
            recyclerChildAssets.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            if (isExpanded) {
                val childAdapter = WalletAssetAdapter(item.assets, isPrivacyEnabled)
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
            }
        }
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<WalletCategorySummary>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setItemsWithPrivacy(newItems: List<WalletCategorySummary>, privacyEnabled: Boolean) {
        items = newItems
        isPrivacyEnabled = privacyEnabled
        notifyDataSetChanged()
    }
}
