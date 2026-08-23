package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.i18n.AppStrings
import com.example.data.model.ProcessorType
import com.example.data.queue.DynamicIslandState
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusCancelRed
import com.example.ui.theme.StatusPauseAmber
import com.example.ui.theme.StatusStartGreen
import kotlinx.coroutines.delay

@Composable
fun DynamicIslandHeader(
    strings: AppStrings,
    islandState: DynamicIslandState,
    onMenuClick: () -> Unit,
    onQueueClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )

    // Multi-language idle greetings, cycling every 6 seconds (verified against real-world cadence)
    val greetingTranslations = strings.idleGreetings
    var currentGreetingIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(6000L)
            currentGreetingIndex = (currentGreetingIndex + 1) % greetingTranslations.size
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 230.dp, max = 310.dp)
                .heightIn(min = 48.dp)
                .shadow(12.dp, CircleShape, spotColor = CyanPrimary)
                .clip(CircleShape)
                .background(Color.Black)
                .border(1.dp, GlassBorderWhite, CircleShape)
                .testTag("dynamic_island_header"),
            color = Color.Black
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Menu Hamburger -> Settings
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("header_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTab,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center: Dynamic Island Processing Status -> Queue
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable { onQueueClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        when (islandState) {
                            is DynamicIslandState.Processing -> {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CyanPrimary)
                                        .alpha(dotAlpha)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${(islandState.progress * 100).toInt()}%",
                                    color = CyanPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            is DynamicIslandState.Success -> {
                                Text(
                                    text = islandState.message ?: strings.statusSuccess,
                                    color = if (islandState.isWarning) StatusPauseAmber else StatusStartGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            is DynamicIslandState.Error -> {
                                Text(
                                    text = strings.statusError,
                                    color = StatusCancelRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            is DynamicIslandState.Idle -> {
                                AnimatedContent(
                                    targetState = greetingTranslations[currentGreetingIndex],
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.85f))
                                            .togetherWith(fadeOut(animationSpec = tween(600)) + scaleOut(targetScale = 0.85f))
                                    },
                                    label = "IdleGreetingAnimation"
                                ) { text ->
                                    Text(
                                        text = text,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Right: Logo + Water Drop -> Home
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onHomeClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("header_logo_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.appName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = strings.appName,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Progress line under capsule if processing
                if (islandState is DynamicIslandState.Processing) {
                    val processingState = islandState
                    LinearProgressIndicator(
                        progress = { processingState.progress },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(2.dp),
                        color = CyanPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    if (processingState.processorType == ProcessorType.SOFTWARE) {
                        Text(
                            text = strings.safeModeSwitchedNotice,
                            color = Color(0xFFFFA726), // Warning Orange color
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
