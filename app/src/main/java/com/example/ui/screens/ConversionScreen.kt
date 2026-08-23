package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.data.util.AppLogger
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.model.ConversionMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import com.example.data.model.ProcessingTask
import com.example.data.model.ProcessorType
import com.example.data.model.TaskType
import com.example.data.queue.TaskQueueManager
import com.example.data.util.OfficeToPdfConverter
import com.example.data.util.StorageManager
import com.example.ui.components.ProcessorBadge
import com.example.ui.components.SaveFileDialog
import com.example.ui.components.ScreenHeader
import com.example.ui.components.HintCard
import com.example.ui.components.FilePickZone
import com.example.ui.components.NumberedStep
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.FormatSupportRow
import com.example.ui.theme.SurfaceCardLow
import androidx.compose.foundation.layout.Box
import com.example.ui.theme.CategoryConvertCyan
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.SurfaceCardHigh
import com.example.ui.theme.TextMuted
import java.io.File

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.ConfirmType

import androidx.compose.runtime.collectAsState
import com.example.data.prefs.PreferencesManager
import com.example.data.util.SharedImportManager

@Composable
fun ConversionScreen(
    strings: AppStrings,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val namingPattern by prefsManager.namingPatternFlow.collectAsState(initial = "{name}_compressed")
    val showTechnicalBadges by prefsManager.showTechnicalBadgesFlow.collectAsState(initial = false)

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var conversionMode by remember { mutableStateOf(ConversionMode.PRINT) }

    LaunchedEffect(Unit) {
        val uris = SharedImportManager.consumeUris()
        if (uris.isNotEmpty()) {
            val uri = uris.first()
            val name = StorageManager.getFileNameFromUri(context, uri) ?: "office_doc_${System.currentTimeMillis()}"
            selectedUri = uri
            selectedName = name
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // غير حرج: بعض الموفرين لا يدعمون أخذ صلاحية دائمة
                    AppLogger.logSilentFailure("ConversionScreen", "فشل أخذ صلاحية دائمة على ملف المستند للتحويل", e)
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val name = StorageManager.getFileNameFromUri(context, uri) ?: "office_doc_${System.currentTimeMillis()}"
                        withContext(Dispatchers.Main) {
                            selectedUri = uri
                            selectedName = name
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("ConversionScreen", "فشل قراءة بيانات الملف المختار للتحويل", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("ConversionScreen", "فشل فتح الملف من المنتقي للتحويل", e)
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
            title = strings.convertSection,
            accent = CategoryConvertCyan,
            icon = Icons.Default.PictureInPicture,
            subtitle = strings.convertDesc,
            onBack = onNavigateBack
        )

        if (showTechnicalBadges) {
            ProcessorBadge(strings = strings, processorType = ProcessorType.SOFTWARE)
        }

        HintCard(text = strings.convertNotice, accent = CategoryConvertCyan)

        FormatSupportRow(formats = listOf("DOCX", "XLSX", "PPTX"))

        NumberedStep(step = 1, title = strings.selectFile, accent = CategoryConvertCyan) {
            FilePickZone(
                hasSelection = selectedUri != null,
                emptyTitle = strings.selectFile,
                emptySubtitle = "DOCX · XLSX · PPTX",
                selectedSummary = selectedName.ifBlank { null },
                accent = CategoryConvertCyan,
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "application/msword",
                            "application/vnd.ms-excel",
                            "application/vnd.ms-powerpoint"
                        )
                    )
                },
                testTag = "conversion_file_pick_zone"
            )
        }

        
        NumberedStep(step = 2, title = strings.conversionModeLabel, accent = CategoryConvertCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    ConversionMode.PRINT to (strings.conversionModePrintTitle to strings.conversionModePrintDesc),
                    ConversionMode.SCREENSHOT to (strings.conversionModeScreenshotTitle to strings.conversionModeScreenshotDesc)
                ).forEach { (mode, texts) ->
                    val selected = conversionMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) CategoryConvertCyan else GlassBorderWhite,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { conversionMode = mode }
                            .testTag("conversion_mode_${mode.name.lowercase()}"),
                        color = if (selected) CategoryConvertCyan.copy(alpha = 0.12f) else SurfaceCardLow,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // radio dot
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .border(2.dp, if (selected) CategoryConvertCyan else TextMuted, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(CategoryConvertCyan)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = texts.first,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = texts.second,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        NumberedStep(step = 3, title = strings.startProcessing, accent = CategoryConvertCyan) {

            PrimaryActionButton(
                text = strings.startProcessing,
                enabled = selectedUri != null,
                onClick = {
                    if (selectedUri == null) {
                        Toast.makeText(context, strings.selectFile, Toast.LENGTH_SHORT).show()
                    } else {
                        showSaveDialog = true
                    }
                },
                containerColor = CategoryConvertCyan,
                testTag = "start_conversion_button"
            )
        }

        if (showSaveDialog && selectedUri != null) {
            val uriToConvert = selectedUri ?: return@Column
            val nameToConvert = selectedName
            val rawBase = nameToConvert.substringBeforeLast(".")
            val defaultOutputName = if (namingPattern.contains("{name}")) namingPattern.replace("{name}", rawBase) else "${rawBase}_converted"

            SaveFileDialog(
                strings = strings,
                defaultName = defaultOutputName,
                extension = "pdf",
                onDismiss = { showSaveDialog = false },
                onSave = { customFileName ->
                    val finalOutputName = customFileName.ifBlank { defaultOutputName }
                    val params = com.example.data.model.TaskParams(conversionMode = conversionMode.name)
                    val task = ProcessingTask(
                        title = strings.convertSection,
                        subtitle = "$nameToConvert → $finalOutputName.pdf",
                        taskType = TaskType.CONVERSION,
                        sourceUris = listOf(uriToConvert),
                        outputFileName = finalOutputName,
                        outputExtension = "pdf",
                        processorType = ProcessorType.SOFTWARE,
                        paramsJson = params.toJson(),
                        executeBlock = { onProgress, _, _, _, _ ->
                            OfficeToPdfConverter.convertOfficeToPdf(
                                context = context,
                                uri = uriToConvert,
                                fileName = nameToConvert,
                                onProgress = onProgress,
                                strings = strings,
                                mode = conversionMode
                            )
                        }
                    )
                    TaskQueueManager.getInstance(context).addTask(task)
                    Toast.makeText(context, "${strings.statusProcessing} ($finalOutputName.pdf)", Toast.LENGTH_SHORT).show()
                    showSaveDialog = false
                    onNavigateBack()
                }
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
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
        AppLogger.logSilentFailure("ConversionScreen", "فشل استخراج اسم الملف من Uri: $uri", e)
    }
    return name
}
