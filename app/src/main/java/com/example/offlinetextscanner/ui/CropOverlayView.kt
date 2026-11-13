package com.example.offlinetextscanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.offlinetextscanner.R
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val normalizedCorners = defaultMargins()
    private var imageWidth = 0
    private var imageHeight = 0
    private var activeCorner = -1
    private var activeEdge = -1
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_blue)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.background_white)
        style = Paint.Style.FILL
    }
    private val selectedHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_blue)
        style = Paint.Style.FILL
    }

    fun setImageGeometryAndCorners(width: Int, height: Int, corners: List<PointF>) {
        require(width > 0 && height > 0) { "Image dimensions must be positive." }
        require(corners.size == CORNER_COUNT) { "Exactly four image corners are required." }
        imageWidth = width
        imageHeight = height
        val candidate = corners.map {
            PointF((it.x / width).coerceIn(0f, 1f), (it.y / height).coerceIn(0f, 1f))
        }
        normalizedCorners.clear()
        normalizedCorners.addAll(if (isValidQuad(candidate)) candidate else defaultMargins())
        invalidate()
    }

    fun getOrderedImageCorners(): List<PointF> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        return orderCorners(normalizedCorners).map {
            PointF(it.x * imageWidth, it.y * imageHeight)
        }
    }

    fun resetToDefaultMargins() {
        normalizedCorners.clear()
        normalizedCorners.addAll(defaultMargins())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val displayed = normalizedCorners.map(::mapImagePointToView)
        val cropPath = Path().apply {
            moveTo(displayed[0].x, displayed[0].y)
            for (index in 1 until displayed.size) lineTo(displayed[index].x, displayed[index].y)
            close()
        }
        val outside = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addPath(cropPath)
        }
        canvas.drawPath(outside, shadePaint)
        canvas.drawPath(cropPath, linePaint)
        val radius = resources.displayMetrics.density * 9f
        displayed.forEachIndexed { index, point ->
            canvas.drawCircle(
                point.x,
                point.y,
                radius,
                if (index == activeCorner) selectedHandlePaint else handlePaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (width <= 0 || height <= 0) return false
                val touchRadius = resources.displayMetrics.density * 32f
                val displayed = normalizedCorners.map(::mapImagePointToView)
                val nearestCorner = displayed.indices.minByOrNull { index ->
                    distance(event.x, event.y, displayed[index].x, displayed[index].y)
                }
                if (nearestCorner != null &&
                    distance(
                        event.x,
                        event.y,
                        displayed[nearestCorner].x,
                        displayed[nearestCorner].y
                    ) <= touchRadius
                ) {
                    activeCorner = nearestCorner
                } else {
                    activeEdge = (0 until CORNER_COUNT).minByOrNull { edge ->
                        val next = (edge + 1) % CORNER_COUNT
                        distance(
                            event.x,
                            event.y,
                            (displayed[edge].x + displayed[next].x) / 2f,
                            (displayed[edge].y + displayed[next].y) / 2f
                        )
                    } ?: -1
                    val next = (activeEdge + 1) % CORNER_COUNT
                    if (activeEdge < 0 || distance(
                            event.x,
                            event.y,
                            (displayed[activeEdge].x + displayed[next].x) / 2f,
                            (displayed[activeEdge].y + displayed[next].y) / 2f
                        ) > touchRadius
                    ) {
                        activeEdge = -1
                        return false
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCorner < 0 && activeEdge < 0) return false
                val target = mapViewPointToImage(event.x, event.y)
                val candidate = normalizedCorners.map { PointF(it.x, it.y) }.toMutableList()
                if (activeCorner >= 0) {
                    candidate[activeCorner] = target
                } else {
                    val next = (activeEdge + 1) % CORNER_COUNT
                    val centerX = (candidate[activeEdge].x + candidate[next].x) / 2f
                    val centerY = (candidate[activeEdge].y + candidate[next].y) / 2f
                    val deltaX = target.x - centerX
                    val deltaY = target.y - centerY
                    candidate[activeEdge] = translated(candidate[activeEdge], deltaX, deltaY)
                    candidate[next] = translated(candidate[next], deltaX, deltaY)
                }
                if (isValidQuad(candidate)) {
                    normalizedCorners.clear()
                    normalizedCorners.addAll(candidate)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeCorner = -1
                activeEdge = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun mapImagePointToView(point: PointF): PointF {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return PointF(point.x * width, point.y * height)
        }
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f
        return PointF(
            point.x * imageWidth * scale + offsetX,
            point.y * imageHeight * scale + offsetY
        )
    }

    private fun mapViewPointToImage(x: Float, y: Float): PointF {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return PointF(
                (x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
            )
        }
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f
        return PointF(
            ((x - offsetX) / scale / imageWidth).coerceIn(0f, 1f),
            ((y - offsetY) / scale / imageHeight).coerceIn(0f, 1f)
        )
    }

    private fun translated(point: PointF, dx: Float, dy: Float) = PointF(
        (point.x + dx).coerceIn(0f, 1f),
        (point.y + dy).coerceIn(0f, 1f)
    )

    private fun isValidQuad(points: List<PointF>): Boolean {
        if (points.size != CORNER_COUNT) return false
        var sign = 0
        var area = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val after = points[(index + 2) % points.size]
            area += current.x * next.y - next.x * current.y
            val cross = (next.x - current.x) * (after.y - next.y) -
                (next.y - current.y) * (after.x - next.x)
            if (kotlin.math.abs(cross) < MIN_CORNER_CROSS) return false
            val currentSign = if (cross > 0f) 1 else -1
            if (sign != 0 && currentSign != sign) return false
            sign = currentSign
        }
        return kotlin.math.abs(area) >= MIN_QUAD_AREA
    }

    private fun orderCorners(points: List<PointF>): List<PointF> {
        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        val clockwise = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val start = clockwise.indices.minByOrNull { clockwise[it].x + clockwise[it].y } ?: 0
        return List(CORNER_COUNT) { clockwise[(start + it) % CORNER_COUNT] }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()

    private fun defaultMargins() = mutableListOf(
        PointF(DEFAULT_MARGIN, DEFAULT_MARGIN),
        PointF(1f - DEFAULT_MARGIN, DEFAULT_MARGIN),
        PointF(1f - DEFAULT_MARGIN, 1f - DEFAULT_MARGIN),
        PointF(DEFAULT_MARGIN, 1f - DEFAULT_MARGIN)
    )

    private companion object {
        const val CORNER_COUNT = 4
        const val DEFAULT_MARGIN = 0.05f
        const val MIN_CORNER_CROSS = 0.00001f
        const val MIN_QUAD_AREA = 0.001f
    }
}
