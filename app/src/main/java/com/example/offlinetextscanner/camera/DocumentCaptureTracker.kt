package com.example.offlinetextscanner.camera

import com.example.offlinetextscanner.imageprocessing.DocumentQuad

class DocumentCaptureTracker(
    private val requiredStableFrames: Int = DEFAULT_STABLE_FRAMES,
    private val maximumCornerMovement: Float = DEFAULT_MAX_CORNER_MOVEMENT,
    private val minimumSharpness: Double = DEFAULT_MINIMUM_SHARPNESS
) {
    private var previous: DocumentQuad? = null
    private var stableFrames = 0
    private var missingFrames = 0
    private var captureIssued = false

    init {
        require(requiredStableFrames > 0)
        require(maximumCornerMovement >= 0f)
        require(minimumSharpness >= 0.0)
    }

    /** Returns true exactly once for a stable, sufficiently sharp document. */
    @Synchronized
    fun observe(quad: DocumentQuad?, sharpness: Double): Boolean {
        if (quad == null) {
            previous = null
            stableFrames = 0
            if (++missingFrames >= MISSING_FRAMES_TO_REARM) captureIssued = false
            return false
        }
        missingFrames = 0
        val last = previous
        stableFrames = if (last != null && quad.maxCornerDistance(last) <= maximumCornerMovement) {
            stableFrames + 1
        } else {
            1
        }
        previous = quad
        if (!captureIssued && stableFrames >= requiredStableFrames && sharpness >= minimumSharpness) {
            captureIssued = true
            return true
        }
        return false
    }

    @Synchronized
    fun resetAfterCaptureFailure() {
        captureIssued = false
        stableFrames = 0
        previous = null
        missingFrames = 0
    }

    companion object {
        const val DEFAULT_STABLE_FRAMES = 5
        const val DEFAULT_MAX_CORNER_MOVEMENT = 0.025f
        const val DEFAULT_MINIMUM_SHARPNESS = 80.0
        private const val MISSING_FRAMES_TO_REARM = 3
    }
}
