package com.example.offlinetextscanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.offlinetextscanner.ocr.BidiTextHelper
import com.example.offlinetextscanner.ocr.StructuredOCRPage
import com.example.offlinetextscanner.ocr.TextWord
import com.example.offlinetextscanner.utils.CoordinateMapper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val normalFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x443399FF
        style = Paint.Style.FILL
    }
    private val normalStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA1689FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x6678D7FF
        style = Paint.Style.FILL
    }
    private val selectedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0878D1.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val marqueePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0878D1.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var documentBitmap: Bitmap? = null
    private var page: StructuredOCRPage? = null
    private val selectedIds = linkedSetOf<String>()
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var isMarquee = false
    private var isMultiTouchGesture = false
    private var isLongPress = false
    private val marquee = RectF()

    var onSelectionChanged: ((String, Int) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentPage = page ?: return false
                val anchor = mapToImage(detector.focusX, detector.focusY, currentPage)
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
                val anchoredPoint = mapToView(anchor.x, anchor.y, currentPage)
                panX += detector.focusX - anchoredPoint.x
                panY += detector.focusY - anchoredPoint.y
                constrainPan(currentPage)
                invalidate()
                return true
            }
        }
    )

    fun setDocumentData(bitmap: Bitmap?, ocrPage: StructuredOCRPage?) {
        documentBitmap = bitmap
        page = ocrPage
        selectedIds.clear()
        zoom = 1f
        panX = 0f
        panY = 0f
        notifySelectionChanged()
        invalidate()
    }

    fun selectAllWords() {
        selectedIds.clear()
        page?.allWords?.forEach { selectedIds += it.id }
        notifySelectionChanged()
        invalidate()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifySelectionChanged()
        invalidate()
    }

    fun getSelectedText(): String {
        val currentPage = page ?: return ""
        return BidiTextHelper.buildFormattedTextFromSelectedWords(
            currentPage.allWords.filter { it.id in selectedIds }
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentPage = page ?: return false
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastPointerX = event.x
                lastPointerY = event.y
                isMarquee = false
                isMultiTouchGesture = false
                isLongPress = false
                postDelayed(longPressRunnable, longPressTimeout)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouchGesture = true
                removeCallbacks(longPressRunnable)
                removeCallbacks(singleTapRunnable)
                pendingTapWord = null
                lastPointerX = centerX(event)
                lastPointerY = centerY(event)
                isMarquee = false
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> return true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val x = centerX(event)
                    val y = centerY(event)
                    panX += x - lastPointerX
                    panY += y - lastPointerY
                    lastPointerX = x
                    lastPointerY = y
                    constrainPan(currentPage)
                } else {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (isMarquee || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        removeCallbacks(longPressRunnable)
                        isMarquee = true
                        marquee.set(
                            min(downX, event.x), min(downY, event.y),
                            max(downX, event.x), max(downY, event.y)
                        )
                        selectWordsInMarquee(marquee, currentPage)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isLongPress) {
                    isLongPress = false
                } else if (isMultiTouchGesture) {
                    isMultiTouchGesture = false
                } else if (isMarquee) {
                    marquee.setEmpty()
                    isMarquee = false
                } else {
                    val tapWord = findWord(event.x, event.y, currentPage)
                    if (tapWord != null) {
                        val now = event.eventTime
                        if (now - lastTapTime <= doubleTapTimeout &&
                            abs(event.x - lastTapX) < touchSlop * 2 &&
                            abs(event.y - lastTapY) < touchSlop * 2
                        ) {
                            selectLine(tapWord, currentPage)
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapX = event.x
                            lastTapY = event.y
                            pendingTapWord = tapWord
                            postDelayed(singleTapRunnable, doubleTapTimeout)
                        }
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                removeCallbacks(singleTapRunnable)
                pendingTapWord = null
                marquee.setEmpty()
                isMarquee = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var pendingTapWord: TextWord? = null
    private val doubleTapTimeout = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
    private val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val singleTapRunnable = Runnable {
        pendingTapWord?.let { word ->
            if (word.id in selectedIds) selectedIds.remove(word.id) else selectedIds.add(word.id)
            notifySelectionChanged()
            invalidate()
        }
        pendingTapWord = null
    }
    private val longPressRunnable = Runnable {
        val currentPage = page ?: return@Runnable
        val word = findWord(downX, downY, currentPage) ?: return@Runnable
        removeCallbacks(singleTapRunnable)
        pendingTapWord = null
        lastTapTime = 0L
        isLongPress = true
        currentPage.allWords.filter { it.blockIndex == word.blockIndex }
            .forEach { selectedIds.add(it.id) }
        notifySelectionChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPage = page ?: return
        val transform = transform(currentPage)
        val bitmap = documentBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            val rect = RectF(
                transform.offsetX,
                transform.offsetY,
                transform.offsetX + currentPage.imageWidth * transform.scale,
                transform.offsetY + currentPage.imageHeight * transform.scale
            )
            canvas.drawBitmap(bitmap, null, rect, imagePaint)
        }

        currentPage.allWords.forEach { word ->
            val box = mapBox(word, currentPage)
            val selected = word.id in selectedIds
            canvas.drawRoundRect(box, 5f, 5f, if (selected) selectedFill else normalFill)
            canvas.drawRoundRect(box, 5f, 5f, if (selected) selectedStroke else normalStroke)
        }
        if (!marquee.isEmpty) canvas.drawRect(marquee, marqueePaint)
    }

    private fun selectLine(word: TextWord, currentPage: StructuredOCRPage) {
        currentPage.allWords.filter {
            it.blockIndex == word.blockIndex && it.lineIndex == word.lineIndex
        }.forEach { selectedIds.add(it.id) }
        pendingTapWord = null
        removeCallbacks(singleTapRunnable)
        lastTapTime = 0L
        notifySelectionChanged()
    }

    private fun selectWordsInMarquee(rect: RectF, currentPage: StructuredOCRPage) {
        val imageRect = CoordinateMapper.mapViewRectToImage(
            rect, currentPage.imageWidth, currentPage.imageHeight, width, height,
            userZoom = zoom, userPanX = panX, userPanY = panY
        )
        currentPage.allWords.filter { word ->
            val box = word.boundingBox
            box.left < imageRect.right && box.right > imageRect.left &&
                box.top < imageRect.bottom && box.bottom > imageRect.top
        }.forEach { selectedIds.add(it.id) }
        notifySelectionChanged()
    }

    private fun findWord(x: Float, y: Float, currentPage: StructuredOCRPage): TextWord? {
        val point = mapToImage(x, y, currentPage)
        return currentPage.allWords.firstOrNull {
            it.boundingBox.containsPoint(point.x, point.y, 6f)
        }
    }

    private fun mapToImage(x: Float, y: Float, currentPage: StructuredOCRPage) =
        CoordinateMapper.mapViewPointToImage(
            x, y, currentPage.imageWidth, currentPage.imageHeight, width, height,
            userZoom = zoom, userPanX = panX, userPanY = panY
        )

    private fun mapToView(x: Float, y: Float, currentPage: StructuredOCRPage) =
        CoordinateMapper.mapImagePointToView(
            x, y, currentPage.imageWidth, currentPage.imageHeight, width, height,
            userZoom = zoom, userPanX = panX, userPanY = panY
        )

    private fun mapBox(word: TextWord, currentPage: StructuredOCRPage) =
        CoordinateMapper.mapImageBoxToView(
            word.boundingBox, currentPage.imageWidth, currentPage.imageHeight, width, height,
            userZoom = zoom, userPanX = panX, userPanY = panY
        ).let { RectF(it.left, it.top, it.right, it.bottom) }

    private fun transform(currentPage: StructuredOCRPage) =
        CoordinateMapper.computeTransform(
            currentPage.imageWidth, currentPage.imageHeight, width, height,
            userZoom = zoom, userPanX = panX, userPanY = panY
        )

    private fun constrainPan(currentPage: StructuredOCRPage) {
        val scale = transform(currentPage).scale
        val maxX = max(0f, (currentPage.imageWidth * scale - width) / 2f)
        val maxY = max(0f, (currentPage.imageHeight * scale - height) / 2f)
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    private fun centerX(event: MotionEvent): Float =
        (event.getX(0) + event.getX(1)) / 2f

    private fun centerY(event: MotionEvent): Float =
        (event.getY(0) + event.getY(1)) / 2f

    private fun notifySelectionChanged() {
        onSelectionChanged?.invoke(getSelectedText(), selectedIds.size)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(singleTapRunnable)
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}
