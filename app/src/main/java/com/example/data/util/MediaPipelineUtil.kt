package com.example.data.util

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri

/**
 * Shared, hardened helpers for MediaCodec/MediaExtractor/MediaMuxer based pipelines
 * (video compression, audio compression, audio extraction).
 *
 * These were previously duplicated ad-hoc per screen with a naive
 * `MediaExtractor().apply { setDataSource(context, uri, null) }` call, which is the single
 * biggest real-world cause of "this app rejects most of my videos/audio" complaints: several
 * content providers (cloud-backed / virtual files, some OEM camera & gallery apps, some
 * document providers) don't negotiate cleanly with that specific overload even though the
 * underlying file is perfectly valid and playable.
 */
object MediaPipelineUtil {

    /** No-progress watchdog: if a transcode loop makes no forward progress for this long, abort
     * instead of hanging the foreground service / task queue forever. */
    const val INACTIVITY_TIMEOUT_MS = 60_000L

    /**
     * Opens a [MediaExtractor] for [uri] with a resilient two-step strategy:
     * 1. The standard `setDataSource(context, uri, headers)` overload (works for the vast
     *    majority of local files).
     * 2. A raw, locally-opened [android.os.ParcelFileDescriptor] fallback, which sidesteps
     *    whatever internal streaming negotiation the first overload does and is far more
     *    compatible with tricky content providers.
     *
     * Throws [IllegalStateException] with [failureMessage] only if both strategies fail.
     */
    fun openExtractorRobust(context: Context, uri: Uri, failureMessage: String): MediaExtractor {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return extractor
        } catch (e: Exception) {
            AppLogger.logSilentFailure("MediaPipelineUtil", "فشل فتح الملف عبر Uri مباشرة، تجربة بديل عبر واصف ملف", e)
        }
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                return extractor
            }
        } catch (e: Exception) {
            AppLogger.logSilentFailure("MediaPipelineUtil", "فشل فتح الملف عبر واصف الملف البديل أيضاً", e)
        }
        throw IllegalStateException(failureMessage)
    }
}
