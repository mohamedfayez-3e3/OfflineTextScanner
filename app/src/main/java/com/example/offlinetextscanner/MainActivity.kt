package com.example.offlinetextscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.activities.CameraActivity
import com.example.offlinetextscanner.activities.PreviewActivity
import com.example.offlinetextscanner.databinding.ActivityMainBinding
import com.example.offlinetextscanner.gallery.GalleryManager
import com.example.offlinetextscanner.utils.FileHelper
import com.example.offlinetextscanner.utils.PermissionHelper

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var galleryManager: GalleryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionHelper = PermissionHelper(this) { granted ->
            if (granted) {
                startActivity(Intent(this, CameraActivity::class.java))
            } else {
                Toast.makeText(this, R.string.err_camera_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
        galleryManager = GalleryManager(this, ::handleGallerySelection)

        binding.btnScanCamera.setOnClickListener { permissionHelper.requestCameraPermission() }
        binding.btnImportGallery.setOnClickListener { galleryManager.openPhotoPicker() }
    }

    private fun handleGallerySelection(selectedUri: Uri?) {
        if (selectedUri == null) {
            Toast.makeText(this, R.string.err_gallery_canceled, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val imageFile = FileHelper.copyUriToTempFile(this, selectedUri)
            startActivity(
                Intent(this, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_IMAGE_PATH, imageFile.absolutePath)
                    putExtra(PreviewActivity.EXTRA_SOURCE_TYPE, PreviewActivity.SOURCE_GALLERY)
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to import selected image.", error)
            val message = if (!FileHelper.hasSufficientStorage(this)) {
                getString(R.string.err_insufficient_storage)
            } else {
                getString(R.string.err_invalid_image)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
