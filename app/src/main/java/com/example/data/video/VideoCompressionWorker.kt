package com.example.data.video

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.i18n.getAppStrings
import com.example.data.model.CompressionPreset
import com.example.data.prefs.PreferencesManager
import com.example.data.util.AppLogger
import com.example.data.util.StorageManager
import com.example.service.ProcessingForegroundService
import com.example.ui.components.CustomCompressionSettings
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * Chunked, constraint-aware background compression. This is the piece the original pipeline was
 * missing entirely: video compression used to run as one unbroken pass inside a plain
 * CoroutineScope, with no awareness of battery level, charging state, or thermal status, and no
 * way to resume if the process died mid-file.
 *
 * Each work run compresses one bounded time-range chunk of the source video (see
 * [CHUNK_DURATION_MS]), checkpoints its resume point via [ChunkCheckpointStore], and re-enqueues
 * itself for the next chunk. Between chunks, WorkManager re-evaluates [Constraints] (battery not
 * low / charging / storage not low), so a job started while charging and then unplugged will
 * naturally pause until the constraint is satisfied again — no manual polling loop needed at the
 * WorkManager level. The additional [BatteryThermalGuard] check inside the chunk itself handles
 * thermal throttling, which WorkManager's Constraints API doesn't expose.
 */
class VideoCompressionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "VideoCompressionWorker"
        private const val CHUNK_DURATION_MS = 20_000L // ~20s of source video per chunk

        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_PRESET = "preset"
        const val KEY_CUSTOM_QUALITY = "custom_quality"
        const val KEY_CUSTOM_MAX_DIM = "custom_max_dim"
        const val KEY_CUSTOM_BITRATE_KBPS = "custom_bitrate_kbps"
        const val KEY_MUTE_AUDIO = "mute_audio"
        const val KEY_ALLOW_ON_BATTERY = "allow_on_battery"
        const val KEY_JOB_KEY = "job_key"
        const val KEY_PROGRESS = "progress"

        /**
         * Enqueues background compression for [uri], honoring the user's charging preference.
         * Chunking/resume state is tracked internally per [jobKey] (stable id derived from the
         * source uri + output name so re-enqueuing the same logical job resumes instead of
         * restarting).
         */
        fun enqueue(
            context: Context,
            jobKey: String,
            uri: Uri,
            outputName: String,
            preset: CompressionPreset,
            customSettings: CustomCompressionSettings?,
            muteAudio: Boolean,
            requireCharging: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresCharging(requireCharging)
                .setRequiresBatteryNotLow(!requireCharging)
                .setRequiresStorageNotLow(true)
                .build()

            val data = workDataOf(
                KEY_SOURCE_URI to uri.toString(),
                KEY_OUTPUT_NAME to outputName,
                KEY_PRESET to preset.name,
                KEY_CUSTOM_QUALITY to (customSettings?.quality ?: 65),
                KEY_CUSTOM_MAX_DIM to (customSettings?.maxDimension ?: 1080),
                KEY_CUSTOM_BITRATE_KBPS to (customSettings?.videoBitrateKbps ?: 2000),
                KEY_MUTE_AUDIO to muteAudio,
                KEY_ALLOW_ON_BATTERY to !requireCharging,
                KEY_JOB_KEY to jobKey
            )

            val request = OneTimeWorkRequestBuilder<VideoCompressionWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(jobKey, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, jobKey: String) {
            WorkManager.getInstance(context).cancelUniqueWork(jobKey)
            ChunkCheckpointStore.clear(context, jobKey)
        }
    }

    override suspend fun doWork(): Result {
        val jobKey = inputData.getString(KEY_JOB_KEY) ?: return Result.failure()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)?.let { Uri.parse(it) } ?: return Result.failure()
        val outputName = inputData.getString(KEY_OUTPUT_NAME) ?: "compressed"
        val preset = try {
            CompressionPreset.valueOf(inputData.getString(KEY_PRESET) ?: CompressionPreset.MEDIUM.name)
        } catch (e: Exception) {
            AppLogger.logSilentFailure(TAG, "Invalid stored preset, defaulting to MEDIUM", e)
            CompressionPreset.MEDIUM
        }
        val customSettings = if (preset == CompressionPreset.CUSTOM) {
            CustomCompressionSettings(
                quality = inputData.getInt(KEY_CUSTOM_QUALITY, 65),
                maxDimension = inputData.getInt(KEY_CUSTOM_MAX_DIM, 1080),
                videoBitrateKbps = inputData.getInt(KEY_CUSTOM_BITRATE_KBPS, 2000)
            )
        } else null
        val muteAudio = inputData.getBoolean(KEY_MUTE_AUDIO, false)
        val allowOnBattery = inputData.getBoolean(KEY_ALLOW_ON_BATTERY, true)

        return try {
            setForeground(createForegroundInfo(0))

            val durationMs = probeDurationMs(applicationContext, sourceUri)
            val totalChunks = if (durationMs <= 0) 1 else ((durationMs + CHUNK_DURATION_MS - 1) / CHUNK_DURATION_MS).toInt().coerceAtLeast(1)

            val checkpoint = ChunkCheckpointStore.load(applicationContext, jobKey)
            val startChunkIndex = (checkpoint?.completedChunkIndex ?: -1) + 1
            val resumeMs = checkpoint?.resumeMs ?: 0L

            if (startChunkIndex >= totalChunks) {
                // A previous run registered the last chunk but died before finalizing/saving.
                // Finish the finalize step instead of silently reporting success with no output.
                return finalizeAndPersist(jobKey, sourceUri, outputName)
            }

            val chunkOutput = StorageManager.createTempFile(applicationContext, "vada_chunk_", "mp4")
            val startMs = if (startChunkIndex == 0) 0L else resumeMs
            val endMs = (startChunkIndex + 1) * CHUNK_DURATION_MS

            var lastNotifiedPercent = -1
            val result = VideoTranscoder.transcodeSegment(
                context = applicationContext,
                uri = sourceUri,
                outputFile = chunkOutput,
                preset = preset,
                customSettings = customSettings,
                muteAudio = muteAudio,
                startMs = startMs,
                endMs = if (startChunkIndex + 1 >= totalChunks) Long.MAX_VALUE else endMs,
                onProgress = { pct ->
                    val overallPct = ((startChunkIndex + pct) / totalChunks * 100).toInt().coerceIn(0, 100)
                    setProgressAsync(workDataOf(KEY_PROGRESS to overallPct))
                    // Only push a notification when the whole percentage actually changes - see
                    // AudioCompressionWorker's identical fix for why: ProcessingForegroundService
                    // does a blocking `runBlocking` DataStore read on every call, and Transformer
                    // polls progress roughly every 400ms.
                    if (overallPct != lastNotifiedPercent) {
                        lastNotifiedPercent = overallPct
                        ProcessingForegroundService.startService(
                            applicationContext, outputName, overallPct, "Background"
                        )
                    }
                },
                shouldPause = { BatteryThermalGuard.shouldPause(applicationContext, allowOnBattery) }
            )

            if (!result.success) {
                chunkOutput.delete()
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // Persist this chunk's playable mp4 into the job's chunk directory rather than
            // discarding it - see VideoChunkAssembler doc. This is the piece that used to be
            // entirely missing: chunks were checkpointed as "done" but never combined or saved
            // anywhere reachable by the user.
            val persisted = VideoChunkAssembler.registerChunk(applicationContext, jobKey, startChunkIndex, chunkOutput)
            if (persisted == null) {
                AppLogger.logError(TAG, "Failed to persist chunk $startChunkIndex for $jobKey", IllegalStateException("registerChunk failed"))
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            ChunkCheckpointStore.save(applicationContext, jobKey, result.lastGoodPositionMs, startChunkIndex)

            if (startChunkIndex + 1 < totalChunks) {
                enqueueContinuation(applicationContext, jobKey)
                Result.success()
            } else {
                finalizeAndPersist(jobKey, sourceUri, outputName)
            }
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Chunked background compression failed for $jobKey", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * Remuxes the job's registered chunks into one final ".mp4", saves it through the same
     * [StorageManager.saveFinalOutput] path the foreground (non-battery-friendly) queue uses,
     * records it in history, and only then clears the job's checkpoint/chunk-directory state.
     */
    private suspend fun finalizeAndPersist(jobKey: String, sourceUri: Uri, outputName: String): Result {
        val finalTemp = StorageManager.createTempFile(applicationContext, "vada_video_final_", "mp4")
        val finalized = VideoChunkAssembler.finalizeToContainer(applicationContext, jobKey, finalTemp)
        if (!finalized) {
            finalTemp.delete()
            AppLogger.logError(TAG, "Failed to finalize registered chunks for $jobKey", IllegalStateException("finalizeToContainer failed"))
            // Keep chunks + checkpoint so a retry can re-attempt finalize instead of starting over empty.
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        return try {
            val prefs = PreferencesManager.getInstance(applicationContext)
            val customSafUri = prefs.customSafUriFlow.firstOrNull()
            val langCode = prefs.languageCode.firstOrNull() ?: "ar"
            val strings = getAppStrings(langCode)

            val origSize = StorageManager.getFileSizeFromUri(applicationContext, sourceUri)
            val savedOutput = StorageManager.saveFinalOutput(
                applicationContext, finalTemp, outputName, "mp4", customSafUri, strings
            )

            try {
                val db = com.example.data.db.AppDatabase.getInstance(applicationContext)
                val historyRepository = com.example.data.db.HistoryRepository(db.historyDao())
                val outcome = if (origSize > 0) {
                    VideoTranscoder.evaluateCompressionResult(origSize, savedOutput.length, wasPassthrough = false)
                } else null
                historyRepository.insert(
                    com.example.data.db.HistoryEntity(
                        fileName = savedOutput.name,
                        fileType = "VIDEO",
                        operationName = outputName,
                        originalSizeBytes = if (origSize > 0) origSize else savedOutput.length,
                        processedSizeBytes = savedOutput.length,
                        outputPath = savedOutput.pathOrUri,
                        processorType = "HARDWARE",
                        status = "COMPLETED",
                        compressionOutcome = outcome?.name
                    )
                )
            } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Saved background video output but failed to record history entry", e)
            }

            ProcessingForegroundService.startService(applicationContext, outputName, 100, "Background")
            // Only clear job state AFTER a successful save — otherwise retries have nothing left to finalize.
            ChunkCheckpointStore.clear(applicationContext, jobKey)
            VideoChunkAssembler.clear(applicationContext, jobKey)
            Result.success()
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Failed to persist finalized background video for $jobKey", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            finalTemp.delete()
        }
    }

    private fun enqueueContinuation(context: Context, jobKey: String) {
        // Re-enqueue for the next chunk under the same unique work name. Constraints are rebuilt
        // from the same allowOnBattery flag stored in inputData, so a job that requires charging
        // keeps requiring it for every subsequent chunk, and WorkManager re-evaluates battery/
        // charging/storage state fresh before running each one.
        val allowOnBattery = inputData.getBoolean(KEY_ALLOW_ON_BATTERY, true)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(!allowOnBattery)
            .setRequiresBatteryNotLow(allowOnBattery)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<VideoCompressionWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(jobKey, ExistingWorkPolicy.REPLACE, request)
    }

    private fun probeDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            AppLogger.logSilentFailure(TAG, "Could not probe duration for chunk planning", e)
            0L
        } finally {
            try { retriever.release() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Failed to release MediaMetadataRetriever", e)
            }
        }
    }

    private suspend fun createForegroundInfo(progress: Int): ForegroundInfo {
        val prefs = PreferencesManager.getInstance(applicationContext)
        val langCode = prefs.languageCode.firstOrNull() ?: "ar"
        val strings = getAppStrings(langCode)

        val mainIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            applicationContext, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, ProcessingForegroundService.CHANNEL_ID)
            .setContentTitle(strings.videoSection)
            .setContentText("$progress%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingMainIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(ProcessingForegroundService.NOTIFICATION_ID, notification)
    }
}
