package com.example.offlinetextscanner.utils

import android.content.Context
import android.content.Intent
import com.example.offlinetextscanner.R

object ShareHelper {
    fun shareText(context: Context, text: String) {
        if (text.isBlank()) return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, context.getString(R.string.share_chooser_title))
        )
    }
}
