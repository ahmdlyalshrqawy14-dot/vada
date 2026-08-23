package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.i18n.AppStrings
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.HintCard
import com.example.ui.theme.CategoryAudioOrange
import com.example.ui.theme.CategoryConvertCyan
import com.example.ui.theme.CategoryDocumentPink
import com.example.ui.theme.CategoryImageGreen
import com.example.ui.theme.CategoryVideoPurple
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusSaveBlue
import com.example.ui.theme.TextMuted

private data class HomeCategory(
    val title: String,
    val description: String,
    val color: Color,
    val icon: ImageVector,
    val route: String,
    val tag: String
)

@Composable
fun HomeScreen(
    strings: AppStrings,
    onNavigateToCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsDenied by remember { mutableStateOf(false) }

    fun refreshNotificationState() {
        notificationsDenied = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        } else false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNotificationState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshNotificationState()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val categories = listOf(
        HomeCategory(strings.videoSection, strings.videoDesc, CategoryVideoPurple, Icons.Default.Videocam, "video", "home_card_video"),
        HomeCategory(strings.audioSection, strings.audioDesc, CategoryAudioOrange, Icons.Default.AudioFile, "audio", "home_card_audio"),
        HomeCategory(strings.imageSection, strings.imageDesc, CategoryImageGreen, Icons.Default.Image, "image", "home_card_image"),
        HomeCategory(strings.documentSection, strings.documentDesc, CategoryDocumentPink, Icons.Default.Description, "files", "home_card_document"),
        HomeCategory(strings.convertSection, strings.convertDesc, CategoryConvertCyan, Icons.Default.PictureInPicture, "convert", "home_card_convert")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            text = strings.appName,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = strings.appSubtitle,
            color = TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        if (notificationsDenied) {
            HintCard(
                text = strings.notificationPermissionBody,
                accent = StatusSaveBlue
            )
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusSaveBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.openAppSettings, fontWeight = FontWeight.Bold)
            }
        }

        // 2-column grid: faster scan, fewer vertical scrolls
        categories.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    GlassmorphicCard(
                        title = item.title,
                        description = item.description,
                        categoryColor = item.color,
                        icon = item.icon,
                        testTag = item.tag,
                        onClick = { onNavigateToCategory(item.route) },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        HintCard(
            text = strings.offlineDisclaimer,
            accent = CyanPrimary
        )

        Spacer(Modifier.height(88.dp))
    }
}
