package com.example.offlinetextscanner.ocr

import android.graphics.Bitmap

object OCRResultHolder {
    @Volatile
    var latestStructuredPage: StructuredOCRPage? = null

    @Volatile
    var latestDisplayBitmap: Bitmap? = null

    fun clear() {
        latestStructuredPage = null
        latestDisplayBitmap = null
    }
}
