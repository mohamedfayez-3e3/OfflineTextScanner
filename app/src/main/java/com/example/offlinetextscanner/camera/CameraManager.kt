package com.example.offlinetextscanner.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.offlinetextscanner.imageprocessing.DocumentDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onCameraError: (Throwable) -> Unit
) {
    var imageCapture: ImageCapture? = null
        private set
    private var boundCamera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null
    private var lastAnalysisTimeMs = 0L

    fun startCamera(
        onReady: () -> Unit = {},
        onDocumentDetected: (DocumentDetector.Detection?) -> Unit = {}
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider.hasCamera(selector)) {
                    onCameraError(IllegalStateException("Back camera is unavailable."))
                    return@addListener
                }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val openCvAvailable = DocumentDetector.ensureOpenCvLoaded()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis = analysis
                val executor = Executors.newSingleThreadExecutor()
                analysisExecutor = executor
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAnalysisTimeMs < ANALYSIS_INTERVAL_MS) return@setAnalyzer
                        lastAnalysisTimeMs = now
                        val detection = if (openCvAvailable) DocumentDetector.detect(image) else null
                        onDocumentDetected(detection)
                    } catch (error: Exception) {
                        Log.w(TAG, "Live document analysis failed for a camera frame.", error)
                        onDocumentDetected(null)
                    } catch (error: LinkageError) {
                        Log.e(TAG, "OpenCV native analysis failed.", error)
                        onDocumentDetected(null)
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                imageCapture = capture
                onReady()
            } catch (error: Exception) {
                Log.e(TAG, "Unable to initialize the CameraX pipeline.", error)
                close()
                onCameraError(error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val hasFlash: Boolean
        get() = boundCamera?.cameraInfo?.hasFlashUnit() == true

    fun setFlashEnabled(enabled: Boolean) {
        imageCapture?.flashMode = if (enabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    fun close() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        boundCamera = null
        imageCapture = null
    }

    private companion object {
        const val TAG = "CameraManager"
        const val ANALYSIS_INTERVAL_MS = 100L
    }
}
