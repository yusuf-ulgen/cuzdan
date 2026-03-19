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

class ProfitLossChartFragment : Fragment() {

    private var _binding: FragmentProfitLossChartBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfitLossChartBinding.inflate(inflater, container, false)
        
        setupChart()
        
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        return binding.root
    }

    private fun setupChart() {
        val entries = listOf(
            Entry(0f, 1000f),
            Entry(1f, 1100f),
            Entry(2f, 950f),
            Entry(3f, 1200f),
            Entry(4f, 1800f),
            Entry(5f, 1750f),
            Entry(6f, 1600f),
            Entry(7f, 1900f)
        )

        val accentViolet = resources.getColor(com.example.cuzdan.R.color.accent_violet, null)
        val accentVioletGlow = resources.getColor(com.example.cuzdan.R.color.accent_violet_glow, null)

        val dataSet = LineDataSet(entries, "Kar/Zarar").apply {
            color = accentViolet
            lineWidth = 3f
            setDrawCircles(false)
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            
            setDrawFilled(true)
            fillDrawable = resources.getDrawable(com.example.cuzdan.R.drawable.bg_chart_gradient, null)
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            
            xAxis.apply {
                isEnabled = false
            }
            axisLeft.apply {
                isEnabled = false
            }
            axisRight.apply {
                isEnabled = false
            }
            
            animateX(1200)
        }

        // Setup the big chart (the main one)
        val bigDataSet = LineDataSet(entries, "Total").apply {
            color = accentViolet
            lineWidth = 5f
            setDrawCircles(true)
            setCircleColor(accentViolet)
            circleRadius = 4f
            setCircleHoleColor(resources.getColor(com.example.cuzdan.R.color.bg_dark_start, null))
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            
            setDrawFilled(true)
            fillDrawable = resources.getDrawable(com.example.cuzdan.R.drawable.bg_chart_gradient, null)
        }

        binding.bigLineChart.apply {
            data = LineData(bigDataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            
            xAxis.apply {
                textColor = resources.getColor(com.example.cuzdan.R.color.text_secondary, null)
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                axisLineColor = resources.getColor(com.example.cuzdan.R.color.divider_color, null)
            }
            axisLeft.apply {
                textColor = resources.getColor(com.example.cuzdan.R.color.text_secondary, null)
                setDrawGridLines(true)
                gridColor = resources.getColor(com.example.cuzdan.R.color.divider_color, null)
                gridLineWidth = 0.5f
                axisLineColor = resources.getColor(com.example.cuzdan.R.color.divider_color, null)
                labelCount = 6
            }
            axisRight.isEnabled = false
            
            animateX(1500)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
