package com.example.offlinetextscanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.offlinetextscanner.imageprocessing.DocumentDetector
import kotlin.math.max

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF39D98A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3039D98A
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private var detection: DocumentDetector.Detection? = null

    fun setDocumentDetection(value: DocumentDetector.Detection?) {
        detection = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = detection ?: return
        val points = current.quad.corners
        if (width <= 0 || height <= 0) return
        val sourceWidth = current.frameWidth.toFloat()
        val sourceHeight = current.frameHeight.toFloat()
        val scale = max(width / sourceWidth, height / sourceHeight)
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f
        fun x(index: Int) = points[index].x * sourceWidth * scale + offsetX
        fun y(index: Int) = points[index].y * sourceHeight * scale + offsetY
        val path = Path().apply {
            moveTo(x(0), y(0))
            for (index in 1 until points.size) lineTo(x(index), y(index))
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, boundaryPaint)
        val radius = resources.displayMetrics.density * 5f
        points.indices.forEach { canvas.drawCircle(x(it), y(it), radius, handlePaint) }
    }
}
