package com.example.offlinetextscanner.ocr

import android.graphics.Rect

enum class ReadingOrder {
    LTR,
    RTL,
    MIXED
}

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun containsPoint(x: Float, y: Float, tolerancePx: Float = 0f): Boolean =
        x >= left - tolerancePx && x <= right + tolerancePx &&
            y >= top - tolerancePx && y <= bottom + tolerancePx

    fun intersects(other: BoundingBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun toRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        fun union(boxes: List<BoundingBox>): BoundingBox {
            if (boxes.isEmpty()) return BoundingBox(0, 0, 0, 0)
            return BoundingBox(
                left = boxes.minOf { it.left },
                top = boxes.minOf { it.top },
                right = boxes.maxOf { it.right },
                bottom = boxes.maxOf { it.bottom }
            )
        }
    }
}

data class TextWord(
    val id: String,
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float,
    val lineIndex: Int,
    val blockIndex: Int,
    val isRtl: Boolean
)

data class TextLine(
    val id: String,
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float,
    val words: List<TextWord>,
    val blockIndex: Int,
    val readingOrder: ReadingOrder
)

data class TextBlock(
    val id: String,
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float,
    val lines: List<TextLine>,
    val blockIndex: Int,
    val readingOrder: ReadingOrder
)

data class StructuredOCRPage(
    val fullText: String,
    val blocks: List<TextBlock>,
    val meanConfidence: Int,
    val languageCode: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val processingTimeMs: Long
) {
    val allLines: List<TextLine> get() = blocks.flatMap { it.lines }
    val allWords: List<TextWord> get() = allLines.flatMap { it.words }
}
