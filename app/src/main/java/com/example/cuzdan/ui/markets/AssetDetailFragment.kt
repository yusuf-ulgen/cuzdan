package com.example.cuzdan.ui.markets

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.cuzdan.R
import androidx.transition.TransitionInflater
import android.content.res.ColorStateList
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.databinding.FragmentAssetDetailBinding
import com.example.cuzdan.ui.assets.PriceAlertBottomSheet
import com.example.cuzdan.util.HapticManager
import com.example.cuzdan.util.formatCurrency
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal

@AndroidEntryPoint
class AssetDetailFragment : Fragment() {

    private var _binding: FragmentAssetDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AssetDetailViewModel by viewModels()
    private val args: AssetDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        sharedElementEnterTransition = TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
        _binding = FragmentAssetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupListeners()
        observeState()
        
        if (args.assetType == "NAKIT" && args.symbol == "TRY") {
            binding.layoutCostContainer.visibility = View.GONE
            binding.textAmountLabel.text = "TL"
        }
        
        viewModel.init(args.symbol, args.name, args.assetType, args.currency)

    }

    private fun setupToolbar() {
        binding.textTitleDetail.text = args.name
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnDelete.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Varlığı Sil")
                .setMessage("Bu varlığı portföyünüzden silmek istediğinize emin misiniz?")
                .setPositiveButton("Sil") { _, _ -> viewModel.deleteAsset() }
                .setNegativeButton("İptal", null)
                .show()
        }
        binding.btnAlert.setOnClickListener {
            val state = viewModel.uiState.value
            val bottomSheet = PriceAlertBottomSheet(
                symbol = state.symbol,
                name = state.name,
                assetType = AssetType.valueOf(args.assetType),
                currentPrice = state.currentPrice,
                onAlertSet = { alert ->
                    viewModel.setPriceAlert(alert)
                    Toast.makeText(requireContext(), "Alarm kuruldu!", Toast.LENGTH_SHORT).show()
                }
            )
            bottomSheet.show(childFragmentManager, PriceAlertBottomSheet.TAG)
        }
    }


    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            HapticManager.tap(it)
            val amountStr = binding.editAmount.text.toString()
            val costStr = binding.editCost.text.toString()
            
            if (amountStr.isEmpty()) {
                binding.editAmount.error = "Miktar boş olamaz"
                return@setOnClickListener
            }
            
            val amount = amountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val cost = costStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            
            viewModel.saveAsset(amount, cost, args.assetType)
        }

        binding.toggleTransactionType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val type = if (checkedId == R.id.btnBuy) TransactionType.BUY else TransactionType.SELL
                viewModel.setTransactionType(type)
                
                if (type == TransactionType.SELL) {
                    binding.layoutCostContainer.visibility = View.GONE
                    binding.btnSave.text = "SATIŞI KAYDET"
                    binding.btnSave.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.accent_red, null))
                } else {
                    if (args.assetType != "NAKIT" || args.symbol != "TRY") {
                        binding.layoutCostContainer.visibility = View.VISIBLE
                    }
                    binding.btnSave.text = "ALIŞI KAYDET"
                    binding.btnSave.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.accent_violet, null))
                }
            }
        }

        binding.chartRangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnRange1W -> viewModel.updateRange("1w")
                    R.id.btnRange1M -> viewModel.updateRange("1mo")
                    R.id.btnRange1Y -> viewModel.updateRange("1y")
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                    if (state.isSaved) {
                        HapticManager.success(requireContext())
                        Toast.makeText(requireContext(), "Varlık başarıyla güncellendi", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    if (state.isDeleted) {
                        HapticManager.success(requireContext())
                        Toast.makeText(requireContext(), "Varlık silindi", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    if (state.errorMessage != null) {
                        HapticManager.error(requireContext())
                        Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateUI(state: AssetDetailUiState) {
        binding.textCurrentPrice.text = state.currentPrice.formatCurrency()
        binding.textPortfolioName.text = "Portföy: ${state.portfolioName}"
        
        // Load icon (Simplified placeholder logic, can be enhanced with coil if url available)
        val iconRes = when(args.assetType) {
            "KRIPTO" -> R.drawable.ic_crypto
            "FON" -> R.drawable.ic_funds
            "BIST" -> R.drawable.ic_bist
            "EMTIA" -> R.drawable.ic_currency
            "NAKIT", "DOVIZ" -> {
                if (args.symbol == "TRY" || args.symbol == "TL") R.drawable.ic_tl
                else if (args.symbol == "USD") R.drawable.ic_usd
                else if (args.symbol == "EUR") R.drawable.ic_eur
                else R.drawable.ic_currency
            }
            else -> R.drawable.ic_asset_placeholder
        }
        binding.ivAssetIconDetail.setImageResource(iconRes)
        
        // Show held amount
        binding.textCurrentAmountHeld.text = "Eldeki: ${state.currentAmount.toPlainString()}"
        
        val isPositive = state.dailyChangePercentage >= BigDecimal.ZERO
        val colorAttr = if (isPositive) com.example.cuzdan.R.attr.pill_green_text else com.example.cuzdan.R.attr.pill_red_text
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(colorAttr, typedValue, true)
        val colorInt = typedValue.data

        binding.textPriceChange.text = String.format("%%%+.2f", state.dailyChangePercentage)
        binding.textPriceChange.setTextColor(colorInt)
        
        setupChart(state.history)
    }

    private fun setupChart(history: List<Pair<Long, Double>>) {
        if (history.isEmpty()) {
            binding.priceChart.setNoDataText("Geçmiş veri bulunamadı")
            binding.priceChart.invalidate()
            return
        }

        val entries = history.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }

        val accentViolet = resources.getColor(R.color.pastel_violet, null)

        val dataSet = LineDataSet(entries, "Fiyat").apply {
            color = accentViolet
            lineWidth = 3f
            setDrawCircles(false)
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            
            setDrawFilled(true)
            fillDrawable = resources.getDrawable(R.drawable.bg_chart_gradient_light, null)
        }

        binding.priceChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            
            xAxis.isEnabled = false
            axisLeft.apply {
                val textColorAttr = com.example.cuzdan.R.attr.textSecondary
                val typedValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(textColorAttr, typedValue, true)

                textColor = typedValue.data
                setDrawGridLines(false)
                axisLineColor = Color.TRANSPARENT
            }
            axisRight.isEnabled = false
            
            setTouchEnabled(true)
            setPinchZoom(true)
            animateX(1200)
            invalidate()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
