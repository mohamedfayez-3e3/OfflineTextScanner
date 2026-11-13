package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader
import java.io.File

class ImagePreprocessor {
    @Volatile
    private var isOpenCvInitialized = false

    @Synchronized
    fun ensureOpenCvLoaded(): Boolean {
        if (!isOpenCvInitialized) {
            isOpenCvInitialized = try {
                OpenCVLoader.initLocal()
            } catch (error: Exception) {
                Log.e(TAG, "OpenCV initialization failed.", error)
                false
            } catch (error: LinkageError) {
                Log.e(TAG, "Bundled OpenCV native library could not be loaded.", error)
                false
            }
        }
        return isOpenCvInitialized
    }

    fun detectDocumentCorners(bitmap: Bitmap): List<PointF>? {
        if (!ensureOpenCvLoaded()) return null
        return try {
            DocumentDetector.detectDocumentFromBitmap(bitmap)?.orderedCorners
        } catch (error: Exception) {
            Log.e(TAG, "Document boundary detection failed.", error)
            null
        } catch (error: LinkageError) {
            Log.e(TAG, "OpenCV document detection failed.", error)
            null
        }
    }

    fun applyPerspectiveCrop(sourceBitmap: Bitmap, corners: List<PointF>): Bitmap {
        if (!ensureOpenCvLoaded()) return copyFallback(sourceBitmap)
        var warped: Bitmap? = null
        return try {
            warped = PerspectiveCorrector.warpPerspective(sourceBitmap, corners)
            val deskewed = ImageEnhancer.deskewBitmapIfNeeded(warped)
            if (deskewed !== warped) warped.recycle()
            deskewed
        } catch (error: Exception) {
            Log.e(TAG, "Perspective correction failed; retaining the uncropped image.", error)
            warped?.takeIf { it !== sourceBitmap && !it.isRecycled }?.recycle()
            copyFallback(sourceBitmap)
        } catch (error: LinkageError) {
            Log.e(TAG, "OpenCV perspective correction failed; retaining the uncropped image.", error)
            warped?.takeIf { it !== sourceBitmap && !it.isRecycled }?.recycle()
            copyFallback(sourceBitmap)
        }
    }

    fun applyEnhancementMode(
        sourceBitmap: Bitmap,
        mode: EnhancementMode
    ): Bitmap {
        if (!ensureOpenCvLoaded()) return copyFallback(sourceBitmap)
        return try {
            ImageEnhancer.enhanceBitmapForOcr(sourceBitmap, mode)
        } catch (error: Exception) {
            Log.e(TAG, "Image enhancement failed; retaining the source image.", error)
            copyFallback(sourceBitmap)
        } catch (error: LinkageError) {
            Log.e(TAG, "OpenCV enhancement failed; retaining the source image.", error)
            copyFallback(sourceBitmap)
        }
    }

    fun preprocess(
        imageFile: File,
        applyOpenCvEnhancement: Boolean = true,
        mode: EnhancementMode = EnhancementMode.DOCUMENT_CLEANUP
    ): Bitmap? {
        val normalized = ImageUtils.decodeAndNormalizeBitmap(imageFile) ?: return null
        if (!applyOpenCvEnhancement) return normalized
        val enhanced = applyEnhancementMode(normalized, mode)
        if (enhanced !== normalized) normalized.recycle()
        return enhanced
    }

    private fun copyFallback(sourceBitmap: Bitmap): Bitmap =
        sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Unable to copy the source image for fallback.")

    private companion object {
        const val TAG = "ImagePreprocessor"
    }
}
