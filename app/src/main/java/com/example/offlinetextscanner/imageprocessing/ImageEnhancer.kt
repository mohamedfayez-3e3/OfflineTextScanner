package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object ImageEnhancer {
    fun enhanceBitmapForOcr(sourceBitmap: Bitmap): Bitmap {
        val source = Mat()
        val gray = Mat()
        val contrast = Mat()
        val denoised = Mat()
        val threshold = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        try {
            Utils.bitmapToMat(sourceBitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            clahe.apply(gray, contrast)
            Imgproc.GaussianBlur(contrast, denoised, Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(
                denoised,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                12.0
            )
            return Bitmap.createBitmap(
                sourceBitmap.width,
                sourceBitmap.height,
                Bitmap.Config.ARGB_8888
            ).also { Utils.matToBitmap(threshold, it) }
        } finally {
            clahe.collectGarbage()
            source.release()
            gray.release()
            contrast.release()
            denoised.release()
            threshold.release()
        }
    }
}
