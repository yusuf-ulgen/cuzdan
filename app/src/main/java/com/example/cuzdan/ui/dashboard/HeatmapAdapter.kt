package com.example.cuzdan.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import android.util.TypedValue
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.databinding.ItemHeatmapBlockBinding
import java.math.BigDecimal
import java.math.RoundingMode

class HeatmapAdapter : ListAdapter<Asset, HeatmapAdapter.HeatmapViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeatmapViewHolder {
        val binding = ItemHeatmapBlockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeatmapViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeatmapViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HeatmapViewHolder(private val binding: ItemHeatmapBlockBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: Asset) {
            binding.textSymbol.text = asset.symbol
            
            val profitPercent = if (asset.averageBuyPrice > BigDecimal.ZERO) {
                asset.currentPrice.subtract(asset.averageBuyPrice)
                    .divide(asset.averageBuyPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100"))
            } else BigDecimal.ZERO

            binding.textProfitPercent.text = String.format("%+.1f%%", profitPercent)

            // Determine color based on profit
            val context = binding.root.context
            val colorInt = when {
                profitPercent >= BigDecimal("5.0") -> ContextCompat.getColor(context, R.color.heatmap_green_intense)
                profitPercent >= BigDecimal("2.0") -> ContextCompat.getColor(context, R.color.heatmap_green_medium)
                profitPercent > BigDecimal.ZERO -> ContextCompat.getColor(context, R.color.heatmap_green_light)
                profitPercent <= BigDecimal("-5.0") -> ContextCompat.getColor(context, R.color.heatmap_red_intense)
                profitPercent <= BigDecimal("-2.0") -> ContextCompat.getColor(context, R.color.heatmap_red_medium)
                profitPercent < BigDecimal.ZERO -> ContextCompat.getColor(context, R.color.heatmap_red_light)
                else -> {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(R.attr.surfaceGlassStrong, typedValue, true)
                    typedValue.data
                }
            }
            
            binding.root.setCardBackgroundColor(colorInt)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Asset>() {
        override fun areItemsTheSame(oldItem: Asset, newItem: Asset): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Asset, newItem: Asset): Boolean = oldItem == newItem
    }
}
