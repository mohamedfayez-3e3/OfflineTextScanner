package com.example.offlinetextscanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {
    private const val MAX_OCR_DIMENSION = 2200
    private const val TAG = "ImageUtils"

    fun decodeAndNormalizeBitmap(
        imageFile: File,
        maxDimension: Int = MAX_OCR_DIMENSION
    ): Bitmap? {
        require(maxDimension > 0)
        if (!imageFile.isFile || imageFile.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(imageFile.absolutePath, options) ?: return null
        return resizeIfNeeded(applyExifOrientation(raw, imageFile.absolutePath), maxDimension)
    }

    private fun applyExifOrientation(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val orientation = ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                    matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 ->
                    matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                    matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 ->
                    matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 ->
                    matrix.setRotate(-90f)
                else -> return bitmap
            }
            val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (corrected !== bitmap) bitmap.recycle()
            corrected
        } catch (error: Exception) {
            Log.w(TAG, "Could not read or apply image orientation.", error)
            bitmap
        }
    }

    fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int = MAX_OCR_DIMENSION): Bitmap {
        require(maxDimension > 0)
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / currentMax
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }
}
