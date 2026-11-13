package com.example.offlinetextscanner.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.camera.CameraManager
import com.example.offlinetextscanner.camera.DocumentCaptureTracker
import com.example.offlinetextscanner.camera.ImageCaptureHandler
import com.example.offlinetextscanner.databinding.ActivityCameraBinding
import com.example.offlinetextscanner.gallery.GalleryManager
import com.example.offlinetextscanner.utils.FileHelper

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var imageCaptureHandler: ImageCaptureHandler
    private lateinit var galleryManager: GalleryManager
    private val captureTracker = DocumentCaptureTracker()
    private var flashEnabled = false
    private var isCapturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageCaptureHandler = ImageCaptureHandler(this)
        galleryManager = GalleryManager(this, ::openGalleryImage)
        cameraManager = CameraManager(this, this, binding.viewFinder) {
            Toast.makeText(this, R.string.err_camera_unavailable, Toast.LENGTH_LONG).show()
            finish()
        }
        binding.cameraProgressBar.visibility = View.VISIBLE
        cameraManager.startCamera(
            onReady = {
                binding.cameraProgressBar.visibility = View.GONE
                binding.btnFlash.isEnabled = cameraManager.hasFlash
                cameraManager.setFlashEnabled(flashEnabled)
                updateFlashLabel()
            },
            onDocumentDetected = { detection ->
                val shouldCapture = captureTracker.observe(
                    detection?.quad,
                    detection?.sharpness ?: 0.0
                )
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        binding.documentOverlay.setDocumentDetection(detection)
                        if (shouldCapture) captureDocumentImage()
                    }
                }
            }
        )
        binding.btnCloseCamera.setOnClickListener { finish() }
        binding.btnCaptureImage.setOnClickListener { captureDocumentImage() }
        binding.btnFlash.setOnClickListener {
            if (cameraManager.hasFlash) {
                flashEnabled = !flashEnabled
                cameraManager.setFlashEnabled(flashEnabled)
                updateFlashLabel()
            }
        }
        binding.btnGallery.setOnClickListener { galleryManager.openPhotoPicker() }
    }

    private fun captureDocumentImage() {
        if (isCapturing) return
        isCapturing = true
        binding.btnCaptureImage.isEnabled = false
        binding.cameraProgressBar.visibility = View.VISIBLE
        imageCaptureHandler.captureImage(
            cameraManager.imageCapture,
            onSuccess = { savedUri ->
                val imagePath = savedUri.path
                if (imagePath == null) {
                    isCapturing = false
                    captureTracker.resetAfterCaptureFailure()
                    binding.cameraProgressBar.visibility = View.GONE
                    binding.btnCaptureImage.isEnabled = true
                    Toast.makeText(this, R.string.err_invalid_image, Toast.LENGTH_LONG).show()
                    return@captureImage
                }
                startActivity(
                    Intent(this, PreviewActivity::class.java).apply {
                        putExtra(PreviewActivity.EXTRA_IMAGE_PATH, imagePath)
                        putExtra(PreviewActivity.EXTRA_SOURCE_TYPE, PreviewActivity.SOURCE_CAMERA)
                    }
                )
                finish()
            },
            onError = { message ->
                isCapturing = false
                captureTracker.resetAfterCaptureFailure()
                binding.cameraProgressBar.visibility = View.GONE
                binding.btnCaptureImage.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun openGalleryImage(uri: Uri?) {
        if (uri == null) {
            Toast.makeText(this, R.string.err_gallery_canceled, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val imageFile = FileHelper.copyUriToTempFile(this, uri)
            startActivity(
                Intent(this, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_IMAGE_PATH, imageFile.absolutePath)
                    putExtra(PreviewActivity.EXTRA_SOURCE_TYPE, PreviewActivity.SOURCE_GALLERY)
                }
            )
            finish()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to import the gallery image.", error)
            Toast.makeText(this, R.string.err_invalid_image, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateFlashLabel() {
        binding.btnFlash.text = if (flashEnabled) "Flash on" else "Flash off"
    }

    override fun onDestroy() {
        if (::cameraManager.isInitialized) cameraManager.close()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CameraActivity"
    }
}
