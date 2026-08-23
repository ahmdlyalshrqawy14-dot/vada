package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFormatDetectionTest {

    private fun detectOutputFormat(headerBytes: ByteArray): String {
        return if (headerBytes.size >= 8 &&
            headerBytes[0] == 0x89.toByte() &&
            headerBytes[1] == 0x50.toByte() &&
            headerBytes[2] == 0x4E.toByte() &&
            headerBytes[3] == 0x47.toByte() &&
            headerBytes[4] == 0x0D.toByte() &&
            headerBytes[5] == 0x0A.toByte() &&
            headerBytes[6] == 0x1A.toByte() &&
            headerBytes[7] == 0x0A.toByte()
        ) {
            "png"
        } else {
            "jpg"
        }
    }

    private fun determineTargetExtension(
        selectedFormats: List<String>,
        combineToPdf: Boolean
    ): String {
        if (combineToPdf) return "pdf"

        val allPng = selectedFormats.all { it == "png" }
        val hasPng = selectedFormats.any { it == "png" }
        val hasJpg = selectedFormats.any { it != "png" }
        val isMixed = hasPng && hasJpg

        return if (selectedFormats.size > 1 || isMixed) {
            "zip"
        } else if (allPng) {
            "png"
        } else {
            "jpg"
        }
    }

    data class TestItem(val name: String, val originalFormat: String, val hasTransparency: Boolean)

    private fun determineTargetExtensionFromItems(
        items: List<TestItem>,
        combineToPdf: Boolean,
        forcedFormat: String? = null
    ): String {
        if (combineToPdf) return "pdf"
        if (items.size > 1) return "zip"
        if (forcedFormat != null) return forcedFormat

        val allPng = items.all { it.originalFormat == "png" || it.hasTransparency }
        val hasPng = items.any { it.originalFormat == "png" || it.hasTransparency }
        val hasJpg = items.any { it.originalFormat != "png" && !it.hasTransparency }
        val isMixed = hasPng && hasJpg

        return if (isMixed) {
            "zip"
        } else if (allPng) {
            "png"
        } else {
            "jpg"
        }
    }

    private fun getZipEntryExtension(item: TestItem, forcedFormat: String? = null): String {
        return forcedFormat ?: (if (item.hasTransparency || item.originalFormat == "png") "png" else "jpg")
    }

    @Test
    fun testForcedPngOnJpgImage() {
        val item = TestItem("photo.jpg", "jpg", hasTransparency = false)
        val ext = determineTargetExtensionFromItems(listOf(item), combineToPdf = false, forcedFormat = "png")
        assertEquals("png", ext)
    }

    @Test
    fun testForcedWebpOnJpgImage() {
        val item = TestItem("photo.jpg", "jpg", hasTransparency = false)
        val ext = determineTargetExtensionFromItems(listOf(item), combineToPdf = false, forcedFormat = "webp")
        assertEquals("webp", ext)
    }

    @Test
    fun testForcedWebpInZipEntries() {
        val item1 = TestItem("photo.jpg", "jpg", hasTransparency = false)
        val item2 = TestItem("icon.png", "png", hasTransparency = true)
        val targetExt = determineTargetExtensionFromItems(listOf(item1, item2), combineToPdf = false, forcedFormat = "webp")
        assertEquals("zip", targetExt)
        assertEquals("webp", getZipEntryExtension(item1, forcedFormat = "webp"))
        assertEquals("webp", getZipEntryExtension(item2, forcedFormat = "webp"))
    }

    @Test
    fun testSinglePngWithTransparency() {
        val item = TestItem("logo.png", "png", hasTransparency = true)
        assertEquals("png", determineTargetExtensionFromItems(listOf(item), combineToPdf = false))
    }

    @Test
    fun testMixedJpgAndTransparentPngInZip() {
        val item1 = TestItem("photo.jpg", "jpg", hasTransparency = false)
        val item2 = TestItem("icon.png", "png", hasTransparency = true)

        val targetExt = determineTargetExtensionFromItems(listOf(item1, item2), combineToPdf = false)
        assertEquals("zip", targetExt)

        assertEquals("jpg", getZipEntryExtension(item1))
        assertEquals("png", getZipEntryExtension(item2))
    }

    @Test
    fun testDetectPngMagicBytes() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        assertEquals("png", detectOutputFormat(pngHeader))
    }

    @Test
    fun testDetectJpegMagicBytes() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46)
        assertEquals("jpg", detectOutputFormat(jpegHeader))
    }

    @Test
    fun testSinglePngPreserved() {
        val ext = determineTargetExtension(listOf("png"), combineToPdf = false)
        assertEquals("png", ext)
    }

    @Test
    fun testSingleJpgPreserved() {
        val ext = determineTargetExtension(listOf("jpg"), combineToPdf = false)
        assertEquals("jpg", ext)
    }

    @Test
    fun testMultiplePngReturnsZip() {
        val ext = determineTargetExtension(listOf("png", "png"), combineToPdf = false)
        assertEquals("zip", ext)
    }

    @Test
    fun testMixedFormatsReturnsZip() {
        val ext = determineTargetExtension(listOf("png", "jpg"), combineToPdf = false)
        assertEquals("zip", ext)
    }

    @Test
    fun testCombineToPdfTakesPrecedence() {
        val ext = determineTargetExtension(listOf("png"), combineToPdf = true)
        assertEquals("pdf", ext)
    }

    @Test
    fun testPdfImageDimensionScaling() {
        val origW = 3000
        val origH = 4000
        val maxImageDimension = 1280 // HEAVY

        val maxDim = maxOf(origW, origH)
        val shouldScale = maxDim > maxImageDimension
        assertEquals(true, shouldScale)

        val scale = maxImageDimension.toFloat() / maxDim.toFloat()
        val targetW = (origW * scale).toInt().coerceAtLeast(1)
        val targetH = (origH * scale).toInt().coerceAtLeast(1)

        assertEquals(960, targetW)
        assertEquals(1280, targetH)
    }

    @Test
    fun testPdfImageSmallerThanMaxDimensionNotScaled() {
        val origW = 800
        val origH = 600
        val maxImageDimension = 1920 // MEDIUM

        val maxDim = maxOf(origW, origH)
        val shouldScale = maxDim > maxImageDimension
        assertEquals(false, shouldScale)
    }
}
