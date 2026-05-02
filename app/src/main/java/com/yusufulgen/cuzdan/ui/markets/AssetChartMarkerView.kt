package com.yusufulgen.cuzdan.ui.markets

import android.content.Context
import android.widget.TextView
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.util.formatCurrency
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class AssetChartMarkerView(
    context: Context, 
    private val history: List<Pair<Long, Double>>,
    private val currency: String
) : MarkerView(context, R.layout.layout_chart_marker) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val tvDate: TextView = findViewById(R.id.tvDate)

    override fun refreshContent(e: Entry, highlight: Highlight) {
        val index = e.x.toInt()
        if (index >= 0 && index < history.size) {
            val point = history[index]
            tvContent.text = point.second.toBigDecimal().formatCurrency(currency)
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            tvDate.text = sdf.format(Date(point.first))
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height * 1.1f).toFloat())
    }
}
