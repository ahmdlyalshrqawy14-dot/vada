package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.model.ProcessorType
import com.example.data.model.TaskStatus
import com.example.data.queue.TaskQueueManager
import com.example.ui.components.ErrorLogDialog
import com.example.ui.components.ProcessorBadge
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusCancelRed
import com.example.ui.theme.StatusPauseAmber
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.SurfaceCardHigh
import com.example.ui.theme.SurfaceCardLow
import com.example.ui.theme.TextMuted

import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ScreenHeader
import com.example.ui.components.ConfirmType

@Composable
fun QueueScreen(
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val queueManager = remember { TaskQueueManager.getInstance(context) }
    val prefsManager = remember { com.example.data.prefs.PreferencesManager.getInstance(context) }

    val activeTasks by queueManager.activeTasks.collectAsState()
    val queuedList by queueManager.queueList.collectAsState()
    val showTechnicalBadges by prefsManager.showTechnicalBadgesFlow.collectAsState(initial = false)

    var showErrorDialogForMsg by remember { mutableStateOf<String?>(null) }
    var taskToCancelId by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Queue,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = strings.queueTab,
                color = CyanPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Active Tasks Section
        if (activeTasks.isNotEmpty()) {
            Text(
                text = "${strings.activeTaskHeader} (${activeTasks.size})",
                color = CyanPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            activeTasks.forEach { task ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCardHigh)
                        .border(1.dp, GlassBorderWhite, RoundedCornerShape(20.dp))
                        .testTag("active_task_card_${task.id}"),
                    color = SurfaceCardHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (showTechnicalBadges) {
                            ProcessorBadge(strings = strings, processorType = task.processorType)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = task.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = task.subtitle,
                            color = TextMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress percentage & bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusLabel = when {
                                task.status == TaskStatus.COMPLETED && task.compressionSkipped -> strings.videoCompressionSkippedShort
                                task.status == TaskStatus.RUNNING -> strings.statusProcessing
                                task.status == TaskStatus.PAUSED -> strings.statusPaused
                                task.status == TaskStatus.COMPLETED -> strings.statusSuccess
                                task.status == TaskStatus.FAILED -> strings.statusError
                                task.status == TaskStatus.CANCELLED -> strings.cancel
                                else -> strings.statusProcessing
                            }

                            val statusColor = when {
                                task.status == TaskStatus.COMPLETED && task.compressionSkipped -> StatusPauseAmber
                                task.status == TaskStatus.RUNNING -> CyanPrimary
                                task.status == TaskStatus.PAUSED -> StatusPauseAmber
                                task.status == TaskStatus.COMPLETED -> StatusStartGreen
                                task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELLED -> StatusCancelRed
                                else -> CyanPrimary
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedVisibility(
                                    visible = task.status == TaskStatus.COMPLETED,
                                    enter = scaleIn() + fadeIn(),
                                    exit = scaleOut() + fadeOut()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = strings.successDescription,
                                            tint = StatusStartGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                }
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "${(task.progress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyanPrimary,
                            trackColor = BackgroundDark
                        )

                        if (task.processorType == ProcessorType.SOFTWARE) {
                            Text(
                                text = strings.safeModeSwitchedNotice,
                                color = Color(0xFFFFA726), // Warning Orange color
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls Row (Pause/Resume, Cancel, Error Log)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PAUSED) {
                                Button(
                                    onClick = { queueManager.togglePauseActiveTask(task.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusPauseAmber),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_pause_button_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = if (task.status == TaskStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (task.status == TaskStatus.PAUSED) strings.statusProcessing else strings.statusPaused,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = { taskToCancelId = task.id },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusCancelRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cancel_active_task_button_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.cancel,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = strings.cancel, color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            if (task.status == TaskStatus.FAILED && task.errorMessage != null) {
                                OutlinedButton(
                                    onClick = { showErrorDialogForMsg = task.errorMessage },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = strings.copyErrorLog,
                                        tint = StatusCancelRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Queued Items
        if (queuedList.isNotEmpty()) {
            Text(
                text = "${strings.queueTab} (${queuedList.size})",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            queuedList.forEach { queuedTask ->
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
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = queuedTask.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = queuedTask.subtitle,
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { taskToCancelId = queuedTask.id }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.cancel,
                                tint = StatusCancelRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Empty Queue State
        if (activeTasks.isEmpty() && queuedList.isEmpty()) {
            EmptyStateView(
                title = strings.emptyQueue,
                body = strings.offlineDisclaimer,
                icon = Icons.Default.Queue
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    showErrorDialogForMsg?.let { errMsg ->
        ErrorLogDialog(
            strings = strings,
            errorMessage = errMsg,
            onDismiss = { showErrorDialogForMsg = null }
        )
    }

    taskToCancelId?.let { idPendingCancel ->
        ConfirmActionDialog(
            strings = strings,
            title = strings.confirmCancelTitle,
            body = strings.confirmCancelBody,
            type = ConfirmType.DESTRUCTIVE,
            onConfirm = {
                taskToCancelId = null
                queueManager.cancelTask(idPendingCancel)
            },
            onDismiss = {
                taskToCancelId = null
            }
        )
    }
}
