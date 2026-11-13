package com.example.offlinetextscanner.ocr

sealed class OCRResult {
    data class Success(
        val extractedText: String,
        val meanConfidence: Int,
        val languageCode: String,
        val structuredPage: StructuredOCRPage
    ) : OCRResult()

    data class Error(
        val errorType: ErrorType,
        val details: String? = null
    ) : OCRResult()

    enum class ErrorType {
        MISSING_TESSDATA,
        UNSUPPORTED_LANGUAGE,
        INIT_FAILED,
        EMPTY_RESULT,
        INSUFFICIENT_STORAGE,
        PROCESSING_FAILURE
    }
}
