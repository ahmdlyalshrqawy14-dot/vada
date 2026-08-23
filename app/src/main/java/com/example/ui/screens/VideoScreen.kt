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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.i18n.StringsArabic
import com.example.data.util.AppLogger
import com.example.data.model.CompressionOutcome
import com.example.data.model.CompressionPreset
import com.example.data.model.ProcessingTask
import com.example.data.model.ProcessorType
import com.example.data.model.TaskType
import com.example.data.queue.DynamicIslandState
import com.example.data.queue.TaskQueueManager
import com.example.data.util.FileValidator
import com.example.data.util.StorageManager
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
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.SurfaceCardHigh
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
import com.example.data.video.VideoCompressionWorker
import com.example.data.video.VideoProcessor
import com.example.data.model.TaskParams

@Composable
fun VideoScreen(
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

    val selectedFiles = remember { mutableStateListOf<SelectedFileItem>() }
    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    var customSettings by remember { mutableStateOf(CustomCompressionSettings(quality = 65, maxDimension = 1080, videoBitrateKbps = 2000)) }
    var muteAudio by remember { mutableStateOf(false) }
    var rotateDegrees by remember { mutableStateOf(0) }
    var trimStartFraction by remember { mutableStateOf(0f) }
    var trimEndFraction by remember { mutableStateOf(1f) }
    var backgroundBatteryFriendly by remember { mutableStateOf(false) }
    var requireChargingForBackground by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val islandState by TaskQueueManager.getInstance(context).islandState.collectAsState()
    val activeTasks by TaskQueueManager.getInstance(context).activeTasks.collectAsState()

    val isVideoProcessing = (islandState as? DynamicIslandState.Processing) != null &&
            activeTasks.any { it.taskType == TaskType.VIDEO }

    val currentProcessorType = if (isVideoProcessing) {
        (islandState as DynamicIslandState.Processing).processorType
    } else {
        ProcessorType.HARDWARE
    }

    val currentBadgeText = if (isVideoProcessing) {
        if (currentProcessorType == ProcessorType.HARDWARE) {
            strings.videoMethodRealEncoding
        } else {
            strings.videoMethodPassthrough
        }
    } else {
        strings.videoFullReencodingNotice
    }

    LaunchedEffect(lastPreset) {
        try {
            selectedPreset = CompressionPreset.valueOf(lastPreset)
        } catch (e: Exception) {
            // غير حرج: استخدام القيمة الافتراضية إذا كانت القيمة السابقة المحفوظة غير صالحة
            AppLogger.logSilentFailure("VideoScreen", "فشل تحليل الإعداد المسبق المحفوظ: $lastPreset", e)
        }
    }

    LaunchedEffect(Unit) {
        val uris = SharedImportManager.consumeUris()
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                val name = StorageManager.getFileNameFromUri(context, uri) ?: "video_${System.currentTimeMillis()}"
                SelectedFileItem(uri, name)
            }
            selectedFiles.clear()
            selectedFiles.addAll(items)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            try {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // غير حرج: بعض الموفرين لا يدعمون منح صلاحية دائمة عبر URI
                        AppLogger.logSilentFailure("VideoScreen", "فشل أخذ صلاحية دائمة على الملف", e)
                    }
                }

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val validItems = mutableListOf<SelectedFileItem>()
                        val rejected = mutableListOf<String>()
                        var sawHighRiskContainer = false
                        uris.forEach { uri ->
                            val validation = FileValidator.validateFile(context, uri)
                            val name = StorageManager.getFileNameFromUri(context, uri) ?: "video_${System.currentTimeMillis()}"
                            if (validation.isValid) {
                                validItems.add(SelectedFileItem(uri, name))
                                // isHighRiskContainer was computed by FileValidator (AVI/MKV/WEBM -
                                // MediaExtractor support for these varies across OEM builds) but was
                                // never actually read anywhere in the app until now - the warning it
                                // was clearly meant to drive never reached the user.
                                if (validation.isHighRiskContainer) sawHighRiskContainer = true
                            } else {
                                rejected.add(validation.errorMessage ?: strings.errorInvalidVideoFile)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (validItems.isNotEmpty()) {
                                selectedFiles.addAll(validItems)
                            }
                            if (rejected.isNotEmpty()) {
                                Toast.makeText(context, rejected.first(), Toast.LENGTH_SHORT).show()
                            } else if (sawHighRiskContainer) {
                                Toast.makeText(context, strings.videoHighRiskContainerWarning, Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("VideoScreen", "فشل التحقق من ملفات الفيديو المختارة", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.logError("VideoScreen", "فشل فتح ملفات الفيديو من المنتقي", e)
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
            title = strings.videoSection,
            accent = CategoryVideoPurple,
            icon = Icons.Default.Videocam,
            subtitle = strings.videoDesc,
            onBack = onNavigateBack
        )

        if (showTechnicalBadges) {
            ProcessorBadge(
                strings = strings,
                processorType = currentProcessorType,
                customText = currentBadgeText
            )
        }

        SectionStepLabel(text = strings.audioStepSelectFiles, tint = CategoryVideoPurple)

        // File Selection Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = SurfaceCardHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (selectedFiles.isNotEmpty()) {
                    Text(
                        text = "${strings.selectFiles}: ${selectedFiles.size}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
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
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedButton(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("select_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CategoryVideoPurple)
                ) {
                    Text(
                        text = strings.selectFiles,
                        color = CategoryVideoPurple,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Supported-format hint: previously there was no indication at all of what the
                // picker actually accepts, so a rejected file (or one silently preserved
                // unchanged via passthrough remux) looked like a bug rather than a known format
                // limitation. AVI/MKV/WEBM get an extra persistent caveat now too - FileValidator
                // already computed isHighRiskContainer for exactly these containers, but nothing
                // in the UI ever surfaced it before this pass.
                FormatSupportRow(
                    formats = listOf("MP4", "MOV", "MKV", "WebM", "AVI", "FLV", "TS"),
                    caveat = strings.videoHighRiskContainerWarning
                )
            }
        }

        // Compression Preset Selector
        SectionStepLabel(text = strings.audioStepConfigureQuality, tint = CategoryVideoPurple)
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
                showMaxDimension = true,
                showVideoBitrate = true
            )
        }

        SectionStepLabel(text = strings.videoStepTrimRotate, tint = CategoryVideoPurple)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCardLow)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(16.dp)),
            color = SurfaceCardLow
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(strings.rotateLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 90, 180, 270).forEach { deg ->
                        val selected = rotateDegrees == deg
                        OutlinedButton(
                            onClick = { rotateDegrees = deg },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (selected) CategoryVideoPurple else GlassBorderWhite
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) CategoryVideoPurple.copy(alpha = 0.25f) else Color.Transparent
                            )
                        ) {
                            Text("${deg}°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(strings.trimLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${strings.trimStart}: ${(trimStartFraction * 100).toInt()}%  •  ${strings.trimEnd}: ${(trimEndFraction * 100).toInt()}%",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                androidx.compose.material3.Slider(
                    value = trimStartFraction,
                    onValueChange = { trimStartFraction = it.coerceIn(0f, (trimEndFraction - 0.02f).coerceAtLeast(0f)) },
                    modifier = Modifier.fillMaxWidth().testTag("trim_start_slider")
                )
                androidx.compose.material3.Slider(
                    value = trimEndFraction,
                    onValueChange = { trimEndFraction = it.coerceIn((trimStartFraction + 0.02f).coerceAtMost(1f), 1f) },
                    modifier = Modifier.fillMaxWidth().testTag("trim_end_slider")
                )
            }
        }

        // Mute Audio Option
        SectionStepLabel(text = strings.audioStepAdditionalOptions, tint = CategoryVideoPurple)
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
                    checked = muteAudio,
                    onCheckedChange = {
                        muteAudio = it
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CategoryVideoPurple,
                        uncheckedColor = TextMuted
                    ),
                    modifier = Modifier.testTag("mute_audio_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.muteAudioLabel,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.muteAudioDesc,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Background / Battery-Friendly Scheduling
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
                            checkedColor = CategoryVideoPurple,
                            uncheckedColor = TextMuted
                        ),
                        modifier = Modifier.testTag("background_battery_friendly_checkbox")
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
                                checkedColor = CategoryVideoPurple,
                                uncheckedColor = TextMuted
                            ),
                            modifier = Modifier.testTag("require_charging_checkbox")
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
                    Toast.makeText(context, strings.selectFiles, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = selectedFiles.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_video_processing_button"),
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
            val defaultOutputName = if (isMultiple) {
                "compressed"
            } else if (namingPattern.contains("{name}")) {
                namingPattern.replace("{name}", firstBase)
            } else {
                "${firstBase}_compressed"
            }
            SaveFileDialog(
                strings = strings,
                defaultName = defaultOutputName,
                extension = "mp4",
                fileCount = filesSnapshot.size,
                sampleBaseName = firstBase,
                onDismiss = { showSaveDialog = false },
                onSave = { input ->
                    val typed = input.ifBlank { defaultOutputName }
                    val queueManager = TaskQueueManager.getInstance(context)
                    // مهمة منفصلة لكل فيديو مختار
                    filesSnapshot.forEach { item ->
                        val itemBase = item.name.substringBeforeLast(".")
                        val outputName = if (isMultiple) "${itemBase}_$typed" else typed
                        val itemUri = item.uri

                        if (backgroundBatteryFriendly) {
                            val safeJobKey = "video_${itemUri.toString().hashCode()}_${outputName.hashCode()}"
                            VideoCompressionWorker.enqueue(
                                context = context,
                                jobKey = safeJobKey,
                                uri = itemUri,
                                outputName = outputName,
                                preset = selectedPreset,
                                customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                muteAudio = muteAudio,
                                requireCharging = requireChargingForBackground
                            )
                        } else {
                            val durationMs = VideoProcessor.probeDurationMs(context, itemUri)
                            val trimStartMs = (durationMs * trimStartFraction).toLong()
                            val trimEndMs = if (trimEndFraction >= 0.99f) -1L else (durationMs * trimEndFraction).toLong()
                            val params = TaskParams(
                                preset = selectedPreset.name,
                                muteAudio = muteAudio,
                                quality = customSettings.quality,
                                maxDimension = customSettings.maxDimension,
                                videoBitrateKbps = customSettings.videoBitrateKbps,
                                rotateDegrees = rotateDegrees,
                                trimStartMs = trimStartMs,
                                trimEndMs = trimEndMs,
                                isCustom = selectedPreset == CompressionPreset.CUSTOM
                            )
                            val task = ProcessingTask(
                                title = strings.videoSection,
                                subtitle = "${item.name} → $outputName.mp4",
                                taskType = TaskType.VIDEO,
                                sourceUris = listOf(itemUri),
                                outputFileName = outputName,
                                outputExtension = "mp4",
                                processorType = ProcessorType.HARDWARE,
                                paramsJson = params.toJson(),
                                executeBlock = { onProgress, onProcessorChanged, onCompressionSkipped, onOutcomeEvaluated, shouldPause ->
                                    VideoProcessor.process(
                                        context = context,
                                        uri = itemUri,
                                        preset = selectedPreset,
                                        customSettings = if (selectedPreset == CompressionPreset.CUSTOM) customSettings else null,
                                        muteAudio = muteAudio,
                                        rotateDegrees = rotateDegrees,
                                        trimStartMs = trimStartMs,
                                        trimEndMs = trimEndMs,
                                        onProgress = onProgress,
                                        onProcessorChanged = onProcessorChanged,
                                        onCompressionSkipped = onCompressionSkipped,
                                        onOutcomeEvaluated = onOutcomeEvaluated,
                                        shouldPause = shouldPause,
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


        Spacer(modifier = Modifier.height(80.dp))
    }
}
