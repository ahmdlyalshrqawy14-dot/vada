package com.example.data.audio

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
import com.example.data.video.BatteryThermalGuard
import com.example.data.video.ChunkCheckpointStore
import com.example.service.ProcessingForegroundService
import com.example.ui.components.CustomCompressionSettings
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * Chunked, constraint-aware background audio compression. Mirrors
 * [com.example.data.video.VideoCompressionWorker] exactly, reusing the same generic
 * [BatteryThermalGuard] (battery/thermal/storage checks) and [ChunkCheckpointStore] (resume-point
 * persistence) — neither is video-specific, so no duplication was needed for those two pieces.
 *
 * Handles both standalone audio compression and audio-extraction-from-video the same way, since
 * [com.example.data.audio.AudioTranscoder] treats both identically (keep audio, drop video).
 */
class AudioCompressionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AudioCompressionWorker"
        private const val CHUNK_DURATION_MS = 30_000L // ~30s of source audio per chunk

        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_PRESET = "preset"
        const val KEY_CUSTOM_QUALITY = "custom_quality"
        const val KEY_ALLOW_ON_BATTERY = "allow_on_battery"
        const val KEY_JOB_KEY = "job_key"
        const val KEY_PROGRESS = "progress"

        /**
         * Enqueues background compression for [uri] (audio file or video to extract audio from),
         * honoring the user's charging preference. Chunking/resume state is tracked internally
         * per [jobKey].
         */
        fun enqueue(
            context: Context,
            jobKey: String,
            uri: Uri,
            outputName: String,
            preset: CompressionPreset,
            customSettings: CustomCompressionSettings?,
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
                KEY_ALLOW_ON_BATTERY to !requireCharging,
                KEY_JOB_KEY to jobKey
            )

            val request = OneTimeWorkRequestBuilder<AudioCompressionWorker>()
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
            CustomCompressionSettings(quality = inputData.getInt(KEY_CUSTOM_QUALITY, 65))
        } else null
        val allowOnBattery = inputData.getBoolean(KEY_ALLOW_ON_BATTERY, true)

        return try {
            setForeground(createForegroundInfo(0))

            val durationMs = probeDurationMs(applicationContext, sourceUri)
            val totalChunks = if (durationMs <= 0) 1 else ((durationMs + CHUNK_DURATION_MS - 1) / CHUNK_DURATION_MS).toInt().coerceAtLeast(1)

            val checkpoint = ChunkCheckpointStore.load(applicationContext, jobKey)
            val startChunkIndex = (checkpoint?.completedChunkIndex ?: -1) + 1
            val resumeMs = checkpoint?.resumeMs ?: 0L

            if (startChunkIndex >= totalChunks) {
                // A previous run already appended the last chunk but died before finalizing/
                // saving (process kill between ChunkCheckpointStore.save and the finalize step
                // below). Don't silently report success without ever producing the file - finish
                // the finalize step now instead of re-entering the main chunk loop.
                return finalizeAndPersist(jobKey, sourceUri, outputName)
            }

            val chunkOutput = StorageManager.createTempFile(applicationContext, "vada_audio_chunk_", "m4a")
            val startMs = if (startChunkIndex == 0) 0L else resumeMs
            val endMs = (startChunkIndex + 1) * CHUNK_DURATION_MS

            var lastNotifiedPercent = -1
            val result = AudioTranscoder.transcodeSegment(
                context = applicationContext,
                uri = sourceUri,
                outputFile = chunkOutput,
                preset = preset,
                customSettings = customSettings,
                startMs = startMs,
                endMs = if (startChunkIndex + 1 >= totalChunks) Long.MAX_VALUE else endMs,
                onProgress = { pct ->
                    val overallPct = ((startChunkIndex + pct) / totalChunks * 100).toInt().coerceIn(0, 100)
                    setProgressAsync(workDataOf(KEY_PROGRESS to overallPct))
                    // Only push a new foreground-service notification when the whole percentage
                    // actually changes. Transformer polls progress every ~400ms, and each call
                    // into ProcessingForegroundService rebuilds a notification that reads user
                    // preferences via a blocking `runBlocking` DataStore read on the service's
                    // (main) thread - firing that on every single poll tick was a real, avoidable
                    // main-thread stutter source under sustained background compression.
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

            // Fold this chunk's encoded audio into the job's running stream immediately (see
            // AudioChunkAssembler doc). This is the piece that used to be entirely missing: chunks
            // were checkpointed as "done" but their actual audio was never combined or saved
            // anywhere, so only the final chunk's throwaway temp file ever existed, and even that
            // was never moved out of the cache directory.
            val appended = AudioChunkAssembler.appendChunk(applicationContext, jobKey, chunkOutput)
            chunkOutput.delete()
            if (!appended) {
                AppLogger.logError(TAG, "Failed to append chunk $startChunkIndex into job stream for $jobKey", IllegalStateException("appendChunk failed"))
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
            AppLogger.logError(TAG, "Chunked background audio compression failed for $jobKey", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * Remuxes the job's accumulated chunk stream into one final ".m4a", saves it through the same
     * [StorageManager.saveFinalOutput] path the foreground (non-battery-friendly) queue uses - so
     * background jobs land in Downloads/Vada (or the user's configured SAF folder) exactly like
     * foreground ones instead of being left as an orphaned cache file - records it in history, and
     * only then clears the job's checkpoint/accumulation state.
     */
    private suspend fun finalizeAndPersist(jobKey: String, sourceUri: Uri, outputName: String): Result {
        val finalTemp = StorageManager.createTempFile(applicationContext, "vada_audio_final_", "m4a")
        val finalized = AudioChunkAssembler.finalizeToContainer(applicationContext, jobKey, finalTemp)
        if (!finalized) {
            finalTemp.delete()
            AppLogger.logError(TAG, "Failed to finalize accumulated audio for $jobKey", IllegalStateException("finalizeToContainer failed"))
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        return try {
            val prefs = PreferencesManager.getInstance(applicationContext)
            val customSafUri = prefs.customSafUriFlow.firstOrNull()
            val langCode = prefs.languageCode.firstOrNull() ?: "ar"
            val strings = getAppStrings(langCode)

            val origSize = StorageManager.getFileSizeFromUri(applicationContext, sourceUri)
            val savedOutput = StorageManager.saveFinalOutput(
                applicationContext, finalTemp, outputName, "m4a", customSafUri, strings
            )

            try {
                val db = com.example.data.db.AppDatabase.getInstance(applicationContext)
                val historyRepository = com.example.data.db.HistoryRepository(db.historyDao())
                val outcome = if (origSize > 0) {
                    AudioTranscoder.evaluateCompressionResult(origSize, savedOutput.length, wasPassthrough = false)
                } else null
                historyRepository.insert(
                    com.example.data.db.HistoryEntity(
                        fileName = savedOutput.name,
                        fileType = "AUDIO",
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
                // History is a nice-to-have record, not the deliverable itself - the file is
                // already safely saved above, so a history-write failure must not fail the job.
                AppLogger.logSilentFailure(TAG, "Saved background audio output but failed to record history entry", e)
            }

            ProcessingForegroundService.startService(applicationContext, outputName, 100, "Background")
            // Only clear job state AFTER a successful save — otherwise retries have nothing left to finalize.
            ChunkCheckpointStore.clear(applicationContext, jobKey)
            AudioChunkAssembler.clear(applicationContext, jobKey)
            Result.success()
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Failed to persist finalized background audio for $jobKey", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            finalTemp.delete()
        }
    }

    private fun enqueueContinuation(context: Context, jobKey: String) {
        val allowOnBattery = inputData.getBoolean(KEY_ALLOW_ON_BATTERY, true)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(!allowOnBattery)
            .setRequiresBatteryNotLow(allowOnBattery)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<AudioCompressionWorker>()
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
            .setContentTitle(strings.audioSection)
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
