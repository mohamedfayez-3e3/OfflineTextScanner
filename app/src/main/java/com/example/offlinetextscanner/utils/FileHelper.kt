package com.example.offlinetextscanner.utils

import android.content.Context
import android.net.Uri
import com.example.offlinetextscanner.ocr.OCRConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileHelper {
    private const val TESSERACT_DIR_NAME = "tesseract"
    private const val TESSDATA_SUBDIR_NAME = "tessdata"
    private const val MIN_FREE_SPACE_BYTES = 15L * 1024L * 1024L

    fun hasSufficientStorage(context: Context): Boolean =
        context.filesDir.usableSpace >= MIN_FREE_SPACE_BYTES

    @Throws(IOException::class)
    fun createTempImageFile(context: Context, prefix: String = "SCAN_"): File {
        if (!hasSufficientStorage(context)) {
            throw IOException("Insufficient internal storage space.")
        }
        val scanDir = File(context.cacheDir, "scans")
        if (!scanDir.exists() && !scanDir.mkdirs()) {
            throw IOException("Could not create temporary scan directory.")
        }
        return File.createTempFile(prefix, ".jpg", scanDir)
    }

    @Throws(IOException::class)
    fun copyUriToTempFile(context: Context, sourceUri: Uri): File {
        val destination = createTempImageFile(context, "GALLERY_")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to open the selected image.")
            if (destination.length() == 0L) {
                throw IOException("Selected image is empty.")
            }
            return destination
        } catch (error: Exception) {
            destination.delete()
            if (error is IOException) throw error
            throw IOException("Failed to copy the selected image.", error)
        }
    }

    @Throws(IOException::class)
    fun prepareTessDataDirectory(context: Context): String {
        if (!hasSufficientStorage(context)) {
            throw IOException("Insufficient internal storage to extract OCR language data.")
        }
        val baseDir = File(context.filesDir, TESSERACT_DIR_NAME)
        val tessDataDir = File(baseDir, TESSDATA_SUBDIR_NAME)
        if (!tessDataDir.exists() && !tessDataDir.mkdirs()) {
            throw IOException("Could not create tessdata directory.")
        }

        val assetFiles = context.assets.list(TESSDATA_SUBDIR_NAME)?.toSet().orEmpty()
        val missingFiles = mutableListOf<String>()
        for (fileName in OCRConfig.REQUIRED_TRAINEDDATA_FILES) {
            val target = File(tessDataDir, fileName)
            if (target.isFile && target.length() > 0L) continue
            if (fileName !in assetFiles) {
                missingFiles += fileName
                continue
            }

            val temporary = File(tessDataDir, "$fileName.tmp")
            try {
                context.assets.open("$TESSDATA_SUBDIR_NAME/$fileName").use { input ->
                    FileOutputStream(temporary).use { output -> input.copyTo(output) }
                }
                if (temporary.length() == 0L || !temporary.renameTo(target)) {
                    throw IOException("Could not install OCR language data: $fileName")
                }
            } finally {
                temporary.delete()
            }
        }
        if (missingFiles.isNotEmpty()) {
            throw MissingTessDataException(missingFiles)
        }
        return baseDir.absolutePath
    }

    class MissingTessDataException(val missingFiles: List<String>) :
        IOException("Missing OCR language files: ${missingFiles.joinToString(", ")}")
}
