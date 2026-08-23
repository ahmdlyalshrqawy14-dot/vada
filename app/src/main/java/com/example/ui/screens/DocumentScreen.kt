package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.data.prefs.PreferencesManager
import com.example.data.util.AppLogger
import com.example.data.util.SharedImportManager
import com.example.data.util.DocumentProcessor
import com.example.data.util.PdfOperation
import com.example.data.util.SplitMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.example.data.model.CompressionPreset
import com.example.data.model.ProcessingTask
import com.example.data.model.ProcessorType
import com.example.data.model.TaskType
import com.example.data.queue.TaskQueueManager
import com.example.data.util.StorageManager
import com.example.ui.components.CompressionPresetSelector
import com.example.ui.components.CustomCompressionControls
import com.example.ui.components.CustomCompressionSettings
import com.example.ui.components.ProcessorBadge
import com.example.ui.components.ReorderableFileList
import com.example.ui.components.SaveFileDialog
import com.example.ui.components.SelectedFileItem
import com.example.ui.components.ScreenHeader
import com.example.ui.components.HintCard
import com.example.ui.components.NumberedStep
import com.example.ui.components.PrimaryActionButton
import com.example.ui.theme.CategoryDocumentPink
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusStartGreen
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kot     throw IllegalStateException(strings.errorPdfPasswordProtected, e)
    }
}


enum class SplitMode {
    SPLIT_ALL, RANGE
}

@Composable
fun DocumentScreen(
    strings: AppStrings,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val namingPattern by prefsManager.namingPatternFlow.collectAsState(initial = "{name}_compressed")
    val lastPreset by prefsManager.lastPresetFlow.collectAsState(initial = "MEDIUM")
    val showTechnicalBadges by prefsManager.showTechnicalBadgesFlow.collectAsState(initial = false)

    var selectedOperation by remember { mutableStateOf(PdfOperation.COMPRESS) }
    var splitMode by remember { mutableStateOf(SplitMode.SPLIT_ALL) }
    var rangeText by remember { mutableStateOf("") }
    val selectedFiles = remember { mutableStateListOf<SelectedFileItem>() }
    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    var customSettings by remember { mutableStateOf(CustomCompressionSettings(quality = 70, maxDimension = 1600)) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lastPreset) {
        try {
            selectedPreset = CompressionPreset.valueOf(lastPreset)
        } catch (e: Exception) {
            // غير حرج: استخدام القيمة الافتراضية إذا فشل تحليل الإعداد المسبق
            AppLogger.logSilentFailure("DocumentScreen", "فشل تحليل الإعداد المسبق المحفوظ: $lastPreset", e)
        }
    }

    LaunchedEffect(Unit) {
        val uris = SharedImportManager.consumeUris()
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                val name = StorageManager.getFileNameFromUri(context, uri) ?: "doc_${System.currentTimeMillis()}"
                SelectedFileItem(uri, name)
            }
            selectedFiles.clear()
            selectedFiles.addAll(items)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            try {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // غير حرج: بعض الموفرين لا يدعمون منح صلاحية دائمة
                        AppLogger.logSilentFailure("DocumentScreen", "فشل أخذ صلاحية دائمة على ملف المستند", e)
                    }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val items = uris.map { uri ->
                            val name = StorageManager.getFileNameFromUri(context, uri) ?: "doc_${System.currentTimeMillis()}"
                            SelectedFileItem(uri, name)
                        }
                        withContext(Dispatchers.Main) {
                            selectedFiles.addAll(items)
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("DocumentScreen", "فشل قراءة بيانات المستندات المختارة", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("DocumentScreen", "فشل فتح ملفات المستندات من المنتقي", e)
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
            title = strings.documentSection,
            accent = CategoryDocumentPink,
            icon = Icons.Default.Description,
            subtitle = strings.documentDesc,
            onBack = onNavigateBack
        )

        if (showTechnicalBadges) {
            ProcessorBadge(strings = strings, processorType = ProcessorType.SOFTWARE)
        }

        // Operations Radio Selector
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x99131822))
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = Color(0x99131822)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val operations = listOf(
                    PdfOperation.COMPRESS to strings.pdfOperationCompress,
                    PdfOperation.SPLIT to strings.pdfOperationSplit,
                    PdfOperation.MERGE to strings.pdfOperationMerge,
                    PdfOperation.EXTRACT_TEXT to strings.pdfOperationExtractText
                )

                operations.forEach { (op, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedOperation == op,
                            onClick = { selectedOperation = op },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CategoryDocumentPink,
                                unselectedColor = Color(0xFFA0AAB5)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Sub-options for Split
        if (selectedOperation == PdfOperation.SPLIT) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x66131822))
                    .border(1.dp, GlassBorderWhite, RoundedCornerShape(16.dp)),
                color = Color(0x66131822)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = splitMode == SplitMode.SPLIT_ALL,
                            onClick = { splitMode = SplitMode.SPLIT_ALL },
                            colors = RadioButtonDefaults.colors(selectedColor = CategoryDocumentPink)
                        )
                        Text(
                            text = strings.splitAllPages,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = splitMode == SplitMode.RANGE,
                            onClick = { splitMode = SplitMode.RANGE },
                            colors = RadioButtonDefaults.colors(selectedColor = CategoryDocumentPink)
                        )
                        Text(
                            text = strings.splitSpecificRange,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (splitMode == SplitMode.RANGE) {
                        OutlinedTextField(
                            value = rangeText,
                            onValueChange = { rangeText = it },
                            placeholder = { Text(strings.rangePlaceholder, color = Color(0xFF607080)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("page_range_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CategoryDocumentPink,
                                unfocusedBorderColor = Color(0xFF1B2230),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (!isValidPageRange(rangeText)) {
                            Text(
                                text = strings.errorInvalidPageRange,
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("page_range_error")
                            )
                        }
                    }
                }
            }
        }

        // Selected Files List
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
                onDelete = { idx -> selectedFiles.removeAt(idx) },
                onReorder = { from, to ->
                    val item = selectedFiles.removeAt(from)
                    selectedFiles.add(to, item)
                }
            )
        }

        // File Selection Button
        OutlinedButton(
            onClick = { pdfPickerLauncher.launch("application/pdf") },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("select_pdf_button"),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CategoryDocumentPink)
        ) {
            Text(
                text = strings.selectFiles,
                color = CategoryDocumentPink,
                fontWeight = FontWeight.Bold
            )
        }

        // Preset Selector if Compress
        if (selectedOperation == PdfOperation.COMPRESS) {
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
        }

        val isSingleFileOperation = selectedOperation != PdfOperation.MERGE
        val showMultiFileWarning = isSingleFileOperation && selectedFiles.size > 1

        if (showMultiFileWarning) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33FF9800))
                    .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(14.dp))
                    .testTag("multi_file_warning"),
                color = Color(0x33FF9800)
            ) {
                Text(
                    text = strings.warnSingleFileOperation,
                    color = Color(0xFFFFD08A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        val rangeInvalid = selectedOperation == PdfOperation.SPLIT &&
            splitMode == SplitMode.RANGE &&
            !isValidPageRange(rangeText)

        // Start Processing Button
        Button(
            onClick = {
                if (selectedFiles.isEmpty()) {
                    Toast.makeText(context, strings.selectFiles, Toast.LENGTH_SHORT).show()
                } else if (rangeInvalid) {
                    Toast.makeText(context, strings.errorInvalidPageRange, Toast.LENGTH_LONG).show()
                } else {
                    showSaveDialog = true
                }
            },
            enabled = selectedFiles.isNotEmpty() && !rangeInvalid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_doc_processing_button"),
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
            val targetExt = when {
                selectedOperation == PdfOperation.EXTRACT_TEXT -> "txt"
                selectedOperation == PdfOperation.SPLIT && splitMode == SplitMode.SPLIT_ALL -> "zip"
                else -> "pdf"
            }

            val rawBase = selectedFiles.first().name.substringBeforeLast(".")
            val defaultName = if (namingPattern.contains("{name}")) namingPattern.replace("{name}", rawBase) else "${rawBase}_processed"

            SaveFileDialog(
                strings = strings,
                defaultName = defaultName,
                extension = targetExt,
                onDismiss = { showSaveDialog = false },
                onSave = { customFileName ->
                    val finalOutputName = customFileName.ifBlank { defaultName }
                    val params = com.example.data.model.TaskParams(
                        preset = selectedPreset.name,
                        quality = customSettings.quality,
                        maxDimension = customSettings.maxDimension,
                        isCustom = selectedPreset == CompressionPreset.CUSTOM,
                        pdfOperation = selectedOperation.name,
                        splitMode = splitMode.name,
                        rangeText = rangeText
                    )
                    val task = ProcessingTask(
                        title = strings.documentSection,
                        subtitle = "${if (isSingleFileOperation) 1 else selectedFiles.size} PDF → $finalOutputName.$targetExt",
                        taskType = TaskType.DOCUMENT,
                        sourceUris = selectedFiles.map { it.uri },
                        outputFileName = finalOutputName,
                        outputExtension = targetExt,
                        processorType = ProcessorType.SOFTWARE,
                        paramsJson = params.toJson(),
                        executeBlock = { onProgress, _, _, _, _ ->
                            DocumentProcessor.process(
                                context = context,
                                files = selectedFiles.map { it.uri },
                                operation = selectedOperation,
                                splitMode = splitMode,
                                rangeText = rangeText,
                                preset = selectedPreset,
                                customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                outputExt = targetExt,
                                onProgress = onProgress,
                                strings = strings
                            )
                        }
                    )
                    TaskQueueManager.getInstance(context).addTask(task)
                    Toast.makeText(context, "${strings.statusProcessing} ($finalOutputName.$targetExt)", Toast.LENGTH_SHORT).show()
                    showSaveDialog = false
                    onNavigateBack()
                }
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

fun isValidPageRange(text: String): Boolean = DocumentProcessor.isValidPageRange(text)

fun parsePageRange(text: String, maxPage: Int = Int.MAX_VALUE): List<Int> =
    DocumentProcessor.parsePageRange(text, maxPage)

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
        Log.e("DocumentScreen", "Error querying file name from Uri: $uri", e)
    }
    return name
}
