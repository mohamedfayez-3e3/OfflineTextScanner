package com.example.offlinetextscanner.imageprocessing

import org.junit.Assert.assertEquals
import org.junit.Test

class PerspectiveCorrectorTest {
    @Test
    fun outputDimensionsMatchProjectedQuadrilateralEdges() {
        val quad = DocumentQuad(
            listOf(
                Point2D(0.1f, 0.1f),
                Point2D(0.9f, 0.1f),
                Point2D(0.9f, 0.9f),
                Point2D(0.1f, 0.9f)
            )
        )

        val dimensions = PerspectiveCorrector.calculateOutputDimensions(1000, 1500, quad)

        assertEquals(799, dimensions.width)
        assertEquals(1199, dimensions.height)
    }
}
