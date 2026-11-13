package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
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

    fun preprocess(imageFile: File, applyOpenCvEnhancement: Boolean = true): Bitmap? {
        val normalized = ImageUtils.decodeAndNormalizeBitmap(imageFile) ?: return null
        if (!applyOpenCvEnhancement) return normalized
        if (!ensureOpenCvLoaded()) {
            Log.w(TAG, "OpenCV is unavailable; using the normalized original image.")
            return normalized
        }
        return try {
            ImageEnhancer.enhanceBitmapForOcr(normalized).also { normalized.recycle() }
        } catch (error: Exception) {
            Log.e(TAG, "OpenCV enhancement failed; using the normalized original image.", error)
            normalized
        } catch (error: LinkageError) {
            Log.e(TAG, "OpenCV native processing failed; using the normalized original image.", error)
            normalized
        }
    }

    private companion object {
        const val TAG = "ImagePreprocessor"
    }
}
