package com.example.data.queue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.i18n.getAppStrings
import com.example.data.model.CompressionPreset
import com.example.data.model.ConversionMode
import com.example.data.model.TaskParams
import com.example.data.model.TaskType
import com.example.data.prefs.PreferencesManager
import com.example.data.util.DocumentProcessor
import com.example.data.util.OfficeToPdfConverter
import com.example.data.util.PdfOperation
import com.example.data.util.SplitMode
import com.example.data.util.StorageManager
import com.example.data.video.VideoProcessor
import com.example.ui.components.CustomCompressionSettings
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.io.FileOutputStream

object TaskRebuilder {

    suspend fun rebuildExecute(
        context: Context,
        taskType: TaskType,
        uris: List<Uri>,
        params: TaskParams,
        outputExtension: String
    ): File {
        val lang = PreferencesManager.getInstance(context).languageCode.firstOrNull() ?: "ar"
        val strings = getAppStrings(lang)
        val preset = try {
            CompressionPreset.valueOf(params.preset)
        } catch (_: Exception) {
            CompressionPreset.MEDIUM
        }
        val custom = if (params.isCustom) {
            CustomCompressionSettings(
                quality = params.quality,
                maxDimension = params.maxDimension,
                videoBitrateKbps = params.videoBitrateKbps
            )
        } else null

        return when (taskType) {
            TaskType.VIDEO -> VideoProcessor.process(
                context = context,
                uri = uris.first(),
                preset = preset,
                customSettings = custom,
                muteAudio = params.muteAudio,
                rotateDegrees = params.rotateDegrees,
                trimStartMs = params.trimStartMs,
                trimEndMs = params.trimEndMs,
                onProgress = {},
                onProcessorChanged = {},
                onCompressionSkipped = {},
                onOutcomeEvaluated = {},
                strings = strings
            )
            TaskType.AUDIO -> {
                val out = StorageManager.createTempFile(context, "vada_aud_rec_", "m4a")
                val result = com.example.data.audio.AudioTranscoder.transcodeSegment(
                    context = context,
                    uri = uris.first(),
                    outputFile = out,
                    preset = preset,
                    customSettings = custom,
                    onProgress = {}
                )
                if (!result.success || !out.exists() || out.length() <= 0) {
                    out.delete()
                    throw IllegalStateException(strings.errorAudioTranscodeFailed)
                }
                out
            }
            TaskType.CONVERSION -> {
                val displayName = resolveDisplayName(context, uris.first()) ?: "document.docx"
                OfficeToPdfConverter.convertOfficeToPdf(
                    context = context,
                    uri = uris.first(),
                    fileName = displayName,
                    onProgress = {},
                    strings = strings,
                    mode = ConversionMode.fromName(params.conversionMode)
                )
            }
            TaskType.DOCUMENT -> {
                val operation = try {
                    PdfOperation.valueOf(params.pdfOperation ?: "COMPRESS")
                } catch (_: Exception) {
                    PdfOperation.COMPRESS
                }
                val splitMode = try {
                    SplitMode.valueOf(params.splitMode ?: "SPLIT_ALL")
                } catch (_: Exception) {
                    SplitMode.SPLIT_ALL
                }
                DocumentProcessor.process(
                    context = context,
                    files = uris,
                    operation = operation,
                    splitMode = splitMode,
                    rangeText = params.rangeText ?: "",
                    preset = preset,
                    customSettings = custom,
                    outputExt = outputExtension,
                    onProgress = {},
                    strings = strings
                )
            }
            TaskType.IMAGE -> rebuildImage(context, uris, params, preset, custom, outputExtension, strings)
        }
    }

    private fun rebuildImage(
        context: Context,
        uris: List<Uri>,
        params: TaskParams,
        preset: CompressionPreset,
        custom: CustomCompressionSettings?,
        outputExtension: String,
        strings: com.example.data.i18n.AppStrings
    ): File {
        val quality = when (preset) {
            CompressionPreset.LIGHT -> 90
            CompressionPreset.MEDIUM -> 65
            CompressionPreset.HEAVY -> 40
            CompressionPreset.CUSTOM -> custom?.quality ?: 50
        }
        val maxDim = when (preset) {
            CompressionPreset.LIGHT -> 2560
            CompressionPreset.MEDIUM -> 1920
            CompressionPreset.HEAVY -> 1280
            CompressionPreset.CUSTOM -> custom?.maxDimension ?: 1600
        }
        val out = StorageManager.createTempFile(context, "recovered_img_", outputExtension)

        if (params.combineToPdf || outputExtension.equals("pdf", true)) {
            val pdf = PdfDocument()
            var pageNo = 0
            var ok = 0
            uris.forEach { uri ->
                val bmp = decodeScaled(context, uri, maxDim) ?: return@forEach
                pageNo++
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageNo).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdf.finishPage(page)
                bmp.recycle()
                ok++
            }
            if (ok == 0) {
                pdf.close()
                out.delete()
                throw IllegalStateException(strings.errorImageToPdfAllFailed)
            }
            FileOutputStream(out).use { pdf.writeTo(it) }
            pdf.close()
            return out
        }

        val uri = uris.first()
        val bmp = decodeScaled(context, uri, maxDim)
            ?: throw IllegalStateException(strings.errorCannotOpenFile)
        try {
            val format = when (outputExtension.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> if (android.os.Build.VERSION.SDK_INT >= 30) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                else -> Bitmap.CompressFormat.JPEG
            }
            FileOutputStream(out).use { fos ->
                if (!bmp.compress(format, quality.coerceIn(1, 100), fos)) {
                    throw IllegalStateException(strings.errorCannotOpenFile)
                }
            }
        } finally {
            bmp.recycle()
        }
        return out
    }

    private fun decodeScaled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        var sample = 1
        val w = bounds.outWidth
        val h = bounds.outHeight
        while (w / sample > maxDim || h / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment
    }
}
