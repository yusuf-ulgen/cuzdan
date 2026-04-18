package com.yusufulgen.cuzdan.ui.alerts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yusufulgen.cuzdan.data.local.entity.PriceAlert
import com.yusufulgen.cuzdan.databinding.ItemPriceAlertBinding

class PriceAlertListAdapter : RecyclerView.Adapter<PriceAlertListAdapter.VH>() {

    private var items: List<PriceAlert> = emptyList()

    fun submit(list: List<PriceAlert>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPriceAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(private val binding: ItemPriceAlertBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PriceAlert) {
            binding.textName.text = item.name
            binding.textSymbol.text = item.symbol
            val condition = when (item.condition.name) {
                "ABOVE" -> "≥"
                "BELOW" -> "≤"
                else -> "="
            }
            binding.textTarget.text = "$condition ${item.targetPrice}"
        }
    }
}

