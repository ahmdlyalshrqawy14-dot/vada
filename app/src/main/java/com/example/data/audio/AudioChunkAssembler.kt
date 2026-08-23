package com.example.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.data.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Fixes a critical gap in the original chunked background-compression design: each chunk produced
 * by [AudioTranscoder] via [AudioCompressionWorker] was written to its own throwaway temp ".m4a"
 * file, and nothing ever combined those chunks or wrote a final file anywhere the user could reach
 * it - the *only* survivor was the last chunk's temp file, sitting in cache, un-persisted, and the
 * worker reported `Result.success()` regardless. In practice this meant "compress in background"
 * silently produced nothing for any audio longer than one chunk.
 *
 * This object gives the worker a genuine, process-death-safe way to accumulate chunks:
 *  1. After every chunk, [appendChunk] demuxes that chunk's single AAC track and appends its raw
 *     access units (as ADTS frames, which is a self-describing elementary-stream format) onto one
 *     running ".aac" file per job, keyed by jobKey so a WorkManager retry/re-run after process
 *     death picks up the same file instead of starting over. Plain file-append survives process
 *     death because there's no muxer session state to lose - unlike keeping a MediaMuxer open
 *     across separate doWork() invocations, which is not possible since Muxer state is in-memory
 *     only and each WorkManager run is a fresh process/coroutine.
 *  2. Once the last chunk is appended, [finalizeToContainer] remuxes the accumulated raw ADTS
 *     stream into one playable ".m4a" (MP4) container in a single MediaMuxer pass.
 *
 * Why ADTS as the intermediate format specifically: MediaExtractor recognizes raw ADTS AAC streams
 * natively (no wrapping needed), so appending is literally a file append, and re-reading it back
 * for the final remux needs no custom parsing.
 *
 * Known limitation, stated plainly rather than hidden: this assumes every chunk was encoded with
 * AAC-LC (the AAC profile Media3's DefaultEncoderFactory/MediaCodec produce by default when no
 * explicit profile is requested, which is what [AudioTranscoder] does). If that default ever
 * changes, the hardcoded ADTS profile field below needs to change with it - there is no runtime
 * negotiation of profile here.
 */
object AudioChunkAssembler {

    private const val TAG = "AudioChunkAssembler"
    private const val ADTS_HEADER_SIZE = 7

    // ISO 14496-3 Table 1.16 sampling_frequency_index. AAC only defines these rates; anything else
    // (e.g. a source with an exotic sample rate the encoder didn't already normalize) falls back to
    // the closest standard rate to avoid crashing, but is logged since it indicates a real mismatch.
    private val SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
        16000, 12000, 11025, 8000, 7350
    )

    private fun sampleRateIndexFor(sampleRate: Int): Int {
        val idx = SAMPLE_RATES.indexOf(sampleRate)
        if (idx >= 0) return idx
        AppLogger.logSilentFailure(
            TAG, "Unrecognized AAC sample rate $sampleRate Hz; falling back to nearest standard rate",
            IllegalArgumentException("sampleRate=$sampleRate")
        )
        return SAMPLE_RATES.indices.minByOrNull { kotlin.math.abs(SAMPLE_RATES[it] - sampleRate) } ?: 4
    }

    /** Stable, per-job accumulation file. Cache-dir-backed so it survives a WorkManager retry. */
    fun runningStreamFile(context: Context, jobKey: String): File {
        val safeName = jobKey.hashCode().toString()
        return File(context.cacheDir, "vada_audio_job_${safeName}.aac")
    }

    /**
     * Demuxes [chunkFile]'s single audio track and appends every sample to the job's running
     * ADTS file, each prefixed with a correct ADTS header derived from that chunk's own
     * MediaFormat (sample rate / channel count can legitimately be identical across chunks of the
     * same source, but we re-read per chunk rather than assume, since a source could in principle
     * combine tracks with different formats across an edit).
     *
     * Returns false (and appends nothing) if [chunkFile] has no readable audio track - this
     * indicates a genuinely corrupt chunk export, and the worker should treat it as a chunk
     * failure (retry) rather than silently producing a gap in the assembled audio.
     */
    fun appendChunk(context: Context, jobKey: String, chunkFile: File): Boolean {
        if (!chunkFile.exists() || chunkFile.length() <= 0) return false

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(chunkFile.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) {
                AppLogger.logError(TAG, "No audio track found in chunk ${chunkFile.name}", IllegalStateException("no audio track"))
                return false
            }
            extractor.selectTrack(audioTrackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val freqIdx = sampleRateIndexFor(sampleRate)

            val maxInputSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024 // generous fallback; grown below if a sample genuinely exceeds it
            }
            var sampleBuffer = ByteBuffer.allocate(maxInputSize)

            val outFile = runningStreamFile(context, jobKey)
            FileOutputStream(outFile, /* append = */ true).use { out ->
                val header = ByteArray(ADTS_HEADER_SIZE)
                while (true) {
                    sampleBuffer.clear()
                    var sampleSize = extractor.readSampleData(sampleBuffer, 0)
                    if (sampleSize < 0) break

                    if (sampleSize > sampleBuffer.capacity()) {
                        // Rare, but don't silently truncate audio - grow and retry the same sample.
                        sampleBuffer = ByteBuffer.allocate(sampleSize)
                        sampleSize = extractor.readSampleData(sampleBuffer, 0)
                    }

                    writeAdtsHeader(header, ADTS_HEADER_SIZE + sampleSize, freqIdx, channelCount)
                    out.write(header)

                    sampleBuffer.rewind()
                    val bytes = ByteArray(sampleSize)
                    sampleBuffer.get(bytes, 0, sampleSize)
                    out.write(bytes)

                    extractor.advance()
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Failed to append chunk ${chunkFile.name} to job stream", e)
            false
        } finally {
            try { extractor.release() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Failed to release MediaExtractor", e)
            }
        }
    }

    private fun writeAdtsHeader(header: ByteArray, frameLengthWithHeader: Int, freqIdx: Int, channelCount: Int) {
        val profileMinusOne = 1 // AAC-LC (objectType 2) -> ADTS "profile" field = objectType - 1
        header[0] = 0xFF.toByte()
        header[1] = 0xF9.toByte() // MPEG-4, Layer 0, protection_absent=1 (no CRC)
        header[2] = ((profileMinusOne shl 6) + (freqIdx shl 2) + (channelCount shr 2)).toByte()
        header[3] = (((channelCount and 3) shl 6) + (frameLengthWithHeader shr 11)).toByte()
        header[4] = ((frameLengthWithHeader and 0x7FF) shr 3).toByte()
        header[5] = ((((frameLengthWithHeader and 7) shl 5) + 0x1F)).toByte()
        header[6] = 0xFC.toByte()
    }

    /**
     * Remuxes the job's accumulated raw ADTS stream into a playable ".m4a" container at
     * [outputFile]. Call only after the last chunk has been appended. Does not delete the source
     * ADTS accumulation file - the caller (worker) owns cleanup once it has confirmed the final
     * output was persisted via StorageManager.saveFinalOutput.
     */
    fun finalizeToContainer(context: Context, jobKey: String, outputFile: File): Boolean {
        val streamFile = runningStreamFile(context, jobKey)
        if (!streamFile.exists() || streamFile.length() <= 0) {
            AppLogger.logError(TAG, "No accumulated audio stream to finalize for job", IllegalStateException(jobKey))
            return false
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(streamFile.absolutePath)
            if (extractor.trackCount == 0) {
                AppLogger.logError(TAG, "Accumulated ADTS stream has no tracks", IllegalStateException(jobKey))
                return false
            }
            val format = extractor.getTrackFormat(0)
            extractor.selectTrack(0)

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(format)
            muxer.start()

            val bufferInfo = android.media.MediaCodec.BufferInfo()
            val maxInputSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024
            }
            var buffer = ByteBuffer.allocate(maxInputSize)

            while (true) {
                buffer.clear()
                var sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                if (sampleSize > buffer.capacity()) {
                    buffer = ByteBuffer.allocate(sampleSize)
                    sampleSize = extractor.readSampleData(buffer, 0)
                }
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                // Every AAC access unit is independently decodable, so marking each as a sync
                // sample is correct for audio (unlike video, where only real I-frames may be
                // marked this way) - but read it from the extractor rather than hardcoding, since
                // that's the actual source of truth rather than an assumption.
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            true
        } catch (e: Exception) {
            AppLogger.logError(TAG, "Failed to finalize accumulated audio stream into container", e)
            if (outputFile.exists()) outputFile.delete()
            false
        } finally {
            try { muxer?.stop() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "MediaMuxer.stop() failed (stream may be malformed)", e)
            }
            try { muxer?.release() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Failed to release MediaMuxer", e)
            }
            try { extractor.release() } catch (e: Exception) {
                AppLogger.logSilentFailure(TAG, "Failed to release MediaExtractor", e)
            }
        }
    }

    fun clear(context: Context, jobKey: String) {
        try { runningStreamFile(context, jobKey).delete() } catch (e: Exception) {
            AppLogger.logSilentFailure(TAG, "Failed to delete job accumulation file", e)
        }
    }
}
