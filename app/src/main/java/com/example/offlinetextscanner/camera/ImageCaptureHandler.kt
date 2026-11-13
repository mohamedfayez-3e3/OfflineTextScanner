package com.example.offlinetextscanner.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.example.offlinetextscanner.utils.FileHelper
import java.io.File

class ImageCaptureHandler(private val context: Context) {
    fun captureImage(
        imageCapture: ImageCapture?,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Camera is not ready for capture.")
            return
        }
        val photoFile: File = try {
            FileHelper.createTempImageFile(context, "CAMERA_")
        } catch (error: Exception) {
            onError(error.message ?: "Failed to create image file.")
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(Uri.fromFile(photoFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    photoFile.delete()
                    onError(exception.message ?: "Image capture failed.")
                }
            }
        )
    }
}
