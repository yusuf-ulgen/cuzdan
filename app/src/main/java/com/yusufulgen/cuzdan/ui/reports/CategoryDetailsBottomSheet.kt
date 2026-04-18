package com.yusufulgen.cuzdan.ui.reports

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yusufulgen.cuzdan.data.local.entity.Asset
import com.yusufulgen.cuzdan.databinding.BottomSheetCategoryDetailsBinding
import com.yusufulgen.cuzdan.databinding.ItemCategoryAssetBinding
import com.yusufulgen.cuzdan.util.formatCurrency
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.math.BigDecimal
import java.math.RoundingMode

class CategoryDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCategoryDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCategoryDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getTheme(): Int {
        return com.yusufulgen.cuzdan.R.style.CustomBottomSheetDialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val categoryName = arguments?.getString(ARG_CATEGORY_NAME) ?: ""
        val assets = arguments?.getParcelableArrayList<Asset>(ARG_ASSETS) ?: emptyList<Asset>()
        
        binding.textCategoryTitle.text = categoryName
        binding.btnClose.setOnClickListener { dismiss() }
        
        setupRecyclerView(assets)
    }

    private fun setupRecyclerView(assets: List<Asset>) {
        binding.recyclerCategoryAssets.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCategoryAssets.adapter = CategoryAssetAdapter(assets)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY_NAME = "category_name"
        private const val ARG_ASSETS = "assets"

        fun newInstance(categoryName: String, assets: List<Asset>): CategoryDetailsBottomSheet {
            val fragment = CategoryDetailsBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_CATEGORY_NAME, categoryName)
                putParcelableArrayList(ARG_ASSETS, ArrayList(assets))
            }
            return fragment
        }
    }

    private inner class CategoryAssetAdapter(private val assets: List<Asset>) :
        RecyclerView.Adapter<CategoryAssetAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemCategoryAssetBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCategoryAssetBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val asset = assets[position]
            val value = asset.amount.multiply(asset.currentPrice)
            val cost = asset.amount.multiply(asset.averageBuyPrice)
            val profitLoss = value.subtract(cost)
            
            holder.binding.apply {
                textAssetSymbol.text = asset.symbol
                textAssetValue.text = value.formatCurrency()
                
                val plPerc = if (cost.compareTo(BigDecimal.ZERO) != 0) {
                    profitLoss.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else BigDecimal.ZERO
                
                textAssetChangePerc.text = String.format("%%%+.1f", plPerc)
                
                // Günlük değişim tutarı
                val dailyChangeAbs = asset.amount.multiply(
                    asset.currentPrice.multiply(asset.dailyChangePercentage.divide(BigDecimal("100"), 8, RoundingMode.HALF_UP))
                )
                
                textAssetChangeAbs.text = String.format("%s  %%%+.1f", 
                    dailyChangeAbs.formatCurrency(),
                    asset.dailyChangePercentage
                )
                
                // Renkler
                val plNeutral = profitLoss.abs() < BigDecimal("0.01")
                val plColor = when {
                    plNeutral -> "#888888"
                    profitLoss > BigDecimal.ZERO -> "#4CAF50"
                    else -> "#FF5252"
                }
                textAssetValue.setTextColor(Color.parseColor(plColor))
                textAssetChangePerc.setTextColor(Color.parseColor(plColor))
                textAssetChangePerc.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#15${plColor.removePrefix("#")}"))
                
                val dailyNeutral = asset.dailyChangePercentage.abs() < BigDecimal("0.01")
                val dailyColor = when {
                    dailyNeutral -> "#888888"
                    asset.dailyChangePercentage > BigDecimal.ZERO -> "#4CAF50"
                    else -> "#FF5252"
                }
                textAssetChangeAbs.setTextColor(Color.parseColor(dailyColor))
            }
        }

        override fun getItemCount() = assets.size
    }
}
