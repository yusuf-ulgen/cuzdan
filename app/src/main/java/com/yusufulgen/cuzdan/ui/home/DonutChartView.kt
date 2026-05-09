package com.yusufulgen.cuzdan.ui.home

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
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
    private var labelColor: Int = android.graphics.Color.WHITE
    
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null

    fun setLabelColor(color: Int) {
        labelColor = color
        invalidate()
    }


    data class Segment(val percentage: Float, val color: Int, val label: String)

    fun setSegments(newSegments: List<Segment>) {
        segments = newSegments
        if (animator?.isRunning != true) {
            animationProgress = 1f
        }
        invalidate()
    }

    fun animateChart() {
        animator?.cancel()
        animationProgress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000 // 1 second
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setStrokeWidth(widthDp: Float) {
        strokeWidthPx = widthDp * resources.displayMetrics.density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = minOf(width, height).toFloat()
        val padding = size * 0.15f // Orta boy bir padding, grafiği koca yapıp yazılara yer bırakıyoruz
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
            val fullSweepAngle = segment.percentage * 360f
            val sweepAngle = fullSweepAngle * animationProgress
            
            canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
            
            // Draw Label and Line
            drawSegmentLabel(canvas, startAngle, fullSweepAngle, segment, size, animationProgress)
            
            startAngle += fullSweepAngle
        }
    }

    private fun drawSegmentLabel(canvas: Canvas, startAngle: Float, sweepAngle: Float, segment: Segment, size: Float, alphaProgress: Float) {
        if (segment.percentage < 0.05f) return // Küçük dilimler için yazı karmaşasını önle
        
        val alphaInt = (255 * alphaProgress).toInt()

        val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (rectF.width() / 2f) + (strokeWidthPx / 2f)
        
        // Çizginin başladığı nokta (donutun dış sınırı)
        val startX = centerX + (radius * Math.cos(midAngle)).toFloat()
        val startY = centerY + (radius * Math.sin(midAngle)).toFloat()
        
        // Kırılma noktası (dirsek)
        val elbowLen = 30f
        val elbowX = centerX + ((radius + elbowLen) * Math.cos(midAngle)).toFloat()
        val elbowY = centerY + ((radius + elbowLen) * Math.sin(midAngle)).toFloat()
        
        val isRightSide = Math.cos(midAngle) > 0
        
        // Yatay çizginin ucu
        val horizontalLineLen = 40f
        val endX = if (isRightSide) elbowX + horizontalLineLen else elbowX - horizontalLineLen
        val endY = elbowY

        // Çizgileri çiz
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = segment.color
            strokeWidth = 3f
            style = Paint.Style.STROKE
            alpha = alphaInt
        }
        canvas.drawLine(startX, startY, elbowX, elbowY, linePaint)
        canvas.drawLine(elbowX, elbowY, endX, endY, linePaint)

        // Yazıyı çiz
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 26f
            textAlign = if (isRightSide) Paint.Align.LEFT else Paint.Align.RIGHT
            isFakeBoldText = true
            alpha = alphaInt
        }
        
        val percentageText = "%${String.format("%.0f", segment.percentage * 100)} "
        val labelText = if (isRightSide) "$percentageText${segment.label}" else "${segment.label} $percentageText"
        
        // Yazı konumu (X pozisyonuna biraz daha padding ekle)
        val textX = if (isRightSide) endX + 8f else endX - 8f
        val textY = endY + 10f
        
        // Yazıyı çiz
        canvas.drawText(labelText, textX, textY, textPaint)
    }

}
