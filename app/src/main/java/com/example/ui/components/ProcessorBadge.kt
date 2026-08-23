package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.model.ProcessorType
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.TextMuted

@Composable
fun ProcessorBadge(
    strings: AppStrings,
    processorType: ProcessorType,
    modifier: Modifier = Modifier,
    customText: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    val isHardware = processorType == ProcessorType.HARDWARE
    val badgeColor = if (isHardware) StatusStartGreen else CategoryVideoPurple
    val badgeIcon = if (isHardware) Icons.Default.FlashOn else Icons.Default.Build
    val badgeText = customText ?: if (isHardware) strings.hwProcessorName else strings.swProcessorName

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.10f))
            .border(1.dp, badgeColor.copy(alpha = 0.30f), CircleShape)
            .clickable { showDialog = true }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("processor_badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = badgeIcon,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = badgeText,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = strings.processorDialogTitle,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(11.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.processorDialogTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = strings.processorDialogBody,
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = strings.ok, color = CyanPrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131822),
            titleContentColor = Color.White,
            textContentColor = TextMuted
        )
    }
}
