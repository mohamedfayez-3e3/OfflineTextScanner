package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

enum class EnhancementMode {
    ORIGINAL_COLOR,
    GRAYSCALE,
    BLACK_AND_WHITE,
    ADAPTIVE_THRESHOLD,
    ENHANCED_CONTRAST,
    DOCUMENT_CLEANUP
}

object ImageEnhancer {
    fun enhanceBitmapForOcr(
        sourceBitmap: Bitmap,
        mode: EnhancementMode = EnhancementMode.DOCUMENT_CLEANUP
    ): Bitmap {
        if (mode == EnhancementMode.ORIGINAL_COLOR) {
            return sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: error("Unable to copy the source image.")
        }

        val source = Mat()
        val gray = Mat()
        val output = Mat()
        try {
            Utils.bitmapToMat(sourceBitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            when (mode) {
                EnhancementMode.ORIGINAL_COLOR -> Unit
                EnhancementMode.GRAYSCALE -> gray.copyTo(output)
                EnhancementMode.BLACK_AND_WHITE -> {
                    val contrast = Mat()
                    val blurred = Mat()
                    try {
                        applyClahe(gray, contrast)
                        Imgproc.GaussianBlur(contrast, blurred, Size(3.0, 3.0), 0.0)
                        Imgproc.threshold(
                            blurred, output, 0.0, 255.0,
                            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
                        )
                    } finally {
                        contrast.release()
                        blurred.release()
                    }
                }
                EnhancementMode.ADAPTIVE_THRESHOLD -> {
                    val contrast = Mat()
                    val blurred = Mat()
                    try {
                        applyClahe(gray, contrast)
                        Imgproc.GaussianBlur(contrast, blurred, Size(3.0, 3.0), 0.0)
                        adaptiveThreshold(blurred, output, 31, 10.0)
                    } finally {
                        contrast.release()
                        blurred.release()
                    }
                }
                EnhancementMode.ENHANCED_CONTRAST -> applyContrastAndSharpen(gray, output)
                EnhancementMode.DOCUMENT_CLEANUP -> cleanupDocument(gray, output)
            }

            return Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
                .also { Utils.matToBitmap(output, it) }
        } finally {
            source.release()
            gray.release()
            output.release()
        }
    }

    fun deskewBitmapIfNeeded(sourceBitmap: Bitmap): Bitmap {
        val source = Mat()
        val gray = Mat()
        val binary = Mat()
        val points = Mat()
        var rotation = Mat()
        var result = Mat()
        try {
            Utils.bitmapToMat(sourceBitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.threshold(
                gray, binary, 0.0, 255.0,
                Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU
            )
            Core.findNonZero(binary, points)
            if (points.total() < 200L) return sourceBitmap

            val pointList = org.opencv.core.MatOfPoint()
            val points2f = org.opencv.core.MatOfPoint2f()
            try {
                points.convertTo(pointList, CvType.CV_32S)
                pointList.convertTo(points2f, CvType.CV_32F)
                val angle = Imgproc.minAreaRect(points2f).angle.let {
                    when {
                        it < -45.0 -> it + 90.0
                        it > 45.0 -> it - 90.0
                        else -> it
                    }
                }
                if (abs(angle) < 0.6 || abs(angle) > 12.0) return sourceBitmap
                rotation = Imgproc.getRotationMatrix2D(
                    org.opencv.core.Point(source.cols() / 2.0, source.rows() / 2.0),
                    angle,
                    1.0
                )
                Imgproc.warpAffine(
                    source, result, rotation, source.size(),
                    Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE
                )
                return Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
                    .also { Utils.matToBitmap(result, it) }
            } finally {
                pointList.release()
                points2f.release()
            }
        } catch (_: Exception) {
            return sourceBitmap
        } catch (_: LinkageError) {
            return sourceBitmap
        } finally {
            source.release()
            gray.release()
            binary.release()
            points.release()
            rotation.release()
            result.release()
        }
    }

    private fun cleanupDocument(gray: Mat, output: Mat) {
        val kernelSize = (minOf(gray.cols(), gray.rows()) / 30).coerceIn(15, 51)
            .let { if (it % 2 == 0) it + 1 else it }
        val backgroundKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(kernelSize.toDouble(), kernelSize.toDouble())
        )
        val background = Mat()
        val normalized = Mat()
        val contrast = Mat()
        try {
            Imgproc.morphologyEx(gray, background, Imgproc.MORPH_CLOSE, backgroundKernel)
            Core.divide(gray, background, normalized, 255.0)
            applyClahe(normalized, contrast)
            adaptiveThreshold(contrast, output, 35, 9.0)
        } finally {
            backgroundKernel.release()
            background.release()
            normalized.release()
            contrast.release()
        }
    }

    private fun applyContrastAndSharpen(source: Mat, output: Mat) {
        val contrast = Mat()
        val blurred = Mat()
        try {
            applyClahe(source, contrast)
            Imgproc.GaussianBlur(contrast, blurred, Size(0.0, 0.0), 1.5)
            Core.addWeighted(contrast, 1.4, blurred, -0.4, 0.0, output)
        } finally {
            contrast.release()
            blurred.release()
        }
    }

    private fun applyClahe(source: Mat, output: Mat) {
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        try {
            clahe.apply(source, output)
        } finally {
            clahe.collectGarbage()
        }
    }

    private fun adaptiveThreshold(source: Mat, output: Mat, blockSize: Int, constant: Double) {
        val size = blockSize.coerceAtLeast(3).let { if (it % 2 == 0) it + 1 else it }
        Imgproc.adaptiveThreshold(
            source, output, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            size, constant
        )
    }
}
