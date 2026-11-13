package com.example.offlinetextscanner.ocr

import com.googlecode.tesseract.android.TessBaseAPI

object OCRConfig {
    const val LANG_ENGLISH = "eng"
    const val LANG_ARABIC = "ara"
    const val LANG_MIXED_ARA_ENG = "ara+eng"

    val SUPPORTED_LANGUAGE_MODES = setOf(LANG_ENGLISH, LANG_ARABIC, LANG_MIXED_ARA_ENG)
    val REQUIRED_TRAINEDDATA_FILES = listOf("eng.traineddata", "ara.traineddata")

    const val DEFAULT_OCR_ENGINE_MODE = TessBaseAPI.OEM_LSTM_ONLY
    const val DEFAULT_PAGE_SEG_MODE = TessBaseAPI.PageSegMode.PSM_AUTO

    fun isSupportedLanguage(languageCode: String): Boolean =
        languageCode in SUPPORTED_LANGUAGE_MODES
}
