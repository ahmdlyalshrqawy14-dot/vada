package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.OceanGold
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = true,
    isProcessing: Boolean = false
) {
    val bgColor1 = if (darkTheme) Color(0xFF0B0E14) else Color(0xFFF0F4F8)
    val bgColor2 = if (darkTheme) Color(0xFF131822) else Color(0xFFE2E8F0)
    val bgColor3 = if (darkTheme) Color(0xFF002338) else Color(0xFFD0E2F5)

    // أثناء المعالجة: اعرض gradient ثابت فقط بدون أي رسوم متحركة لتوفير موارد المعالج
    if (isProcessing) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(bgColor1, bgColor2, bgColor3),
                    startY = 0f,
                    endY = height
                )
            )
            // سحب ثابتة (بدون حركة) لإبقاء مظهر الخلفية مألوفاً
            val cloudAlpha = if (darkTheme) 0.04f else 0.08f
            val cloudColor = Color.White.copy(alpha = cloudAlpha)
            drawCircle(cloudColor, radius = width * 0.25f, center = Offset(width * 0.1f, height * 0.15f))
            drawCircle(cloudColor, radius = width * 0.20f, center = Offset(width * 0.25f, height * 0.12f))
            drawCircle(cloudColor, radius = width * 0.30f, center = Offset(width * 0.85f, height * 0.35f))
            drawCircle(cloudColor, radius = width * 0.22f, center = Offset(width * 0.70f, height * 0.38f))
            drawCircle(cloudColor, radius = width * 0.28f, center = Offset(width * 0.05f, height * 0.70f))
            drawCircle(cloudColor, radius = width * 0.35f, center = Offset(width * 0.5f, height * 0.9f))
        }
        return
    }

    // الحالة الطبيعية (Idle): رسوم متحركة كاملة
    val infiniteTransition = rememberInfiniteTransition(label = "OceanAnimation")

    // Sine Wave offset (12 seconds linear)
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveOffset"
    )

    // Ripple Rings scale & alpha (8 seconds)
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Vertical Gradient Background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(bgColor1, bgColor2, bgColor3),
                startY = 0f,
                endY = height
            )
        )

        // 2. Soft Animated Clouds (4 cloud clusters using overlapping circles)
        val cloudAlpha = if (darkTheme) 0.04f else 0.08f
        val cloudColor = Color.White.copy(alpha = cloudAlpha)

        // Cloud 1 - Top Left
        drawCircle(cloudColor, radius = width * 0.25f, center = Offset(width * 0.1f, height * 0.15f))
        drawCircle(cloudColor, radius = width * 0.20f, center = Offset(width * 0.25f, height * 0.12f))

        // Cloud 2 - Mid Right
        drawCircle(cloudColor, radius = width * 0.30f, center = Offset(width * 0.85f, height * 0.35f))
        drawCircle(cloudColor, radius = width * 0.22f, center = Offset(width * 0.70f, height * 0.38f))

        // Cloud 3 - Lower Left
        drawCircle(cloudColor, radius = width * 0.28f, center = Offset(width * 0.05f, height * 0.70f))

        // Cloud 4 - Bottom Center
        drawCircle(cloudColor, radius = width * 0.35f, center = Offset(width * 0.5f, height * 0.9f))


        // 3. Expanding Ocean Ripple Rings in Top Right Corner (Ocean Gold + Cyan)
        val rippleCenter = Offset(width * 0.85f, height * 0.15f)
        val maxRadius = width * 0.45f

        for (i in 0..2) {
            val ringProgress = (rippleProgress + i * 0.333f) % 1f
            val currentRadius = ringProgress * maxRadius
            val ringAlpha = (1f - ringProgress) * 0.25f
            val ringColor = if (i % 2 == 0) OceanGold else CyanPrimary

            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = currentRadius,
                center = rippleCenter,
                style = Stroke(width = 2f)
            )
        }

        // 4. Animated Organic Sine & Cosine Waves at the bottom
        val waveHeight = height * 0.04f
        val baseWaveY = height * 0.88f

        val pathSine = Path().apply {
            moveTo(0f, height)
            for (x in 0..width.toInt() step 15) {
                val xPos = x.toFloat()
                val yPos = baseWaveY + sin((xPos / width * 3 * Math.PI + waveOffset).toDouble()).toFloat() * waveHeight
                lineTo(xPos, yPos)
            }
            lineTo(width, height)
            close()
        }

        val waveAlpha1 = if (darkTheme) 0.12f else 0.20f
        drawPath(
            path = pathSine,
            brush = Brush.verticalGradient(
                colors = listOf(CyanPrimary.copy(alpha = waveAlpha1), Color.Transparent),
                startY = baseWaveY - waveHeight,
                endY = height
            )
        )

        val pathCosine = Path().apply {
            moveTo(0f, height)
            for (x in 0..width.toInt() step 15) {
                val xPos = x.toFloat()
                val yPos = baseWaveY + 15.dp.toPx() + cos((xPos / width * 2.5 * Math.PI - waveOffset).toDouble()).toFloat() * (waveHeight * 0.8f)
                lineTo(xPos, yPos)
            }
            lineTo(width, height)
            close()
        }

        val waveAlpha2 = if (darkTheme) 0.08f else 0.15f
        drawPath(
            path = pathCosine,
            brush = Brush.verticalGradient(
                colors = listOf(CyanPrimary.copy(alpha = waveAlpha2), Color.Transparent),
                startY = baseWaveY,
                endY = height
            )
        )
    }
}
