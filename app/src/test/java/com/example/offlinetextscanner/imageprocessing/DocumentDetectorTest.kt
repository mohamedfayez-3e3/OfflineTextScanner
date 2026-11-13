package com.example.offlinetextscanner.imageprocessing

import org.junit.Assert.assertEquals
import org.junit.Test
import org.opencv.core.Point

class DocumentDetectorTest {
    @Test
    fun ordersCornersFromTopLeftClockwise() {
        val ordered = DocumentDetector.orderCorners(
            arrayOf(
                Point(200.0, 200.0),
                Point(100.0, 100.0),
                Point(100.0, 200.0),
                Point(200.0, 100.0)
            )
        )

        assertEquals(listOf(100.0, 200.0, 200.0, 100.0), ordered.map { it.x })
        assertEquals(listOf(100.0, 100.0, 200.0, 200.0), ordered.map { it.y })
    }
}
