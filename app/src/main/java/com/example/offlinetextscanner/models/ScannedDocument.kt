package com.example.offlinetextscanner.models

import android.net.Uri

data class ScannedDocument(
    val imageUri: Uri,
    val extractedText: String,
    val languageCode: String,
    val confidenceScore: Int = 0,
    val timestampMillis: Long = System.currentTimeMillis()
)
