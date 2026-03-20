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
                    ChartPeriod.SevenDays -> "Son 7 Gün"
                    ChartPeriod.OneMonth -> "Son 1 Ay"
                    ChartPeriod.AllTime -> "Tüm Zamanlar"
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
        
        binding.textTotalBalance.text = last.totalValue.formatCurrency(last.currency)
        
        val diff = last.totalValue.subtract(first.totalValue)
        val perc = if (first.totalValue > BigDecimal.ZERO) {
            diff.divide(first.totalValue, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else BigDecimal.ZERO
        
        val color = if (diff >= BigDecimal.ZERO) 
            resources.getColor(R.color.accent_green, null) 
        else 
            resources.getColor(R.color.accent_red, null)
            
        binding.textDailyChange.apply {
            text = "${diff.formatCurrency(last.currency, showSign = true)} (%${perc.setScale(2, java.math.RoundingMode.HALF_UP)})"
            setTextColor(color)
        }
    }

    private fun showPeriodMenu() {
        val options = arrayOf("Son 7 Gün", "Son 1 Ay", "Tüm Zamanlar", "Özel Tarih Aralığı")
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Tarih Aralığı Seçin")
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
            Entry(index.toFloat(), history.totalValue.toFloat())
        }

        val accentViolet = resources.getColor(R.color.accent_violet, null)

        val dataSet = LineDataSet(entries, "Total").apply {
            color = accentViolet
            lineWidth = 4f
            setDrawCircles(true)
            setCircleColor(accentViolet)
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            
            setDrawFilled(true)
            fillDrawable = resources.getDrawable(R.drawable.bg_chart_gradient, null)
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
                textColor = resources.getColor(R.color.text_secondary, null)
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                axisLineColor = resources.getColor(R.color.divider_color, null)
                
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
                textColor = resources.getColor(R.color.text_secondary, null)
                setDrawGridLines(true)
                gridColor = resources.getColor(R.color.divider_color, null)
                gridLineWidth = 0.5f
                axisLineColor = resources.getColor(R.color.divider_color, null)
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
