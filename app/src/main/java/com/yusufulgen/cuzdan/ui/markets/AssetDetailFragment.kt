package com.yusufulgen.cuzdan.ui.markets

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
import com.yusufulgen.cuzdan.R
import androidx.transition.TransitionInflater
import android.content.res.ColorStateList
import com.yusufulgen.cuzdan.data.local.entity.AssetType
import com.yusufulgen.cuzdan.databinding.FragmentAssetDetailBinding
import com.yusufulgen.cuzdan.ui.assets.PriceAlertBottomSheet
import com.yusufulgen.cuzdan.util.showToast
import com.yusufulgen.cuzdan.util.HapticManager
import com.yusufulgen.cuzdan.util.formatCurrency
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
        
        try {
            setupToolbar()
            setupListeners()
            observeState()
            
            if (args.assetType == "NAKIT" && args.symbol == "TRY") {
                binding.layoutCostContainer.visibility = View.GONE
                binding.textAmountLabel.text = "TL"
            }
            
            viewModel.init(args.symbol, args.name, args.assetType, args.currency)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(R.string.toast_detail_load_error)
            findNavController().navigateUp()
        }
    }

    private fun setupToolbar() {
        binding.textTitleDetail.text = getLocalizedAssetName(args.name)
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnDelete.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.detail_title))
                .setMessage(getString(R.string.reset_warning_message))
                .setPositiveButton(getString(R.string.dialog_confirm)) { _, _ -> viewModel.deleteAsset() }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }
        binding.btnAlert.setOnClickListener {
            val state = viewModel.uiState.value
            val currentType = try { AssetType.valueOf(args.assetType) } catch (e: Exception) { AssetType.BIST }
            val bottomSheet = PriceAlertBottomSheet(
                symbol = state.symbol,
                name = state.name,
                assetType = currentType,
                currentPrice = state.currentPrice,
                onAlertSet = { alert ->
                    viewModel.setPriceAlert(alert)
                    showToast(R.string.toast_alert_created)
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
                binding.editAmount.error = getString(R.string.alert_error_price)
                return@setOnClickListener
            }
            
            val amount = amountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val cost = costStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            
            // Maliyet zorunlu: BUY modunda ve NAKIT olmayan varlıklarda
            val isBuyMode = binding.toggleTransactionType.checkedButtonId == R.id.btnBuy
            val isNakit = args.assetType == "NAKIT"
            if (isBuyMode && !isNakit && (costStr.isEmpty() || cost <= BigDecimal.ZERO)) {
                binding.editCost.error = getString(R.string.alert_error_price)
                return@setOnClickListener
            }
            
            viewModel.saveAsset(amount, cost, args.assetType)
        }

        binding.toggleTransactionType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val type = if (checkedId == R.id.btnBuy) TransactionType.BUY else TransactionType.SELL
                viewModel.setTransactionType(type)
                
                if (type == TransactionType.SELL) {
                    binding.layoutCostContainer.visibility = View.GONE
                    binding.btnSave.text = getString(R.string.detail_sell)
                    binding.btnSave.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.accent_red, null))
                } else {
                    if (args.assetType != "NAKIT" || args.symbol != "TRY") {
                        binding.layoutCostContainer.visibility = View.VISIBLE
                    }
                    binding.btnSave.text = getString(R.string.detail_buy)
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
                        showToast(R.string.toast_asset_saved)
                        findNavController().navigateUp()
                    }
                    if (state.isDeleted) {
                        HapticManager.success(requireContext())
                        showToast(R.string.toast_asset_deleted)
                        findNavController().navigateUp()
                    }
                    if (state.errorMessage != null) {
                        HapticManager.error(requireContext())
                        showToast(state.errorMessage)
                    }
                }
            }
        }
    }

    private fun updateUI(state: AssetDetailUiState) {
        binding.textCurrentPrice.text = state.currentPrice.formatCurrency(state.displayCurrency)
        binding.textPortfolioName.text = getString(R.string.detail_portfolio_prefix, state.portfolioName)
        
        // Load icon: each asset type uses its own primitive category icon
        val iconRes = when(args.assetType) {
            "KRIPTO" -> R.drawable.ic_crypto
            "FON" -> R.drawable.ic_funds
            "BIST" -> R.drawable.ic_bist
            "EMTIA" -> R.drawable.ic_commodity
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
        binding.textCurrentAmountHeld.text = getString(R.string.detail_held_amount, state.currentAmount.toPlainString())
        
        val isPositive = state.dailyChangePercentage >= BigDecimal.ZERO
        val colorAttr = if (isPositive) com.yusufulgen.cuzdan.R.attr.pill_green_text else com.yusufulgen.cuzdan.R.attr.pill_red_text
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(colorAttr, typedValue, true)
        val colorInt = typedValue.data

        binding.textPriceChange.text = String.format("%%%+.2f", state.dailyChangePercentage)
        binding.textPriceChange.setTextColor(colorInt)
        
        setupChart(state.history)
    }

    private fun setupChart(history: List<Pair<Long, Double>>) {
        if (history.isEmpty()) {
            binding.priceChart.setNoDataText(getString(R.string.error_loading))
            binding.priceChart.invalidate()
            return
        }

        val entries = history.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }

        val accentViolet = resources.getColor(R.color.pastel_violet, null)

        val dataSet = LineDataSet(entries, getString(R.string.label_profit_loss)).apply {
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
                val textColorAttr = com.yusufulgen.cuzdan.R.attr.textSecondary
                val typedValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(textColorAttr, typedValue, true)

                textColor = typedValue.data
                setDrawGridLines(true) // Enabled for better price reference
                gridColor = Color.parseColor("#33FFFFFF") // Subtle grid
                setDrawLabels(true) // Ensure labels are visible
                axisLineColor = Color.TRANSPARENT
                setPosition(com.github.mikephil.charting.components.YAxis.YAxisLabelPosition.INSIDE_CHART)
            }
            axisRight.isEnabled = false
            
            setTouchEnabled(true)
            setPinchZoom(true)
            animateX(1200)
            invalidate()
        }

    }

    private fun getLocalizedAssetName(name: String): String {
        return when {
            name == "Türk Lirası" || name == "Turkish Lira" -> getString(R.string.currency_try).replace(" (₺)", "")
            name == "Amerikan Doları" || name == "US Dollar" || name == "American Dollar" || name == "United States Dollar" -> getString(R.string.currency_usd).replace(" ($)", "")
            name == "Euro" -> getString(R.string.currency_eur).replace(" (€)", "")
            name == "İngiliz Sterlini" || name == "British Pound" -> getString(R.string.currency_gbp)
            name == "İsviçre Frangı" || name == "Swiss Franc" -> getString(R.string.currency_chf)
            name == "Japon Yeni" || name == "Japanese Yen" -> getString(R.string.currency_jpy)
            name == "Avustralya Doları" || name == "Australian Dollar" -> getString(R.string.currency_aud)
            name == "Kanada Doları" || name == "Canadian Dollar" -> getString(R.string.currency_cad)
            name == "Altın (Ons)" || name == "Gold (Oz)" -> getString(R.string.commodity_gold_oz)
            name == "Gram Altın" || name == "Gram Gold" -> getString(R.string.commodity_gram_gold)
            name == "Altın" || name == "Gold" -> getString(R.string.commodity_gold)
            name == "Gümüş" || name == "Silver" -> getString(R.string.commodity_silver)
            name == "Bakır" || name == "Copper" -> getString(R.string.commodity_copper)
            name == "Platin" || name == "Platinum" -> getString(R.string.commodity_platinum)
            name == "Paladyum" || name == "Palladium" -> getString(R.string.commodity_palladium)
            else -> name
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
