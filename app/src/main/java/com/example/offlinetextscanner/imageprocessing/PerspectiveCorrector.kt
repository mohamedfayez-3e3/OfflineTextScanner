package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveCorrector {
    data class OutputDimensions(val width: Int, val height: Int)

    /** Warps a normalized top-left, top-right, bottom-right, bottom-left crop into a rectangle. */
    fun correct(source: Bitmap, quad: DocumentQuad): Bitmap? {
        if (source.width <= 1 || source.height <= 1 || !DocumentDetector.ensureOpenCvLoaded()) return null
        val sourcePoints = quad.corners.map { point ->
            Point(
                (point.x.coerceIn(0f, 1f) * (source.width - 1)).toDouble(),
                (point.y.coerceIn(0f, 1f) * (source.height - 1)).toDouble()
            )
        }
        val dimensions = calculateOutputDimensions(source.width, source.height, quad)
        if (dimensions.width.toLong() * dimensions.height > MAX_OUTPUT_PIXELS) return null

        val sourceMat = Mat()
        val warped = Mat()
        val from = MatOfPoint2f(*sourcePoints.toTypedArray())
        val to = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(dimensions.width - 1.0, 0.0),
            Point(dimensions.width - 1.0, dimensions.height - 1.0),
            Point(0.0, dimensions.height - 1.0)
        )
        var transform: Mat? = null
        return try {
            Utils.bitmapToMat(source, sourceMat)
            val matrix = Imgproc.getPerspectiveTransform(from, to)
            transform = matrix
            Imgproc.warpPerspective(
                sourceMat,
                warped,
                matrix,
                Size(dimensions.width.toDouble(), dimensions.height.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_REPLICATE
            )
            Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(warped, it)
            }
        } catch (_: Exception) {
            null
        } finally {
            sourceMat.release()
            warped.release()
            from.release()
            to.release()
            transform?.release()
        }
    }

    fun warpPerspective(source: Bitmap, corners: List<PointF>): Bitmap {
        require(corners.size == 4) { "Exactly four crop corners are required." }
        val quad = DocumentQuad(
            corners.map {
                Point2D(
                    (it.x / source.width).coerceIn(0f, 1f),
                    (it.y / source.height).coerceIn(0f, 1f)
                )
            }
        )
        return correct(source, quad)
            ?: throw IllegalStateException("Unable to apply perspective correction.")
    }

    fun calculateOutputDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        quad: DocumentQuad
    ): OutputDimensions {
        require(sourceWidth > 1 && sourceHeight > 1)
        val points = quad.corners.map {
            Point(
                (it.x.coerceIn(0f, 1f) * (sourceWidth - 1)).toDouble(),
                (it.y.coerceIn(0f, 1f) * (sourceHeight - 1)).toDouble()
            )
        }
        return OutputDimensions(
            width = max(
                distance(points[0], points[1]),
                distance(points[3], points[2])
            ).roundToInt().coerceAtLeast(2),
            height = max(
                distance(points[0], points[3]),
                distance(points[1], points[2])
            ).roundToInt().coerceAtLeast(2)
        )
    }

    private fun distance(first: Point, second: Point): Double =
        hypot(first.x - second.x, first.y - second.y)

    private const val MAX_OUTPUT_PIXELS = 40_000_000L
}
