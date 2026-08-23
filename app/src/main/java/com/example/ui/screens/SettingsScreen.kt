package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.data.util.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.db.AppDatabase
import com.example.data.db.HistoryRepository
import com.example.data.i18n.AppStrings
import com.example.data.prefs.PreferencesManager
import com.example.data.util.StorageManager
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.ConfirmType
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusPauseAmber
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.SurfaceCardHigh
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val historyRepo = remember { HistoryRepository(AppDatabase.getInstance(context).historyDao()) }

    val currentLang by prefsManager.languageCode.collectAsState(initial = "ar")
    val isDarkTheme by prefsManager.darkTheme.collectAsState(initial = true)
    val customSafUri by prefsManager.safStorageUri.collectAsState(initial = "")
    val namingPattern by prefsManager.namingPatternFlow.collectAsState(initial = "{name}_compressed")
    var customPatternInput by remember { mutableStateOf("") }
    LaunchedEffect(namingPattern) {
        if (customPatternInput.isBlank() &&
            namingPattern !in listOf("{name}_compressed", "{name}_vada", "vada_{name}")
        ) {
            customPatternInput = namingPattern
        }
    }
    val notificationsEnabled by prefsManager.notificationsEnabledFlow.collectAsState(initial = true)
    val showTechnicalBadges by prefsManager.showTechnicalBadgesFlow.collectAsState(initial = false)

    val historyItems by historyRepo.allHistory.collectAsState(initial = emptyList())
    val totalSavedBytes = remember(historyItems) {
        historyItems.sumOf { (it.originalSizeBytes - it.processedSizeBytes).coerceAtLeast(0) }
    }

    var pendingLangCode by remember { mutableStateOf<String?>(null) }
    var showCleanTempConfirm by remember { mutableStateOf(false) }

    val safFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                scope.launch {
                    prefsManager.setSafStorageUri(uri.toString())
                }
            } catch (e: Exception) {
                Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Screen Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = strings.settingsTab,
                color = CyanPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ================= SECTION 1: GENERAL (عام) =================
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.generalSettings,
                color = CyanPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Language Selector Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = SurfaceCardHigh
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.languageSetting,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("lang_ar_radio")
                    ) {
                        RadioButton(
                            selected = currentLang == "ar",
                            onClick = { if (currentLang != "ar") pendingLangCode = "ar" },
                            colors = RadioButtonDefaults.colors(selectedColor = CyanPrimary)
                        )
                        Text(
                            text = strings.arabic,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("lang_en_radio")
                    ) {
                        RadioButton(
                            selected = currentLang == "en",
                            onClick = { if (currentLang != "en") pendingLangCode = "en" },
                            colors = RadioButtonDefaults.colors(selectedColor = CyanPrimary)
                        )
                        Text(
                            text = strings.english,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Dark Theme Switch Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = SurfaceCardHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.themeSetting,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { scope.launch { prefsManager.setDarkTheme(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = CyanPrimary
                    ),
                    modifier = Modifier.testTag("theme_switch")
                )
            }
        }

        // ================= SECTION 2: STORAGE & NAMING (التخزين والتسمية) =================
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = Color(0xFFA855F7), // Purple accent
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.storageAndNaming,
                color = Color(0xFFA855F7),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Cumulative Saved Space Stat Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(StatusStartGreen.copy(alpha = 0.12f))
                .border(1.dp, StatusStartGreen.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = StatusStartGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.cumulativeSpaceSaved,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StorageManager.formatFileSize(totalSavedBytes),
                            color = StatusStartGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Storage Location Selector
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = SurfaceCardHigh
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.storagePathSetting,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val displayPath = if (customSafUri.isNotEmpty()) customSafUri else "Downloads/Vada"
                Text(
                    text = displayPath,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { safFolderPickerLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("change_storage_path_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary)
                ) {
                    Text(
                        text = strings.changePath,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Auto Naming Pattern Selector Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = SurfaceCardHigh
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.autoNamingPattern,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val patterns = listOf("{name}_compressed", "{name}_vada", "vada_{name}")
                val isCustomPattern = namingPattern.isNotBlank() && namingPattern !in patterns
                patterns.forEach { pat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = namingPattern == pat,
                            onClick = { scope.launch { prefsManager.setNamingPattern(pat) } },
                            colors = RadioButtonDefaults.colors(selectedColor = CyanPrimary)
                        )
                        Text(
                            text = pat,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Custom user-defined naming pattern
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    RadioButton(
                        selected = isCustomPattern,
                        onClick = {
                            val pattern = customPatternInput.ifBlank { "{name}_custom" }
                            scope.launch { prefsManager.setNamingPattern(pattern) }
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = CyanPrimary)
                    )
                    Text(
                        text = strings.customNamingPatternLabel,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = customPatternInput,
                    onValueChange = { customPatternInput = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = strings.customNamingPatternHint,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = GlassBorderWhite,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = CyanPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_naming_pattern_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val pattern = customPatternInput.trim()
                        if (pattern.isNotEmpty()) {
                            scope.launch { prefsManager.setNamingPattern(pattern) }
                            Toast.makeText(context, pattern, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = customPatternInput.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_custom_naming_pattern_button")
                ) {
                    Text(
                        text = strings.customNamingPatternSave,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ================= SECTION 3: NOTIFICATIONS & PERMISSIONS (الأذونات والإشعارات) =================
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = StatusPauseAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.notificationsAndPermissions,
                color = StatusPauseAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Notification Toggle & System Settings Option
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp)),
            color = SurfaceCardHigh
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.processingNotifications,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { scope.launch { prefsManager.setNotificationsEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, strings.statusError, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.openAppSettingsDesc,
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Battery Optimization Notice Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(StatusPauseAmber.copy(alpha = 0.12f))
                .border(1.dp, StatusPauseAmber.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = StatusPauseAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.batteryOptTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = strings.batteryOptDesc,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            // Prefer the system battery-optimization list (Play-policy friendly).
                            // Fall back to the direct request intent, then to app details.
                            val candidates = listOf(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                            var launched = false
                            for (intent in candidates) {
                                try {
                                    context.startActivity(intent)
                                    launched = true
                                    break
                                } catch (_: Exception) {
                                }
                            }
                            if (!launched) {
                                Toast.makeText(context, strings.errorBatterySettingsUnavailable, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            AppLogger.logSilentFailure("SettingsScreen", "تعذر فتح إعدادات تجاهل تحسين البطارية", e)
                            Toast.makeText(context, strings.errorBatterySettingsUnavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusPauseAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("open_battery_settings_button")
                ) {
                    Text(
                        text = strings.batteryOptButton,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ================= SECTION 4: ADVANCED & MAINTENANCE (متقدم وصيانة) =================
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = null,
                tint = Color(0xFFF97316), // Orange accent
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.advancedAndMaintenance,
                color = Color(0xFFF97316),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Show Technical Badges Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCardHigh)
                .border(1.dp, GlassBorderWhite, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.showTechnicalBadges,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.showTechnicalBadgesDesc,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = showTechnicalBadges,
                onCheckedChange = { enabled ->
                    scope.launch { prefsManager.setShowTechnicalBadges(enabled) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyanPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = TextMuted.copy(alpha = 0.4f)
                )
            )
        }

        // Clear Temp Files Button
        OutlinedButton(
            onClick = { showCleanTempConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("clean_temp_files_button"),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BackgroundDark)
        ) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.cleanTempFiles,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Footer App Metadata
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vada — v2.0.0",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.offlineDisclaimer,
                color = CyanPrimary.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Confirmation Dialog for Language Change
    pendingLangCode?.let { langCode ->
        ConfirmActionDialog(
            strings = strings,
            title = strings.confirmLanguageChangeTitle,
            body = strings.confirmLanguageChangeBody,
            type = ConfirmType.NORMAL,
            onConfirm = {
                pendingLangCode = null
                scope.launch { prefsManager.setLanguageCode(langCode) }
            },
            onDismiss = {
                pendingLangCode = null
            }
        )
    }

    // Confirmation Dialog for Clean Temp Files
    if (showCleanTempConfirm) {
        ConfirmActionDialog(
            strings = strings,
            title = strings.cleanTempFiles,
            body = strings.cleanTempFilesDesc,
            type = ConfirmType.DESTRUCTIVE,
            onConfirm = {
                showCleanTempConfirm = false
                StorageManager.cleanTempFiles(context)
                Toast.makeText(context, strings.cleanTempSuccess, Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCleanTempConfirm = false
            }
        )
    }
}
