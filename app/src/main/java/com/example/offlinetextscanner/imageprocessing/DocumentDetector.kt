package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2

object DocumentDetector {
    fun ensureOpenCvLoaded(): Boolean {
        val loaded = try {
            OpenCVLoader.initLocal()
        } catch (error: Exception) {
            Log.e(TAG, "OpenCV initialization failed.", error)
            false
        } catch (error: LinkageError) {
            Log.e(TAG, "OpenCV native library could not be loaded.", error)
            false
        }
        if (!loaded) Log.e(TAG, "OpenCV local initialization returned false.")
        return loaded
    }

    /**
     * Finds the largest plausible four-sided contour in the luminance plane.
     * Coordinates in the result are normalized in the upright image space.
     */
    fun detect(image: ImageProxy): Detection? {
        if (image.format != ImageFormat.YUV_420_888 || image.planes.isEmpty()) return null
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val bufferStart = buffer.position()
        val width = image.width
        val height = image.height
        val luminance = ByteArray(width * height)
        val row = ByteArray(plane.rowStride)
        for (y in 0 until height) {
            row.fill(0)
            val rowStart = bufferStart + y * plane.rowStride
            buffer.position(rowStart)
            val rowLength = plane.rowStride.coerceAtMost(buffer.limit() - rowStart)
            buffer.get(row, 0, rowLength)
            for (x in 0 until width) {
                val column = x * plane.pixelStride
                if (column < rowLength) luminance[y * width + x] = row[column]
            }
        }

        val raw = Mat(height, width, CvType.CV_8UC1)
        val upright = Mat()
        try {
            raw.put(0, 0, luminance)
            when (image.imageInfo.rotationDegrees) {
                90 -> Core.rotate(raw, upright, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(raw, upright, Core.ROTATE_180)
                270 -> Core.rotate(raw, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> raw.copyTo(upright)
            }
            return detectUpright(upright)
        } finally {
            raw.release()
            upright.release()
        }
    }

    fun detectDocumentFromBitmap(bitmap: Bitmap): Detection? {
        if (!ensureOpenCvLoaded()) return null
        val color = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, color)
            if (color.channels() == 1) color.copyTo(gray) else Imgproc.cvtColor(color, gray, Imgproc.COLOR_RGBA2GRAY)
            detectUpright(gray)
        } finally {
            color.release()
            gray.release()
        }
    }

    private fun detectUpright(upright: Mat): Detection? {
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.GaussianBlur(upright, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 45.0, 130.0)
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            var bestCorners: List<Point2D>? = null
            var bestArea = 0.0
            val minArea = upright.width() * upright.height() * MIN_AREA_RATIO
            for (contour in contours) {
                val points = MatOfPoint2f(*contour.toArray())
                val approximation = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(points, true)
                    Imgproc.approxPolyDP(points, approximation, perimeter * APPROXIMATION_RATIO, true)
                    val vertices = approximation.toArray()
                    if (vertices.size != 4) continue
                    val polygon = MatOfPoint(*vertices)
                    try {
                        if (!Imgproc.isContourConvex(polygon)) continue
                        val area = kotlin.math.abs(Imgproc.contourArea(polygon))
                        if (area <= bestArea || area < minArea) continue
                        val ordered = orderCorners(vertices)
                        if (!isPlausibleQuad(ordered)) continue
                        bestArea = area
                        bestCorners = ordered.map {
                            Point2D(
                                (it.x / upright.width()).toFloat().coerceIn(0f, 1f),
                                (it.y / upright.height()).toFloat().coerceIn(0f, 1f)
                            )
                        }
                    } finally {
                        polygon.release()
                    }
                } finally {
                    points.release()
                    approximation.release()
                }
            }

            val corners = bestCorners ?: return null
            val outline = MatOfPoint(*corners.map {
                Point(
                    (it.x * upright.width()).toDouble(),
                    (it.y * upright.height()).toDouble()
                )
            }.toTypedArray())
            val documentRegion = upright.submat(Imgproc.boundingRect(outline))
            val laplacian = Mat()
            val mean = MatOfDouble()
            val deviation = MatOfDouble()
            return try {
                Imgproc.Laplacian(documentRegion, laplacian, CvType.CV_64F)
                Core.meanStdDev(laplacian, mean, deviation)
                val sharpness = deviation.get(0, 0)[0].let { it * it }
                Detection(DocumentQuad(corners), sharpness, upright.width(), upright.height())
            } finally {
                documentRegion.release()
                outline.release()
                laplacian.release()
                mean.release()
                deviation.release()
            }
        } finally {
            contours.forEach(Mat::release)
            blurred.release()
            edges.release()
            hierarchy.release()
        }
    }

    internal fun orderCorners(points: Array<Point>): List<Point> {
        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        val clockwise = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val start = clockwise.indices.minByOrNull { index ->
            clockwise[index].x + clockwise[index].y
        } ?: 0
        return List(4) { clockwise[(start + it) % 4] }
    }

    private fun isPlausibleQuad(points: List<Point>): Boolean {
        val lengths = points.indices.map { index ->
            val first = points[index]
            val second = points[(index + 1) % points.size]
            kotlin.math.hypot(first.x - second.x, first.y - second.y)
        }
        val minimumEdge = lengths.minOrNull() ?: return false
        val maximumEdge = lengths.maxOrNull() ?: return false
        if (minimumEdge < MIN_EDGE_PIXELS || maximumEdge / minimumEdge > MAX_EDGE_RATIO) return false

        return points.indices.all { index ->
            val current = points[index]
            val previous = points[(index + points.size - 1) % points.size]
            val next = points[(index + 1) % points.size]
            val firstX = previous.x - current.x
            val firstY = previous.y - current.y
            val secondX = next.x - current.x
            val secondY = next.y - current.y
            val denominator = kotlin.math.hypot(firstX, firstY) * kotlin.math.hypot(secondX, secondY)
            denominator > 0.0 && kotlin.math.abs((firstX * secondX + firstY * secondY) / denominator) <=
                MAX_ANGLE_COSINE
        }
    }

    data class Detection(
        val quad: DocumentQuad,
        val sharpness: Double,
        val frameWidth: Int,
        val frameHeight: Int
    ) {
        val orderedCorners: List<PointF>
            get() = quad.corners.map {
                PointF(it.x * frameWidth, it.y * frameHeight)
            }
    }

    private const val MIN_AREA_RATIO = 0.04
    private const val APPROXIMATION_RATIO = 0.02
    private const val MIN_EDGE_PIXELS = 18.0
    private const val MAX_EDGE_RATIO = 10.0
    private const val MAX_ANGLE_COSINE = 0.92
    private const val TAG = "DocumentDetector"
}

data class Point2D(val x: Float, val y: Float)

data class DocumentQuad(val corners: List<Point2D>) {
    init {
        require(corners.size == 4) { "A document boundary must have four corners." }
    }

    fun maxCornerDistance(other: DocumentQuad): Float =
        corners.indices.maxOf { index ->
            val dx = corners[index].x - other.corners[index].x
            val dy = corners[index].y - other.corners[index].y
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
}
