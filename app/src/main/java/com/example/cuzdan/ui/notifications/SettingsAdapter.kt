package com.example.cuzdan.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.databinding.ItemSettingsBinding

class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onSwitchChanged: (Int, Boolean) -> Unit = { _, _ -> },
    private val onItemClicked: (Int) -> Unit = {}
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSettingsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            textSettingTitle.text = item.title
            
            if (item.iconRes != null) {
                imageSettingIcon.visibility = View.VISIBLE
                imageSettingIcon.setImageResource(item.iconRes)
            } else {
                imageSettingIcon.visibility = View.GONE
            }
            
            if (item.hasSwitch) {
                switchSetting.visibility = View.VISIBLE
                imageChevron.visibility = View.GONE
                textSettingValue.visibility = View.GONE
                switchSetting.isChecked = item.isSwitchChecked
                switchSetting.setOnCheckedChangeListener { _, isChecked ->
                    onSwitchChanged(item.id, isChecked)
                }
            } else {
                switchSetting.visibility = View.GONE
                imageChevron.visibility = View.VISIBLE
                textSettingValue.visibility = if (item.value != null) View.VISIBLE else View.GONE
                textSettingValue.text = item.value
            }

            root.setOnClickListener {
                if (!item.hasSwitch) onItemClicked(item.id)
            }
        }
    }

    override fun getItemCount() = items.size
}
