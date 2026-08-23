package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusCancelRed
import com.example.ui.theme.StatusSaveBlue
import com.example.ui.theme.StatusStartGreen
import com.example.ui.theme.TextMuted

enum class ConfirmType {
    START, DESTRUCTIVE, NORMAL
}

@Composable
fun ConfirmActionDialog(
    strings: AppStrings,
    title: String,
    body: String,
    type: ConfirmType = ConfirmType.NORMAL,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = when (type) {
        ConfirmType.START -> StatusStartGreen
        ConfirmType.DESTRUCTIVE -> StatusCancelRed
        ConfirmType.NORMAL -> CyanPrimary
    }

    val icon = when (type) {
        ConfirmType.START -> Icons.Default.CheckCircle
        ConfirmType.DESTRUCTIVE -> Icons.Default.Warning
        ConfirmType.NORMAL -> Icons.Default.Info
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = body,
                color = TextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = strings.confirm,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = strings.cancel,
                    color = TextMuted
                )
            }
        },
        containerColor = Color(0xFF131822),
        titleContentColor = Color.White,
        textContentColor = TextMuted
    )
}

/**
 * Removes characters that are illegal in Android/FAT file names: / \ : * ? " < > |
 * Also strips control characters, collapses whitespace, and trims leading/trailing
 * dots and spaces (which Android hides or rejects).
 *
 * Returns an empty string when nothing usable remains, so callers can block saving.
 */
fun sanitizeFileName(input: String): String {
    val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    val cleaned = buildString {
        for (ch in input) {
            when {
                ch in illegal -> append('_')
                ch.code < 0x20 || ch.code == 0x7F -> {}
                else -> append(ch)
            }
        }
    }
    return cleaned
        .replace(Regex("\\s+"), " ")
        .replace(Regex("_{2,}"), "_")
        .trim()
        .trim('.', ' ')
        .take(120)
        .trim()
}

/**
 * Save dialog.
 *
 * - Single file ([fileCount] <= 1): the user types the full output file name.
 * - Multiple files ([fileCount] > 1): each selected file becomes its own separate output file,
 *   so asking for one full name makes no sense. Instead the user picks a shared naming suffix
 *   that is appended to every original file name, and a live example is shown.
 *
 * The typed value is always sanitized before [onSave]; if nothing valid remains,
 * saving is blocked and an inline error is shown under the field.
 */
@Composable
fun SaveFileDialog(
    strings: AppStrings,
    defaultName: String,
    extension: String,
    noticeText: String? = null,
    fileCount: Int = 1,
    sampleBaseName: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isMultiple = fileCount > 1
    var fileName by remember { mutableStateOf(defaultName) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val sanitized = sanitizeFileName(fileName)


    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = StatusSaveBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMultiple) "${strings.save} ($fileCount)" else strings.save,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = if (isMultiple) strings.saveSuffixPrompt else strings.saveNamePrompt,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = {
                            fileName = it
                            nameError = null
                        },
                        isError = nameError != null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_file_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusSaveBlue,
                            unfocusedBorderColor = BackgroundDark,
                            errorBorderColor = StatusCancelRed,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            errorTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ".$extension",
                        color = StatusSaveBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                nameError?.let { message ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        color = StatusCancelRed,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.testTag("save_file_name_error")
                    )
                }
                if (fileName != sanitized && nameError == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${strings.saveNameSanitizedNotice} $sanitized",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.testTag("save_file_name_sanitized")
                    )
                }
                if (isMultiple) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val suffix = sanitized
                    val base = sampleBaseName.ifBlank { "file" }
                    val exampleName = if (suffix.isEmpty()) base else "${base}_$suffix"

                    Text(
                        text = "${strings.saveMultiFileExample} $exampleName.$extension",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.testTag("save_file_multi_example")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        color = Color.Transparent
                    ) {
                        Text(
                            text = strings.saveMultiFileNotice,
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (noticeText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .padding(10.dp),
                        color = Color.Transparent
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = noticeText,
                                color = CyanPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusSaveBlue.copy(alpha = 0.12f))
                        .padding(10.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        text = strings.savePathNotice,
                        color = StatusSaveBlue,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = sanitizeFileName(fileName)
                    if (finalName.isEmpty()) {
                        nameError = strings.saveNameInvalidError
                    } else {
                        nameError = null
                        onSave(finalName)
                    }
                },

                colors = ButtonDefaults.buttonColors(containerColor = StatusSaveBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_file_dialog_confirm")
            ) {
                Text(
                    text = strings.save,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = strings.cancel,
                    color = TextMuted
                )
            }
        },
        containerColor = Color(0xFF131822)
    )
}

@Composable
fun ErrorLogDialog(
    strings: AppStrings,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusCancelRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.statusError,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0E14))
                    .border(1.dp, BackgroundDark, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                color = Color(0xFF0B0E14)
            ) {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("voda_error_log", errorMessage)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, strings.copyErrorLog, Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = strings.copyErrorLog,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.copyErrorLog,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = strings.ok, color = TextMuted)
            }
        },
        containerColor = Color(0xFF131822)
    )
}
