package com.example.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.i18n.AppStrings
import com.example.data.i18n.StringsArabic
import com.example.data.model.CompressionOutcome
import com.example.data.model.CompressionPreset
import com.example.data.model.ProcessorType
import com.example.data.util.AppLogger
import com.example.data.util.StorageManager
import com.example.ui.components.CustomCompressionSettings
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * Foreground video pipeline: chunked so pause actually stops encoding (Media3 Transformer
 * has no pause API — we cancel the in-flight export and retry the current ~20s chunk).
 * Also applies trim + rotation.
 */
object VideoProcessor {

    private const val CHUNK_MS = 20_000L

    suspend fun process(
        context: Context,
        uri: Uri,
        preset: CompressionPreset,
        customSettings: CustomCompressionSettings? = null,
        muteAudio: Boolean,
        rotateDegrees: Int = 0,
        trimStartMs: Long = 0L,
        trimEndMs: Long = -1L,
        onProgress: suspend (Float) -> Unit,
        onProcessorChanged: (ProcessorType) -> Unit,
        onCompressionSkipped: (Boolean) -> Unit,
        onOutcomeEvaluated: (CompressionOutcome) -> Unit,
        shouldPause: suspend () -> Boolean = { false },
        strings: AppStrings = StringsArabic
    ): File {
        val origSize = StorageManager.getFileSizeFromUri(context, uri)
        val durationMs = probeDurationMs(context, uri)
        val clipStart = trimStartMs.coerceAtLeast(0L)
        val clipEnd = if (trimEndMs > 0L && durationMs > 0L) {
            trimEndMs.coerceIn(clipStart + 200L, durationMs)
        } else if (durationMs > 0L) durationMs else Long.MAX_VALUE

        onProcessorChanged(ProcessorType.HARDWARE)

        val jobKey = "fg_${UUID.randomUUID()}"
        val temps = mutableListOf<File>()
        try {
            val result = runTiered(
                context = context,
                uri = uri,
                preset = preset,
                customSettings = customSettings,
                muteAudio = muteAudio,
                rotateDegrees = rotateDegrees,
                clipStart = clipStart,
                clipEnd = clipEnd,
                durationMs = durationMs,
                jobKey = jobKey,
                origSize = origSize,
                onProgress = onProgress,
                shouldPause = shouldPause,
                forcePassthrough = false,
                temps = temps
            )
            if (result != null) {
                val outcome = VideoTranscoder.evaluateCompressionResult(
                    origSize, result.length(), wasPassthrough = false
                )
                onOutcomeEvaluated(outcome)
                onCompressionSkipped(outcome == CompressionOutcome.NO_COMPRESSION)
                return result
            }

            onProcessorChanged(ProcessorType.SOFTWARE)
            val passthrough = runTiered(
                context = context,
                uri = uri,
                preset = preset,
                customSettings = customSettings,
                muteAudio = muteAudio,
                rotateDegrees = rotateDegrees,
                clipStart = clipStart,
                clipEnd = clipEnd,
                durationMs = durationMs,
                jobKey = "${jobKey}_pt",
                origSize = origSize,
                onProgress = onProgress,
                shouldPause = shouldPause,
                forcePassthrough = true,
                temps = temps
            )
            if (passthrough != null) {
                onOutcomeEvaluated(
                    VideoTranscoder.evaluateCompressionResult(origSize, passthrough.length(), wasPassthrough = true)
                )
                onCompressionSkipped(true)
                return passthrough
            }
        } finally {
            VideoChunkAssembler.clear(context, jobKey)
            VideoChunkAssembler.clear(context, "${jobKey}_pt")
            temps.forEach { if (it.exists()) it.delete() }
        }

        throw IllegalStateException(strings.errorVideoTranscodeFailed)
    }

    private suspend fun runTiered(
        context: Context,
        uri: Uri,
        preset: CompressionPreset,
        customSettings: CustomCompressionSettings?,
        muteAudio: Boolean,
        rotateDegrees: Int,
        clipStart: Long,
        clipEnd: Long,
        durationMs: Long,
        jobKey: String,
        origSize: Long,
        onProgress: suspend (Float) -> Unit,
        shouldPause: suspend () -> Boolean,
        forcePassthrough: Boolean,
        temps: MutableList<File>
    ): File? {
        val span = if (clipEnd == Long.MAX_VALUE || clipEnd <= clipStart) {
            if (durationMs > 0) durationMs - clipStart else CHUNK_MS
        } else {
            clipEnd - clipStart
        }.coerceAtLeast(1L)
        val totalChunks = ((span + CHUNK_MS - 1) / CHUNK_MS).toInt().coerceAtLeast(1)

        var chunkIndex = 0
        var cursor = clipStart
        val effectiveEnd = if (clipEnd == Long.MAX_VALUE) Long.MAX_VALUE else clipEnd

        while (chunkIndex < totalChunks) {
            while (shouldPause()) delay(400)

            val chunkEnd = if (effectiveEnd == Long.MAX_VALUE) {
                if (chunkIndex + 1 >= totalChunks) Long.MAX_VALUE else cursor + CHUNK_MS
            } else {
                (cursor + CHUNK_MS).coerceAtMost(effectiveEnd)
            }

            val chunkFile = StorageManager.createTempFile(context, "vada_vchunk_", "mp4")
            temps.add(chunkFile)
            val result = try {
                VideoTranscoder.transcodeSegment(
                    context = context,
                    uri = uri,
                    outputFile = chunkFile,
                    preset = preset,
                    customSettings = customSettings,
                    muteAudio = muteAudio,
                    startMs = cursor,
                    endMs = chunkEnd,
                    onProgress = { pct ->
                        val overall = ((chunkIndex + pct) / totalChunks.toFloat()).coerceIn(0f, 0.99f)
                        onProgress(overall)
                    },
                    shouldPause = shouldPause,
                    bitrateReductionFactor = if (forcePassthrough) 1.0f else 1.0f,
                    forcePassthroughAttempt = forcePassthrough,
                    rotateDegrees = rotateDegrees
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("VideoProcessor", "Chunk $chunkIndex failed", e)
                chunkFile.delete()
                return null
            }

            if (!result.success) {
                chunkFile.delete()
                if (shouldPause()) continue
                return null
            }

            val persisted = VideoChunkAssembler.registerChunk(context, jobKey, chunkIndex, chunkFile)
            if (persisted == null) return null

            cursor = if (chunkEnd == Long.MAX_VALUE) Long.MAX_VALUE else chunkEnd
            chunkIndex++
            if (cursor != Long.MAX_VALUE && cursor >= effectiveEnd && effectiveEnd != Long.MAX_VALUE) break
        }

        val finalTemp = StorageManager.createTempFile(context, "vada_vid_", "mp4")
        val ok = VideoChunkAssembler.finalizeToContainer(context, jobKey, finalTemp)
        if (!ok || !finalTemp.exists() || finalTemp.length() <= 0) {
            finalTemp.delete()
            return null
        }
        onProgress(1.0f)

        if (!forcePassthrough && origSize > 0 && finalTemp.length() >= origSize &&
            rotateDegrees == 0 && clipStart == 0L && (trimIsFull(clipEnd, durationMs))
        ) {
            // Size did not shrink: retry once at 80% bitrate as a single extra pass on the result.
            val retry = StorageManager.createTempFile(context, "vada_vid_retry_", "mp4")
            temps.add(retry)
            val retryResult = try {
                VideoTranscoder.transcodeSegment(
                    context = context,
                    uri = Uri.fromFile(finalTemp),
                    outputFile = retry,
                    preset = preset,
                    customSettings = customSettings,
                    muteAudio = muteAudio,
                    onProgress = onProgress,
                    shouldPause = shouldPause,
                    bitrateReductionFactor = 0.8f,
                    rotateDegrees = 0
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logSilentFailure("VideoProcessor", "Bitrate retry failed", e)
                null
            }
            if (retryResult != null && retryResult.success && retry.exists() && retry.length() > 0 && retry.length() < origSize) {
                finalTemp.delete()
                // Remove from temps so the outer finally doesn't delete the file we return.
                temps.remove(retry)
                return retry
            }
            retry.delete()
            temps.remove(retry)
        }
        return finalTemp
    }

    private fun trimIsFull(clipEnd: Long, durationMs: Long): Boolean {
        if (clipEnd == Long.MAX_VALUE) return true
        if (durationMs <= 0) return true
        return clipEnd >= durationMs - 250
    }

    fun probeDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            AppLogger.logSilentFailure("VideoProcessor", "duration probe failed", e)
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
