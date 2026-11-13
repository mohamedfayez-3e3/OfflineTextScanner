package com.example.offlinetextscanner

import com.example.offlinetextscanner.ocr.BoundingBox
import com.example.offlinetextscanner.utils.CoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMappingTest {
    @Test
    fun mappingRoundTripsAfterFitCenterZoomAndPan() {
        val imageX = 390f
        val imageY = 880f
        val viewPoint = CoordinateMapper.mapImagePointToView(
            imageX, imageY, 1000, 1500, 1080, 1920,
            userZoom = 2.2f, userPanX = -45f, userPanY = 32f
        )
        val imagePoint = CoordinateMapper.mapViewPointToImage(
            viewPoint.x, viewPoint.y, 1000, 1500, 1080, 1920,
            userZoom = 2.2f, userPanX = -45f, userPanY = 32f
        )

        assertEquals(imageX, imagePoint.x, 0.01f)
        assertEquals(imageY, imagePoint.y, 0.01f)
    }

    @Test
    fun mappedWordBoxUsesSameScaleAndOffsetAsTouchCoordinates() {
        val box = BoundingBox(100, 200, 300, 260)
        val viewBox = CoordinateMapper.mapImageBoxToView(
            box, 1000, 1500, 1080, 1920,
            userZoom = 1.5f, userPanX = 20f, userPanY = -30f
        )
        val topLeft = CoordinateMapper.mapViewPointToImage(
            viewBox.left, viewBox.top, 1000, 1500, 1080, 1920,
            userZoom = 1.5f, userPanX = 20f, userPanY = -30f
        )

        assertEquals(box.left.toFloat(), topLeft.x, 0.01f)
        assertEquals(box.top.toFloat(), topLeft.y, 0.01f)
    }
}
