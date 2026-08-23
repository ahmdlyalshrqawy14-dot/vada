package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.i18n.StringsArabic
import com.example.data.util.AppLogger
import com.example.data.model.CompressionPreset
import com.example.data.model.ProcessingTask
import com.example.data.model.ProcessorType
import com.example.data.model.TaskType
import com.example.data.queue.TaskQueueManager
import com.example.data.util.StorageManager
import com.example.data.util.FileValidator
import com.example.data.audio.AudioCompressionWorker
import com.example.data.audio.AudioTranscoder
import com.example.data.model.CompressionOutcome
import com.example.ui.components.CompressionPresetSelector
import com.example.ui.components.CustomCompressionControls
import com.example.ui.components.CustomCompressionSettings
import com.example.ui.components.ProcessorBadge
import com.example.ui.components.SectionStepLabel
import com.example.ui.components.FormatSupportRow
import com.example.ui.components.ReorderableFileList
import com.example.ui.components.SaveFileDialog
import com.example.ui.components.SelectedFileItem
import com.example.ui.components.ScreenHeader
import com.example.ui.theme.CategoryAudioOrange
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.SurfaceCardLow
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
fun AudioScreen(
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

    var extractFromVideo by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<SelectedFileItem>() }
    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    var customSettings by remember { mutableStateOf(CustomCompressionSettings()) }
    var processorType by remember { mutableStateOf(ProcessorType.HARDWARE) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showClearSelectionConfirm by remember { mutableStateOf(false) }
    var pendingExtractToggleValue by remember { mutableStateOf(false) }
    var backgroundBatteryFriendly by remember { mutableStateOf(false) }
    var requireChargingForBackground by remember { mutableStateOf(false) }

    LaunchedEffect(lastPreset) {
        try {
            selectedPreset = CompressionPreset.valueOf(lastPreset)
        } catch (e: Exception) {
            // غير حرج: استخدام القيمة الافتراضية إذا فشل تحليل الإعداد المسبق
            AppLogger.logSilentFailure("AudioScreen", "فشل تحليل الإعداد المسبق المحفوظ: $lastPreset", e)
        }
    }

    LaunchedEffect(Unit) {
        val uris = SharedImportManager.consumeUris()
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                val name = StorageManager.getFileNameFromUri(context, uri) ?: "audio_${System.currentTimeMillis()}"
                SelectedFileItem(uri, name)
            }
            selectedFiles.clear()
            selectedFiles.addAll(items)
        }
    }

    // Multi Audio Picker Launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            try {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // غير حرج: بعض الموفرين لا يدعمون منح صلاحية دائمة
                        AppLogger.logSilentFailure("AudioScreen", "فشل أخذ صلاحية دائمة على ملف الصوت", e)
                    }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // مطابقة لسلوك VideoScreen: التحقق الفعلي (بايتات السحر/magic bytes + عدم
                        // كون الملف فارغاً/تالفاً) قبل قبول الملف، بدل قبول أي Uri بصمت كما كان
                        // يحدث سابقاً هنا فقط (بينما VideoScreen كان يطبّق هذا التحقق منذ البداية).
                        val validItems = mutableListOf<SelectedFileItem>()
                        val rejected = mutableListOf<String>()
                        var sawHighRiskFlacOnOldDevice = false
                        uris.forEach { uri ->
                            val validation = FileValidator.validateFile(context, uri)
                            val name = StorageManager.getFileNameFromUri(context, uri) ?: "audio_${System.currentTimeMillis()}"
                            if (validation.isValid) {
                                validItems.add(SelectedFileItem(uri, name))
                                // Only worth warning about on devices that actually lack the FLAC
                                // MediaCodec decoder (pre-Android 8.0 / API 27) - flagging it
                                // unconditionally on every device, including ones where FLAC works
                                // fine, would just be noise the user learns to ignore.
                                if (validation.detectedFormat == "FLAC Audio" &&
                                    android.os.Build.VERSION.SDK_INT < 27
                                ) {
                                    sawHighRiskFlacOnOldDevice = true
                                }
                            } else {
                                rejected.add(validation.errorMessage ?: strings.errorInvalidAudioFile)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (validItems.isNotEmpty()) {
                                selectedFiles.addAll(validItems)
                            }
                            if (rejected.isNotEmpty()) {
                                Toast.makeText(context, rejected.first(), Toast.LENGTH_SHORT).show()
                            } else if (sawHighRiskFlacOnOldDevice) {
                                Toast.makeText(context, strings.audioFlacCompatWarning, Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("AudioScreen", "فشل قراءة أو التحقق من ملفات الصوت", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("AudioScreen", "فشل فتح ملفات الصوت من المنتقي", e)
                Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Multi Video Picker Launcher for extraction
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            try {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // غير حرج: بعض الموفرين لا يدعمون منح صلاحية دائمة
                        AppLogger.logSilentFailure("AudioScreen", "فشل أخذ صلاحية دائمة على ملف الفيديو لاستخراج الصوت", e)
                    }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val validItems = mutableListOf<SelectedFileItem>()
                        val rejected = mutableListOf<String>()
                        uris.forEach { uri ->
                            val validation = FileValidator.validateFile(context, uri)
                            val name = StorageManager.getFileNameFromUri(context, uri) ?: "video_${System.currentTimeMillis()}"
                            if (validation.isValid) {
                                validItems.add(SelectedFileItem(uri, name))
                            } else {
                                rejected.add(validation.errorMessage ?: strings.errorInvalidVideoFile)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (validItems.isNotEmpty()) {
                                selectedFiles.addAll(validItems)
                                processorType = ProcessorType.HARDWARE
                            }
                            if (rejected.isNotEmpty()) {
                                Toast.makeText(context, rejected.first(), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("AudioScreen", "فشل قراءة أو التحقق من ملفات الفيديو", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("AudioScreen", "فشل فتح ملفات الفيديو من المنتقي", e)
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
            title = strings.audioSection,
            accent = CategoryAudioOrange,
            icon = Icons.Default.AudioFile,
            subtitle = strings.audioDesc,
            onBack = onNavigateBack
        )

        if (showTechnicalBadges) {
            ProcessorBadge(strings = strings, processorType = processorType)
        }

        SectionStepLabel(text = strings.audioStepSelectFiles, tint = CategoryAudioOrange)

        // Extract from video Checkbox
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCardLow)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(16.dp)),
            color = SurfaceCardLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = extractFromVideo,
                    onCheckedChange = { checked ->
                        if (selectedFiles.isNotEmpty()) {
                            // تجنّب مسح اختيار المستخدم الحالي بصمت: نطلب تأكيداً صريحاً أولاً.
                            pendingExtractToggleValue = checked
                            showClearSelectionConfirm = true
                        } else {
                            extractFromVideo = checked
                            processorType = if (checked) ProcessorType.HARDWARE else ProcessorType.SOFTWARE
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CategoryAudioOrange,
                        uncheckedColor = TextMuted
                    ),
                    modifier = Modifier.testTag("extract_video_audio_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.extractFromVideoLabel,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.extractFromVideoDesc,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Selected Files List (multi-selection supported)
        if (selectedFiles.isNotEmpty()) {
            Text(
                text = "${strings.selectFiles}: ${selectedFiles.size}",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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

        // Pick Files Button
        OutlinedButton(
            onClick = {
                if (extractFromVideo) {
                    videoPickerLauncher.launch("video/*")
                } else {
                    audioPickerLauncher.launch("audio/*")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("select_audio_button"),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CategoryAudioOrange)
        ) {
            Text(
                text = strings.selectFiles,
                color = CategoryAudioOrange,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        FormatSupportRow(
            formats = if (extractFromVideo) {
                listOf("MP4", "MOV", "MKV", "WebM", "AVI", "FLV", "TS")
            } else {
                listOf("MP3", "M4A", "AAC", "WAV", "FLAC", "OGG")
            },
            caveat = if (!extractFromVideo) strings.audioFlacCompatWarning else null
        )


        // Compression Preset Selector
        SectionStepLabel(text = strings.audioStepConfigureQuality, tint = CategoryAudioOrange)
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
                isAudioQuality = true,
                showMaxDimension = false,
                showVideoBitrate = false
            )
        }

        // Background / Battery-Friendly Scheduling
        SectionStepLabel(text = strings.audioStepAdditionalOptions, tint = CategoryAudioOrange)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCardLow)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(16.dp)),
            color = SurfaceCardLow
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = backgroundBatteryFriendly,
                        onCheckedChange = { backgroundBatteryFriendly = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CategoryAudioOrange,
                            uncheckedColor = TextMuted
                        ),
                        modifier = Modifier.testTag("audio_background_battery_friendly_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.audioBackgroundToggleLabel,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.audioBackgroundToggleDesc,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                if (backgroundBatteryFriendly) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 32.dp, top = 4.dp)
                    ) {
                        Checkbox(
                            checked = requireChargingForBackground,
                            onCheckedChange = { requireChargingForBackground = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CategoryAudioOrange,
                                uncheckedColor = TextMuted
                            ),
                            modifier = Modifier.testTag("audio_require_charging_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.audioRequireChargingLabel,
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Start Processing Button
        Button(
            onClick = {
                if (selectedFiles.isNotEmpty()) {
                    showSaveDialog = true
                } else {
                    Toast.makeText(context, strings.selectFile, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = selectedFiles.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_audio_processing_button"),
            colors = ButtonDefaults.buttonColors(containerColor = StatusStartGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (selectedFiles.size > 1) "${strings.startProcessing} (${selectedFiles.size})" else strings.startProcessing,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showSaveDialog && selectedFiles.isNotEmpty()) {
            val filesSnapshot = selectedFiles.toList()
            val isMultiple = filesSnapshot.size > 1
            val firstBase = filesSnapshot.first().name.substringBeforeLast(".")
            val defaultSuffix = if (extractFromVideo) "audio" else "compressed"
            val defaultName = if (isMultiple) {
                defaultSuffix
            } else if (extractFromVideo) {
                "${firstBase}_audio"
            } else {
                if (namingPattern.contains("{name}")) namingPattern.replace("{name}", firstBase) else "${firstBase}_compressed"
            }

            SaveFileDialog(
                strings = strings,
                defaultName = defaultName,
                extension = "m4a",
                fileCount = filesSnapshot.size,
                sampleBaseName = firstBase,
                onDismiss = { showSaveDialog = false },
                onSave = { input ->
                    val typed = input.ifBlank { defaultName }
                    val queueManager = TaskQueueManager.getInstance(context)
                    // مهمة منفصلة لكل ملف بدل مهمة واحدة تحمل قائمة uris
                    filesSnapshot.forEach { item ->
                        val itemBase = item.name.substringBeforeLast(".")
                        val outputName = if (isMultiple) "${itemBase}_$typed" else typed
                        val itemUri = item.uri

                        if (backgroundBatteryFriendly) {
                            // Battery-aware background path via WorkManager - same architecture
                            // as the video module's VideoCompressionWorker. Handles extraction-
                            // from-video and standalone-audio-compression identically since
                            // AudioTranscoder treats both the same way.
                            val safeJobKey = "audio_${itemUri.toString().hashCode()}_${outputName.hashCode()}"
                            AudioCompressionWorker.enqueue(
                                context = context,
                                jobKey = safeJobKey,
                                uri = itemUri,
                                outputName = outputName,
                                preset = selectedPreset,
                                customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                requireCharging = requireChargingForBackground
                            )
                        } else {
                            val params = com.example.data.model.TaskParams(
                                preset = selectedPreset.name,
                                quality = customSettings.quality,
                                isCustom = selectedPreset == CompressionPreset.CUSTOM,
                                extractFromVideo = extractFromVideo
                            )
                            val task = ProcessingTask(
                                title = strings.audioSection,
                                subtitle = "${item.name} → $outputName.m4a",
                                taskType = TaskType.AUDIO,
                                sourceUris = listOf(itemUri),
                                outputFileName = outputName,
                                outputExtension = "m4a",
                                processorType = processorType,
                                paramsJson = params.toJson(),
                                executeBlock = { onProgress, onProcessorChanged, onCompressionSkipped, onOutcomeEvaluated, _ ->
                                    processAudioFiles(
                                        context = context,
                                        files = listOf(itemUri),
                                        isExtraction = extractFromVideo,
                                        preset = selectedPreset,
                                        customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                        onProgress = onProgress,
                                        onProcessorChanged = onProcessorChanged,
                                        onCompressionSkipped = onCompressionSkipped,
                                        onOutcomeEvaluated = onOutcomeEvaluated,
                                        strings = strings
                                    )
                                }
                            )
                            queueManager.addTask(task)
                        }
                    }
                    Toast.makeText(
                        context,
                        "${strings.statusProcessing} (${filesSnapshot.size})",
                        Toast.LENGTH_SHORT
                    ).show()
                    showSaveDialog = false
                    onNavigateBack()
                }
            )
        }


        if (showClearSelectionConfirm) {
            ConfirmActionDialog(
                strings = strings,
                title = strings.confirmClearSelectionTitle,
                body = strings.confirmClearSelectionBody,
                type = ConfirmType.DESTRUCTIVE,
                onConfirm = {
                    extractFromVideo = pendingExtractToggleValue
                    selectedFiles.clear()
                    processorType = if (pendingExtractToggleValue) ProcessorType.HARDWARE else ProcessorType.SOFTWARE
                    showClearSelectionConfirm = false
                },
                onDismiss = { showClearSelectionConfirm = false }
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Orchestrates the tiered transcode strategy for a single audio job: compress at the requested
 * quality first, then fall back to a maximally-compatible "passthrough attempt" so the file is
 * never lost outright. Both [isExtraction] (pull audio out of a video) and standalone audio
 * compression go through the exact same [com.example.data.audio.AudioTranscoder] call now - the
 * old code had two separate hand-written functions here (`extractAudioTrackFromVideo` doing a
 * raw copy, `transcodeAudioToAac` doing a full re-encode) that had to be kept in sync by hand;
 * Transformer's `setRemoveVideo(true)` does the right thing for either source type on its own.
 */
private suspend fun processAudioFiles(
    context: Context,
    files: List<Uri>,
    isExtraction: Boolean,
    preset: CompressionPreset,
    customSettings: CustomCompressionSettings? = null,
    onProgress: suspend (Float) -> Unit,
    onProcessorChanged: (ProcessorType) -> Unit,
    onCompressionSkipped: (Boolean) -> Unit,
    onOutcomeEvaluated: (CompressionOutcome) -> Unit,
    strings: AppStrings = StringsArabic
): File {
    if (files.isEmpty()) {
        throw IllegalStateException(strings.errorAudioTranscodeFailed)
    }
    val uri = files.first()
    val tempOutput = StorageManager.createTempFile(context, "vada_aud_", "m4a")
    val origSize = StorageManager.getFileSizeFromUri(context, uri)

    // 1. Compress at the requested quality.
    try {
        onProcessorChanged(ProcessorType.HARDWARE)
        if (tempOutput.exists()) tempOutput.delete()
        val result = AudioTranscoder.transcodeSegment(
            context = context,
            uri = uri,
            outputFile = tempOutput,
            preset = preset,
            customSettings = customSettings,
            onProgress = onProgress
        )
        if (result.success && tempOutput.exists() && tempOutput.length() > 0) {
            val outcome = if (origSize > 0) {
                AudioTranscoder.evaluateCompressionResult(origSize, tempOutput.length(), wasPassthrough = false)
            } else {
                CompressionOutcome.SUCCESS
            }
            onOutcomeEvaluated(outcome)
            onCompressionSkipped(outcome == CompressionOutcome.NO_COMPRESSION && origSize > 0)
            return tempOutput
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (tempOutput.exists()) tempOutput.delete()
        throw e
    } catch (e: Exception) {
        val errorContext = if (isExtraction) "فشل استخراج/تحويل المسار الصوتي من الفيديو" else "فشل تحويل ترميز الصوت إلى AAC"
        AppLogger.logError("AudioScreen", errorContext, e)
    }

    // 2. Compression failed outright -> ask Transformer for the most permissive possible
    // re-encode as a last-resort rescue, so the audio itself isn't lost.
    try {
        onProcessorChanged(ProcessorType.SOFTWARE)
        if (tempOutput.exists()) tempOutput.delete()
        val passthroughResult = AudioTranscoder.transcodeSegment(
            context = context,
            uri = uri,
            outputFile = tempOutput,
            preset = preset,
            customSettings = customSettings,
            onProgress = onProgress,
            forcePassthroughAttempt = true
        )
        if (passthroughResult.success && tempOutput.exists() && tempOutput.length() > 0) {
            AppLogger.logSilentFailure(
                "AudioScreen",
                "Audio compression could not complete at requested quality; preserved via best-effort passthrough re-encode",
                IllegalStateException("compression unavailable for this input")
            )
            val outcome = AudioTranscoder.evaluateCompressionResult(origSize, tempOutput.length(), wasPassthrough = true)
            onOutcomeEvaluated(outcome)
            onCompressionSkipped(true)
            return tempOutput
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (tempOutput.exists()) tempOutput.delete()
        throw e
    } catch (e: Exception) {
        AppLogger.logError("AudioScreen", "Passthrough re-encode attempt failed", e)
    }

    if (tempOutput.exists()) tempOutput.delete()
    throw IllegalStateException(if (isExtraction) strings.errorAudioExtractFailed else strings.errorAudioTranscodeFailed)
}
