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
            Entry(6f, 1600f)
        )

        val dataSet = LineDataSet(entries, "Kar/Zarar").apply {
            color = Color.WHITE
            setCircleColor(Color.WHITE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.WHITE
            fillAlpha = 30
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false
            xAxis.textColor = Color.WHITE
            setTouchEnabled(true)
            animateX(1000)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
