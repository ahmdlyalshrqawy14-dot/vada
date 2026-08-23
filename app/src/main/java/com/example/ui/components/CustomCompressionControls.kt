package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.SurfaceCardLow
import com.example.ui.theme.TextMuted

/**
 * Real user-controlled compression parameters, used when CompressionPreset.CUSTOM is selected.
 * quality: 0..100 (image quality / audio quality mapped to AAC bitrate)
 * maxDimension: max output pixel dimension (image / document images / video height)
 * videoBitrateKbps: target video bitrate in kbps
 */
data class CustomCompressionSettings(
    val quality: Int = 65,
    val maxDimension: Int = 1920,
    val videoBitrateKbps: Int = 2000
)

/** Maps a 0..100 quality value onto an AAC bitrate between 32 kbps and 320 kbps. */
fun customAudioBitrate(quality: Int): Int {
    val q = quality.coerceIn(0, 100)
    return (32_000 + (q / 100f) * (320_000 - 32_000)).toInt().coerceIn(32_000, 320_000)
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                color = CyanPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CyanPrimary,
                activeTrackColor = CyanPrimary,
                inactiveTrackColor = TextMuted
            ),
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        )
    }
}

@Composable
fun CustomCompressionControls(
    strings: AppStrings,
    settings: CustomCompressionSettings,
    onSettingsChange: (CustomCompressionSettings) -> Unit,
    showQuality: Boolean = true,
    isAudioQuality: Boolean = false,
    showMaxDimension: Boolean = true,
    showVideoBitrate: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCardLow)
            .border(1.dp, GlassBorderWhite, RoundedCornerShape(16.dp))
            .testTag("custom_compression_controls"),
        color = SurfaceCardLow,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = strings.customControlsTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = strings.customControlsHint,
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (showQuality) {
                LabeledSlider(
                    label = if (isAudioQuality) strings.customAudioQualityLabel else strings.customQualityLabel,
                    valueText = if (isAudioQuality)
                        "${settings.quality} (${customAudioBitrate(settings.quality) / 1000} kbps)"
                    else
                        "${settings.quality}",
                    value = settings.quality.toFloat(),
                    range = 0f..100f,
                    steps = 99,
                    testTag = "custom_quality_slider",
                    onValueChange = { onSettingsChange(settings.copy(quality = it.toInt().coerceIn(0, 100))) }
                )
            }

            if (showMaxDimension) {
                LabeledSlider(
                    label = strings.customMaxDimensionLabel,
                    valueText = "${settings.maxDimension} px",
                    value = settings.maxDimension.toFloat(),
                    range = 320f..3840f,
                    steps = 0,
                    testTag = "custom_dimension_slider",
                    onValueChange = {
                        onSettingsChange(settings.copy(maxDimension = (it.toInt() / 16) * 16))
                    }
                )
            }

            if (showVideoBitrate) {
                LabeledSlider(
                    label = strings.customBitrateLabel,
                    valueText = "${settings.videoBitrateKbps} kbps",
                    value = settings.videoBitrateKbps.toFloat(),
                    range = 200f..12000f,
                    steps = 0,
                    testTag = "custom_bitrate_slider",
                    onValueChange = {
                        onSettingsChange(settings.copy(videoBitrateKbps = (it.toInt() / 50) * 50))
                    }
                )
            }
        }
    }
}
