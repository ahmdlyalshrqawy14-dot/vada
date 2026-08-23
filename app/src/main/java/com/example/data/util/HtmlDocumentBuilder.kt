package com.example.data.util

import android.graphics.Color as AndroidColor
import com.example.data.model.DocParagraph
import com.example.data.model.DocParagraphType
import com.example.data.model.DocTextRun

/**
 * Builds semantic HTML that mirrors the *original* document structure —
 * headings, paragraphs, tables, lists, inline styles, images.
 * No decorative banners or redesign. Goal: visual fidelity to the source file.
 */
object HtmlDocumentBuilder {

    fun buildDocxHtml(paragraphs: List<DocParagraph>, documentTitle: String): String {
        val body = StringBuilder()
        var inBulletList = false
        var i = 0
        while (i < paragraphs.size) {
            val p = paragraphs[i]
            if (p.isBlank && p.type != DocParagraphType.IMAGE) {
                i++
                continue
            }

            if (p.type == DocParagraphType.IMAGE && !p.imageBase64.isNullOrBlank()) {
                if (inBulletList) {
                    body.append("</ul>")
                    inBulletList = false
                }
                val mime = p.imageMime ?: "image/png"
                body.append("<p class=\"doc-image\"><img src=\"data:")
                    .append(mime)
                    .append(";base64,")
                    .append(p.imageBase64)
                    .append("\" alt=\"\"/></p>")
                i++
                continue
            }

            if (p.type == DocParagraphType.TABLE_ROW && p.tableCells != null) {
                if (inBulletList) {
                    body.append("</ul>")
                    inBulletList = false
                }
                body.append("<table class=\"doc-table\">")
                while (i < paragraphs.size &&
                    paragraphs[i].type == DocParagraphType.TABLE_ROW &&
                    paragraphs[i].tableCells != null
                ) {
                    val row = paragraphs[i].tableCells
                    if (row == null) {
                        i++
                        continue
                    }
                    body.append("<tr>")
                    row.forEach { cellRuns ->
                        val plain = cellRuns.joinToString("") { it.text }
                        body.append("<td dir=\"")
                            .append(detectDirection(plain))
                            .append("\">")
                            .append(buildRunsHtml(cellRuns))
                            .append("</td>")
                    }
                    body.append("</tr>")
                    i++
                }
                body.append("</table>")
                continue
            }

            if (p.type == DocParagraphType.BULLET_ITEM) {
                if (!inBulletList) {
                    body.append("<ul class=\"doc-list\">")
                    inBulletList = true
                }
                body.append("<li dir=\"")
                    .append(detectDirection(p.plainText))
                    .append("\">")
                    .append(buildRunsHtml(p.runs))
                    .append("</li>")
                i++
                continue
            } else if (inBulletList) {
                body.append("</ul>")
                inBulletList = false
            }

            val tag = when (p.type) {
                DocParagraphType.TITLE, DocParagraphType.HEADING_1 -> "h1"
                DocParagraphType.HEADING_2 -> "h2"
                DocParagraphType.HEADING_3 -> "h3"
                else -> "p"
            }
            val alignStyle = alignmentStyle(p.alignment)
            body.append('<').append(tag)
                .append(" dir=\"").append(detectDirection(p.plainText)).append('"')
                .append(alignStyle).append('>')
                .append(buildRunsHtml(p.runs))
                .append("</").append(tag).append('>')
            i++
        }
        if (inBulletList) body.append("</ul>")

        return wrapHtmlDocument(body.toString(), documentTitle)
    }

    fun buildPptxHtml(slides: List<OfficeToPdfConverter.PptxSlide>, documentTitle: String): String {
        val body = StringBuilder()
        slides.forEach { slide ->
            body.append("<div class=\"slide-page\">")

            slide.title?.let { titleRuns ->
                if (titleRuns.isNotEmpty()) {
                    val plain = titleRuns.joinToString("") { it.text }
                    body.append("<h1 class=\"slide-title\" dir=\"")
                        .append(detectDirection(plain))
                        .append("\">")
                        .append(buildRunsHtml(titleRuns))
                        .append("</h1>")
                }
            }

            var inList = false
            slide.paragraphs.forEach { p ->
                if (p.isBlank) return@forEach
                if (p.type == DocParagraphType.BULLET_ITEM) {
                    if (!inList) {
                        body.append("<ul class=\"doc-list\">")
                        inList = true
                    }
                    body.append("<li dir=\"")
                        .append(detectDirection(p.plainText))
                        .append("\">")
                        .append(buildRunsHtml(p.runs))
                        .append("</li>")
                } else {
                    if (inList) {
                        body.append("</ul>")
                        inList = false
                    }
                    body.append("<p dir=\"")
                        .append(detectDirection(p.plainText))
                        .append("\">")
                        .append(buildRunsHtml(p.runs))
                        .append("</p>")
                }
            }
            if (inList) body.append("</ul>")
            body.append("</div>")
        }
        return wrapHtmlDocument(body.toString(), documentTitle)
    }

    /** Neutral table HTML for XLSX — same content, no decorative chrome. */
    fun buildXlsxHtml(sheets: List<OfficeToPdfConverter.ExcelSheet>, documentTitle: String): String {
        val body = StringBuilder()
        sheets.forEach { sheet ->
            if (sheet.rows.isEmpty()) return@forEach
            if (sheets.size > 1) {
                body.append("<h2 dir=\"")
                    .append(detectDirection(sheet.sheetName))
                    .append("\">")
                    .append(escapeHtml(sheet.sheetName))
                    .append("</h2>")
            }
            val numCols = (sheet.maxColIndex + 1).coerceAtLeast(1)
            body.append("<table class=\"doc-table\">")
            sheet.rows.forEach { row ->
                body.append("<tr>")
                for (c in 0 until numCols) {
                    val runs = row.cells[c]?.runs ?: emptyList()
                    val plain = runs.joinToString("") { it.text }
                    body.append("<td dir=\"")
                        .append(detectDirection(plain))
                        .append("\">")
                        .append(if (runs.isEmpty()) "&nbsp;" else buildRunsHtml(runs))
                        .append("</td>")
                }
                body.append("</tr>")
            }
            body.append("</table>")
        }
        return wrapHtmlDocument(body.toString(), documentTitle)
    }

    private fun alignmentStyle(alignment: String?): String = when (alignment) {
        "center" -> " style=\"text-align:center;\""
        "right" -> " style=\"text-align:right;\""
        "left" -> " style=\"text-align:left;\""
        "both", "justify" -> " style=\"text-align:justify;\""
        else -> ""
    }

    private fun buildRunsHtml(runs: List<DocTextRun>): String {
        val sb = StringBuilder()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val styles = mutableListOf<String>()
            if (run.isBold) styles.add("font-weight:bold")
            if (run.isItalic) styles.add("font-style:italic")
            if (run.isUnderline) styles.add("text-decoration:underline")
            run.fontSizePt?.let { styles.add("font-size:${it}pt") }
            run.colorHex?.let { hex ->
                parseColorSafe(hex)?.let { color ->
                    val rgb = String.format("%06X", color and 0xFFFFFF)
                    styles.add("color:#$rgb")
                }
            }
            val styleAttr = if (styles.isNotEmpty()) " style=\"${styles.joinToString(";")}\"" else ""
            sb.append("<span").append(styleAttr).append('>')
                .append(escapeHtml(run.text))
                .append("</span>")
        }
        return sb.toString()
    }

    private fun detectDirection(text: String): String {
        for (ch in text) {
            if (ch.code in 0x0600..0x06FF || ch.code in 0x0750..0x077F ||
                ch.code in 0x08A0..0x08FF || ch.code in 0xFB50..0xFDFF || ch.code in 0xFE70..0xFEFF
            ) {
                return "rtl"
            }
            if (ch.isLetter()) return "ltr"
        }
        return "ltr"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br/>")
    }

    private fun parseColorSafe(hex: String): Int? {
        return try {
            var clean = hex.trim().removePrefix("#")
            if (clean.length == 8) clean = clean.substring(2) // AARRGGBB → RRGGBB
            when (clean.length) {
                6 -> AndroidColor.parseColor("#$clean")
                3 -> {
                    val r = clean[0]; val g = clean[1]; val b = clean[2]
                    AndroidColor.parseColor("#$r$r$g$g$b$b")
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun wrapHtmlDocument(bodyContent: String, documentTitle: String): String {
        val safeTitle = escapeHtml(documentTitle)
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <title>$safeTitle</title>
            <style>
                @page { margin: 15mm 12mm; size: A4; }
                * { box-sizing: border-box; }
                @media print {
            body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            img { max-width: 100% !important; page-break-inside: avoid; }
            table { page-break-inside: auto; }
            tr { page-break-inside: avoid; }
          }
          body {
                    font-family: 'Noto Naskh Arabic', 'Droid Arabic Naskh', 'Segoe UI', Tahoma, sans-serif;
                    font-size: 11pt;
                    line-height: 1.5;
                    color: #000000;
                    margin: 0;
                    padding: 0;
                }
                h1 { font-size: 18pt; font-weight: bold; margin: 0 0 10pt 0; }
                h2 { font-size: 14pt; font-weight: bold; margin: 12pt 0 6pt 0; }
                h3 { font-size: 12pt; font-weight: bold; margin: 10pt 0 4pt 0; }
                p { margin: 4pt 0; }
                ul.doc-list { margin: 4pt 0; padding-inline-start: 22pt; }
                li { margin: 2pt 0; }
                table.doc-table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 8pt 0;
                    table-layout: fixed;
                }
                table.doc-table td {
                    border: 0.5pt solid #666666;
                    padding: 4pt 6pt;
                    vertical-align: top;
                    font-size: 10pt;
                    word-wrap: break-word;
                }
                p.doc-image { text-align: center; margin: 8pt 0; }
                p.doc-image img { max-width: 100%; height: auto; image-rendering: auto; }
                div.slide-page { page-break-after: always; min-height: 80vh; }
                div.slide-page:last-child { page-break-after: auto; }
                h1.slide-title { font-size: 20pt; margin-bottom: 12pt; }
            </style>
            </head>
            <body>
            $bodyContent
            </body>
            </html>
        """.trimIndent()
    }
}
