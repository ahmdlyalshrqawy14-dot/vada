package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeConversionFormatValidationTest {

    private val legacyErrorMessage = "هذا التطبيق يدعم فقط الصيغ الحديثة (docx, xlsx, pptx). يمكنك فتح الملف القديم في Word/Excel وحفظه بصيغة حديثة ثم إعادة المحاولة"
    private val unsupportedErrorMessage = "صيغة الملف غير مدعومة"

    private fun validateOfficeDocument(fileName: String, headerBytes: ByteArray): String? {
        val ext = fileName.substringAfterLast(".", "").lowercase()

        val isOle2Binary = headerBytes.size >= 4 &&
                headerBytes[0] == 0xD0.toByte() &&
                headerBytes[1] == 0xCF.toByte() &&
                headerBytes[2] == 0x11.toByte() &&
                headerBytes[3] == 0xE0.toByte()

        if (isOle2Binary || ext == "doc" || ext == "xls" || ext == "ppt") {
            return legacyErrorMessage
        }

        if (ext != "docx" && ext != "xlsx" && ext != "pptx") {
            return unsupportedErrorMessage
        }

        return null // Valid modern format
    }

    @Test
    fun testLegacyExtensionDetection() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertEquals(legacyErrorMessage, validateOfficeDocument("test.doc", zipHeader))
        assertEquals(legacyErrorMessage, validateOfficeDocument("report.xls", zipHeader))
        assertEquals(legacyErrorMessage, validateOfficeDocument("presentation.ppt", zipHeader))
    }

    @Test
    fun testOle2BinarySignatureDetectionEvenWithDocxExtension() {
        // File renamed from .doc to .docx with OLE2 header D0 CF 11 E0
        val ole2Header = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte())
        assertEquals(legacyErrorMessage, validateOfficeDocument("fake_document.docx", ole2Header))
        assertEquals(legacyErrorMessage, validateOfficeDocument("fake_sheet.xlsx", ole2Header))
        assertEquals(legacyErrorMessage, validateOfficeDocument("fake_slides.pptx", ole2Header))
    }

    @Test
    fun testCompletelyUnsupportedExtension() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertEquals(unsupportedErrorMessage, validateOfficeDocument("archive.rar", zipHeader))
        assertEquals(unsupportedErrorMessage, validateOfficeDocument("document.odt", zipHeader))
        assertEquals(unsupportedErrorMessage, validateOfficeDocument("picture.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun testValidModernOfficeDocument() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertEquals(null, validateOfficeDocument("document.docx", zipHeader))
        assertEquals(null, validateOfficeDocument("spreadsheet.xlsx", zipHeader))
        assertEquals(null, validateOfficeDocument("presentation.pptx", zipHeader))
    }
}
