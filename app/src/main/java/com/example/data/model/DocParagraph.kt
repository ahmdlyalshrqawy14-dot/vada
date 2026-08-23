package com.example.data.model

enum class DocParagraphType {
    NORMAL,
    TITLE,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    TABLE_ROW,
    SLIDE_HEADER,
    BULLET_ITEM,
    IMAGE
}

data class DocTextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontSizePt: Float? = null,
    val colorHex: String? = null
)

/**
 * Structural unit extracted from an Office document.
 * tableCells: each cell is a list of runs (paragraphs inside a cell are joined
 * with newline runs so multi-paragraph cells stay faithful).
 * imageBase64 / imageMime: inline image from DOCX media.
 */
data class DocParagraph(
    val type: DocParagraphType = DocParagraphType.NORMAL,
    val runs: List<DocTextRun> = emptyList(),
    val alignment: String? = null, // "left", "center", "right", "both"
    val tableCells: List<List<DocTextRun>>? = null,
    val imageBase64: String? = null,
    val imageMime: String? = null
) {
    val plainText: String
        get() = runs.joinToString("") { it.text }

    val isBlank: Boolean
        get() = plainText.isBlank() &&
            tableCells.isNullOrEmpty() &&
            imageBase64.isNullOrBlank()
}
