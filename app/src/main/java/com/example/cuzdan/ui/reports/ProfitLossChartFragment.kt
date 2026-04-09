package com.example.cuzdan.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.cuzdan.databinding.FragmentProfitLossChartBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.PortfolioHistory
import com.example.cuzdan.util.formatCurrency
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
        
        binding.textTotalBalance.text = last.profitLoss.formatCurrency(last.currency, showSign = true)
        
        // "Kar/Zarar Değişimi" should show cumulative P/L over time, i.e. profitLoss series.
        val diff = last.profitLoss.subtract(first.profitLoss)
        val perc = BigDecimal.ZERO
        
        val colorAttr = if (diff >= BigDecimal.ZERO) com.example.cuzdan.R.attr.pill_green_text else com.example.cuzdan.R.attr.pill_red_text
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(colorAttr, typedValue, true)
        val colorInt = typedValue.data
            
        binding.textDailyChange.apply {
            text = diff.formatCurrency(last.currency, showSign = true)
            setTextColor(colorInt)
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
        }

        binding.bigLineChart.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            
            // Set Marker
            val mv = ChartMarkerView(requireContext(), data)
            mv.chartView = this
            marker = mv
            
            xAxis.apply {
                val textColorAttr = com.example.cuzdan.R.attr.textSecondary
                val dividerColorAttr = com.example.cuzdan.R.attr.divider_light
                val typedValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(textColorAttr, typedValue, true)
                val colorSecondary = typedValue.data
                requireContext().theme.resolveAttribute(dividerColorAttr, typedValue, true)
                val colorDivider = typedValue.data

                textColor = colorSecondary
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                axisLineColor = colorDivider
                
                // Add labels if possible (every few points)
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.toInt()
                        return if (idx >= 0 && idx < data.size) {
                            sdf.format(Date(data[idx].date))
                        } else ""
                    }
                }
            }
            axisLeft.apply {
                val textColorAttr = com.example.cuzdan.R.attr.textSecondary
                val dividerColorAttr = com.example.cuzdan.R.attr.divider_light
                val typedValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(textColorAttr, typedValue, true)
                val colorSecondary = typedValue.data
                requireContext().theme.resolveAttribute(dividerColorAttr, typedValue, true)
                val colorDivider = typedValue.data

                textColor = colorSecondary
                setDrawGridLines(true)
                gridColor = colorDivider
                gridLineWidth = 0.5f
                axisLineColor = colorDivider
            }
            axisRight.isEnabled = false
            
            invalidate()
            animateX(1000)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
