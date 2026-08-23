package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.SurfaceCardLow
import com.example.ui.theme.TextMuted

enum class OutputImageFormat(
    val formatKey: String?
) {
    AUTO(null),
    JPG("jpg"),
    PNG("png"),
    WEBP("webp")
}

private fun OutputImageFormat.title(strings: AppStrings): String = when (this) {
    OutputImageFormat.AUTO -> strings.imageFormatAutoTitle
    OutputImageFormat.JPG -> strings.imageFormatJpgTitle
    OutputImageFormat.PNG -> strings.imageFormatPngTitle
    OutputImageFormat.WEBP -> strings.imageFormatWebpTitle
}

private fun OutputImageFormat.description(strings: AppStrings): String = when (this) {
    OutputImageFormat.AUTO -> strings.imageFormatAutoDesc
    OutputImageFormat.JPG -> strings.imageFormatJpgDesc
    OutputImageFormat.PNG -> strings.imageFormatPngDesc
    OutputImageFormat.WEBP -> strings.imageFormatWebpDesc
}

@Composable
fun ImageOutputFormatSelector(
    strings: AppStrings,
    selectedFormat: OutputImageFormat,
    onFormatSelected: (OutputImageFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.imageOutputFormatTitle,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutputImageFormat.entries.forEach { option ->
            val isSelected = selectedFormat == option

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) CyanGlow else SurfaceCardLow)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) CyanPrimary else GlassBorderWhite,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onFormatSelected(option) }
                    .testTag("format_${option.name.lowercase()}"),
                color = if (isSelected) CyanGlow else SurfaceCardLow,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onFormatSelected(option) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = CyanPrimary,
                            unselectedColor = TextMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title(strings),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = option.description(strings),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
