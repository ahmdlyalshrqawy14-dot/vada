package com.example.data.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.model.CompressionOutcome
import com.example.data.model.CompressionPreset
import com.example.data.util.AppLogger
import com.example.ui.components.CustomCompressionSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VideoTranscoder {

    data class SegmentResult(
        val success: Boolean,
        val lastGoodPositionMs: Long,
        val paused: Boolean = false
    )

    fun evaluateCompressionResult(originalSize: Long, resultSize: Long, wasPassthrough: Boolean): CompressionOutcome {
        if (wasPassthrough) return CompressionOutcome.NO_COMPRESSION
        if (originalSize <= 0 || resultSize <= 0 || resultSize >= originalSize) {
            return CompressionOutcome.NO_COMPRESSION
        }
        val reductionPercent = ((originalSize - resultSize).toDouble() / originalSize.toDouble()) * 100.0
        return when {
            reductionPercent >= 5.0 -> CompressionOutcome.SUCCESS
            reductionPercent > 0.0 -> CompressionOutcome.MARGINAL
            else -> CompressionOutcome.NO_COMPRESSION
        }
    }

    suspend fun transcodeSegment(
        context: Context,
        uri: Uri,
        outputFile: File,
        preset: CompressionPreset,
        customSettings: CustomCompressionSettings? = null,
        muteAudio: Boolean,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
        onProgress: suspend (Float) -> Unit,
        shouldPause: suspend () -> Boolean = { false },
        bitrateReductionFactor: Float = 1.0f,
        forcePassthroughAttempt: Boolean = false,
        rotateDegrees: Int = 0
    ): SegmentResult {
        withContext(Dispatchers.IO) {
            while (shouldPause()) {
                delay(400)
            }
        }

        return withContext(Dispatchers.Main) {
            if (outputFile.exists()) outputFile.delete()

            val (maxHeight, baseBitrate) = when (preset) {
                CompressionPreset.HEAVY -> 720 to 750_000
                CompressionPreset.MEDIUM -> 1080 to 1_500_000
                CompressionPreset.LIGHT -> 1080 to 3_200_000
                CompressionPreset.CUSTOM -> (customSettings?.maxDimension ?: 1080) to (customSettings?.videoBitrateKbps ?: 2000) * 1000
            }
            val targetBitrate = if (forcePassthroughAttempt) {
                null
            } else {
                (baseBitrate * bitrateReductionFactor).toInt().coerceAtLeast(150_000)
            }

            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0L))
            if (endMs != Long.MAX_VALUE) clippingBuilder.setEndPositionMs(endMs)
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(clippingBuilder.build())
                .build()

            val videoEffects = mutableListOf<Effect>()
            if (!forcePassthroughAttempt) {
                videoEffects.add(Presentation.createForHeight(maxHeight))
            }
            if (rotateDegrees != 0) {
                videoEffects.add(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(rotateDegrees.toFloat())
                        .build()
                )
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(muteAudio)
                .setEffects(Effects(ImmutableList.of<AudioProcessor>(), ImmutableList.copyOf(videoEffects)))
                .build()

            val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
            if (targetBitrate != null) {
                encoderFactoryBuilder.setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(targetBitrate).build()
                )
            }
            if (!forcePassthroughAttempt && preset == CompressionPreset.CUSTOM && customSettings != null) {
                val audioBitrate = com.example.ui.components.customAudioBitrate(customSettings.quality)
                encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                    androidx.media3.transformer.AudioEncoderSettings.Builder().setBitrate(audioBitrate).build()
                )
            }

            val transformerBuilder = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactoryBuilder.build())

            var lastReportedPositionMs = startMs
            val pausedFlag = AtomicBoolean(false)

            val result = suspendCancellableCoroutine<Boolean> { cont ->
                var progressJob: kotlinx.coroutines.Job? = null

                val transformer = transformerBuilder
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            progressJob?.cancel()
                            if (!cont.isActive) return
                            if (pausedFlag.get()) {
                                cont.resume(false)
                            } else {
                                AppLogger.logError("VideoTranscoder", "Transformer export failed", exportException)
                                cont.resumeWithException(exportException)
                            }
                        }
                    })
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)

                progressJob = CoroutineScope(Dispatchers.Main).launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val pct = (holder.progress / 100f).coerceIn(0f, 1f)
                            onProgress(pct)
                            val span = if (endMs == Long.MAX_VALUE) 0L else (endMs - startMs)
                            if (span > 0) {
                                lastReportedPositionMs = startMs + (span * pct).toLong()
                            }
                        }
                        if (shouldPause()) {
                            pausedFlag.set(true)
                            try { transformer.cancel() } catch (e: Exception) {
                                AppLogger.logSilentFailure("VideoTranscoder", "cancel on pause failed", e)
                            }
                            break
                        }
                        delay(400)
                    }
                }

                cont.invokeOnCancellation {
                    progressJob?.cancel()
                    try { transformer.cancel() } catch (e: Exception) {
                        AppLogger.logSilentFailure("VideoTranscoder", "Failed to cancel in-flight Transformer export", e)
                    }
                }
            }

            if (result) {
                lastReportedPositionMs = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else endMs
                onProgress(1.0f)
            }

            SegmentResult(
                success = result && outputFile.exists() && outputFile.length() > 0,
                lastGoodPositionMs = lastReportedPositionMs,
                paused = pausedFlag.get()
            )
        }
    }
}
