package com.example.offlinetextscanner.ocr

object OCRStructureBuilder {
    fun buildBlocks(words: List<TextWord>): List<TextBlock> =
        words.groupBy { it.blockIndex }
            .toSortedMap()
            .mapNotNull { (blockIndex, blockWords) ->
                val lines = blockWords.groupBy { it.lineIndex }
                    .toSortedMap()
                    .mapNotNull { (lineIndex, lineWords) ->
                        if (lineWords.isEmpty()) {
                            null
                        } else {
                            val orderedWords = BidiTextHelper.orderWordsInLine(lineWords)
                            val lineText = orderedWords.joinToString(" ") { it.text }
                            TextLine(
                                id = "b${blockIndex}l${lineIndex}",
                                text = lineText,
                                boundingBox = BoundingBox.union(orderedWords.map { it.boundingBox }),
                                confidence = orderedWords.map { it.confidence }.average().toFloat(),
                                words = orderedWords,
                                blockIndex = blockIndex,
                                readingOrder = BidiTextHelper.detectReadingOrder(lineText)
                            )
                        }
                    }
                    .sortedBy { it.boundingBox.top }
                if (lines.isEmpty()) {
                    null
                } else {
                    val text = lines.joinToString("\n") { it.text }
                    TextBlock(
                        id = "b$blockIndex",
                        text = text,
                        boundingBox = BoundingBox.union(lines.map { it.boundingBox }),
                        confidence = lines.map { it.confidence }.average().toFloat(),
                        lines = lines,
                        blockIndex = blockIndex,
                        readingOrder = BidiTextHelper.detectReadingOrder(text)
                    )
                }
            }
}
