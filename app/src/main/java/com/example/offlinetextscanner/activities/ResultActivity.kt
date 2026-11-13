package com.example.offlinetextscanner.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.MainActivity
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.databinding.ActivityResultBinding
import com.example.offlinetextscanner.utils.ClipboardHelper
import com.example.offlinetextscanner.utils.ShareHelper

class ResultActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EXTRACTED_TEXT = "extra_extracted_text"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_LANGUAGE = "extra_language"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etExtractedText.setText(intent.getStringExtra(EXTRA_EXTRACTED_TEXT).orEmpty())
        val confidence = intent.getIntExtra(EXTRA_CONFIDENCE, 0)
        val language = intent.getStringExtra(EXTRA_LANGUAGE).orEmpty()
        binding.tvConfidenceMeta.text = getString(R.string.result_metadata, language, confidence)

        binding.btnCopyAll.setOnClickListener {
            val copied = ClipboardHelper.copyToClipboard(
                this,
                binding.etExtractedText.text?.toString().orEmpty()
            )
            Toast.makeText(
                this,
                if (copied) R.string.text_copied_toast else R.string.err_clipboard_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.btnShareText.setOnClickListener {
            val text = binding.etExtractedText.text?.toString().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, R.string.err_empty_ocr_result, Toast.LENGTH_SHORT).show()
            } else {
                ShareHelper.shareText(this, text)
            }
        }
        binding.btnScanAnother.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
    }
}
