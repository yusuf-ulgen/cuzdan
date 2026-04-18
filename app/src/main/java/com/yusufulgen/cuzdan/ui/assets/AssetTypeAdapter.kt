package com.yusufulgen.cuzdan.ui.assets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yusufulgen.cuzdan.databinding.ItemAssetTypeBinding

data class AssetType(val id: Int, val title: String, val assetType: String, val iconRes: Int)

class AssetTypeAdapter(private val items: List<AssetType>, private val onClick: (AssetType) -> Unit) :
    RecyclerView.Adapter<AssetTypeAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAssetTypeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssetTypeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textTitle.text = item.title
        holder.binding.imageIcon.setImageResource(item.iconRes)
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
