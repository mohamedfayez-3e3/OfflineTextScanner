package com.example.offlinetextscanner.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.camera.CameraManager
import com.example.offlinetextscanner.camera.ImageCaptureHandler
import com.example.offlinetextscanner.databinding.ActivityCameraBinding

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var imageCaptureHandler: ImageCaptureHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageCaptureHandler = ImageCaptureHandler(this)
        cameraManager = CameraManager(this, this, binding.viewFinder) {
            Toast.makeText(this, R.string.err_camera_unavailable, Toast.LENGTH_LONG).show()
            finish()
        }
        binding.cameraProgressBar.visibility = View.VISIBLE
        cameraManager.startCamera { binding.cameraProgressBar.visibility = View.GONE }
        binding.btnCloseCamera.setOnClickListener { finish() }
        binding.btnCaptureImage.setOnClickListener { captureDocumentImage() }
    }

    private fun captureDocumentImage() {
        binding.btnCaptureImage.isEnabled = false
        binding.cameraProgressBar.visibility = View.VISIBLE
        imageCaptureHandler.captureImage(
            cameraManager.imageCapture,
            onSuccess = { savedUri ->
                val imagePath = savedUri.path
                if (imagePath == null) {
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
                binding.cameraProgressBar.visibility = View.GONE
                binding.btnCaptureImage.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        )
    }
}
