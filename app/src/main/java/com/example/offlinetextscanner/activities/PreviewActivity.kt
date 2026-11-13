package com.example.offlinetextscanner.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinetextscanner.R
import com.example.offlinetextscanner.databinding.ActivityPreviewBinding
import com.example.offlinetextscanner.imageprocessing.EnhancementMode
import com.example.offlinetextscanner.imageprocessing.ImagePreprocessor
import com.example.offlinetextscanner.imageprocessing.ImageUtils
import com.example.offlinetextscanner.ocr.OCRConfig
import com.example.offlinetextscanner.ocr.OCRProcessor
import com.example.offlinetextscanner.ocr.OCRResult
import com.example.offlinetextscanner.ocr.OCRResultHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
        private const val PREVIEW_MAX_DIMENSION = 1400
    }

    private lateinit var binding: ActivityPreviewBinding
    private val imagePreprocessor = ImagePreprocessor()
    private lateinit var ocrProcessor: OCRProcessor
    private var imageFile: File? = null
    private var sourceType = SOURCE_CAMERA
    private var originalBitmap: Bitmap? = null
    private var processedPreview: Bitmap? = null
    private var selectedMode = EnhancementMode.DOCUMENT_CLEANUP
    private var showingOriginal = true
    private var previewJob: Job? = null
    private var previewGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ocrProcessor = OCRProcessor(this)
        sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE) ?: SOURCE_CAMERA

        val file = intent.getStringExtra(EXTRA_IMAGE_PATH)?.let(::File)
        if (file == null || !file.isFile || file.length() == 0L) {
            Toast.makeText(this, R.string.err_invalid_image, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        imageFile = file
        setupControls()
        loadPreview(file)
    }

    private fun setupControls() {
        binding.spEnhancementMode.setSelection(EnhancementMode.DOCUMENT_CLEANUP.ordinal)
        binding.spEnhancementMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMode = EnhancementMode.entries[position]
                if (!showingOriginal) showProcessedPreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.btnResetCrop.setOnClickListener {
            binding.cropOverlayView.resetToDefaultMargins()
            showOriginalPreview()
        }
        binding.btnBeforeAfter.setOnClickListener {
            if (showingOriginal) showProcessedPreview() else showOriginalPreview()
        }
        binding.btnRetake.setOnClickListener {
            imageFile?.delete()
            if (sourceType == SOURCE_CAMERA) {
                startActivity(Intent(this, CameraActivity::class.java))
            }
            finish()
        }
        binding.btnConfirmOcr.setOnClickListener {
            imageFile?.let(::runOcrPipeline)
        }
    }

    private fun loadPreview(file: File) {
        lifecycleScope.launch {
            val bitmapAndCorners = withContext(Dispatchers.IO) {
                val bitmap = ImageUtils.decodeAndNormalizeBitmap(file, PREVIEW_MAX_DIMENSION)
                    ?: return@withContext null
                try {
                    val corners = withContext(Dispatchers.Default) {
                        imagePreprocessor.detectDocumentCorners(bitmap)
                    }
                    currentCoroutineContext().ensureActive()
                    bitmap to corners
                } catch (error: CancellationException) {
                    bitmap.recycle()
                    throw error
                }
            }
            if (bitmapAndCorners == null) {
                Toast.makeText(this@PreviewActivity, R.string.err_image_decode, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            val (bitmap, corners) = bitmapAndCorners
            originalBitmap = bitmap
            binding.ivPreviewImage.setImageBitmap(bitmap)
            binding.cropOverlayView.setImageGeometryAndCorners(
                bitmap.width,
                bitmap.height,
                corners ?: fullImageCorners(bitmap.width, bitmap.height)
            )
            showOriginalPreview()
        }
    }

    private fun showOriginalPreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        binding.loadingOverlay.visibility = View.GONE
        showingOriginal = true
        originalBitmap?.let(binding.ivPreviewImage::setImageBitmap)
        binding.cropOverlayView.visibility = View.VISIBLE
        binding.btnBeforeAfter.setText(R.string.show_enhanced)
    }

    private fun showProcessedPreview() {
        val source = originalBitmap ?: return
        val sourceCopy = source.copy(Bitmap.Config.ARGB_8888, false) ?: run {
            showError(getString(R.string.err_image_decode))
            return
        }
        val corners = binding.cropOverlayView.getOrderedImageCorners().map { PointF(it.x, it.y) }
        val mode = selectedMode
        val generation = ++previewGeneration
        showingOriginal = false
        binding.cropOverlayView.visibility = View.GONE
        binding.btnBeforeAfter.setText(R.string.show_original)
        binding.loadingOverlay.visibility = View.VISIBLE
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    val cropped = imagePreprocessor.applyPerspectiveCrop(sourceCopy, corners)
                    try {
                        val processed = imagePreprocessor.applyEnhancementMode(cropped, mode)
                        try {
                            currentCoroutineContext().ensureActive()
                            processed
                        } catch (error: CancellationException) {
                            processed.recycle()
                            throw error
                        }
                    } finally {
                        if (cropped !== sourceCopy && !cropped.isRecycled) cropped.recycle()
                    }
                } finally {
                    if (!sourceCopy.isRecycled) sourceCopy.recycle()
                }
            }
            if (isFinishing || showingOriginal || generation != previewGeneration) {
                result.recycle()
                if (generation == previewGeneration) binding.loadingOverlay.visibility = View.GONE
                return@launch
            }
            if (processedPreview !== result) processedPreview?.recycle()
            processedPreview = result
            binding.ivPreviewImage.setImageBitmap(result)
            binding.loadingOverlay.visibility = View.GONE
        }
    }

    private fun getSelectedLanguageCode(): String = when (binding.rgLanguageMode.checkedRadioButtonId) {
        R.id.rbLangEng -> OCRConfig.LANG_ENGLISH
        R.id.rbLangAra -> OCRConfig.LANG_ARABIC
        else -> OCRConfig.LANG_MIXED_ARA_ENG
    }

    private fun runOcrPipeline(file: File) {
        val selectedLanguage = getSelectedLanguageCode()
        val mode = selectedMode
        val applyEnhancement = mode != EnhancementMode.ORIGINAL_COLOR
        val corners = binding.cropOverlayView.getOrderedImageCorners().map { PointF(it.x, it.y) }
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.btnConfirmOcr.isEnabled = false
        binding.btnRetake.isEnabled = false
        binding.spEnhancementMode.isEnabled = false

        lifecycleScope.launch {
            var displayCopy: Bitmap? = null
            try {
                val ocrBitmap = withContext(Dispatchers.Default) {
                    val decoded = ImageUtils.decodeAndNormalizeBitmap(file)
                        ?: return@withContext null
                    val cropped = try {
                        imagePreprocessor.applyPerspectiveCrop(decoded, corners)
                    } finally {
                        if (!decoded.isRecycled) decoded.recycle()
                    }
                    val result = try {
                        if (applyEnhancement) {
                            imagePreprocessor.applyEnhancementMode(cropped, mode)
                        } else {
                            cropped.copy(Bitmap.Config.ARGB_8888, false)
                                ?: error("Unable to copy the cropped image.")
                        }
                    } finally {
                        if (!cropped.isRecycled) cropped.recycle()
                    }
                    try {
                        currentCoroutineContext().ensureActive()
                        result
                    } catch (error: CancellationException) {
                        result.recycle()
                        throw error
                    }
                }
                if (ocrBitmap == null) {
                    showError(getString(R.string.err_image_decode))
                    return@launch
                }

                try {
                    when (val result = ocrProcessor.recognizeText(ocrBitmap, selectedLanguage)) {
                        is OCRResult.Success -> {
                            displayCopy = ocrBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                ?: error("Unable to preserve the processed scan for image selection.")
                            val oldBitmap = OCRResultHolder.latestDisplayBitmap
                            OCRResultHolder.clear()
                            if (oldBitmap != null && !oldBitmap.isRecycled) oldBitmap.recycle()
                            OCRResultHolder.latestStructuredPage = result.structuredPage
                            OCRResultHolder.latestDisplayBitmap = displayCopy
                            displayCopy = null
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
                } finally {
                    if (!ocrBitmap.isRecycled) ocrBitmap.recycle()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to process the selected document.", error)
                showError(getString(R.string.err_ocr_failed, error.localizedMessage ?: error.javaClass.simpleName))
            } finally {
                displayCopy?.recycle()
                binding.loadingOverlay.visibility = View.GONE
                binding.btnConfirmOcr.isEnabled = true
                binding.btnRetake.isEnabled = true
                binding.spEnhancementMode.isEnabled = true
            }
        }
    }

    private fun fullImageCorners(width: Int, height: Int): List<PointF> = listOf(
        PointF(0f, 0f),
        PointF((width - 1).coerceAtLeast(0).toFloat(), 0f),
        PointF((width - 1).coerceAtLeast(0).toFloat(), (height - 1).coerceAtLeast(0).toFloat()),
        PointF(0f, (height - 1).coerceAtLeast(0).toFloat())
    )

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
        previewJob?.cancel()
        binding.ivPreviewImage.setImageDrawable(null)
        originalBitmap?.takeIf { !it.isRecycled }?.recycle()
        processedPreview?.takeIf { !it.isRecycled }?.recycle()
        super.onDestroy()
    }
}
