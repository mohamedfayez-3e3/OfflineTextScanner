package com.example.offlinetextscanner.ocr

object BidiTextHelper {
    fun containsArabicScript(text: String): Boolean = text.any { character ->
        character.code in 0x0600..0x06FF ||
            character.code in 0x0750..0x077F ||
            character.code in 0x08A0..0x08FF ||
            character.code in 0xFB50..0xFDFF ||
            character.code in 0xFE70..0xFEFF
    }

    fun containsLatinScript(text: String): Boolean =
        text.any { it in 'A'..'Z' || it in 'a'..'z' }

    fun detectReadingOrder(text: String): ReadingOrder {
        val hasArabic = containsArabicScript(text)
        val hasLatin = containsLatinScript(text)
        return when {
            hasArabic && hasLatin -> ReadingOrder.MIXED
            hasArabic -> ReadingOrder.RTL
            else -> ReadingOrder.LTR
        }
    }

    fun orderWordsInLine(words: List<TextWord>): List<TextWord> {
        if (words.size < 2) return words
        val arabicCount = words.count { it.isRtl }
        return when (arabicCount) {
            words.size -> words.sortedByDescending { it.boundingBox.centerX }
            0 -> words.sortedBy { it.boundingBox.centerX }
            else -> words
        }
    }

    fun buildFormattedTextFromSelectedWords(
        selectedWords: Collection<TextWord>
    ): String {
        if (selectedWords.isEmpty()) return ""
        val groups = selectedWords.groupBy { it.blockIndex to it.lineIndex }
            .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        val output = mutableListOf<String>()
        var previousBlock: Int? = null
        for ((key, words) in groups) {
            if (previousBlock != null && previousBlock != key.first) output += ""
            previousBlock = key.first
            output += orderWordsInLine(words).joinToString(" ") { it.text }
        }
        return output.joinToString("\n").trim()
    }
}
