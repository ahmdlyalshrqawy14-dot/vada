package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.data.i18n.AppStrings
import com.example.data.model.CompressionPreset
import com.example.ui.components.CustomCompressionSettings
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PdfOperation {
    COMPRESS, SPLIT, MERGE, EXTRACT_TEXT
}

enum class SplitMode {
    SPLIT_ALL, RANGE
}

object DocumentProcessor {
    fun loadPdfOrThrowClearError(inputStream: java.io.InputStream, strings: AppStrings): PDDocument {
        return try {
            PDDocument.load(inputStream)
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw IllegalStateException(strings.errorPdfPasswordProtected, e)
        }
    }


    fun process(
        context: Context,
        files: List<Uri>,
        operation: PdfOperation,
        splitMode: SplitMode,
        rangeText: String,
        preset: CompressionPreset,
        customSettings: CustomCompressionSettings? = null,
        outputExt: String,
        onProgress: (Float) -> Unit,
        strings: AppStrings
    ): File {
        val tempOutput = StorageManager.createTempFile(context, "voda_doc_", outputExt)

        when (operation) {
            PdfOperation.COMPRESS -> {
                compressPdf(context, files.first(), tempOutput, preset, onProgress, strings, customSettings)
            }
            PdfOperation.MERGE -> {
                mergePdfs(context, files, tempOutput, onProgress, strings)
            }
            PdfOperation.SPLIT -> {
                if (splitMode == SplitMode.SPLIT_ALL) {
                    splitAllPdfPagesToZip(context, files.first(), tempOutput, onProgress, strings)
                } else {
                    extractPdfPageRange(context, files.first(), tempOutput, rangeText, onProgress, strings)
                }
            }
            PdfOperation.EXTRACT_TEXT -> {
                extractTextFromPdf(context, files.first(), tempOutput, onProgress, strings)
            }
        }

        onProgress(1.0f)
        return tempOutput
    }

    fun compressPdf(
        context: Context,
        uri: Uri,
        outputFile: File,
        preset: CompressionPreset,
        onProgress: (Float) -> Unit,
        strings: AppStrings,
        customSettings: CustomCompressionSettings? = null

    ) {
        val qualityInt = when (preset) {
            CompressionPreset.HEAVY -> 40
            CompressionPreset.MEDIUM -> 65
            CompressionPreset.LIGHT -> 85
            CompressionPreset.CUSTOM -> customSettings?.quality ?: 70
        }

        val maxImageDimension = when (preset) {
            CompressionPreset.LIGHT -> 2560
            CompressionPreset.MEDIUM -> 1920
            CompressionPreset.HEAVY -> 1280
            CompressionPreset.CUSTOM -> customSettings?.maxDimension ?: 1600
        }

        val tempPass1 = StorageManager.createTempFile(context, "vada_pdf_pass1_", "pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                loadPdfOrThrowClearError(inputStream, strings).use { document ->
                    val pageCount = document.numberOfPages
                    val replacedCosStreams = mutableSetOf<COSStream>()

                    for (i in 0 until pageCount) {
                        val page = document.getPage(i)
                        val resources = page.resources
                        if (resources != null) {
                            val names = resources.xObjectNames.toList()
                            for (name in names) {
                                // Each embedded image is now isolated: PDFBox can throw on plenty of
                                // real-world images inside real-world PDFs - CMYK/JPX-encoded images,
                                // unusual color spaces, corrupted streams, images referencing filters
                                // it doesn't fully support. Previously a single such image aborted the
                                // *entire* compression job (the exception propagated all the way out),
                                // so one bad picture on page 47 of a 50-page PDF meant total rejection.
                                // Now we just skip that one image (leave it as-is, uncompressed) and
                                // keep going - the user still gets a compressed PDF.
                                try {
                                    val xObject = resources.getXObject(name)
                                    if (xObject is PDImageXObject) {
                                        val oldCosStream = xObject.cosStream
                                        val bitmap = xObject.image
                                        if (bitmap != null) {
                                            val origW = bitmap.width
                                            val origH = bitmap.height
                                            val maxDim = maxOf(origW, origH)
                                            val finalBitmap = if (maxDim > maxImageDimension) {
                                                val scale = maxImageDimension.toFloat() / maxDim.toFloat()
                                                val targetW = (origW * scale).toInt().coerceAtLeast(1)
                                                val targetH = (origH * scale).toInt().coerceAtLeast(1)
                                                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                                                if (scaled != bitmap) {
                                                    bitmap.recycle()
                                                }
                                                scaled
                                            } else {
                                                bitmap
                                            }

                                            try {
                                                val baos = ByteArrayOutputStream()
                                                finalBitmap.compress(Bitmap.CompressFormat.JPEG, qualityInt, baos)
                                                val compressedBytes = baos.toByteArray()
                                                val newImage = JPEGFactory.createFromByteArray(document, compressedBytes)
                                                resources.put(name, newImage)
                                                if (oldCosStream != null) {
                                                    replacedCosStreams.add(oldCosStream)
                                                }
                                            } finally {
                                                finalBitmap.recycle()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.logSilentFailure(
                                        "DocumentScreen",
                                        "تعذر ضغط صورة مضمّنة ($name) في الصفحة ${i + 1}، سيتم تركها كما هي والمتابعة",
                                        e
                                    )
                                }
                            }
                        }
                        onProgress(((i + 1).toFloat() / pageCount.toFloat() * 0.85f).coerceIn(0f, 0.85f))
                    }

                    // Explicit COS stream cleanup for replaced objects
                    val cosDoc = document.document
                    for (stream in replacedCosStreams) {
                        try {
                            cosDoc.objects.removeIf { it?.`object` == stream }
                        } catch (e: Exception) {
                            AppLogger.logSilentFailure("DocumentScreen", "فشل إزالة كائن COS المستبدل أثناء تحسين PDF", e)
                        }
                    }

                    FileOutputStream(tempPass1).use { outputStream ->
                        document.save(outputStream)
                    }
                }
            }

            val pass1Size = tempPass1.length()
            Log.d("DocumentScreen", "PDF compress pass1 size: $pass1Size bytes")

            // Pass 2: Re-load and save to rebuild Xref and discard any residual orphaned objects
            onProgress(0.92f)
            tempPass1.inputStream().use { pass1In ->
                PDDocument.load(pass1In).use { repackedDoc ->
                    FileOutputStream(outputFile).use { finalOut ->
                        repackedDoc.save(finalOut)
                    }
                }
            }

            val pass2Size = outputFile.length()
            Log.d("DocumentScreen", "PDF compress pass2 (repacked) size: $pass2Size bytes (savings from repack: ${pass1Size - pass2Size} bytes)")
        } finally {
            if (tempPass1.exists()) {
                tempPass1.delete()
            }
        }
        onProgress(1.0f)
    }

    fun mergePdfs(
        context: Context,
        files: List<Uri>,
        outputFile: File,
        onProgress: (Float) -> Unit,
        strings: AppStrings
    ) {
        val merger = PDFMergerUtility()
        val tempFiles = mutableListOf<File>()
        try {
            val totalFiles = files.size
            files.forEachIndexed { index, uri ->
                val tempInput = StorageManager.createTempFile(context, "merge_src_$index", "pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempInput).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFiles.add(tempInput)
                merger.addSource(tempInput)
                onProgress(((index + 1).toFloat() / (totalFiles * 2).toFloat()).coerceIn(0f, 0.45f))
            }
            FileOutputStream(outputFile).use { outputStream ->
                merger.destinationStream = outputStream
                try {
                    merger.mergeDocuments(null)
                } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                    throw IllegalStateException(strings.errorPdfPasswordProtected, e)
                }
            }
        } finally {
            tempFiles.forEach { it.delete() }
        }
        onProgress(1.0f)
    }

    fun splitAllPdfPagesToZip(
        context: Context,
        uri: Uri,
        outputZipFile: File,
        onProgress: (Float) -> Unit,
        strings: AppStrings
    ) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            loadPdfOrThrowClearError(inputStream, strings).use { document ->
                val pageCount = document.numberOfPages
                ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
                    for (i in 0 until pageCount) {
                        PDDocument().use { singlePageDoc ->
                            val page = document.getPage(i)
                            singlePageDoc.importPage(page)
                            val baos = ByteArrayOutputStream()
                            singlePageDoc.save(baos)
                            zipOut.putNextEntry(ZipEntry("page_${i + 1}.pdf"))
                            zipOut.write(baos.toByteArray())
                            zipOut.closeEntry()
                        }
                        onProgress(((i + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 0.95f))
                    }
                }
            }
        }
        onProgress(1.0f)
    }

    fun extractPdfPageRange(
        context: Context,
        uri: Uri,
        outputFile: File,
        rangeText: String,
        onProgress: (Float) -> Unit,
        strings: AppStrings
    ) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            loadPdfOrThrowClearError(inputStream, strings).use { document ->
                val totalPages = document.numberOfPages
                val targetPages = parsePageRange(rangeText, totalPages)
                if (targetPages.isEmpty()) {
                    throw IllegalStateException(strings.errorInvalidPageRange)
                }
                PDDocument().use { newDoc ->
                    targetPages.forEachIndexed { index, pageNum ->
                        if (pageNum in 1..totalPages) {
                            newDoc.importPage(document.getPage(pageNum - 1))
                        }
                        onProgress(((index + 1).toFloat() / targetPages.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 0.95f))
                    }
                    FileOutputStream(outputFile).use { outputStream ->
                        newDoc.save(outputStream)
                    }
                }
            }
        }
        onProgress(1.0f)
    }

    fun extractTextFromPdf(
        context: Context,
        uri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit,
        strings: AppStrings
    ) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            loadPdfOrThrowClearError(inputStream, strings).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                if (text.isNullOrBlank()) {
                    throw IllegalStateException(strings.errorPdfTextExtractionFailed)
                }
                FileOutputStream(outputFile).use { outputStream ->
                    outputStream.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
        onProgress(1.0f)
    }

    fun parsePageRange(text: String, maxPage: Int = Int.MAX_VALUE): List<Int> {
        val pages = mutableListOf<Int>()
        text.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val sub = trimmed.split("-")
                if (sub.size == 2) {
                    val start = sub[0].toIntOrNull()
                    val end = sub[1].toIntOrNull()
                    if (start != null && end != null && start <= end) {
                        for (p in start..end) {
                            if (p in 1..maxPage) pages.add(p)
                        }
                    }
                }
            } else {
                val p = trimmed.toIntOrNull()
                if (p != null && p in 1..maxPage) pages.add(p)
            }
        }
        return pages.distinct().sorted()
    }

    /**
     * تحقق من صيغة نطاق الصفحات قبل السماح ببدء المعالجة.
     * لا يُسمح بنص فارغ أو نص لا يحتوي أي رقم صفحة صالح.
     */
    fun isValidPageRange(text: String): Boolean {
        if (text.isBlank()) return false
        return parsePageRange(text).isNotEmpty()
    }
}
