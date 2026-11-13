package com.example.offlinetextscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.offlinetextscanner.utils.FileHelper
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
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
            Log.e(TAG, "Unable to prepare local Tesseract data.", error)
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
            if (!api.init(dataPath, languageCode, OCRConfig.DEFAULT_OCR_ENGINE_MODE)) {
                return@withContext OCRResult.Error(OCRResult.ErrorType.INIT_FAILED)
            }

            api.pageSegMode = OCRConfig.DEFAULT_PAGE_SEG_MODE
            api.setVariable("preserve_interword_spaces", "1")
            val startedAt = System.nanoTime()
            api.setImage(bitmap)
            val text = api.getUTF8Text()?.trim().orEmpty()
            if (text.isEmpty()) {
                return@withContext OCRResult.Error(OCRResult.ErrorType.EMPTY_RESULT)
            }

            val confidence = api.meanConfidence()
            val blocks = extractBlocks(api)
            val page = StructuredOCRPage(
                fullText = text,
                blocks = blocks,
                meanConfidence = confidence,
                languageCode = languageCode,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                processingTimeMs = (System.nanoTime() - startedAt) / 1_000_000L
            )
            OCRResult.Success(text, confidence, languageCode, page)
        } catch (error: Exception) {
            Log.e(TAG, "Tesseract OCR failed.", error)
            OCRResult.Error(
                OCRResult.ErrorType.PROCESSING_FAILURE,
                error.localizedMessage ?: error.javaClass.simpleName
            )
        } catch (error: LinkageError) {
            Log.e(TAG, "Tesseract native OCR processing failed.", error)
            OCRResult.Error(
                OCRResult.ErrorType.PROCESSING_FAILURE,
                error.localizedMessage ?: error.javaClass.simpleName
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

    private fun extractBlocks(api: TessBaseAPI): List<TextBlock> {
        val iterator: ResultIterator = api.resultIterator ?: return emptyList()
        val words = mutableListOf<TextWord>()
        var blockIndex = -1
        var lineIndex = -1
        var wordIndex = 0

        try {
            iterator.begin()
            do {
                if (iterator.isAtBeginningOf(PageIteratorLevel.RIL_BLOCK) || blockIndex < 0) {
                    blockIndex++
                    lineIndex = 0
                } else if (iterator.isAtBeginningOf(PageIteratorLevel.RIL_TEXTLINE)) {
                    lineIndex++
                }

                val value = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    val rect = iterator.getBoundingRect(PageIteratorLevel.RIL_WORD)
                    if (rect != null && rect.width() > 0 && rect.height() > 0) {
                        val box = BoundingBox(rect.left, rect.top, rect.right, rect.bottom)
                        val word = TextWord(
                            id = "b${blockIndex}l${lineIndex}w${wordIndex++}",
                            text = value,
                            boundingBox = box,
                            confidence = iterator.confidence(PageIteratorLevel.RIL_WORD),
                            lineIndex = lineIndex,
                            blockIndex = blockIndex,
                            isRtl = BidiTextHelper.containsArabicScript(value)
                        )
                        words += word
                    }
                }
            } while (iterator.next(PageIteratorLevel.RIL_WORD))
        } catch (error: Exception) {
            Log.w(TAG, "Could not read all Tesseract word coordinates.", error)
        } finally {
            try {
                iterator.delete()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Tesseract result iterator cleanup failed.", error)
            }
        }

        return OCRStructureBuilder.buildBlocks(words)
    }

    private companion object {
        const val TAG = "OCRProcessor"
    }
}
