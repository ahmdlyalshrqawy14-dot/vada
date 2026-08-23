package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.data.util.AppLogger
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
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
import com.example.data.db.HistoryEntity
import com.example.data.db.HistoryRepository
import com.example.data.i18n.AppStrings
import com.example.data.util.StorageManager
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.ConfirmType
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusCancelRed
import com.example.ui.theme.StatusPauseAmber
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.SurfaceCardHigh
import com.example.ui.theme.SurfaceCardLow
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.io.File

import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton

import androidx.compose.material.icons.filled.Edit

@Composable
fun HistoryScreen(
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { HistoryRepository(AppDatabase.getInstance(context).historyDao()) }
    val historyList by repository.allHistory.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") } // ALL, VIDEO, AUDIO, IMAGE, DOC
    var showHwSwInfoDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<HistoryEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var isInitialLoading by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(350)
        isInitialLoading = false
    }

    val filteredList = remember(historyList, searchQuery, selectedCategory) {
        historyList.filter { item ->
            val matchesQuery = searchQuery.isBlank() || item.fileName.contains(searchQuery, ignoreCase = true) || item.operationName.contains(searchQuery, ignoreCase = true)
            val matchesCat = when (selectedCategory) {
                "VIDEO" -> item.fileType == "VIDEO" || item.operationName.contains("فيديو", true) || item.operationName.contains("video", true)
                "AUDIO" -> item.fileType == "AUDIO" || item.operationName.contains("صوت", true) || item.operationName.contains("audio", true)
                "IMAGE" -> item.fileType == "IMAGE" || item.operationName.contains("صور", true) || item.operationName.contains("image", true)
                "DOC" -> item.fileType == "DOCUMENT" || item.operationName.contains("مستند", true) || item.operationName.contains("pdf", true) || item.operationName.contains("doc", true)
                else -> true
            }
            matchesQuery && matchesCat
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Title Row with Clear All button & HW/SW Info button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.historyTab,
                    color = CyanPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showHwSwInfoDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = strings.hwSwInfoDescription,
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (historyList.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = strings.clearHistory,
                            tint = StatusCancelRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Search Bar
        if (historyList.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchInHistory, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanPrimary) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = GlassBorderWhite,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SurfaceCardHigh,
                    unfocusedContainerColor = SurfaceCardHigh
                )
            )

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = listOf(
                    "ALL" to strings.filterAll,
                    "VIDEO" to strings.filterVideo,
                    "AUDIO" to strings.filterAudio,
                    "IMAGE" to strings.filterImage,
                    "DOC" to strings.filterDocument
                )
                categories.forEach { (catKey, catName) ->
                    FilterChip(
                        selected = selectedCategory == catKey,
                        onClick = { selectedCategory = catKey },
                        label = { Text(catName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceCardHigh,
                            labelColor = Color.White
                        )
                    )
                }
            }
        }

        // History Entries
        if (isInitialLoading) {
            repeat(3) {
                SkeletonItemCard()
            }
        } else if (filteredList.isNotEmpty()) {
            filteredList.forEach { item ->
                HistoryItemCard(
                    strings = strings,
                    item = item,
                    onOpen = { openFileIntent(context, item.outputPath, strings) },
                    onShare = { shareFileIntent(context, item.outputPath, strings) },
                    onRename = {
                        itemToRename = item
                        renameInputText = item.fileName.substringBeforeLast(".")
                    },
                    onDelete = {
                        scope.launch { repository.delete(item) }
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Color(0xFF303A48),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (historyList.isEmpty()) strings.emptyHistory else strings.noSearchResults,
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showHwSwInfoDialog) {
        AlertDialog(
            onDismissRequest = { showHwSwInfoDialog = false },
            title = {
                Text(
                    text = strings.hwSwInfoTitle,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.hwSwInfoHardwareBody,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        text = strings.hwSwInfoSoftwareBody,
                        color = Color(0xFFA0AAB5),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHwSwInfoDialog = false }) {
                    Text(strings.confirm, color = CyanPrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131822)
        )
    }

    if (showClearConfirm) {
        ConfirmActionDialog(
            strings = strings,
            title = strings.clearHistory,
            body = strings.clearHistoryConfirm,
            type = ConfirmType.DESTRUCTIVE,
            onConfirm = {
                showClearConfirm = false
                scope.launch { repository.clearAll() }
            },
            onDismiss = { showClearConfirm = false }
        )
    }

            if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = {
                Text(
                    text = strings.renameFile,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.newFileNamePrompt,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color(0xFF303A48),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentItem = itemToRename
                        if (currentItem != null && renameInputText.isNotBlank()) {
                            scope.launch {
                                try {
                                    if (currentItem.outputPath.startsWith("content://")) {
                                        val uri = Uri.parse(currentItem.outputPath)
                                        val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                                        var finalName = renameInputText.trim()
                                        val ext = currentItem.fileName.substringAfterLast(".", "")
                                        if (ext.isNotEmpty() && !finalName.endsWith(".$ext", ignoreCase = true)) {
                                            finalName = "$finalName.$ext"
                                        }
                                        val renamed = docFile?.renameTo(finalName) ?: false
                                        if (renamed) {
                                            repository.insert(currentItem.copy(fileName = finalName, outputPath = (docFile?.uri ?: uri).toString()))
                                            Toast.makeText(context, strings.fileRenamedSuccess, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, strings.fileRenameError, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val oldFile = File(currentItem.outputPath)
                                        val ext = oldFile.extension
                                        var finalName = renameInputText.trim()
                                        if (ext.isNotEmpty() && !finalName.endsWith(".$ext", ignoreCase = true)) {
                                            finalName = "$finalName.$ext"
                                        }
                                        val newFile = File(oldFile.parentFile ?: context.filesDir, finalName)
                                        val renamed = if (oldFile.exists()) oldFile.renameTo(newFile) else true
                                        if (renamed) {
                                            repository.insert(currentItem.copy(fileName = finalName, outputPath = newFile.absolutePath))
                                            Toast.makeText(context, strings.fileRenamedSuccess, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, strings.fileRenameError, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.localizedMessage ?: strings.fileRenameError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        itemToRename = null
                    }
                ) {
                    Text(strings.confirm, color = CyanPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = Color(0xFF131822)
        )
    }
}

@Composable
fun HistoryItemCard(
    strings: AppStrings,
    item: HistoryEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCardHigh)
            .border(1.dp, GlassBorderWhite, RoundedCornerShape(18.dp))
            .testTag("history_item_${item.id}"),
        color = SurfaceCardHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val isCompleted = item.status == "COMPLETED"
                val isSkipped = item.status == "COMPLETED_WITHOUT_COMPRESSION"
                val statusText = when (item.compressionOutcome) {
                    "SUCCESS" -> strings.compressionOutcomeSuccess
                    "MARGINAL" -> strings.compressionOutcomeMarginal
                    "NO_COMPRESSION" -> strings.compressionOutcomeNone
                    else -> when {
                        isSkipped -> strings.compressionOutcomeNone
                        isCompleted -> strings.statusSuccess
                        else -> strings.statusError
                    }
                }
                val statusColor = when (item.compressionOutcome) {
                    "SUCCESS" -> StatusStartGreen
                    "MARGINAL" -> StatusPauseAmber
                    "NO_COMPRESSION" -> StatusCancelRed
                    else -> when {
                        isSkipped -> StatusCancelRed
                        isCompleted -> StatusStartGreen
                        else -> StatusCancelRed
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${item.operationName} • ${item.processorType}",
                color = TextMuted,
                fontSize = 12.sp
            )

            if (item.originalSizeBytes > 0 && item.processedSizeBytes > 0) {
                val origStr = StorageManager.formatFileSize(item.originalSizeBytes)
                val procStr = StorageManager.formatFileSize(item.processedSizeBytes)
                val savedPercent = (((item.originalSizeBytes - item.processedSizeBytes).toDouble() / item.originalSizeBytes.toDouble()) * 100).toInt()

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$origStr → $procStr",
                        color = CyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (savedPercent > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CyanPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "↓ $savedPercent%",
                                color = CyanPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Open, Share, Delete Buttons
            val isFileAvailable = remember(item.outputPath) {
                if (item.outputPath.isEmpty()) {
                    false
                } else if (item.outputPath.startsWith("content://")) {
                    true
                } else {
                    File(item.outputPath).exists()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFileAvailable) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = strings.openFile,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.shareFile,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = strings.renameFile,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = strings.delete,
                        tint = StatusCancelRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun openFileIntent(context: Context, path: String, strings: AppStrings) {
    try {
        val uri: Uri
        val mimeType: String
        if (path.startsWith("content://")) {
            uri = Uri.parse(path)
            val ext = path.substringAfterLast(".", "").substringBefore("?")
            mimeType = context.contentResolver.getType(uri) ?: StorageManager.getMimeType(ext)
        } else {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, strings.errorFileDoesNotExist, Toast.LENGTH_SHORT).show()
                return
            }
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            mimeType = StorageManager.getMimeType(file.extension)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, e.localizedMessage ?: strings.errorCannotOpenFile, Toast.LENGTH_SHORT).show()
    }
}

private fun shareFileIntent(context: Context, path: String, strings: AppStrings) {
    try {
        val uri: Uri
        val mimeType: String
        if (path.startsWith("content://")) {
            uri = Uri.parse(path)
            val ext = path.substringAfterLast(".", "").substringBefore("?")
            mimeType = context.contentResolver.getType(uri) ?: StorageManager.getMimeType(ext)
        } else {
            val file = File(path)
            if (!file.exists()) return
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            mimeType = StorageManager.getMimeType(file.extension)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via ${strings.appName}"))
    } catch (e: Exception) {
        AppLogger.logError("HistoryScreen", "فشل مشاركة الملف: $path", e)
        Toast.makeText(context, strings.shareOpenAppFailed, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SkeletonItemCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "SkeletonPulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCardLow)
            .border(1.dp, GlassBorderWhite.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
        color = SurfaceCardLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .alpha(alphaAnim),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .fillMaxWidth(0.35f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )
            }
        }
    }
}
