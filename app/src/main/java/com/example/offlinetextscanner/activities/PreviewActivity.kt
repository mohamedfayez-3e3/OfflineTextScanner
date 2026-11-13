package com.example.offlinetextscanner.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.databinding.ActivityPreviewBinding
import com.example.offlinetextscanner.imageprocessing.ImagePreprocessor
import com.example.offlinetextscanner.imageprocessing.ImageUtils
import com.example.offlinetextscanner.ocr.OCRConfig
import com.example.offlinetextscanner.ocr.OCRProcessor
import com.example.offlinetextscanner.ocr.OCRResult
import com.example.offlinetextscanner.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PreviewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_GALLERY = "gallery"
        private const val TAG = "PreviewActivity"
        private const val PREVIEW_MAX_DIMENSION = 1200
    }

    private lateinit var binding: ActivityPreviewBinding
    private val imagePreprocessor = ImagePreprocessor()
    private lateinit var ocrProcessor: OCRProcessor
    private var imageFile: File? = null
    private var sourceType = SOURCE_CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ocrProcessor = OCRProcessor(this)
        sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE) ?: SOURCE_CAMERA

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val file = imagePath?.let(::File)
        if (file == null || !file.isFile || file.length() == 0L) {
            Toast.makeText(this, R.string.err_invalid_image, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        imageFile = file
        loadPreview(file)
        setupButtons()
    }

    private fun loadPreview(file: File) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    ImageUtils.decodeAndNormalizeBitmap(file, PREVIEW_MAX_DIMENSION)
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to decode preview image.", error)
                    null
                }
            }
            if (bitmap == null) {
                Toast.makeText(this@PreviewActivity, R.string.err_image_decode, Toast.LENGTH_LONG).show()
                finish()
            } else {
                binding.ivPreviewImage.setImageBitmap(bitmap)
            }
        }
    }

    private fun setupButtons() {
        binding.btnRetake.setOnClickListener {
            imageFile?.delete()
            if (sourceType == SOURCE_CAMERA) startActivity(Intent(this, CameraActivity::class.java))
            finish()
        }
        binding.btnConfirmOcr.setOnClickListener {
            imageFile?.let { runOcrPipeline(it) }
        }
    }

    private fun getSelectedLanguageCode(): String = when (binding.rgLanguageMode.checkedRadioButtonId) {
        R.id.rbLangEng -> OCRConfig.LANG_ENGLISH
        R.id.rbLangAra -> OCRConfig.LANG_ARABIC
        else -> OCRConfig.LANG_MIXED_ARA_ENG
    }

    private fun runOcrPipeline(file: File) {
        val selectedLanguage = getSelectedLanguageCode()
        val applyEnhancement = binding.cbEnablePreprocessing.isChecked
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.btnConfirmOcr.isEnabled = false
        binding.btnRetake.isEnabled = false
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    imagePreprocessor.preprocess(file, applyEnhancement)
                }
                if (bitmap == null) {
                    showError(getString(R.string.err_image_decode))
                    return@launch
                }

                val result = try {
                    ocrProcessor.recognizeText(bitmap, selectedLanguage)
                } finally {
                    bitmap.recycle()
                }
                when (result) {
                    is OCRResult.Success -> {
                        startActivity(
                            Intent(this@PreviewActivity, ResultActivity::class.java).apply {
                                putExtra(ResultActivity.EXTRA_EXTRACTED_TEXT, result.extractedText)
                                putExtra(ResultActivity.EXTRA_CONFIDENCE, result.meanConfidence)
                                putExtra(ResultActivity.EXTRA_LANGUAGE, result.languageCode)
                            }
                        )
                        finish()
                    }
                    is OCRResult.Error -> showError(errorMessage(result))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(getString(R.string.err_ocr_failed, error.localizedMessage ?: error.javaClass.simpleName))
            } finally {
                binding.loadingOverlay.visibility = View.GONE
                binding.btnConfirmOcr.isEnabled = true
                binding.btnRetake.isEnabled = true
            }
        }
    }

    private fun errorMessage(result: OCRResult.Error): String = when (result.errorType) {
        OCRResult.ErrorType.MISSING_TESSDATA ->
            getString(R.string.err_missing_tessdata, result.details ?: "eng/ara")
        OCRResult.ErrorType.UNSUPPORTED_LANGUAGE ->
            getString(R.string.err_unsupported_language, result.details.orEmpty())
        OCRResult.ErrorType.INIT_FAILED -> getString(R.string.err_ocr_init)
        OCRResult.ErrorType.EMPTY_RESULT -> getString(R.string.err_empty_ocr_result)
        OCRResult.ErrorType.INSUFFICIENT_STORAGE -> getString(R.string.err_insufficient_storage)
        OCRResult.ErrorType.PROCESSING_FAILURE ->
            getString(R.string.err_ocr_failed, result.details ?: "Unknown error")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        binding.ivPreviewImage.setImageDrawable(null)
        super.onDestroy()
    }

}
