package com.example.offlinetextscanner.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinetextscanner.MainActivity
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.databinding.ActivityResultBinding
import com.example.offlinetextscanner.ocr.OCRResultHolder
import com.example.offlinetextscanner.utils.ClipboardHelper
import com.example.offlinetextscanner.utils.ShareHelper

class ResultActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EXTRACTED_TEXT = "extra_extracted_text"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_LANGUAGE = "extra_language"
    }

    private lateinit var binding: ActivityResultBinding
    private var hasImageSelection = false
    private var selectedImageText = ""
    private var displayBitmap: android.graphics.Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val page = OCRResultHolder.latestStructuredPage
        displayBitmap = OCRResultHolder.latestDisplayBitmap
        hasImageSelection = page?.allWords?.isNotEmpty() == true && displayBitmap != null
        binding.etExtractedText.setText(
            page?.fullText ?: intent.getStringExtra(EXTRA_EXTRACTED_TEXT).orEmpty()
        )
        val confidence = page?.meanConfidence ?: intent.getIntExtra(EXTRA_CONFIDENCE, 0)
        val language = page?.languageCode ?: intent.getStringExtra(EXTRA_LANGUAGE).orEmpty()
        binding.tvConfidenceMeta.text = getString(R.string.result_metadata, language, confidence)

        if (hasImageSelection && page != null) {
            binding.textOverlayView.setDocumentData(displayBitmap, page)
            binding.textOverlayView.onSelectionChanged = { text, _ ->
                selectedImageText = text
            }
            showImageMode()
        } else {
            binding.btnModeImage.isEnabled = false
            binding.showTextOnlyImageFallback()
        }
        setupActions()
    }

    private fun ActivityResultBinding.showTextOnlyImageFallback() {
        imageModeContainer.visibility = View.GONE
        selectionControlsRow.visibility = View.GONE
        etExtractedText.visibility = View.VISIBLE
    }

    private fun setupActions() {
        binding.btnModeImage.setOnClickListener {
            if (hasImageSelection) showImageMode()
            else Toast.makeText(this, R.string.no_image_selection, Toast.LENGTH_SHORT).show()
        }
        binding.btnModeText.setOnClickListener { showEditableTextMode() }
        binding.btnSelectAllWords.setOnClickListener { binding.textOverlayView.selectAllWords() }
        binding.btnClearSelection.setOnClickListener { binding.textOverlayView.clearSelection() }
        binding.btnCopySelected.setOnClickListener { copyText(selectedTextOrEditorSelection()) }
        binding.btnCopyAll.setOnClickListener {
            copyText(binding.etExtractedText.text?.toString().orEmpty())
        }
        binding.btnShareText.setOnClickListener {
            val text = if (binding.imageModeContainer.visibility == View.VISIBLE &&
                selectedImageText.isNotBlank()
            ) {
                selectedImageText
            } else {
                binding.etExtractedText.text?.toString().orEmpty()
            }
            if (text.isBlank()) {
                Toast.makeText(this, R.string.err_empty_ocr_result, Toast.LENGTH_SHORT).show()
            } else {
                ShareHelper.shareText(this, text)
            }
        }
        binding.btnScanAnother.setOnClickListener {
            clearHeldResult()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
    }

    private fun showImageMode() {
        binding.imageModeContainer.visibility = View.VISIBLE
        binding.selectionControlsRow.visibility = View.VISIBLE
        binding.etExtractedText.visibility = View.GONE
    }

    private fun showEditableTextMode() {
        binding.imageModeContainer.visibility = View.GONE
        binding.selectionControlsRow.visibility = View.GONE
        binding.etExtractedText.visibility = View.VISIBLE
    }

    private fun selectedTextOrEditorSelection(): String {
        if (binding.imageModeContainer.visibility == View.VISIBLE) return selectedImageText
        val text = binding.etExtractedText.text?.toString().orEmpty()
        val start = binding.etExtractedText.selectionStart
        val end = binding.etExtractedText.selectionEnd
        return if (start >= 0 && end >= 0 && start != end) {
            text.substring(minOf(start, end), maxOf(start, end))
        } else {
            ""
        }
    }

    private fun copyText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, R.string.no_selection_to_copy, Toast.LENGTH_SHORT).show()
            return
        }
        val copied = ClipboardHelper.copyToClipboard(this, text)
        Toast.makeText(
            this,
            if (copied) R.string.text_copied_toast else R.string.err_clipboard_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearHeldResult() {
        binding.textOverlayView.setDocumentData(null, null)
        val bitmap = OCRResultHolder.latestDisplayBitmap
        OCRResultHolder.clear()
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
        displayBitmap = null
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && displayBitmap != null) {
            clearHeldResult()
        }
        super.onDestroy()
    }
}
