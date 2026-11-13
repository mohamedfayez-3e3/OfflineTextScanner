package com.example.offlinetextscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.offlinetextscanner.utils.FileHelper
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OCRProcessor(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun recognizeText(
        bitmap: Bitmap,
        languageCode: String = OCRConfig.LANG_MIXED_ARA_ENG
    ): OCRResult = withContext(Dispatchers.Default) {
        if (!OCRConfig.isSupportedLanguage(languageCode)) {
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.UNSUPPORTED_LANGUAGE,
                languageCode
            )
        }

        val dataPath = try {
            FileHelper.prepareTessDataDirectory(applicationContext)
        } catch (error: FileHelper.MissingTessDataException) {
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.MISSING_TESSDATA,
                error.missingFiles.joinToString(", ")
            )
        } catch (error: Exception) {
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.INSUFFICIENT_STORAGE,
                error.localizedMessage
            )
        }

        val api = try {
            TessBaseAPI()
        } catch (error: LinkageError) {
            Log.e(TAG, "Tesseract native library could not be loaded.", error)
            return@withContext OCRResult.Error(
                OCRResult.ErrorType.INIT_FAILED,
                error.localizedMessage
            )
        }
        try {
            val initialized = api.init(dataPath, languageCode, OCRConfig.DEFAULT_OCR_ENGINE_MODE)
            if (!initialized) return@withContext OCRResult.Error(OCRResult.ErrorType.INIT_FAILED)

            api.pageSegMode = OCRConfig.DEFAULT_PAGE_SEG_MODE
            api.setImage(bitmap)
            val text = api.getUTF8Text()?.trim().orEmpty()
            if (text.isEmpty()) {
                OCRResult.Error(OCRResult.ErrorType.EMPTY_RESULT)
            } else {
                OCRResult.Success(text, api.meanConfidence(), languageCode)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Tesseract OCR failed.", error)
            OCRResult.Error(
                OCRResult.ErrorType.PROCESSING_FAILURE,
                error.localizedMessage ?: "Unknown Tesseract error"
            )
        } catch (error: LinkageError) {
            Log.e(TAG, "Tesseract native processing failed.", error)
            OCRResult.Error(
                OCRResult.ErrorType.PROCESSING_FAILURE,
                error.localizedMessage ?: "Tesseract native processing failed"
            )
        } finally {
            try {
                api.recycle()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Tesseract cleanup failed.", error)
            } catch (error: LinkageError) {
                Log.w(TAG, "Tesseract native cleanup failed.", error)
            }
        }
    }

    private companion object {
        const val TAG = "OCRProcessor"
    }
}
