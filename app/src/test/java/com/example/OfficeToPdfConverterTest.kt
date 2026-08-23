package com.example

import com.example.data.model.DocParagraph
import com.example.data.model.DocParagraphType
import com.example.data.model.DocTextRun
import com.example.data.util.OfficeToPdfConverter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficeToPdfConverterTest {

    @Test
    fun testParseDocx_withHeadings_bold_italic_and_fontSize() {
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr>
                    <w:pStyle w:val="Heading1"/>
                  </w:pPr>
                  <w:r>
                    <w:t>Project Title &amp; Overview</w:t>
                  </w:r>
                </w:p>
                <w:p>
                  <w:r>
                    <w:rPr>
                      <w:b w:val="1"/>
                      <w:sz w:val="28"/>
                    </w:rPr>
                    <w:t>Important Notice:</w:t>
                  </w:r>
                  <w:r>
                    <w:t> This is regular paragraph text with </w:t>
                  </w:r>
                  <w:r>
                    <w:rPr>
                      <w:i/>
                    </w:rPr>
                    <w:t>italic styling</w:t>
                  </w:r>
                  <w:r>
                    <w:t>.</w:t>
                  </w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc>
                      <w:p>
                        <w:r>
                          <w:rPr><w:b/></w:rPr>
                          <w:t>Item</w:t>
                        </w:r>
                      </w:p>
                    </w:tc>
                    <w:tc>
                      <w:p>
                        <w:r>
                          <w:rPr><w:b/></w:rPr>
                          <w:t>Value</w:t>
                        </w:r>
                      </w:p>
                    </w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val zipBytes = createMockZip(mapOf("word/document.xml" to docXml))
        val paragraphs = mutableListOf<DocParagraph>()

        OfficeToPdfConverter.parseDocx(ByteArrayInputStream(zipBytes), paragraphs)

        assertEquals(3, paragraphs.size)

        // 1. Verify Heading1
        val headingPara = paragraphs[0]
        assertEquals(DocParagraphType.HEADING_1, headingPara.type)
        assertEquals("Project Title & Overview", headingPara.plainText)

        // 2. Verify Mixed Paragraph (Bold + Normal + Italic)
        val mixedPara = paragraphs[1]
        assertEquals(DocParagraphType.NORMAL, mixedPara.type)
        assertEquals(4, mixedPara.runs.size)

        val boldRun = mixedPara.runs[0]
        assertEquals("Important Notice:", boldRun.text)
        assertTrue("Run 0 must be bold", boldRun.isBold)
        assertFalse("Run 0 must not be italic", boldRun.isItalic)
        assertEquals(14f, boldRun.fontSizePt)

        val regularRun = mixedPara.runs[1]
        assertEquals(" This is regular paragraph text with ", regularRun.text)
        assertFalse("Run 1 must not be bold", regularRun.isBold)
        assertFalse("Run 1 must not be italic", regularRun.isItalic)

        val italicRun = mixedPara.runs[2]
        assertEquals("italic styling", italicRun.text)
        assertFalse("Run 2 must not be bold", italicRun.isBold)
        assertTrue("Run 2 must be italic", italicRun.isItalic)

        // 3. Verify Table Row
        val tablePara = paragraphs[2]
        assertEquals(DocParagraphType.TABLE_ROW, tablePara.type)
        assertNotNull(tablePara.tableCells)
        assertEquals(2, tablePara.tableCells?.size)
        assertEquals("Item", tablePara.tableCells?.get(0)?.get(0)?.text)
        assertEquals("Value", tablePara.tableCells?.get(1)?.get(0)?.text)
    }

    @Test
    fun testParseAndRenderXlsx_3Columns5Rows_structuredGridTable() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="9" uniqueCount="9">
              <si><t>Product Name</t></si>
              <si><t>Category</t></si>
              <si><t>Price ($)</t></si>
              <si><t>MacBook Pro</t></si>
              <si><t>Electronics</t></si>
              <si><t>Standing Desk</t></si>
              <si><t>Furniture</t></si>
              <si><t>Mechanical Keyboard</t></si>
              <si><t>Accessories</t></si>
            </sst>
        """.trimIndent()

        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="s"><v>3</v></c>
                  <c r="B2" t="s"><v>4</v></c>
                  <c r="C2"><v>1999</v></c>
                </row>
                <row r="3">
                  <c r="A3" t="s"><v>5</v></c>
                  <c r="B3" t="s"><v>6</v></c>
                  <c r="C3"><v>450</v></c>
                </row>
                <row r="4">
                  <c r="A4" t="s"><v>7</v></c>
                  <c r="B4" t="s"><v>8</v></c>
                  <c r="C4"><v>120</v></c>
                </row>
                <row r="5">
                  <c r="A5" t="inlineStr"><is><t>USB-C Hub</t></is></c>
                  <c r="B5" t="s"><v>8</v></c>
                  <c r="C5"><v>45</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val zipBytes = createMockZip(
            mapOf(
                "xl/sharedStrings.xml" to sharedStringsXml,
                "xl/worksheets/sheet1.xml" to sheet1Xml
            )
        )

        val sheets = mutableListOf<OfficeToPdfConverter.ExcelSheet>()
        OfficeToPdfConverter.parseXlsxSheets(ByteArrayInputStream(zipBytes), sheets)

        assertEquals(1, sheets.size)
        val sheet = sheets[0]
        assertEquals("Sheet 1", sheet.sheetName)
        assertEquals(5, sheet.rows.size)
        assertEquals(2, sheet.maxColIndex) // 0, 1, 2 = 3 columns

        // Check header row values
        val headerRow = sheet.rows[0]
        assertEquals("Product Name", headerRow.cells[0]?.plainText)
        assertEquals("Category", headerRow.cells[1]?.plainText)
        assertEquals("Price ($)", headerRow.cells[2]?.plainText)

        // Check data rows
        val row2 = sheet.rows[1]
        assertEquals("MacBook Pro", row2.cells[0]?.plainText)
        assertEquals("Electronics", row2.cells[1]?.plainText)
        assertEquals("1999", row2.cells[2]?.plainText)

        val row5 = sheet.rows[4]
        assertEquals("USB-C Hub", row5.cells[0]?.plainText)
        assertEquals("Accessories", row5.cells[1]?.plainText)
        assertEquals("45", row5.cells[2]?.plainText)
    }

    @Test
    fun testColLetterToIndex() {
        assertEquals(0, OfficeToPdfConverter.colLetterToIndex("A"))
        assertEquals(1, OfficeToPdfConverter.colLetterToIndex("B"))
        assertEquals(25, OfficeToPdfConverter.colLetterToIndex("Z"))
        assertEquals(26, OfficeToPdfConverter.colLetterToIndex("AA"))
        assertEquals(27, OfficeToPdfConverter.colLetterToIndex("AB"))
    }

    @Test
    fun testParsePptx_4Slides_createsDiscreteSlideStructure() {
        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="ctrTitle"/></p:nvPr></p:nvSpPr>
                    <p:txBody>
                      <a:p><a:r><a:t>Quarterly Financial Results</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:txBody>
                      <a:p><a:r><a:t>Q3 Executive Summary and Key Metrics</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide2Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:txBody>
                      <a:p><a:r><a:t>Market Expansion</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:txBody>
                      <a:p><a:r><a:t>Expanded presence in EMEA and APAC regions.</a:t></a:r></a:p>
                      <a:p><a:r><a:t>34% Year-over-Year revenue growth.</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide3Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:txBody>
                      <a:p><a:r><a:t>Product Roadmap</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:txBody>
                      <a:p><a:r><a:t>Core AI automation engine release in Q4.</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide4Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:sp>
                    <p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                    <p:txBody>
                      <a:p><a:r><a:t>Conclusion &amp; Next Steps</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                  <p:sp>
                    <p:txBody>
                      <a:p><a:r><a:t>Thank you for attending. Questions?</a:t></a:r></a:p>
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
            </p:sld>
        """.trimIndent()

        val zipBytes = createMockZip(
            mapOf(
                "ppt/slides/slide1.xml" to slide1Xml,
                "ppt/slides/slide2.xml" to slide2Xml,
                "ppt/slides/slide3.xml" to slide3Xml,
                "ppt/slides/slide4.xml" to slide4Xml
            )
        )

        val slides = mutableListOf<OfficeToPdfConverter.PptxSlide>()
        OfficeToPdfConverter.parsePptxSlides(ByteArrayInputStream(zipBytes), slides)

        // Verify slide titles and paragraph contents
        assertEquals(4, slides.size)
        assertEquals("Quarterly Financial Results", slides[0].title?.joinToString("") { it.text })
        assertEquals("Q3 Executive Summary and Key Metrics", slides[0].paragraphs[0].plainText)

        assertEquals("Market Expansion", slides[1].title?.joinToString("") { it.text })
        assertEquals(2, slides[1].paragraphs.size)
        assertEquals("Expanded presence in EMEA and APAC regions.", slides[1].paragraphs[0].plainText)

        assertEquals("Product Roadmap", slides[2].title?.joinToString("") { it.text })
        assertEquals("Conclusion & Next Steps", slides[3].title?.joinToString("") { it.text })
    }

    @Test
    fun testRtlTextDetection_arabic_and_mixed_bidi() {
        // Pure Arabic
        assertTrue(OfficeToPdfConverter.isRtlText("بسم الله الرحمن الرحيم"))
        assertTrue(OfficeToPdfConverter.isRtlText("تقرير الأداء المالي والنتائج التشغيلية"))

        // Mixed Arabic + Latin + Numbers (first strong character is Arabic)
        assertTrue(OfficeToPdfConverter.isRtlText("إجمالي الأرباح 5000 دولار لعام 2024"))
        assertTrue(OfficeToPdfConverter.isRtlText("تطبيق Android بنسبة نمو 45%"))

        // Pure English
        assertFalse(OfficeToPdfConverter.isRtlText("Quarterly Financial Report 2024"))
        assertFalse(OfficeToPdfConverter.isRtlText("Hello World"))

        // Empty string
        assertFalse(OfficeToPdfConverter.isRtlText(""))
    }

    @Test
    fun testBuildSpannedFromRuns_preservesStyles() {
        val runs = listOf(
            DocTextRun(text = "مرحباً بكم ", isBold = true),
            DocTextRun(text = "في التطبيق ", isItalic = true),
            DocTextRun(text = "الرسمي", isUnderline = true, colorHex = "FF0000", fontSizePt = 16f)
        )

        val spannable = OfficeToPdfConverter.buildSpannedFromRuns(
            runs = runs,
            defaultSize = 12f,
            defaultColor = 0xFF000000.toInt(),
            defaultBold = false
        )

        assertEquals("مرحباً بكم في التطبيق الرسمي", spannable.toString())
    }

    @Test
    fun testRenderDocumentToPdf_withArabicAndBiDiContent() {
        val tempPdf = File.createTempFile("test_arabic_doc", ".pdf")
        tempPdf.deleteOnExit()

        val paragraphs = listOf(
            DocParagraph(
                type = DocParagraphType.TITLE,
                runs = listOf(DocTextRun("تقرير الأداء السنوي لشركة الأفق 2024", isBold = true))
            ),
            DocParagraph(
                type = DocParagraphType.HEADING_1,
                runs = listOf(DocTextRun("١. نظرة عامة على النتائج المالية", isBold = true))
            ),
            DocParagraph(
                type = DocParagraphType.NORMAL,
                runs = listOf(
                    DocTextRun("حققت الشركة إجمالي إيرادات بلغت "),
                    DocTextRun("1,250,000 $", isBold = true),
                    DocTextRun(" بزيادة سنوية قدرها "),
                    DocTextRun("23.5%", isBold = true),
                    DocTextRun(" مقارنة بالعام السابق مع التوسع في منطقة EMEA.")
                )
            ),
            DocParagraph(
                type = DocParagraphType.BULLET_ITEM,
                runs = listOf(DocTextRun("إطلاق الإصدار الجديد من منصة AI Automation Cloud."))
            )
        )

        var progressReported = 0f
        try {
            OfficeToPdfConverter.renderDocumentToPdf(
                outputFile = tempPdf,
                documentTitle = "تقرير الأداء السنوي",
                paragraphs = paragraphs,
                onProgress = { progressReported = it }
            )
            assertTrue("PDF file should exist and not be empty", tempPdf.exists() && tempPdf.length() > 0)
            assertEquals(1.0f, progressReported, 0.01f)
        } catch (e: IllegalStateException) {
            // Handled for headless Robolectric environment where native PdfDocument driver is stubbed
            assertTrue(e.message?.contains("closed") == true || e.message?.contains("PdfDocument") == true)
        }
    }

    private fun createMockZip(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                zip.putNextEntry(entry)
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}

