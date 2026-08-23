package com.example.data.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.model.CompressionOutcome
import com.example.data.model.CompressionPreset
import com.example.data.util.AppLogger
import com.example.ui.components.CustomCompressionSettings
import com.example.ui.components.customAudioBitrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Audio compression, built entirely on Media3 Transformer — the same single library used for the
 * video module (see [com.example.data.video.VideoTranscoder]), replacing the previous hand-written
 * MediaCodec/MediaExtractor/MediaMuxer pipeline that used to live directly inside AudioScreen.kt.
 *
 * The old code had two separate hand-rolled functions: `extractAudioTrackFromVideo` (a raw
 * MediaExtractor→MediaMuxer copy loop with no re-encode) and `transcodeAudioToAac` (a full
 * MediaCodec decode/encode loop). With Transformer, both collapse into the same call: whether the
 * source is a standalone audio file or a video file, asking Transformer to keep audio and drop
 * video (`setRemoveVideo(true)`) does the right thing either way — there's no source-type branch
 * left to get out of sync between the two paths.
 *
 * Same tradeoff note as the video module: Transformer has no public pause/resume mid-export API,
 * so pausing happens at chunk boundaries via [AudioCompressionWorker], not frame-by-frame.
 */
object AudioTranscoder {

    data class SegmentResult(
        val success: Boolean,
        /** Resume position in milliseconds, source-relative. Persist as the next chunk's start. */
        val lastGoodPositionMs: Long
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

    /**
     * Compresses (or extracts+compresses, if [uri] is a video) the audio track of [uri] into
     * [outputFile] as AAC/M4A, optionally bounded to [startMs]..[endMs] for chunked background
     * processing.
     *
     * [forcePassthroughAttempt] requests the most permissive possible export (device-default
     * bitrate rather than a forced one) for use as a last-resort fallback when compression at the
     * requested quality fails outright.
     */
    suspend fun transcodeSegment(
        context: Context,
        uri: Uri,
        outputFile: File,
        preset: CompressionPreset,
        customSettings: CustomCompressionSettings? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
        onProgress: suspend (Float) -> Unit,
        shouldPause: suspend () -> Boolean = { false },
        bitrateReductionFactor: Float = 1.0f,
        forcePassthroughAttempt: Boolean = false
    ): SegmentResult {
        // Respect a pause requested right before this chunk even starts (thermal/battery/user) —
        // see VideoTranscoder's doc for why this is chunk-boundary granularity, not per-frame.
        //
        // Deliberately run this loop on Dispatchers.IO, *not* Main: shouldPause() (in practice
        // BatteryThermalGuard.shouldPause) does synchronous blocking I/O under the hood - a StatFs
        // call for free storage and a BatteryManager binder call for battery/charging state. The
        // whole function used to be wrapped in withContext(Dispatchers.Main) from the top,
        // including this loop, which meant every 1-second poll while paused (which can be minutes
        // during a real low-battery/thermal pause) hit the main thread with blocking I/O. Only the
        // Transformer-driving portion below actually needs a Looper thread, so it's the only part
        // still switched to Main.
        withContext(Dispatchers.IO) {
            while (shouldPause()) {
                delay(1_000)
            }
        }

        return withContext(Dispatchers.Main) {
        // Transformer must be built and driven from a thread with a Looper.

        if (outputFile.exists()) outputFile.delete()

        val baseBitrate = when (preset) {
            CompressionPreset.HEAVY -> 64_000
            CompressionPreset.MEDIUM -> 96_000
            CompressionPreset.LIGHT -> 128_000
            CompressionPreset.CUSTOM -> customSettings?.let { customAudioBitrate(it.quality) } ?: 96_000
        }
        val targetBitrate = if (forcePassthroughAttempt) {
            null // let Transformer/device pick a safe default rather than forcing a bitrate
        } else {
            (baseBitrate * bitrateReductionFactor).toInt().coerceAtLeast(32_000)
        }

        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs.coerceAtLeast(0L))
        if (endMs != Long.MAX_VALUE) clippingBuilder.setEndPositionMs(endMs)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clippingBuilder.build())
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true) // works identically whether the source is audio-only or a video
            .build()

        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
        if (targetBitrate != null) {
            encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(targetBitrate).build()
            )
        }

        val transformerBuilder = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactoryBuilder.build())

        var lastReportedPositionMs = startMs

        val result = suspendCancellableCoroutine<Boolean> { cont ->
            var progressJob: kotlinx.coroutines.Job? = null

            val transformer = transformerBuilder
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        AppLogger.logError("AudioTranscoder", "Transformer export failed", exportException)
                        progressJob?.cancel()
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)

            progressJob = CoroutineScope(Dispatchers.Main).launch {
                val holder = ProgressHolder()
                while (isActive) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                    }
                    delay(400)
                }
            }

            cont.invokeOnCancellation {
                progressJob?.cancel()
                try { transformer.cancel() } catch (e: Exception) {
                    AppLogger.logSilentFailure("AudioTranscoder", "Failed to cancel in-flight Transformer export", e)
                }
            }
        }

        if (result) {
            lastReportedPositionMs = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else endMs
        }

        onProgress(1.0f)
        SegmentResult(
            success = result && outputFile.exists() && outputFile.length() > 0,
            lastGoodPositionMs = lastReportedPositionMs
        )
        }
    }
}
