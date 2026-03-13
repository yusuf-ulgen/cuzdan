package com.example.cuzdan.ui.crypto

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.databinding.ItemAssetBinding
import com.example.cuzdan.util.formatCurrency

class CryptoAdapter : ListAdapter<Asset, CryptoAdapter.AssetViewHolder>(AssetDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AssetViewHolder(private val binding: ItemAssetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: Asset) {
            binding.tvAssetName.text = asset.name
            binding.tvAssetSymbol.text = asset.symbol
            binding.tvAssetPrice.text = asset.currentPrice.formatCurrency()
            
            // Yüzdelik değişim için şimdilik sabit
            binding.tvAssetChange.text = "▲ %0,00"
        }
    }

    class AssetDiffCallback : DiffUtil.ItemCallback<Asset>() {
        override fun areItemsTheSame(oldItem: Asset, newItem: Asset): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Asset, newItem: Asset): Boolean = oldItem == newItem
    }
}
