package com.example.data.video

import android.content.Context
import com.example.data.util.AppLogger

/**
 * Persists the "last good" resume point for a chunked compression job so that if the process
 * is killed mid-chunk (app swiped away, OOM, WorkManager retry), the next run of
 * VideoCompressionWorker can seek back to the last completed chunk instead of restarting the
 * whole video from frame zero.
 *
 * Backed by a plain SharedPreferences file rather than Room/DataStore: the data is small
 * (one long + one int per in-flight job), short-lived (cleared on completion), and doesn't need
 * migrations or observers — a dedicated table would be overkill for this.
 */
object ChunkCheckpointStore {

    private const val PREFS_NAME = "vada_chunk_checkpoints"

    data class Checkpoint(val resumeMs: Long, val completedChunkIndex: Int)

    fun save(context: Context, jobKey: String, resumeMs: Long, completedChunkIndex: Int) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong("${jobKey}_resumeMs", resumeMs)
                .putInt("${jobKey}_chunk", completedChunkIndex)
                .apply()
        } catch (e: Exception) {
            AppLogger.logSilentFailure("ChunkCheckpointStore", "Failed to save checkpoint for $jobKey", e)
        }
    }

    fun load(context: Context, jobKey: String): Checkpoint? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains("${jobKey}_resumeMs")) return null
            Checkpoint(
                resumeMs = prefs.getLong("${jobKey}_resumeMs", 0L),
                completedChunkIndex = prefs.getInt("${jobKey}_chunk", -1)
            )
        } catch (e: Exception) {
            AppLogger.logSilentFailure("ChunkCheckpointStore", "Failed to load checkpoint for $jobKey", e)
            null
        }
    }

    fun clear(context: Context, jobKey: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove("${jobKey}_resumeMs")
                .remove("${jobKey}_chunk")
                .apply()
        } catch (e: Exception) {
            AppLogger.logSilentFailure("ChunkCheckpointStore", "Failed to clear checkpoint for $jobKey", e)
        }
    }
}
