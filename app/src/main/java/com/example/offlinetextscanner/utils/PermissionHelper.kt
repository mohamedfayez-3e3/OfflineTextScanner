package com.example.offlinetextscanner.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionHelper(
    private val activity: ComponentActivity,
    private val onPermissionResult: (Boolean) -> Unit
) {
    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult(granted)
        }

    fun hasCameraPermission(context: Context = activity): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    fun requestCameraPermission() {
        if (hasCameraPermission()) {
            onPermissionResult(true)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
