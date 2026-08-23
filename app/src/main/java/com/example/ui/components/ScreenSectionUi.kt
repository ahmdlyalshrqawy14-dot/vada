package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.SurfaceCardLow
import com.example.ui.theme.TextMuted

/**
 * Numbered step label shared across processing screens.
 * Prefer [NumberedStep] when wrapping content; this remains for simple one-line headers.
 */
@Composable
fun SectionStepLabel(text: String, tint: Color, step: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
    ) {
        if (step != null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun FormatSupportRow(formats: List<String>, caveat: String? = null) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            formats.forEach { format ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SurfaceCardLow,
                    modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(999.dp))
                ) {
                    Text(
                        text = format,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        if (caveat != null) {
            Text(
                text = caveat,
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, horizontal = 2.dp)
            )
        }
    }
}
