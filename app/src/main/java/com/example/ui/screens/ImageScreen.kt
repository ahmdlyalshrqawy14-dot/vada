package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.util.AppLogger
import android.os.Build
import com.example.data.model.CompressionPreset
import com.example.data.model.ProcessingTask
import com.example.data.model.ProcessorType
import com.example.data.model.TaskType
import com.example.data.queue.TaskQueueManager
import com.example.data.util.StorageManager
import com.example.ui.components.CompressionPresetSelector
import com.example.ui.components.CustomCompressionControls
import com.example.ui.components.CustomCompressionSettings
import com.example.ui.components.ImageOutputFormatSelector
import com.example.ui.components.OutputImageFormat
import com.example.ui.components.ProcessorBadge
import com.example.ui.components.ReorderableFileList
import com.example.ui.components.SaveFileDialog
import com.example.ui.components.SelectedFileItem
import com.example.ui.components.ScreenHeader
import com.example.ui.theme.CategoryImageGreen
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.TextMuted
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.ConfirmType

@Composable
fun ImageScreen(
    strings: AppStrings,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var combineToPdf by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<SelectedFileItem>() }
    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    var customSettings by remember { mutableStateOf(CustomCompressionSettings()) }
    var selectedOutputFormat by remember { mutableStateOf(OutputImageFormat.AUTO) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val prefsManager = remember { com.example.data.prefs.PreferencesManager.getInstance(context) }
    val namingPattern by prefsManager.namingPatternFlow.collectAsState(initial = "{name}_compressed")
    val lastPreset by prefsManager.lastPresetFlow.collectAsState(initial = "MEDIUM")
    val showTechnicalBadges by prefsManager.showTechnicalBadgesFlow.collectAsState(initial = false)

    LaunchedEffect(lastPreset) {
        try {
            selectedPreset = CompressionPreset.valueOf(lastPreset)
        } catch (e: Exception) {
            // غير حرج: استخدام القيمة الافتراضية إذا فشل تحليل الإعداد المسبق
            AppLogger.logSilentFailure("ImageScreen", "فشل تحليل الإعداد المسبق المحفوظ: $lastPreset", e)
        }
    }

    // عند مغادرة الشاشة: حرر أي صور مصغّرة متبقية في الذاكرة لتفادي تسريبها.
    DisposableEffect(Unit) {
        onDispose {
            selectedFiles.forEach { item ->
                item.thumbnailBitmap?.let { bmp -> if (!bmp.isRecycled) bmp.recycle() }
            }
        }
    }

    LaunchedEffect(Unit) {
        val uris = com.example.data.util.SharedImportManager.consumeUris()
        if (uris.isNotEmpty()) {
            val items = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = StorageManager.getFileNameFromUri(context, uri) ?: "image_${System.currentTimeMillis()}"
                    val (format, hasTrans) = detectImageProperties(context, uri)
                    val thumbnail = generateThumbnail(context, uri)
                    SelectedFileItem(uri, name, originalFormat = format, hasTransparency = hasTrans, thumbnailBitmap = thumbnail)
                }
            }
            selectedFiles.clear()
            selectedFiles.addAll(items)
            if (items.any { it.hasTransparency }) {
                Toast.makeText(
                    context,
                    strings.imageTransparentPngNotice,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            try {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // غير حرج: بعض الموفرين لا يدعمون أخذ صلاحية دائمة
                        AppLogger.logSilentFailure("ImageScreen", "فشل أخذ صلاحية دائمة على ملف الصورة", e)
                    }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val items = uris.map { uri ->
                            val name = StorageManager.getFileNameFromUri(context, uri) ?: "image_${System.currentTimeMillis()}"
                            val (format, hasTrans) = detectImageProperties(context, uri)
                            val thumbnail = generateThumbnail(context, uri)
                            SelectedFileItem(uri, name, originalFormat = format, hasTransparency = hasTrans, thumbnailBitmap = thumbnail)
                        }
                        withContext(Dispatchers.Main) {
                            selectedFiles.addAll(items)
                            if (items.any { it.hasTransparency }) {
                                Toast.makeText(
                                    context,
                                    strings.imageTransparentPngNotice,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("ImageScreen", "فشل استخراج بيانات الصور المختارة", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("ImageScreen", "فشل فتح الصور من المنتقي", e)
                Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        ScreenHeader(
            title = strings.imageSection,
            accent = CategoryImageGreen,
            icon = Icons.Default.Image,
            subtitle = strings.imageDesc,
            onBack = onNavigateBack
        )

        if (showTechnicalBadges) {
            ProcessorBadge(strings = strings, processorType = ProcessorType.SOFTWARE)
        }

        // Options: Combine to PDF & Remove EXIF
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x66131822))
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(16.dp)),
            color = Color(0x66131822)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = combineToPdf,
                        onCheckedChange = { combineToPdf = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CategoryImageGreen,
                            uncheckedColor = TextMuted
                        ),
                        modifier = Modifier.testTag("combine_pdf_checkbox")
                    )
                    Text(
                        text = strings.combineToPdfLabel,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = strings.imageExifPrivacyNotice,
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        }

        // Selected Images List
        if (selectedFiles.isNotEmpty()) {
            ReorderableFileList(
                strings = strings,
                files = selectedFiles,
                onMoveUp = { idx ->
                    val item = selectedFiles.removeAt(idx)
                    selectedFiles.add(idx - 1, item)
                },
                onMoveDown = { idx ->
                    val item = selectedFiles.removeAt(idx)
                    selectedFiles.add(idx + 1, item)
                },
                onDelete = { idx ->
                    val removed = selectedFiles.removeAt(idx)
                    removed.thumbnailBitmap?.let { bmp -> if (!bmp.isRecycled) bmp.recycle() }
                },
                onReorder = { from, to ->
                    val item = selectedFiles.removeAt(from)
                    selectedFiles.add(to, item)
                }
            )
        }

        // Select Button
        OutlinedButton(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("select_images_button"),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CategoryImageGreen)
        ) {
            Text(
                text = strings.selectFiles,
                color = CategoryImageGreen,
                fontWeight = FontWeight.Bold
            )
        }

        // Preset Selector (if not combine to PDF)
        if (!combineToPdf) {
            CompressionPresetSelector(
                strings = strings,
                selectedPreset = selectedPreset,
                onPresetSelected = { preset ->
                    selectedPreset = preset
                    coroutineScope.launch { prefsManager.setLastPreset(preset.name) }
                }
            )

            if (selectedPreset == CompressionPreset.CUSTOM) {
                CustomCompressionControls(
                    strings = strings,
                    settings = customSettings,
                    onSettingsChange = { customSettings = it },
                    showQuality = true,
                    showMaxDimension = true,
                    showVideoBitrate = false
                )
            }

            ImageOutputFormatSelector(
                strings = strings,
                selectedFormat = selectedOutputFormat,
                onFormatSelected = { selectedOutputFormat = it }
            )
        }

        // Start Processing Button
        Button(
            onClick = {
                if (selectedFiles.isNotEmpty()) {
                    showSaveDialog = true
                } else {
                    Toast.makeText(context, strings.selectFiles, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = selectedFiles.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_image_processing_button"),
            colors = ButtonDefaults.buttonColors(containerColor = StatusStartGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = strings.startProcessing,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showSaveDialog && selectedFiles.isNotEmpty()) {
            val filesSnapshot = selectedFiles.toList()
            val forcedFormat = selectedOutputFormat.formatKey
            val allPng = filesSnapshot.all { it.originalFormat == "png" || it.hasTransparency }
            val hasPng = filesSnapshot.any { it.originalFormat == "png" || it.hasTransparency }
            val hasJpg = filesSnapshot.any { it.originalFormat != "png" && !it.hasTransparency }
            val isMixed = hasPng && hasJpg

            // عند تعطيل الدمج في PDF: كل صورة تصبح مهمة منفصلة وتُحفظ كملف مستقل (بدون ZIP)
            val perImageTasks = !combineToPdf && filesSnapshot.size > 1

            val targetExt = if (combineToPdf) {
                "pdf"
            } else if (forcedFormat != null) {
                forcedFormat
            } else if (perImageTasks) {
                // كل صورة تحتفظ بصيغتها المناسبة، والامتداد المعروض تقديري للمعاينة
                if (allPng) "png" else "jpg"
            } else if (isMixed) {
                "zip"
            } else if (allPng) {
                "png"
            } else {
                "jpg"
            }

            val hasAnyTransparency = filesSnapshot.any { it.hasTransparency }
            val transparencyNotice = if (hasAnyTransparency && forcedFormat == null) {
                strings.imageTransparentPngNotice
            } else null

            val firstBase = filesSnapshot.first().name.substringBeforeLast(".")
            val rawBase = if (combineToPdf) "images_document" else firstBase
            val defaultName = if (perImageTasks) {
                "compressed"
            } else if (namingPattern.contains("{name}")) {
                namingPattern.replace("{name}", rawBase)
            } else {
                "${rawBase}_compressed"
            }

            SaveFileDialog(
                strings = strings,
                defaultName = defaultName,
                extension = targetExt,
                noticeText = transparencyNotice,
                fileCount = if (perImageTasks) filesSnapshot.size else 1,
                sampleBaseName = firstBase,
                onSave = { customFileName ->
                    val typed = customFileName.ifBlank { defaultName }
                    val queueManager = TaskQueueManager.getInstance(context)

                    if (perImageTasks) {
                        filesSnapshot.forEach { item ->
                            val itemBase = item.name.substringBeforeLast(".")
                            val itemExt = forcedFormat
                                ?: if (item.hasTransparency || item.originalFormat == "png") "png" else "jpg"
                            val outputName = "${itemBase}_$typed"
                            val params = com.example.data.model.TaskParams(
                                preset = selectedPreset.name,
                                quality = customSettings.quality,
                                maxDimension = customSettings.maxDimension,
                                isCustom = selectedPreset == CompressionPreset.CUSTOM,
                                combineToPdf = false,
                                outputFormat = itemExt
                            )
                            val task = ProcessingTask(
                                title = strings.imageSection,
                                subtitle = "${item.name} → $outputName.$itemExt",
                                taskType = TaskType.IMAGE,
                                sourceUris = listOf(item.uri),
                                outputFileName = outputName,
                                outputExtension = itemExt,
                                processorType = ProcessorType.SOFTWARE,
                                paramsJson = params.toJson(),
                                executeBlock = { onProgress, _, _, _, _ ->
                                    processImages(
                                        context = context,
                                        selectedItems = listOf(item),
                                        combineToPdf = false,
                                        preset = selectedPreset,
                                        customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                        targetExt = itemExt,
                                        forcedFormat = forcedFormat,
                                        onProgress = onProgress,
                                        strings = strings
                                    )
                                }
                            )
                            queueManager.addTask(task)
                        }
                        Toast.makeText(
                            context,
                            "${strings.statusProcessing} (${filesSnapshot.size})",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val params = com.example.data.model.TaskParams(
                            preset = selectedPreset.name,
                            quality = customSettings.quality,
                            maxDimension = customSettings.maxDimension,
                            isCustom = selectedPreset == CompressionPreset.CUSTOM,
                            combineToPdf = combineToPdf,
                            outputFormat = targetExt
                        )
                        val task = ProcessingTask(
                            title = strings.imageSection,
                            subtitle = "${filesSnapshot.size} images → $typed.$targetExt",
                            taskType = TaskType.IMAGE,
                            sourceUris = filesSnapshot.map { it.uri },
                            outputFileName = typed,
                            outputExtension = targetExt,
                            processorType = ProcessorType.SOFTWARE,
                            paramsJson = params.toJson(),
                            executeBlock = { onProgress, _, _, _, _ ->
                                processImages(
                                    context = context,
                                    selectedItems = filesSnapshot,
                                    combineToPdf = combineToPdf,
                                    preset = selectedPreset,
                                    customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                    targetExt = targetExt,
                                    forcedFormat = forcedFormat,
                                    onProgress = onProgress,
                                    strings = strings
                                )
                            }
                        )
                        queueManager.addTask(task)
                        Toast.makeText(context, "${strings.statusProcessing} ($typed.$targetExt)", Toast.LENGTH_SHORT).show()
                    }

                    showSaveDialog = false
                    onNavigateBack()
                },
                onDismiss = { showSaveDialog = false }
            )
        }


        Spacer(modifier = Modifier.height(80.dp))
    }
}

private fun getCompressFormat(
    ext: String,
    preset: CompressionPreset,
    quality: Int
): Bitmap.CompressFormat {
    return when (ext.lowercase()) {
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (preset == CompressionPreset.LIGHT && quality >= 95) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP_LOSSY
                }
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
        else -> Bitmap.CompressFormat.JPEG
    }
}

private fun processImages(
    context: Context,
    selectedItems: List<SelectedFileItem>,
    combineToPdf: Boolean,
    preset: CompressionPreset,
    customSettings: CustomCompressionSettings? = null,
    targetExt: String,
    forcedFormat: String? = null,
    onProgress: (Float) -> Unit,
    strings: AppStrings
): File {
    val tempOutput = StorageManager.createTempFile(context, "vada_img_", targetExt)

    if (combineToPdf) {
        val pdfDocument = PdfDocument()
        val total = selectedItems.size
        var successCount = 0

        selectedItems.forEachIndexed { index, item ->
            try {
                val bitmap = decodeAndOrientBitmap(context, item.uri, maxDimension = 1920, config = Bitmap.Config.ARGB_8888)
                if (bitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                    successCount++
                }
            } catch (e: Exception) {
                Log.e("ImageScreen", "Error processing image $index into PDF", e)
            }
            onProgress(((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 0.95f))
        }

        if (successCount == 0) {
            pdfDocument.close()
            if (tempOutput.exists()) tempOutput.delete()
            throw IllegalStateException(strings.errorImageToPdfAllFailed)
        }

        FileOutputStream(tempOutput).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        onProgress(1.0f)
        return tempOutput
    }

    // Single or Multi Image Compression
    val quality = when (preset) {
        CompressionPreset.LIGHT -> 90
        CompressionPreset.MEDIUM -> 65
        CompressionPreset.HEAVY -> 40
        CompressionPreset.CUSTOM -> customSettings?.quality ?: 50
    }

    val maxDimension = when (preset) {
        CompressionPreset.LIGHT -> 2560
        CompressionPreset.MEDIUM -> 1920
        CompressionPreset.HEAVY -> 1280
        CompressionPreset.CUSTOM -> customSettings?.maxDimension ?: 1600
    }

    if (selectedItems.size == 1 && targetExt != "zip") {
        val singleItem = selectedItems.first()
        val effectiveExt = forcedFormat ?: (if (singleItem.hasTransparency || singleItem.originalFormat == "png" || targetExt.lowercase() == "png") "png" else targetExt.lowercase())
        val compressFormat = getCompressFormat(effectiveExt, preset, quality)
        compressSingleImage(
            context = context,
            uri = singleItem.uri,
            outputFile = tempOutput,
            maxDimension = maxDimension,
            quality = quality,
            format = compressFormat,
            forcePngIfTransparent = (forcedFormat == null),
            strings = strings
        )
        onProgress(1.0f)
        return tempOutput
    }

    // Multi-Image (or mixed single image) Compression -> output packaged in ZIP archive
    val totalFiles = selectedItems.size
    var zipSuccessCount = 0
    ZipOutputStream(FileOutputStream(tempOutput)).use { zipOut ->
        selectedItems.forEachIndexed { index, item ->
            val itemExt = forcedFormat ?: (if (item.hasTransparency || item.originalFormat == "png") "png" else "jpg")
            val itemCompressFormat = getCompressFormat(itemExt, preset, quality)
            val tempSingle = StorageManager.createTempFile(context, "vada_sub_img_", itemExt)
            try {
                compressSingleImage(
                    context = context,
                    uri = item.uri,
                    outputFile = tempSingle,
                    maxDimension = maxDimension,
                    quality = quality,
                    format = itemCompressFormat,
                    forcePngIfTransparent = (forcedFormat == null),
                    strings = strings
                )
                if (tempSingle.exists() && tempSingle.length() > 0) {
                    val baseName = item.name.substringBeforeLast(".").ifBlank { "image_${index + 1}" }
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    zipOut.putNextEntry(ZipEntry("${baseName}_compressed.$itemExt"))
                    tempSingle.inputStream().use { inp -> inp.copyTo(zipOut) }
                    zipOut.closeEntry()
                    zipSuccessCount++
                }
            } catch (e: Exception) {
                Log.e("ImageScreen", "Error compressing multi-image $index", e)
            } finally {
                if (tempSingle.exists()) tempSingle.delete()
            }
            onProgress(((index + 1).toFloat() / totalFiles.toFloat()).coerceIn(0f, 0.95f))
        }
    }

    if (zipSuccessCount == 0) {
        if (tempOutput.exists()) tempOutput.delete()
        throw IllegalStateException(strings.errorImageProcessAllFailed)
    }

    onProgress(1.0f)
    return tempOutput
}

/**
 * Probes an image's real pixel dimensions, robustly.
 *
 * `BitmapFactory.decodeStream(..., inJustDecodeBounds = true)` silently fails (returns null,
 * leaves `outWidth`/`outHeight` at -1) for a number of real-world files it can't parse at all:
 * CMYK-encoded JPEGs (common from professional cameras/scanners/Photoshop exports), some HEIC
 * variants, AVIF, and occasionally corrupted/exotic PNGs. The previous code never checked for
 * this failure signal, so the -1/-1 "bounds" silently sailed through the downsampling math and
 * the image was rejected later with a generic error - the image-section counterpart of the video
 * rejection bug. `ImageDecoder` (API 28+) uses a different, more modern decode path that handles
 * most of these correctly, so it's used here as a fallback probe.
 */
private fun probeImageBounds(context: Context, uri: Uri): Pair<Int, Int>? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
    } catch (e: Exception) {
        Log.w("ImageScreen", "BitmapFactory bounds probe failed, will try ImageDecoder fallback", e)
    }
    if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
        return Pair(boundsOptions.outWidth, boundsOptions.outHeight)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return try {
            var w = 0
            var h = 0
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                w = info.size.width
                h = info.size.height
                decoder.setTargetSize(1, 1) // we only need the real dimensions here, skip the full decode cost
            }
            if (w > 0 && h > 0) Pair(w, h) else null
        } catch (e: Exception) {
            Log.w("ImageScreen", "ImageDecoder bounds probe also failed", e)
            null
        }
    }
    return null
}

/**
 * Decodes an image into a software (pixel-readable, compressible) [Bitmap], with a fallback path
 * for formats BitmapFactory can't handle. See [probeImageBounds] for why this fallback exists.
 */
private fun decodeBitmapRobust(
    context: Context,
    uri: Uri,
    sampleSize: Int,
    targetWidth: Int,
    targetHeight: Int,
    config: Bitmap.Config
): Bitmap? {
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = config
    }
    val viaBitmapFactory = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Exception) {
        Log.w("ImageScreen", "BitmapFactory decode failed, will try ImageDecoder fallback", e)
        null
    }
    if (viaBitmapFactory != null) return viaBitmapFactory

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // ImageDecoder defaults to a HARDWARE bitmap, which can't be read pixel-by-pixel
                // (transparency detection) or passed to Bitmap.compress(). Force software memory.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                if (sampleSize > 1 && info.size.width > 0 && info.size.height > 0) {
                    val w = (targetWidth).coerceAtLeast(1)
                    val h = (targetHeight).coerceAtLeast(1)
                    decoder.setTargetSize(w, h)
                }
            }
        } catch (e: Exception) {
            Log.e("ImageScreen", "ImageDecoder decode fallback also failed", e)
            null
        }
    }
    return null
}

private fun decodeAndOrientBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int,
    config: Bitmap.Config = Bitmap.Config.ARGB_8888
): Bitmap? {
    var orientation = android.media.ExifInterface.ORIENTATION_NORMAL
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = android.media.ExifInterface(stream)
            orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        }
    } catch (e: Exception) {
        Log.w("ImageScreen", "Failed to read EXIF orientation", e)
    }

    val bounds = probeImageBounds(context, uri) ?: return null
    val (origW, origH) = bounds

    var sampleSize = 1
    if (origW > maxDimension || origH > maxDimension) {
        val halfW = origW / 2
        val halfH = origH / 2
        while ((halfW / sampleSize) >= maxDimension && (halfH / sampleSize) >= maxDimension) {
            sampleSize *= 2
        }
    }
    val targetW = (origW / sampleSize).coerceAtLeast(1)
    val targetH = (origH / sampleSize).coerceAtLeast(1)

    var bitmap = decodeBitmapRobust(context, uri, sampleSize, targetW, targetH, config) ?: return null

    val matrix = Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
    }

    if (!matrix.isIdentity) {
        try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            bitmap = rotated
        } catch (e: Exception) {
            Log.e("ImageScreen", "Error rotating bitmap with EXIF matrix", e)
        }
    }

    val currentMax = maxOf(bitmap.width, bitmap.height)
    if (currentMax > maxDimension) {
        val scale = maxDimension.toFloat() / currentMax.toFloat()
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled != bitmap) {
                bitmap.recycle()
            }
            bitmap = scaled
        } catch (e: Exception) {
            Log.e("ImageScreen", "Error scaling bitmap to maxDimension", e)
        }
    }

    return bitmap
}

/**
 * ينشئ صورة مصغّرة (Thumbnail) سريعة بحجم [maxDimension] × [maxDimension] كحد أقصى، لعرضها في
 * قائمة الملفات المختارة. يعتمد على [probeImageBounds] و[decodeBitmapRobust] الموجودتين مسبقاً
 * لاستخدام inSampleSize مناسب وتفادي فك تشفير الصورة الكاملة بحجمها الأصلي (توفير للذاكرة والوقت).
 */
private fun generateThumbnail(context: Context, uri: Uri, maxDimension: Int = 96): Bitmap? {
    return try {
        val bounds = probeImageBounds(context, uri) ?: return null
        val (origW, origH) = bounds
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while ((origW / sampleSize) > maxDimension * 2 && (origH / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }
        val targetW = (origW / sampleSize).coerceAtLeast(1)
        val targetH = (origH / sampleSize).coerceAtLeast(1)

        val decoded = decodeBitmapRobust(context, uri, sampleSize, targetW, targetH, Bitmap.Config.ARGB_8888)
            ?: return null

        val largestSide = maxOf(decoded.width, decoded.height)
        if (largestSide <= maxDimension) {
            return decoded
        }

        val scale = maxDimension.toFloat() / largestSide.toFloat()
        val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = try {
            Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
        } catch (e: Exception) {
            Log.w("ImageScreen", "Failed to scale thumbnail bitmap for $uri", e)
            decoded
        }
        if (scaled != decoded) {
            decoded.recycle()
        }
        scaled
    } catch (e: Exception) {
        Log.w("ImageScreen", "Failed to generate thumbnail for $uri", e)
        null
    }
}

private fun bitmapHasTransparentPixels(bitmap: Bitmap): Boolean {
    if (!bitmap.hasAlpha()) return false
    val width = bitmap.width
    val height = bitmap.height
    val step = (maxOf(width, height) / 100).coerceAtLeast(1)
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 255) {
                return true
            }
        }
    }
    val probePoints = listOf(
        Pair(0, 0),
        Pair(width - 1, 0),
        Pair(0, height - 1),
        Pair(width - 1, height - 1),
        Pair(width / 2, height / 2)
    )
    for ((x, y) in probePoints) {
        if (x in 0 until width && y in 0 until height) {
            val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
            if (alpha < 255) return true
        }
    }
    return false
}

private fun compressSingleImage(
    context: Context,
    uri: Uri,
    outputFile: File,
    maxDimension: Int,
    quality: Int,
    format: Bitmap.CompressFormat,
    forcePngIfTransparent: Boolean = true,
    strings: AppStrings
) {
    val bitmap = decodeAndOrientBitmap(context, uri, maxDimension)
        ?: throw IllegalStateException(strings.errorImageProcessAllFailed)
    try {
        val hasTrans = bitmapHasTransparentPixels(bitmap)
        val effectiveFormat = if (forcePngIfTransparent && hasTrans) {
            Bitmap.CompressFormat.PNG
        } else {
            format
        }
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(effectiveFormat, quality, out)
        }
    } finally {
        bitmap.recycle()
    }
}

private fun detectImageProperties(context: Context, uri: Uri): Pair<String, Boolean> {
    val format = detectOutputFormat(context, uri)
    if (format != "png") {
        return Pair("jpg", false)
    }

    val maxDimension = 512
    val bounds = probeImageBounds(context, uri) ?: return Pair("png", true)
    val (origW, origH) = bounds
    var sampleSize = 1
    if (origW > maxDimension || origH > maxDimension) {
        val halfW = origW / 2
        val halfH = origH / 2
        while ((halfW / sampleSize) >= maxDimension && (halfH / sampleSize) >= maxDimension) {
            sampleSize *= 2
        }
    }
    val targetW = (origW / sampleSize).coerceAtLeast(1)
    val targetH = (origH / sampleSize).coerceAtLeast(1)

    val bmp = decodeBitmapRobust(context, uri, sampleSize, targetW, targetH, Bitmap.Config.ARGB_8888)

    val hasTransparency = if (bmp != null) {
        val hasTrans = bitmapHasTransparentPixels(bmp)
        bmp.recycle()
        hasTrans
    } else {
        true
    }

    return Pair("png", hasTransparency)
}

private fun detectOutputFormat(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8)
            val read = stream.read(buffer, 0, 8)
            if (read >= 8 &&
                buffer[0] == 0x89.toByte() &&
                buffer[1] == 0x50.toByte() &&
                buffer[2] == 0x4E.toByte() &&
                buffer[3] == 0x47.toByte() &&
                buffer[4] == 0x0D.toByte() &&
                buffer[5] == 0x0A.toByte() &&
                buffer[6] == 0x1A.toByte() &&
                buffer[7] == 0x0A.toByte()
            ) {
                "png"
            } else {
                "jpg"
            }
        } ?: "jpg"
    } catch (e: Exception) {
        AppLogger.logSilentFailure("ImageScreen", "فشل قراءة ترويسة ملف الصورة: $uri", e)
        "jpg"
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {
        AppLogger.logSilentFailure("ImageScreen", "فشل استخراج اسم ملف الصورة من Uri: $uri", e)
    }
    return name
}
