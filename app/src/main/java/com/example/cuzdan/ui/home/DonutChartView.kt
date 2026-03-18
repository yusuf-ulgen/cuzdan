package com.example.cuzdan.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var segments: List<Segment> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val rectF = RectF()
    private var strokeWidthPx = 60f

    data class Segment(val percentage: Float, val color: Int, val label: String)

    fun setSegments(newSegments: List<Segment>) {
        segments = newSegments
        invalidate()
    }

    fun setStrokeWidth(widthDp: Float) {
        strokeWidthPx = widthDp * resources.displayMetrics.density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = minOf(width, height).toFloat()
        val padding = size * 0.12f // Donut'u tekrar büyüterek eski formuna yakınlaştırıyoruz
        val margin = (strokeWidthPx / 2) + padding
        
        val left = (width - size) / 2 + margin
        val top = (height - size) / 2 + margin
        rectF.set(left, top, left + size - 2 * margin, top + size - 2 * margin)
        
        paint.strokeWidth = strokeWidthPx
        
        var startAngle = -90f
        
        if (segments.isEmpty()) {
            paint.color = 0x20FFFFFF
            canvas.drawArc(rectF, 0f, 360f, false, paint)
            return
        }

        segments.forEach { segment ->
            paint.color = segment.color
            val sweepAngle = segment.percentage * 360f
            canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
            
            // Draw Label and Line
            drawSegmentLabel(canvas, startAngle, sweepAngle, segment, size)
            
            startAngle += sweepAngle
        }
    }

    private fun drawSegmentLabel(canvas: Canvas, startAngle: Float, sweepAngle: Float, segment: Segment, size: Float) {
        if (segment.percentage < 0.01f) return // Don't draw labels for very small segments

        val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (rectF.width() / 2f) + (strokeWidthPx / 2f)
        
        // Line points
        val startX = centerX + (radius * Math.cos(midAngle)).toFloat()
        val startY = centerY + (radius * Math.sin(midAngle)).toFloat()
        
        val elbowRadius = radius + 20f
        val elbowX = centerX + (elbowRadius * Math.cos(midAngle)).toFloat()
        val elbowY = centerY + (elbowRadius * Math.sin(midAngle)).toFloat()
        
        val isRightSide = Math.cos(midAngle) > 0
        val endX = if (isRightSide) elbowX + 25f else elbowX - 25f
        val endY = elbowY

        // Draw Line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = segment.color
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(startX, startY, elbowX, elbowY, linePaint)
        canvas.drawLine(elbowX, elbowY, endX, endY, linePaint)

        // Draw Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = if (isRightSide) Paint.Align.LEFT else Paint.Align.RIGHT
            isFakeBoldText = true
        }
        
        val labelText = "%${String.format("%.1f", segment.percentage * 100).replace(".", ",")} ${segment.label}"
        val textX = if (isRightSide) endX + 10f else endX - 10f
        val textY = endY + 12f
        
        canvas.drawText(labelText, textX, textY, textPaint)
    }
}
