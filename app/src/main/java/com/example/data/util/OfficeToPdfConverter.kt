package com.example.data.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import android.util.Log
import com.example.data.i18n.AppStrings
import com.example.data.i18n.StringsArabic
import com.example.data.model.ConversionMode
import com.example.data.model.DocParagraph
import com.example.data.model.DocParagraphType
import com.example.data.model.DocTextRun
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Office (docx/xlsx/pptx) → PDF.
 *
 * Design goal: preserve the *content and structure* of the original document
 * (paragraphs, runs, tables, slides, images) and print them through Android's
 * real print engine — not a decorative redesign.
 *
 * Path: parse OpenXML → neutral semantic HTML → WebView print-to-PDF.
 * Canvas renderers remain only as test/fallback helpers.
 */
object OfficeToPdfConverter {

    private const val TAG = "OfficeToPdfConverter"

    data class ExcelCell(val colIndex: Int, val runs: List<DocTextRun>) {
        val plainText: String get() = runs.joinToString("") { it.text }
    }

    data class ExcelRow(val rowIndex: Int, val cells: Map<Int, ExcelCell>)

    data class ExcelSheet(
        val sheetName: String,
        val rows: List<ExcelRow>,
        val maxColIndex: Int
    )

    data class PptxSlide(
        val slideNumber: Int,
        val title: List<DocTextRun>?,
        val paragraphs: List<DocParagraph>
    )

    suspend fun convertOfficeToPdf(
        context: Context,
        uri: Uri,
        fileName: String,
        onProgress: (Float) -> Unit,
        strings: AppStrings = StringsArabic,
        mode: ConversionMode = ConversionMode.PRINT
    ): File {
        val tempOutput = StorageManager.createTempFile(context, "vada_conv_", "pdf")
        val ext = fileName.substringAfterLast(".", "").lowercase()

        val isOle2Binary = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(8)
                val read = stream.read(header, 0, 8)
                read >= 4 &&
                    header[0] == 0xD0.toByte() &&
                    header[1] == 0xCF.toByte() &&
                    header[2] == 0x11.toByte() &&
                    header[3] == 0xE0.toByte()
            } ?: false
        } catch (e: Exception) {
            AppLogger.logSilentFailure(TAG, "فشل فحص ترويسة OLE2: $uri", e)
            false
        }

        if (isOle2Binary || ext == "doc" || ext == "xls" || ext == "ppt") {
            throw IllegalArgumentException(strings.errorLegacyOfficeFormat)
        }
        if (ext != "docx" && ext != "xlsx" && ext != "pptx") {
            throw IllegalArgumentException(strings.errorUnsupportedOfficeFormat)
        }

        try {
            when (ext) {
                "docx" -> {
                    val paragraphs = mutableListOf<DocParagraph>()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        parseDocx(inputStream, paragraphs)
                    }
                    if (paragraphs.isEmpty()) {
                        throw IllegalStateException(strings.errorDocxEmptyContent(fileName))
                    }
                    onProgress(0.1f)
                    val html = HtmlDocumentBuilder.buildDocxHtml(paragraphs, fileName)
                    when (mode) {
                        ConversionMode.PRINT ->
                            WebViewPdfPrinter.renderHtmlToPdf(context, html, tempOutput, onProgress)
                        ConversionMode.SCREENSHOT ->
                            ScreenshotHtmlPdfRenderer.renderHtmlToPdf(context, html, tempOutput, onProgress)
                    }
                }
                "xlsx" -> {
                    val sheets = mutableListOf<ExcelSheet>()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        parseXlsxSheets(inputStream, sheets)
                    }
                    if (sheets.isEmpty() || sheets.all { it.rows.isEmpty() }) {
                        throw IllegalStateException(strings.errorXlsxEmptyContent(fileName))
                    }
                    onProgress(0.1f)
                    // Same neutral print path as DOCX — real table layout, no fake chrome
                    val html = HtmlDocumentBuilder.buildXlsxHtml(sheets, fileName)
                    try {
                        when (mode) {
                            ConversionMode.PRINT ->
                                WebViewPdfPrinter.renderHtmlToPdf(context, html, tempOutput, onProgress)
                            ConversionMode.SCREENSHOT ->
                                ScreenshotHtmlPdfRenderer.renderHtmlToPdf(context, html, tempOutput, onProgress)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "HTML XLSX render failed, falling back to Canvas", e)
                        renderXlsxToPdf(tempOutput, fileName, sheets, onProgress)
                    }
                }
                "pptx" -> {
                    val slides = mutableListOf<PptxSlide>()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        parsePptxSlides(inputStream, slides)
                    }
                    if (slides.isEmpty()) {
                        throw IllegalStateException(strings.errorPptxEmptyContent(fileName))
                    }
                    onProgress(0.1f)
                    val html = HtmlDocumentBuilder.buildPptxHtml(slides, fileName)
                    when (mode) {
                        ConversionMode.PRINT ->
                            WebViewPdfPrinter.renderHtmlToPdf(context, html, tempOutput, onProgress)
                        ConversionMode.SCREENSHOT ->
                            ScreenshotHtmlPdfRenderer.renderHtmlToPdf(context, html, tempOutput, onProgress)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting office document $fileName", e)
            if (e is IllegalArgumentException || e is IllegalStateException) throw e
            throw IllegalStateException(
                strings.errorOfficeConversionFailed(fileName, e.message ?: ""),
                e
            )
        }

        // Extra size pass for oversized WebView output (images already optimized above)
        if (tempOutput.exists() && tempOutput.length() > 120_000L) {
            PdfSizeOptimizer.optimizeFile(tempOutput, maxImageEdge = 1280, jpegQuality = 70)
        }

        return tempOutput
    }

    // =========================================================================
    // DOCX
    // =========================================================================

    fun parseDocx(inputStream: InputStream, outputList: MutableList<DocParagraph>) {
        val mediaBytes = mutableMapOf<String, ByteArray>() // word/media/... → bytes
        var documentXml: ByteArray? = null
        var relsXml: ByteArray? = null

        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name.equals("word/document.xml", ignoreCase = true) ->
                        documentXml = zipStream.readBytes()
                    name.equals("word/_rels/document.xml.rels", ignoreCase = true) ->
                        relsXml = zipStream.readBytes()
                    name.startsWith("word/media/", ignoreCase = true) && !entry.isDirectory ->
                        mediaBytes[name.substringAfter("word/media/").lowercase()] =
                            zipStream.readBytes()
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        if (documentXml == null) return

        // rId → media file name
        val relIdToMedia = mutableMapOf<String, String>()
        val relsBytes = relsXml
        if (relsBytes != null) {
            parseDocxRels(relsBytes, relIdToMedia)
        }

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        val docBytes = documentXml ?: return
        parser.setInput(docBytes.inputStream().reader(Charsets.UTF_8))

        var eventType = parser.eventType
        var currentParaType = DocParagraphType.NORMAL
        var currentParaAlignment: String? = null
        var currentRuns = mutableListOf<DocTextRun>()

        var inP = false
        var inPPr = false
        var inR = false
        var inRPr = false
        var inT = false
        var inTbl = false
        var inTr = false
        var inTc = false

        var runBold = false
        var runItalic = false
        var runUnderline = false
        var runFontSize: Float? = null
        var runColor: String? = null
        var runText = StringBuilder()

        var tableCells = mutableListOf<List<DocTextRun>>()
        var currentCellRuns = mutableListOf<DocTextRun>()
        // Accumulate multiple paragraphs inside one cell
        var cellParagraphBuffer = mutableListOf<List<DocTextRun>>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.substringAfter(":")?.lowercase() ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "tbl" -> inTbl = true
                        "tr" -> {
                            inTr = true
                            tableCells = mutableListOf()
                        }
                        "tc" -> {
                            inTc = true
                            cellParagraphBuffer = mutableListOf()
                            currentCellRuns = mutableListOf()
                        }
                        "p" -> {
                            inP = true
                            currentParaType =
                                if (inTc) DocParagraphType.TABLE_ROW else DocParagraphType.NORMAL
                            currentParaAlignment = null
                            currentRuns = mutableListOf()
                            if (inTc) currentCellRuns = mutableListOf()
                        }
                        "ppr" -> inPPr = true
                        "pstyle" -> {
                            if (inPPr && !inTc) {
                                val styleVal = getAttrValue(parser, "val")?.lowercase() ?: ""
                                currentParaType = when {
                                    styleVal.contains("title") && !styleVal.contains("subtitle") ->
                                        DocParagraphType.TITLE
                                    styleVal.contains("heading1") || styleVal == "1" ||
                                        styleVal.contains("heading 1") -> DocParagraphType.HEADING_1
                                    styleVal.contains("heading2") || styleVal == "2" ||
                                        styleVal.contains("heading 2") || styleVal.contains("subtitle") ->
                                        DocParagraphType.HEADING_2
                                    styleVal.contains("heading3") || styleVal == "3" ||
                                        styleVal.contains("heading 3") -> DocParagraphType.HEADING_3
                                    styleVal.contains("list") || styleVal.contains("bullet") ->
                                        DocParagraphType.BULLET_ITEM
                                    else -> DocParagraphType.NORMAL
                                }
                            }
                        }
                        "jc" -> {
                            if (inPPr) {
                                currentParaAlignment = getAttrValue(parser, "val")?.lowercase()
                            }
                        }
                        "numpr" -> {
                            // Numbered/bullet list paragraph
                            if (!inTc && currentParaType == DocParagraphType.NORMAL) {
                                currentParaType = DocParagraphType.BULLET_ITEM
                            }
                        }
                        "r" -> {
                            inR = true
                            runBold = false
                            runItalic = false
                            runUnderline = false
                            runFontSize = null
                            runColor = null
                            runText = StringBuilder()
                        }
                        "rpr" -> inRPr = true
                        "b" -> {
                            if (inRPr) {
                                val v = getAttrValue(parser, "val")?.lowercase()
                                runBold = v == null || (v != "0" && v != "false" && v != "off")
                            }
                        }
                        "i" -> {
                            if (inRPr) {
                                val v = getAttrValue(parser, "val")?.lowercase()
                                runItalic = v == null || (v != "0" && v != "false" && v != "off")
                            }
                        }
                        "u" -> {
                            if (inRPr) {
                                val v = getAttrValue(parser, "val")?.lowercase()
                                runUnderline =
                                    v == null || (v != "none" && v != "0" && v != "false")
                            }
                        }
                        "sz" -> {
                            if (inRPr) {
                                val halfPoints = getAttrValue(parser, "val")?.toFloatOrNull()
                                if (halfPoints != null) runFontSize = halfPoints / 2f
                            }
                        }
                        "color" -> {
                            if (inRPr) {
                                val v = getAttrValue(parser, "val")
                                if (v != null && v.lowercase() != "auto") runColor = v
                            }
                        }
                        "t" -> inT = true
                        "tab" -> if (inR) runText.append("    ")
                        "br" -> if (inR) runText.append("\n")
                        "blip" -> {
                            // DrawingML image reference
                            val embed = getAttrValue(parser, "embed")
                                ?: getAttrValue(parser, "link")
                            if (embed != null) {
                                val mediaName = relIdToMedia[embed]
                                if (mediaName != null) {
                                    val bytes = mediaBytes[mediaName.lowercase()]
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        // Downscale before base64 so HTML/PDF stay lean
                                        val optimized = PdfSizeOptimizer.optimizeImageBytes(bytes)
                                        val b64 = Base64.encodeToString(optimized.bytes, Base64.NO_WRAP)
                                        if (!inTc) {
                                            outputList.add(
                                                DocParagraph(
                                                    type = DocParagraphType.IMAGE,
                                                    imageBase64 = b64,
                                                    imageMime = optimized.mime
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inT && inR) runText.append(parser.text)
                }

                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "t" -> inT = false
                        "rpr" -> inRPr = false
                        "r" -> {
                            inR = false
                            val text = runText.toString()
                            if (text.isNotEmpty()) {
                                val run = DocTextRun(
                                    text = text,
                                    isBold = runBold,
                                    isItalic = runItalic,
                                    isUnderline = runUnderline,
                                    fontSizePt = runFontSize,
                                    colorHex = runColor
                                )
                                currentRuns.add(run)
                                if (inTc) currentCellRuns.add(run)
                            }
                        }
                        "ppr" -> inPPr = false
                        "p" -> {
                            inP = false
                            if (inTc) {
                                if (currentCellRuns.isNotEmpty()) {
                                    cellParagraphBuffer.add(currentCellRuns.toList())
                                }
                            } else {
                                if (currentRuns.isNotEmpty() ||
                                    currentParaType != DocParagraphType.NORMAL
                                ) {
                                    outputList.add(
                                        DocParagraph(
                                            type = currentParaType,
                                            runs = currentRuns,
                                            alignment = currentParaAlignment
                                        )
                                    )
                                }
                            }
                        }
                        "tc" -> {
                            inTc = false
                            // Merge multi-paragraph cell content with newlines
                            val merged = mutableListOf<DocTextRun>()
                            cellParagraphBuffer.forEachIndexed { idx, paraRuns ->
                                if (idx > 0) merged.add(DocTextRun("\n"))
                                merged.addAll(paraRuns)
                            }
                            if (merged.isNotEmpty()) {
                                tableCells.add(merged)
                            } else {
                                tableCells.add(emptyList())
                            }
                        }
                        "tr" -> {
                            inTr = false
                            if (tableCells.isNotEmpty()) {
                                val allRuns = tableCells.flatMapIndexed { idx, cell ->
                                    val prefix =
                                        if (idx > 0) listOf(
                                            DocTextRun("  |  ", isBold = true, colorHex = "718096")
                                        ) else emptyList()
                                    prefix + cell
                                }
                                outputList.add(
                                    DocParagraph(
                                        type = DocParagraphType.TABLE_ROW,
                                        runs = allRuns,
                                        tableCells = tableCells.toList()
                                    )
                                )
                            }
                        }
                        "tbl" -> inTbl = false
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseDocxRels(bytes: ByteArray, out: MutableMap<String, String>) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream().reader(Charsets.UTF_8))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name?.substringAfter(":")?.lowercase() ?: ""
                if (tag == "relationship") {
                    val id = getAttrValue(parser, "Id") ?: getAttrValue(parser, "id")
                    val target = getAttrValue(parser, "Target")
                    val type = getAttrValue(parser, "Type") ?: ""
                    if (id != null && target != null && type.contains("image", ignoreCase = true)) {
                        val fileName = target.substringAfterLast("/").substringAfterLast("\\")
                        out[id] = fileName
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun mimeFromMediaName(name: String): String {
        return when (name.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "image/png"
        }
    }

    // =========================================================================
    // XLSX
    // =========================================================================

    fun parseXlsxSheets(inputStream: InputStream, outputSheets: MutableList<ExcelSheet>) {
        val sharedStrings = mutableListOf<List<DocTextRun>>()
        val sheetEntries = mutableListOf<Pair<String, ByteArray>>() // path → bytes
        var workbookXml: ByteArray? = null
        var workbookRels: ByteArray? = null

        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                when {
                    name == "xl/sharedstrings.xml" ->
                        parseXlsxSharedStrings(zipStream.readBytes(), sharedStrings)
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheetEntries.add(entry.name to zipStream.readBytes())
                    name == "xl/workbook.xml" ->
                        workbookXml = zipStream.readBytes()
                    name == "xl/_rels/workbook.xml.rels" ->
                        workbookRels = zipStream.readBytes()
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        // rId → sheet file path (e.g. worksheets/sheet1.xml)
        val ridToPath = mutableMapOf<String, String>()
        val wbRels = workbookRels
        if (wbRels != null) {
            parseWorkbookRels(wbRels, ridToPath)
        }

        // Ordered list of (sheetName, path)
        val orderedSheets = mutableListOf<Pair<String, String>>()
        val wbXml = workbookXml
        if (wbXml != null) {
            parseWorkbookSheetOrder(wbXml, ridToPath, orderedSheets)
        }

        // Fallback: natural order of sheetN.xml
        if (orderedSheets.isEmpty()) {
            sheetEntries
                .sortedBy { (name, _) ->
                    name.lowercase().substringAfterLast("sheet")
                        .substringBefore(".").toIntOrNull() ?: 0
                }
                .forEach { (name, _) ->
                    val num = name.lowercase().substringAfterLast("sheet")
                        .substringBefore(".")
                    orderedSheets.add("Sheet $num" to name)
                }
        }

        val pathToBytes = sheetEntries.associate { (path, bytes) ->
            path.lowercase() to bytes
        }

        orderedSheets.forEach { (sheetName, path) ->
            val key = path.lowercase().let {
                if (it.startsWith("xl/")) it else "xl/$it"
            }
            val bytes = pathToBytes[key]
                ?: pathToBytes[key.removePrefix("xl/")]
                ?: pathToBytes.entries.firstOrNull { (p, _) ->
                    p.endsWith(path.substringAfterLast("/").lowercase())
                }?.value
            if (bytes == null) return@forEach

            val rows = mutableListOf<ExcelRow>()
            var maxCol = 0
            parseSingleXlsxWorksheet(bytes, sharedStrings) { row, sheetMaxCol ->
                rows.add(row)
                if (sheetMaxCol > maxCol) maxCol = sheetMaxCol
            }
            outputSheets.add(
                ExcelSheet(sheetName = sheetName, rows = rows, maxColIndex = maxCol)
            )
        }
    }

    private fun parseWorkbookRels(bytes: ByteArray, out: MutableMap<String, String>) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream().reader(Charsets.UTF_8))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name?.substringAfter(":")?.lowercase() ?: ""
                if (tag == "relationship") {
                    val id = getAttrValue(parser, "Id") ?: getAttrValue(parser, "id")
                    val target = getAttrValue(parser, "Target")
                    if (id != null && target != null) {
                        out[id] = target
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun parseWorkbookSheetOrder(
        bytes: ByteArray,
        ridToPath: Map<String, String>,
        out: MutableList<Pair<String, String>>
    ) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream().reader(Charsets.UTF_8))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name?.substringAfter(":")?.lowercase() ?: ""
                if (tag == "sheet") {
                    val name = getAttrValue(parser, "name") ?: "Sheet"
                    val rid = getAttrValue(parser, "id")
                        ?: getAttrValue(parser, "r:id")
                        ?: getAttrValue(parser, "rid")
                    val path = if (rid != null) ridToPath[rid] else null
                    if (path != null) {
                        out.add(name to path)
                    }
                }
            }
            event = parser.next()
        }
    }

    fun parseXlsx(inputStream: InputStream, outputList: MutableList<DocParagraph>) {
        val sheets = mutableListOf<ExcelSheet>()
        parseXlsxSheets(inputStream, sheets)
        sheets.forEach { sheet ->
            outputList.add(
                DocParagraph(
                    type = DocParagraphType.HEADING_2,
                    runs = listOf(DocTextRun(sheet.sheetName, isBold = true))
                )
            )
            sheet.rows.forEach { row ->
                val maxCol = sheet.maxColIndex
                val cells = (0..maxCol).map { cIdx ->
                    row.cells[cIdx]?.runs ?: emptyList()
                }
                val allRuns = cells.flatMapIndexed { idx, cell ->
                    val prefix =
                        if (idx > 0) listOf(DocTextRun("   |   ", isBold = true))
                        else emptyList()
                    val content = if (cell.isNotEmpty()) cell else listOf(DocTextRun("-"))
                    prefix + content
                }
                outputList.add(
                    DocParagraph(
                        type = DocParagraphType.TABLE_ROW,
                        runs = allRuns,
                        tableCells = cells
                    )
                )
            }
        }
    }

    private fun parseXlsxSharedStrings(bytes: ByteArray, outputList: MutableList<List<DocTextRun>>) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream().reader(Charsets.UTF_8))

        var eventType = parser.eventType
        var inSi = false
        var inR = false
        var inRPr = false
        var inT = false
        var currentRuns = mutableListOf<DocTextRun>()
        var runBold = false
        var runItalic = false
        var runUnderline = false
        var runFontSize: Float? = null
        var runColor: String? = null
        var runText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(":")?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "si" -> {
                            inSi = true
                            currentRuns = mutableListOf()
                        }
                        "r" -> {
                            inR = true
                            runBold = false
                            runItalic = false
                            runUnderline = false
                            runFontSize = null
                            runColor = null
                            runText = StringBuilder()
                        }
                        "rpr" -> inRPr = true
                        "b" -> if (inRPr) runBold = true
                        "i" -> if (inRPr) runItalic = true
                        "u" -> if (inRPr) runUnderline = true
                        "sz" -> {
                            if (inRPr) {
                                runFontSize = getAttrValue(parser, "val")?.toFloatOrNull()
                            }
                        }
                        "color" -> {
                            if (inRPr) {
                                runColor = getAttrValue(parser, "rgb")
                                    ?: getAttrValue(parser, "theme")
                            }
                        }
                        "t" -> inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inT) {
                        if (inR) runText.append(parser.text)
                        else if (inSi) currentRuns.add(DocTextRun(parser.text))
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "t" -> inT = false
                        "rpr" -> inRPr = false
                        "r" -> {
                            inR = false
                            if (runText.isNotEmpty()) {
                                currentRuns.add(
                                    DocTextRun(
                                        text = runText.toString(),
                                        isBold = runBold,
                                        isItalic = runItalic,
                                        isUnderline = runUnderline,
                                        fontSizePt = runFontSize,
                                        colorHex = runColor
                                    )
                                )
                            }
                        }
                        "si" -> {
                            inSi = false
                            outputList.add(currentRuns)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    fun colLetterToIndex(letters: String): Int {
        var result = 0
        for (ch in letters.uppercase()) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    private fun parseSingleXlsxWorksheet(
        bytes: ByteArray,
        sharedStrings: List<List<DocTextRun>>,
        onRowParsed: (ExcelRow, Int) -> Unit
    ) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream().reader(Charsets.UTF_8))

        var eventType = parser.eventType
        var inC = false
        var inV = false
        var inIs = false
        var inT = false

        var currentRowIndex = 1
        var currentCellRef = ""
        var currentCellType: String? = null
        var currentCellValue = StringBuilder()
        var currentInlineRuns = mutableListOf<DocTextRun>()
        var rowCells = mutableMapOf<Int, ExcelCell>()
        var sheetMaxCol = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(":")?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "row" -> {
                            rowCells = mutableMapOf()
                            val rAttr = getAttrValue(parser, "r")?.toIntOrNull()
                            if (rAttr != null) currentRowIndex = rAttr
                        }
                        "c" -> {
                            inC = true
                            currentCellRef = getAttrValue(parser, "r") ?: ""
                            currentCellType = getAttrValue(parser, "t")
                            currentCellValue = StringBuilder()
                            currentInlineRuns = mutableListOf()
                        }
                        "v" -> inV = true
                        "is" -> inIs = true
                        "t" -> if (inIs) inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV && inC) currentCellValue.append(parser.text)
                    else if (inT && inIs && inC) currentInlineRuns.add(DocTextRun(parser.text))
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "v" -> inV = false
                        "t" -> inT = false
                        "is" -> inIs = false
                        "c" -> {
                            inC = false
                            val colLetters = currentCellRef.takeWhile { it.isLetter() }
                            val colIndex =
                                if (colLetters.isNotEmpty()) colLetterToIndex(colLetters)
                                else rowCells.size
                            if (colIndex > sheetMaxCol) sheetMaxCol = colIndex

                            val runs: List<DocTextRun> = when {
                                currentCellType == "s" -> {
                                    val sIndex = currentCellValue.toString().trim().toIntOrNull()
                                    if (sIndex != null && sIndex in sharedStrings.indices) {
                                        sharedStrings[sIndex]
                                    } else {
                                        listOf(DocTextRun(currentCellValue.toString().trim()))
                                    }
                                }
                                currentCellType == "inlineStr" || currentInlineRuns.isNotEmpty() ->
                                    currentInlineRuns
                                currentCellType == "b" -> {
                                    val isTrue = currentCellValue.toString().trim() == "1"
                                    listOf(DocTextRun(if (isTrue) "TRUE" else "FALSE"))
                                }
                                else -> {
                                    val raw = currentCellValue.toString().trim()
                                    if (raw.isNotEmpty()) listOf(DocTextRun(raw)) else emptyList()
                                }
                            }
                            if (runs.isNotEmpty()) {
                                rowCells[colIndex] = ExcelCell(colIndex = colIndex, runs = runs)
                            }
                        }
                        "row" -> {
                            if (rowCells.isNotEmpty()) {
                                onRowParsed(
                                    ExcelRow(rowIndex = currentRowIndex, cells = rowCells),
                                    sheetMaxCol
                                )
                            }
                            currentRowIndex++
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * Canvas fallback for XLSX — plain grid, no decorative banners/chrome.
     */
    fun renderXlsxToPdf(
        outputFile: File,
        documentTitle: String,
        sheets: List<ExcelSheet>,
        onProgress: (Float) -> Unit
    ) {
        val doc = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val margin = 28f
        val printableWidth = pageWidth - (margin * 2)

        var currentPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = margin

        val dataPaint = TextPaint().apply {
            color = AndroidColor.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val headerPaint = TextPaint(dataPaint).apply { isFakeBoldText = true }
        val gridPaint = Paint().apply {
            color = AndroidColor.rgb(160, 160, 160)
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val sheetTitlePaint = TextPaint().apply {
            color = AndroidColor.BLACK
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        fun pageBreak() {
            doc.finishPage(page)
            currentPageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            yPos = margin
        }

        val totalSheets = sheets.size.coerceAtLeast(1)
        sheets.forEachIndexed { sheetIdx, sheet ->
            if (sheet.rows.isEmpty()) return@forEachIndexed

            if (yPos + 40f > pageHeight - margin) pageBreak()

            if (sheets.size > 1) {
                canvas.drawText(sheet.sheetName, margin, yPos + 12f, sheetTitlePaint)
                yPos += 20f
            }

            val numCols = (sheet.maxColIndex + 1).coerceAtLeast(1)
            val colWidth = printableWidth / numCols.toFloat()
            val rowHeight = 20f

            sheet.rows.forEachIndexed { rIdx, row ->
                if (yPos + rowHeight > pageHeight - margin) pageBreak()

                val rowTop = yPos
                val rowBottom = yPos + rowHeight
                canvas.drawRect(margin, rowTop, margin + printableWidth, rowBottom, gridPaint)

                for (colIdx in 0 until numCols) {
                    val cellX = margin + colIdx * colWidth
                    if (colIdx > 0) {
                        canvas.drawLine(cellX, rowTop, cellX, rowBottom, gridPaint)
                    }
                    val cell = row.cells[colIdx]
                    if (cell != null && cell.runs.isNotEmpty()) {
                        val paint = if (rIdx == 0) headerPaint else dataPaint
                        val spannable = buildSpannedFromRuns(
                            cell.runs, paint.textSize, paint.color, rIdx == 0
                        )
                        if (spannable.isNotEmpty()) {
                            val cellW = (colWidth - 8f).toInt().coerceAtLeast(8)
                            val isRtl = isRtlText(spannable)
                            val layout = StaticLayout.Builder.obtain(
                                spannable, 0, spannable.length, paint, cellW
                            )
                                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                                .setAlignment(
                                    if (isRtl) Layout.Alignment.ALIGN_OPPOSITE
                                    else Layout.Alignment.ALIGN_NORMAL
                                )
                                .setIncludePad(false)
                                .setMaxLines(1)
                                .setEllipsize(TextUtils.TruncateAt.END)
                                .build()
                            canvas.save()
                            canvas.translate(cellX + 4f, rowTop + 3f)
                            layout.draw(canvas)
                            canvas.restore()
                        }
                    }
                }
                yPos += rowHeight
            }
            yPos += 16f
            onProgress(((sheetIdx + 1).toFloat() / totalSheets).coerceIn(0f, 0.95f))
        }

        doc.finishPage(page)
        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
        onProgress(1.0f)
    }

    // =========================================================================
    // PPTX
    // =========================================================================

    fun parsePptxSlides(inputStream: InputStream, outputSlides: MutableList<PptxSlide>) {
        val slideEntries = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    slideEntries.add(entry.name to zipStream.readBytes())
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        slideEntries.sortBy { (name, _) ->
            name.lowercase().substringAfterLast("slide").substringBefore(".").toIntOrNull() ?: 0
        }

        slideEntries.forEach { (name, bytes) ->
            val slideNum = name.lowercase().substringAfterLast("slide")
                .substringBefore(".").toIntOrNull() ?: (outputSlides.size + 1)
            var slideTitle: List<DocTextRun>? = null
            val paragraphs = mutableListOf<DocParagraph>()

            parseSinglePptxSlide(bytes) { pType, runs ->
                if (pType == DocParagraphType.TITLE && slideTitle == null) {
                    slideTitle = runs
                } else {
                    paragraphs.add(DocParagraph(type = pType, runs = runs))
                }
            }

            // If no title placeholder, promote first non-empty paragraph
            if (slideTitle == null && paragraphs.isNotEmpty()) {
                val first = paragraphs.removeAt(0)
                slideTitle = first.runs
            }

            outputSlides.add(
                PptxSlide(slideNumber = slideNum, title = slideTitle, paragraphs = paragraphs)
            )
        }
    }

    fun parsePptx(inputStream: InputStream, outputList: MutableList<DocParagraph>) {
        val slides = mutableListOf<PptxSlide>()
        parsePptxSlides(inputStream, slides)
        slides.forEach { slide ->
            slide.title?.let { tRuns ->
                outputList.add(DocParagraph(type = DocParagraphType.TITLE, runs = tRuns))
            }
            outputList.addAll(slide.paragraphs)
        }
    }

    private fun parseSinglePptxSlide(
        bytes: ByteArray,
        onParagraphParsed: (DocParagraphType, List<DocTextRun>) -> Unit
    ) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream().reader(Charsets.UTF_8))

        var eventType = parser.eventType
        var inP = false
        var inR = false
        var inRPr = false
        var inT = false
        var isTitleShape = false
        var isBullet = false

        var runBold = false
        var runItalic = false
        var runFontSize: Float? = null
        var runColor: String? = null
        var runText = StringBuilder()
        var currentRuns = mutableListOf<DocTextRun>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(":")?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "ph" -> {
                            val type = getAttrValue(parser, "type")?.lowercase()
                            if (type == "title" || type == "ctrtitle") isTitleShape = true
                        }
                        "p" -> {
                            inP = true
                            currentRuns = mutableListOf()
                            isBullet = false
                        }
                        "buChar", "buFont", "buAutoNum" -> isBullet = true
                        "r" -> {
                            inR = true
                            runBold = false
                            runItalic = false
                            runFontSize = null
                            runColor = null
                            runText = StringBuilder()
                        }
                        "rpr" -> {
                            inRPr = true
                            val b = getAttrValue(parser, "b")
                            runBold = b == "1" || b == "true"
                            val i = getAttrValue(parser, "i")
                            runItalic = i == "1" || i == "true"
                            val sz = getAttrValue(parser, "sz")?.toFloatOrNull()
                            if (sz != null) runFontSize = sz / 100f
                        }
                        "srgbclr" -> {
                            if (inRPr) runColor = getAttrValue(parser, "val")
                        }
                        "t" -> inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inT && inR) runText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "t" -> inT = false
                        "rpr" -> inRPr = false
                        "r" -> {
                            inR = false
                            val text = runText.toString()
                            if (text.isNotEmpty()) {
                                currentRuns.add(
                                    DocTextRun(
                                        text = text,
                                        isBold = runBold,
                                        isItalic = runItalic,
                                        fontSizePt = runFontSize,
                                        colorHex = runColor
                                    )
                                )
                            }
                        }
                        "p" -> {
                            inP = false
                            if (currentRuns.isNotEmpty()) {
                                val pType = when {
                                    isTitleShape -> DocParagraphType.TITLE
                                    isBullet -> DocParagraphType.BULLET_ITEM
                                    else -> DocParagraphType.NORMAL
                                }
                                onParagraphParsed(pType, currentRuns)
                            }
                        }
                        "sp" -> isTitleShape = false
                    }
                }
            }
            eventType = parser.next()
        }
    }

    // =========================================================================
    // Shared helpers (used by tests + Canvas fallback)
    // =========================================================================

    fun isRtlText(charSequence: CharSequence): Boolean {
        if (charSequence.isEmpty()) return false
        return TextDirectionHeuristics.FIRSTSTRONG_RTL.isRtl(charSequence, 0, charSequence.length)
    }

    fun buildSpannedFromRuns(
        runs: List<DocTextRun>,
        defaultSize: Float,
        defaultColor: Int,
        defaultBold: Boolean
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        for (run in runs) {
            val start = builder.length
            builder.append(run.text)
            val end = builder.length
            if (start == end) continue

            val isBold = run.isBold || defaultBold
            val isItalic = run.isItalic
            val style = when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold -> Typeface.BOLD
                isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                builder.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.isUnderline) {
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val fontSize = run.fontSizePt ?: defaultSize
            builder.setSpan(
                AbsoluteSizeSpan(fontSize.toInt(), false),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val color = run.colorHex?.let { parseColorSafe(it) } ?: defaultColor
            builder.setSpan(
                ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return builder
    }

    /**
     * Neutral Canvas renderer kept for unit tests / emergency fallback.
     * No decorative banners — content only.
     */
    fun renderDocumentToPdf(
        outputFile: File,
        documentTitle: String,
        paragraphs: List<DocParagraph>,
        onProgress: (Float) -> Unit
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44f
        val printableWidth = pageWidth - margin * 2

        var currentPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = margin

        val textPaint = TextPaint().apply {
            color = AndroidColor.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        fun pageBreak() {
            doc.finishPage(page)
            currentPageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            yPos = margin
        }

        val total = paragraphs.size.coerceAtLeast(1)
        paragraphs.forEachIndexed { pIndex, para ->
            if (para.isBlank) {
                yPos += 6f
                return@forEachIndexed
            }
            if (yPos > pageHeight - margin - 20f) pageBreak()

            val size = when (para.type) {
                DocParagraphType.TITLE, DocParagraphType.HEADING_1 -> 16f
                DocParagraphType.HEADING_2 -> 13f
                DocParagraphType.HEADING_3 -> 12f
                else -> 11f
            }
            val bold = para.type != DocParagraphType.NORMAL &&
                para.type != DocParagraphType.BULLET_ITEM &&
                para.type != DocParagraphType.TABLE_ROW

            yPos = drawParagraphWithStaticLayout(
                getCanvas = { canvas },
                runs = para.runs,
                defaultPaint = textPaint,
                defaultSize = size,
                defaultColor = AndroidColor.BLACK,
                defaultBold = bold,
                printableWidth = printableWidth,
                startX = margin,
                startY = yPos,
                maxPageY = pageHeight - margin,
                alignmentOverride = para.alignment,
                onPageBreak = { pageBreak() }
            )
            yPos += 6f
            onProgress(((pIndex + 1).toFloat() / total).coerceIn(0f, 0.95f))
        }

        doc.finishPage(page)
        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
        onProgress(1.0f)
    }

    fun drawParagraphWithStaticLayout(
        getCanvas: () -> Canvas,
        runs: List<DocTextRun>,
        defaultPaint: TextPaint,
        defaultSize: Float,
        defaultColor: Int,
        defaultBold: Boolean,
        printableWidth: Float,
        startX: Float,
        startY: Float,
        maxPageY: Float,
        alignmentOverride: String? = null,
        onPageBreak: () -> Unit
    ): Float {
        if (runs.isEmpty()) return startY
        val spannable = buildSpannedFromRuns(runs, defaultSize, defaultColor, defaultBold)
        if (spannable.isEmpty()) return startY

        val textPaint = TextPaint(defaultPaint).apply {
            textSize = defaultSize
            color = defaultColor
            isAntiAlias = true
            if (defaultBold) isFakeBoldText = true
        }

        val isRtl = isRtlText(spannable)
        val alignment = when {
            alignmentOverride == "center" -> Layout.Alignment.ALIGN_CENTER
            alignmentOverride == "right" -> Layout.Alignment.ALIGN_OPPOSITE
            alignmentOverride == "left" -> Layout.Alignment.ALIGN_NORMAL
            isRtl -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val targetWidth = printableWidth.toInt().coerceAtLeast(10)
        val staticLayout = StaticLayout.Builder.obtain(
            spannable, 0, spannable.length, textPaint, targetWidth
        )
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setAlignment(alignment)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()

        val totalHeight = staticLayout.height.toFloat()
        var currentY = startY

        if (currentY + totalHeight <= maxPageY) {
            val c = getCanvas()
            c.save()
            c.translate(startX, currentY)
            staticLayout.draw(c)
            c.restore()
            return currentY + totalHeight
        }

        if (currentY > 70f && totalHeight <= (maxPageY - 52f)) {
            onPageBreak()
            currentY = 52f
            val c = getCanvas()
            c.save()
            c.translate(startX, currentY)
            staticLayout.draw(c)
            c.restore()
            return currentY + totalHeight
        }

        var lineIndex = 0
        val lineCount = staticLayout.lineCount
        while (lineIndex < lineCount) {
            var nextLineIndex = lineIndex + 1
            while (nextLineIndex < lineCount &&
                (currentY + (staticLayout.getLineBottom(nextLineIndex - 1) -
                    staticLayout.getLineTop(lineIndex)) <= maxPageY)
            ) {
                nextLineIndex++
            }
            val lastLineInChunk = (nextLineIndex - 1).coerceAtLeast(lineIndex)
            val chunkStart = staticLayout.getLineStart(lineIndex)
            val chunkEnd = staticLayout.getLineEnd(lastLineInChunk)
            if (chunkStart < chunkEnd) {
                val chunkSeq = spannable.subSequence(chunkStart, chunkEnd)
                val chunkLayout = StaticLayout.Builder.obtain(
                    chunkSeq, 0, chunkSeq.length, textPaint, targetWidth
                )
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .setAlignment(alignment)
                    .setLineSpacing(2f, 1.15f)
                    .setIncludePad(false)
                    .build()
                val c = getCanvas()
                c.save()
                c.translate(startX, currentY)
                chunkLayout.draw(c)
                c.restore()
                currentY += chunkLayout.height.toFloat()
            }
            lineIndex = lastLineInChunk + 1
            if (lineIndex < lineCount) {
                onPageBreak()
                currentY = 52f
            }
        }
        return currentY
    }

    private fun getAttrValue(parser: XmlPullParser, attrName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            if (name.equals(attrName, ignoreCase = true) ||
                name.endsWith(":$attrName", ignoreCase = true)
            ) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun parseColorSafe(hex: String): Int? {
        return try {
            var clean = hex.trim().removePrefix("#")
            if (clean.length == 8) clean = clean.substring(2)
            when (clean.length) {
                6 -> AndroidColor.parseColor("#$clean")
                3 -> {
                    val r = clean[0]; val g = clean[1]; val b = clean[2]
                    AndroidColor.parseColor("#$r$r$g$g$b$b")
                }
                else -> null
            }
        } catch (e: Exception) {
            AppLogger.logSilentFailure(TAG, "فشل تحليل لون: $hex", e)
            null
        }
    }
}
