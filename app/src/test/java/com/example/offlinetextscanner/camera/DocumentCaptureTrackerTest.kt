package com.example.offlinetextscanner.camera

import com.example.offlinetextscanner.imageprocessing.DocumentQuad
import com.example.offlinetextscanner.imageprocessing.Point2D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCaptureTrackerTest {
    @Test
    fun capturesOnlyAfterStableSharpFramesAndDoesNotRepeat() {
        val tracker = DocumentCaptureTracker(requiredStableFrames = 3, minimumSharpness = 80.0)
        val quad = sampleQuad()

        assertFalse(tracker.observe(quad, 120.0))
        assertFalse(tracker.observe(quad, 120.0))
        assertTrue(tracker.observe(quad, 120.0))
        assertFalse(tracker.observe(quad, 120.0))
    }

    @Test
    fun blurryOrMovingDocumentsDoNotReachStableCapture() {
        val tracker = DocumentCaptureTracker(requiredStableFrames = 3, minimumSharpness = 80.0)
        val quad = sampleQuad()
        val moved = DocumentQuad(quad.corners.map { Point2D(it.x + 0.1f, it.y) })

        assertFalse(tracker.observe(quad, 20.0))
        assertFalse(tracker.observe(quad, 120.0))
        assertFalse(tracker.observe(moved, 120.0))
        assertFalse(tracker.observe(moved, 120.0))
        assertTrue(tracker.observe(moved, 120.0))
    }

    @Test
    fun missingDocumentRearmsAutoCapture() {
        val tracker = DocumentCaptureTracker(requiredStableFrames = 1, minimumSharpness = 0.0)
        val quad = sampleQuad()
        assertTrue(tracker.observe(quad, 100.0))
        repeat(3) { tracker.observe(null, 0.0) }
        assertTrue(tracker.observe(quad, 100.0))
    }

    private fun sampleQuad() = DocumentQuad(
        listOf(
            Point2D(0.1f, 0.1f),
            Point2D(0.9f, 0.1f),
            Point2D(0.9f, 0.9f),
            Point2D(0.1f, 0.9f)
        )
    )
}
