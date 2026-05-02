package com.yusufulgen.cuzdan.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.yusufulgen.cuzdan.databinding.FragmentProfitLossChartBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.data.local.entity.PortfolioHistory
import com.yusufulgen.cuzdan.util.formatCurrency
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ProfitLossChartFragment : Fragment() {

    private var _binding: FragmentProfitLossChartBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfitLossChartViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfitLossChartBinding.inflate(inflater, container, false)
        
        setupListeners()
        observeState()
        
        return binding.root
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.layoutPeriodSelector.setOnClickListener {
            showPeriodMenu()
        }
    }

    private fun observeState() {
        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { state ->
                binding.progressLoader.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                
                binding.textPeriodLabel.text = when(state.period) {
                    ChartPeriod.SevenDays -> getString(R.string.period_7_days)
                    ChartPeriod.OneMonth -> getString(R.string.period_1_month)
                    ChartPeriod.AllTime -> getString(R.string.period_all_time)
                    is ChartPeriod.Custom -> {
                        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                        "${sdf.format(Date(state.period.startDate))} - ${sdf.format(Date(state.period.endDate))}"
                    }
                }

                if (state.dataPoints.isNotEmpty()) {
                    updateChart(state.dataPoints)
                    updateSummary(state.dataPoints)
                }
            }
        }
    }

    private fun updateSummary(data: List<PortfolioHistory>) {
        val last = data.last()
        val first = data.first()
        
        // Top value: Total Profit/Loss (latest data point)
        binding.textTotalBalance.text = last.profitLoss.formatCurrency(last.currency, showSign = true)
        
        // Color for total P/L
        val isNeutralTotal = last.profitLoss.abs() < BigDecimal("0.01")
        val totalColorAttr = when {
            isNeutralTotal -> com.yusufulgen.cuzdan.R.attr.textSecondary
            last.profitLoss > BigDecimal.ZERO -> com.yusufulgen.cuzdan.R.attr.pill_green_text
            else -> com.yusufulgen.cuzdan.R.attr.pill_red_text
        }
        val totalTypedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(totalColorAttr, totalTypedValue, true)
        binding.textTotalBalance.setTextColor(totalTypedValue.data)
        
        // Bottom value: Daily change (difference between last and second-to-last point, or last - first if only 2 points)
        val prevPoint = if (data.size >= 2) data[data.size - 2] else first
        val diff = last.profitLoss.subtract(prevPoint.profitLoss)

        val isNeutralDaily = diff.abs() < BigDecimal("0.01")
        val dailyColorAttr = when {
            isNeutralDaily -> com.yusufulgen.cuzdan.R.attr.textSecondary
            diff > BigDecimal.ZERO -> com.yusufulgen.cuzdan.R.attr.pill_green_text
            else -> com.yusufulgen.cuzdan.R.attr.pill_red_text
        }
        val dailyTypedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(dailyColorAttr, dailyTypedValue, true)
            
        binding.textDailyChange.apply {
            text = diff.formatCurrency(last.currency, showSign = true)
            setTextColor(dailyTypedValue.data)
        }
    }

    private fun showPeriodMenu() {
        val options = arrayOf(
            getString(R.string.period_7_days),
            getString(R.string.period_1_month),
            getString(R.string.period_all_time),
            getString(R.string.period_custom)
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(getString(R.string.dialog_select_period))
            .setItems(options) { _, which ->
                when(which) {
                    0 -> viewModel.setPeriod(ChartPeriod.SevenDays)
                    1 -> viewModel.setPeriod(ChartPeriod.OneMonth)
                    2 -> viewModel.setPeriod(ChartPeriod.AllTime)
                    3 -> showCustomDatePicker()
                }
            }
            .show()
    }

    private fun showCustomDatePicker() {
        val dialog = CustomDateRangePickerDialog { start, end ->
            viewModel.setPeriod(ChartPeriod.Custom(start, end))
        }
        dialog.show(childFragmentManager, CustomDateRangePickerDialog.TAG)
    }

    private fun updateChart(data: List<PortfolioHistory>) {
        val entries = data.mapIndexed { index, history ->
            Entry(index.toFloat(), history.profitLoss.toFloat())
        }

        val minVal = data.minOfOrNull { it.profitLoss.toFloat() } ?: 0f
        val maxVal = data.maxOfOrNull { it.profitLoss.toFloat() } ?: 0f
        val range = maxVal - minVal

        val accentViolet = resources.getColor(R.color.pastel_violet, null)

        val dataSet = LineDataSet(entries, "P/L").apply {
            color = accentViolet
            lineWidth = 4f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            
            setDrawFilled(true)
            fillDrawable = resources.getDrawable(R.drawable.bg_chart_gradient_light, null)

            // Highlight styling
            highLightColor = resources.getColor(R.color.accent_gold, null)
            highlightLineWidth = 1f
            enableDashedHighlightLine(10f, 5f, 0f)
            setDrawHorizontalHighlightIndicator(true)
            setDrawVerticalHighlightIndicator(true)
        }

        binding.bigLineChart.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            
            val typedValue = android.util.TypedValue()
            val textColor = if (requireContext().theme.resolveAttribute(com.yusufulgen.cuzdan.R.attr.textPrimary, typedValue, true)) {
                typedValue.data
            } else {
                Color.WHITE
            }
            
            val dividerColor = if (requireContext().theme.resolveAttribute(com.yusufulgen.cuzdan.R.attr.divider_light, typedValue, true)) {
                typedValue.data
            } else {
                Color.parseColor("#33FFFFFF")
            }

            // Set Marker
            val mv = ChartMarkerView(requireContext(), data)
            mv.chartView = this
            marker = mv
            
            xAxis.apply {
                isEnabled = true
                this.textColor = textColor
                textSize = 10f
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                axisLineColor = dividerColor
                yOffset = 8f
                
                valueFormatter = object : ValueFormatter() {
                    private val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    private val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index >= 0 && index < data.size) {
                            val timestamp = data[index].date
                            val totalDiff = data.last().date - data.first().date
                            
                            return when {
                                totalDiff < 2 * 24 * 60 * 60 * 1000L -> hourFormat.format(Date(timestamp))
                                totalDiff < 365 * 24 * 60 * 60 * 1000L -> dayFormat.format(Date(timestamp))
                                else -> yearFormat.format(Date(timestamp))
                            }
                        }
                        return ""
                    }
                }
                granularity = 1f
                setLabelCount(4, false)
            }
            axisLeft.apply {
                isEnabled = true
                this.textColor = textColor
                textSize = 10f
                setDrawGridLines(true)
                gridColor = dividerColor
                gridLineWidth = 0.5f
                axisLineColor = Color.TRANSPARENT
                setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                xOffset = 5f
                
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return when {
                            range > 10000 -> {
                                if (value >= 1000000 || value <= -1000000) String.format("%.1fM", value / 1000000)
                                else if (value >= 1000 || value <= -1000) String.format("%.1fK", value / 1000)
                                else String.format("%.0f", value)
                            }
                            range > 100 -> String.format("%.0f", value)
                            else -> String.format("%.2f", value)
                        }
                    }
                }
            }
            axisRight.isEnabled = false
            
            setExtraOffsets(8f, 8f, 8f, 12f)
            invalidate()
            animateX(1000)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
