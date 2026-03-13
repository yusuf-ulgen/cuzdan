package com.example.cuzdan.ui.reports

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.R
import com.example.cuzdan.databinding.ItemReportCategoryBinding

data class ReportCategory(
    val name: String,
    val value: String,
    val changePerc: String,
    val changeAbs: String,
    val isPositive: Boolean
)

class ReportCategoryAdapter(
    private var items: List<ReportCategory>,
    private var isHidden: Boolean = false
) : RecyclerView.Adapter<ReportCategoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemReportCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textCategoryName.text = item.name
        
        if (isHidden) {
            holder.binding.textCategoryValue.text = "*****"
            holder.binding.textCategoryChangeAbs.text = "*****"
        } else {
            holder.binding.textCategoryValue.text = item.value
            holder.binding.textCategoryChangeAbs.text = item.changeAbs
        }
        
        holder.binding.textCategoryChangePerc.text = item.changePerc
        
        val color = if (item.isPositive) {
            Color.parseColor("#4CAF50") // accent_green
        } else {
            Color.parseColor("#FF5252") // accent_red
        }
        
        holder.binding.textCategoryChangePerc.setTextColor(color)
        if (!isHidden && !item.isPositive) {
            holder.binding.textCategoryChangeAbs.setTextColor(color)
            holder.binding.textCategoryValue.setTextColor(color)
        } else if (!isHidden) {
            holder.binding.textCategoryValue.setTextColor(Color.WHITE)
            holder.binding.textCategoryChangeAbs.setTextColor(Color.parseColor("#B0B8D1"))
        }
    }

    override fun getItemCount() = items.size

    fun setHidden(hidden: Boolean) {
        isHidden = hidden
        notifyDataSetChanged()
    }
}
