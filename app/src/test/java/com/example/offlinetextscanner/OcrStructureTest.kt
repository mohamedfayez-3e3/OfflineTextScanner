package com.example.offlinetextscanner

import com.example.offlinetextscanner.ocr.BidiTextHelper
import com.example.offlinetextscanner.ocr.BoundingBox
import com.example.offlinetextscanner.ocr.OCRStructureBuilder
import com.example.offlinetextscanner.ocr.ReadingOrder
import com.example.offlinetextscanner.ocr.TextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrStructureTest {
    @Test
    fun buildsWordLineAndBlockHierarchyWithUnionBounds() {
        val words = listOf(
            word("one", "Hello", 10, 10, 45, 28, 0, 0, false),
            word("two", "world", 50, 10, 85, 28, 0, 0, false),
            word("three", "Next", 10, 40, 42, 58, 0, 1, false),
            word("four", "paragraph", 100, 100, 160, 120, 1, 0, false)
        )

        val blocks = OCRStructureBuilder.buildBlocks(words)

        assertEquals(2, blocks.size)
        assertEquals(2, blocks[0].lines.size)
        assertEquals(2, blocks[0].lines[0].words.size)
        assertEquals("Hello world", blocks[0].lines[0].text)
        assertEquals(BoundingBox(10, 10, 85, 58), blocks[0].boundingBox)
        assertEquals("paragraph", blocks[1].text)
    }

    @Test
    fun ordersArabicWordsByVisualPositionWithoutReversingTheirCharacters() {
        val left = word("l", "شمس", 10, 10, 35, 25, 0, 0, true)
        val right = word("r", "جامعة", 60, 10, 100, 25, 0, 0, true)
        val ordered = BidiTextHelper.orderWordsInLine(listOf(left, right))

        assertEquals(listOf("جامعة", "شمس"), ordered.map { it.text })
        assertEquals(ReadingOrder.RTL, BidiTextHelper.detectReadingOrder("جامعة شمس"))
    }

    @Test
    fun mixedScriptOrderIsPreservedFromTesseractRatherThanReversingArabic() {
        val arabic = word("a", "مرحبا", 80, 10, 120, 25, 0, 0, true)
        val english = word("e", "world", 10, 10, 50, 25, 0, 0, false)
        val originalLogicalOrder = listOf(arabic, english)
        val ordered = BidiTextHelper.orderWordsInLine(
            originalLogicalOrder
        )

        assertEquals(originalLogicalOrder, ordered)
        assertEquals(ReadingOrder.MIXED, BidiTextHelper.detectReadingOrder("مرحبا world"))
        assertTrue(ordered.any { it.text == "مرحبا" })
    }

    private fun word(
        id: String,
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        block: Int,
        line: Int,
        rtl: Boolean
    ) = TextWord(
        id = id,
        text = text,
        boundingBox = BoundingBox(left, top, right, bottom),
        confidence = 90f,
        lineIndex = line,
        blockIndex = block,
        isRtl = rtl
    )
}
