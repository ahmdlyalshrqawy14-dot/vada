package com.example.data.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.data.util.AppLogger
import java.io.File
import java.nio.ByteBuffer

/**
 * Fixes the same critical gap in [VideoCompressionWorker] that [com.example.data.audio.AudioChunkAssembler]
 * fixes for audio: chunks used to be checkpointed as "done" but never combined or saved anywhere a
 * user could reach them - only the last chunk's throwaway cache file ever existed, and even that
 * was never moved out of the cache directory. `Result.success()` was returned regardless, so the
 * background "compress in background" feature silently produced nothing for any video needing more
 * than one chunk.
 *
 * Unlike audio (a single elementary stream that can be appended frame-by-frame into one running
 * file), each video chunk already comes out of [VideoTranscoder] as its own small, fully playable
 * MP4 (H.264 + AAC). So instead of an elementary-stream append trick, this keeps every completed
 * chunk's MP4 file on disk (deterministically named per job+index, so it survives a WorkManager
 * retry/process death same as the checkpoint does) and, once the last chunk lands, does a single
 * remux pass: read both tracks out of every chunk file in order via [MediaExtractor] and write them
 * into one final [MediaMuxer] session, shifting each chunk's sample timestamps by a running
 * per-track offset so the result plays back as one continuous file.
 *
 * Honesty about what this does NOT verify: this was written and reviewed line-by-line against the
 * documented MediaExtractor/MediaMuxer APIs, but not run on a device or emulator (this environment
 * has neither). Two things worth confirming on a real device before relying on it: (1) that every
 * chunk really does share an identical video/audio MediaFormat (same codec profile/level, same
 * channel count) - true here since every chunk comes from the same VideoTranscoder call with the
 * same preset, but would break silently if that ever changed; (2) that writing all of one track's
 * samples before starting the next track (rather than interleaving) still produces a file that
 * seeks well in every target player - it is valid MP4 either way, but interleaved would be more
 * seek-efficient for very long output files.
 */
object VideoChunkAssembler {

    private const val TAG = "VideoChunkAssembler"

    private fun jobDir(context: Context, jobKey: String): File {
        val dir = File(context.cacheDir, "vada_video_job_${jobKey.hashCode()}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun manifestFile(context: Context, jobKey: String): File =
        File(jobDir(context, jobKey), "manifest.txt")

    /**
     * Moves [chunkFile] into the job's persistent chunk directory (so it isn't cleared by
     * [StorageManager]'s generic temp-file cleanup) and appends it to the job's ordered manifest.
     * Returns the new persisted path, or null if the move failed.
     */
    fun registerChunk(context: Context, jobKey: String, chunkIndex: Int, chunkFile: File): File? {
        return try {
            val dir = jobDir(context, jobKey)
            val persisted = File(dir, "chunk_${chunkIndex}.mp4")
            if (persisted.exists()) persisted.delete()
            val moved = chunkFile.renameTo(persisted)
            if (!moved) {
                // renameTo can fail across filesystems/providers; fall back to an explicit copy.
                chunkFile.copyTo(persisted, overwrite = true)
                chunkFile.delete()
            }
            // Rewrite the ordered manifest from disk so retries of the same chunkIndex do not
            // append duplicates (which would mux the same segment twice in the final file).
            rewriteManifest(context, jobKey)
            persisted
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Failed to persist chunk $chunkIndex for job $jobKey", e)
            null
        }
    }

    private fun rewriteManifest(context: Context, jobKey: String) {
        val dir = jobDir(context, jobKey)
        val lines = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("chunk_") && it.name.endsWith(".mp4") }
            ?.sortedBy { file ->
                file.name.removePrefix("chunk_").removeSuffix(".mp4").toIntOrNull() ?: Int.MAX_VALUE
            }
            ?.joinToString(separator = "\n") { it.absolutePath }
            .orEmpty()
        manifestFile(context, jobKey).writeText(if (lines.isEmpty()) "" else "$lines\n")
    }

    private fun orderedChunkFiles(context: Context, jobKey: String): List<File> {
        val manifest = manifestFile(context, jobKey)
        if (!manifest.exists()) {
            // Recover from a missing/corrupt manifest by scanning the job directory.
            return jobDir(context, jobKey).listFiles()
                ?.filter { it.isFile && it.name.startsWith("chunk_") && it.name.endsWith(".mp4") && it.length() > 0 }
                ?.sortedBy { file ->
                    file.name.removePrefix("chunk_").removeSuffix(".mp4").toIntOrNull() ?: Int.MAX_VALUE
                }
                .orEmpty()
        }
        return manifest.readLines()
            .map { File(it) }
            .filter { it.exists() && it.length() > 0 }
            .distinctBy { it.absolutePath }
    }

    /**
     * Remuxes every registered chunk (in the order they were appended) into one continuous
     * [outputFile]. Call only after the last chunk has been registered. Requires every chunk to
     * contain exactly one video and one audio track (which is what [VideoTranscoder] always
     * produces, since it never calls setRemoveAudio(true) *and* setRemoveVideo(true) together).
     */
    fun finalizeToContainer(context: Context, jobKey: String, outputFile: File): Boolean {
        val chunkFiles = orderedChunkFiles(context, jobKey)
        if (chunkFiles.isEmpty()) {
            AppLogger.logError(TAG, "No registered chunks to finalize for job $jobKey", IllegalStateException(jobKey))
            return false
        }

        var muxer: MediaMuxer? = null
        return try {
            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Determine each track's MediaFormat from the first chunk, and add both tracks to the
            // muxer once up front (MediaMuxer requires all addTrack() calls before the first
            // writeSampleData()).
            val firstChunkTracks = readTracks(chunkFiles.first())
            val videoFormat = firstChunkTracks.first
            val audioFormat = firstChunkTracks.second
            if (videoFormat == null && audioFormat == null) {
                AppLogger.logError(TAG, "First chunk has neither video nor audio track", IllegalStateException(jobKey))
                return false
            }
            val videoTrackIndex = videoFormat?.let { muxer.addTrack(it) }
            val audioTrackIndex = audioFormat?.let { muxer.addTrack(it) }
            muxer.start()

            if (videoTrackIndex != null) {
                muxTrackAcrossChunks(chunkFiles, "video/", muxer, videoTrackIndex)
            }
            if (audioTrackIndex != null) {
                muxTrackAcrossChunks(chunkFiles, "audio/", muxer, audioTrackIndex)
            }
            true
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Failed to finalize chunks into container for job $jobKey", e)
            if (outputFile.exists()) outputFile.delete()
            false
        } finally {
            try { muxer?.stop() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "MediaMuxer.stop() failed (stream may be malformed)", e)
            }
            try { muxer?.release() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Failed to release MediaMuxer", e)
            }
        }
    }

    /** Returns (videoFormat, audioFormat) found in [file], either of which may be null. */
    private fun readTracks(file: File): Pair<MediaFormat?, MediaFormat?> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") && video == null -> video = f
                    mime.startsWith("audio/") && audio == null -> audio = f
                }
            }
            video to audio
        } finally {
            try { extractor.release() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Failed to release MediaExtractor while probing tracks", e)
            }
        }
    }

    /**
     * Writes one track type across every chunk file in order into [muxer]'s [muxerTrackIndex],
     * shifting each chunk's timestamps so they continue on from the previous chunk rather than
     * each restarting near zero (every chunk was produced independently by VideoTranscoder, so
     * each one's internal timestamps do restart near zero on their own).
     */
    private fun muxTrackAcrossChunks(
        chunkFiles: List<File>,
        mimePrefix: String,
        muxer: MediaMuxer,
        muxerTrackIndex: Int
    ) {
        var runningOffsetUs = 0L
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        for (chunkFile in chunkFiles) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(chunkFile.absolutePath)
                var trackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith(mimePrefix)) {
                        trackIndex = i
                        format = f
                        break
                    }
                }
                if (trackIndex < 0 || format == null) {
                    AppLogger.logSilentFailure(
                        TAG, "Chunk ${chunkFile.name} has no $mimePrefix track; skipping it for this track",
                        IllegalStateException("missing track")
                    )
                    continue
                }
                extractor.selectTrack(trackIndex)

                val maxInputSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    1024 * 1024
                }
                var buffer = ByteBuffer.allocate(maxInputSize)

                var firstSampleTimeUs = -1L
                var lastSampleTimeUs = 0L
                while (true) {
                    buffer.clear()
                    var sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    if (sampleSize > buffer.capacity()) {
                        buffer = ByteBuffer.allocate(sampleSize)
                        sampleSize = extractor.readSampleData(buffer, 0)
                    }

                    val sampleTimeUs = extractor.sampleTime
                    if (firstSampleTimeUs < 0) firstSampleTimeUs = sampleTimeUs
                    lastSampleTimeUs = sampleTimeUs

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = runningOffsetUs + (sampleTimeUs - firstSampleTimeUs)
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }

                if (firstSampleTimeUs >= 0) {
                    // Advance the offset by this chunk's actual span, plus one estimated sample
                    // duration so the next chunk's first sample doesn't land on the exact same
                    // timestamp as this chunk's last one. There's no per-sample duration field on
                    // MediaExtractor, so this is an estimate (frame rate for video, 1024 samples
                    // at the track's sample rate for AAC) - stated explicitly since it's the one
                    // approximation in this whole path, everything else is exact.
                    val estimatedFrameDurationUs = estimateFrameDurationUs(format, mimePrefix)
                    runningOffsetUs += (lastSampleTimeUs - firstSampleTimeUs) + estimatedFrameDurationUs
                }
            } catch (e: Exception) {
                AppLogger.logError(TAG, "Failed to mux $mimePrefix track from chunk ${chunkFile.name}", e)
            } finally {
                try { extractor.release() } catch (e: Exception) {
                    AppLogger.logSilentFailure(TAG, "Failed to release MediaExtractor", e)
                }
            }
        }
    }

    private fun estimateFrameDurationUs(format: MediaFormat, mimePrefix: String): Long {
        return if (mimePrefix == "video/") {
            val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getInteger(MediaFormat.KEY_FRAME_RATE)
            } else 30
            if (frameRate > 0) 1_000_000L / frameRate else 33_333L
        } else {
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44_100
            // Standard AAC frame = 1024 PCM samples.
            if (sampleRate > 0) (1024L * 1_000_000L) / sampleRate else 21_333L
        }
    }

    fun clear(context: Context, jobKey: String) {
        try {
            jobDir(context, jobKey).deleteRecursively()
        } catch (e: Exception) {
            AppLogger.logSilentFailure(TAG, "Failed to clear job chunk directory", e)
        }
    }
}
