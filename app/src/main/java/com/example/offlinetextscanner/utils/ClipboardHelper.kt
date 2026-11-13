package com.example.offlinetextscanner.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "Extracted OCR Text"
    ): Boolean {
        if (text.isBlank()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
