package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.model.CompressionPreset
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.SurfaceCardLow
import com.example.ui.theme.TextMuted

/**
 * Compact chip grid for compression presets — less scrolling, clearer selection.
 * Selected preset shows a one-line description underneath.
 */
@Composable
fun CompressionPresetSelector(
    strings: AppStrings,
    selectedPreset: CompressionPreset,
    onPresetSelected: (CompressionPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        Triple(CompressionPreset.LIGHT, strings.presetLightTitle, strings.presetLightDesc),
        Triple(CompressionPreset.MEDIUM, strings.presetMediumTitle, strings.presetMediumDesc),
        Triple(CompressionPreset.HEAVY, strings.presetHeavyTitle, strings.presetHeavyDesc),
        Triple(CompressionPreset.CUSTOM, strings.presetCustomTitle, strings.presetCustomDesc)
    )
    val selectedDesc = presets.firstOrNull { it.first == selectedPreset }?.third.orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.compressionPresetLabel,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (preset, title, _) ->
                val isSelected = selectedPreset == preset
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CyanPrimary else GlassBorderWhite,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onPresetSelected(preset) }
                        .testTag("preset_${preset.name.lowercase()}"),
                    color = if (isSelected) CyanGlow else SurfaceCardLow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) CyanPrimary else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 12.dp)
                    )
                }
            }
        }

        if (selectedDesc.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = selectedDesc,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}
