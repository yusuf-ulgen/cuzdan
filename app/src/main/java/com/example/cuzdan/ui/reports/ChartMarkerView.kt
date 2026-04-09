package com.example.cuzdan.ui.reports

import android.content.Context
import android.widget.TextView
import com.example.cuzdan.R
import com.example.cuzdan.util.formatCurrency
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class ChartMarkerView(context: Context, private val dataPoints: List<com.example.cuzdan.data.local.entity.PortfolioHistory>) : MarkerView(context, R.layout.layout_chart_marker) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val tvDate: TextView = findViewById(R.id.tvDate)

    override fun refreshContent(e: Entry, highlight: Highlight) {
        val index = e.x.toInt()
        if (index >= 0 && index < dataPoints.size) {
            val history = dataPoints[index]
            tvContent.text = history.profitLoss.formatCurrency(history.currency, showSign = true)
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            tvDate.text = sdf.format(Date(history.date))
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}
