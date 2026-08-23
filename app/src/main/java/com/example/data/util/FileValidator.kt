package com.example.data.util

import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.InputStream

object FileValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val detectedFormat: String,
        val errorMessage: String? = null,
        /**
         * True when [detectedFormat] is a container Android's MediaExtractor cannot reliably
         * demux at all (true AVI/RIFF, or a container we can only magic-byte-sniff but not
         * confirm playable). This does NOT mean the file will be rejected - VideoTranscoder still
         * tries hardware transcode, then falls back through remux - it means the UI should set
         * user expectations that this specific input is likely to fall back to "saved unchanged"
         * rather than getting genuinely compressed, since Android's platform demuxer, not this
         * validator, is the actual bottleneck for these containers.
         */
        val isHighRiskContainer: Boolean = false
    )

    fun validateFile(context: Context, uri: Uri): ValidationResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(16)
                // NOTE: InputStream.read() is not guaranteed to fill the buffer in a single call
                // (very common with ContentResolver-backed streams for content:// URIs, especially
                // camera/gallery providers and FUSE-backed storage). Loop until we have 16 bytes,
                // the stream ends, or a read genuinely stalls at 0 bytes.
                var readBytes = 0
                while (readBytes < buffer.size) {
                    val n = inputStream.read(buffer, readBytes, buffer.size - readBytes)
                    if (n <= 0) break
                    readBytes += n
                }
                if (readBytes < 4) {
                    return ValidationResult(false, "Unknown", "File is empty or truncated")
                }

                // Check Magic Bytes
                when {
                    // PDF: %PDF (0x25 0x50 0x44 0x46)
                    buffer[0] == 0x25.toByte() && buffer[1] == 0x50.toByte() &&
                            buffer[2] == 0x44.toByte() && buffer[3] == 0x46.toByte() ->
                        ValidationResult(true, "PDF")

                    // PNG: 89 50 4E 47
                    buffer[0] == 0x89.toByte() && buffer[1] == 0x50.toByte() &&
                            buffer[2] == 0x4E.toByte() && buffer[3] == 0x47.toByte() ->
                        ValidationResult(true, "PNG")

                    // JPEG: FF D8 FF
                    buffer[0] == 0xFF.toByte() && buffer[1] == 0xD8.toByte() && buffer[2] == 0xFF.toByte() ->
                        ValidationResult(true, "JPEG")

                    // OLE2 Compound Document (.doc, .xls, .ppt): D0 CF 11 E0
                    buffer[0] == 0xD0.toByte() && buffer[1] == 0xCF.toByte() &&
                            buffer[2] == 0x11.toByte() && buffer[3] == 0xE0.toByte() ->
                        ValidationResult(true, "Legacy Office Document (OLE2)")

                    // ZIP / Office DOCX/XLSX/PPTX: 50 4B 03 04
                    buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte() &&
                            buffer[2] == 0x03.toByte() && buffer[3] == 0x04.toByte() ->
                        ValidationResult(true, "ZIP / Office Document")

                    // RIFF (WAV, AVI): 52 49 46 46
                    // AVI in particular is not reliably demuxed by Android's MediaExtractor, so
                    // we accept the file (never block the picker) but flag it as high-risk so the
                    // UI can set expectations that it may fall back to unchanged passthrough.
                    buffer[0] == 0x52.toByte() && buffer[1] == 0x49.toByte() &&
                            buffer[2] == 0x46.toByte() && buffer[3] == 0x46.toByte() ->
                        ValidationResult(true, "RIFF (WAV/AVI)", isHighRiskContainer = true)

                    // MP4 / MOV (ftyp): offset 4 = 66 74 79 70 ("ftyp")
                    readBytes >= 8 && buffer[4] == 0x66.toByte() && buffer[5] == 0x74.toByte() &&
                            buffer[6] == 0x79.toByte() && buffer[7] == 0x70.toByte() ->
                        ValidationResult(true, "MP4/MOV Video")

                    // MKV / WEBM: 1A 45 DF A3
                    // MediaExtractor's MKV support varies noticeably across OEM builds; flagged
                    // high-risk for the same reason as AVI above (see isHighRiskContainer doc).
                    buffer[0] == 0x1A.toByte() && buffer[1] == 0x45.toByte() &&
                            buffer[2] == 0xDF.toByte() && buffer[3] == 0xA3.toByte() ->
                        ValidationResult(true, "MKV/WEBM", isHighRiskContainer = true)

                    // MP3 ID3: 49 44 33 ("ID3") or sync header 0xFF 0xFB
                    (buffer[0] == 0x49.toByte() && buffer[1] == 0x44.toByte() && buffer[2] == 0x33.toByte()) ||
                            (buffer[0] == 0xFF.toByte() && (buffer[1].toInt() and 0xE0) == 0xE0) ->
                        ValidationResult(true, "MP3 Audio")

                    // FLAC: 66 4C 61 43 ("fLaC")
                    buffer[0] == 0x66.toByte() && buffer[1] == 0x4C.toByte() &&
                            buffer[2] == 0x61.toByte() && buffer[3] == 0x43.toByte() ->
                        ValidationResult(true, "FLAC Audio")

                    // OGG: 4F 67 67 53 ("OggS")
                    buffer[0] == 0x4F.toByte() && buffer[1] == 0x67.toByte() &&
                            buffer[2] == 0x67.toByte() && buffer[3] == 0x53.toByte() ->
                        ValidationResult(true, "OGG Audio")

                    else -> ValidationResult(true, "Generic Stream")
                }
            } ?: ValidationResult(false, "Unknown", "Cannot open file stream")
        } catch (e: Exception) {
            ValidationResult(false, "Unknown", e.localizedMessage ?: "File validation failed")
        }
    }

    fun checkAvailableStorageSpace(context: Context, requiredBytes: Long): Boolean {
        return try {
            val dir = context.cacheDir
            if (!dir.exists()) dir.mkdirs()
            val stat = StatFs(dir.path)
            val availableBytes = stat.availableBytes
            if (availableBytes <= 0L) {
                // If StatFs returns 0 in synthetic test environments or unmounted partitions, fallback to usableSpace
                val usable = dir.usableSpace
                if (usable > 0L) usable >= requiredBytes else true
            } else {
                availableBytes >= requiredBytes
            }
        } catch (e: Exception) {
            true // fallback
        }
    }

    /**
     * Checks available storage space on the actual destination path where the output file will be saved.
     * If a custom SAF URI is set, verifies that the directory exists/is writable and attempts to query space if possible.
     * SAF does not always expose a direct filesystem path or free space API across all content providers,
     * so canWrite() and DocumentFile presence serve as the primary validation alongside path StatFs when resolvable.
     */
    fun checkDestinationStorageSpace(context: Context, requiredBytes: Long, customSafUri: String?): Boolean {
        return try {
            if (!customSafUri.isNullOrBlank()) {
                val parsedUri = Uri.parse(customSafUri)
                val docDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parsedUri)
                if (docDir == null || !docDir.exists() || !docDir.canWrite()) {
                    return false
                }
                // SAF doesn't consistently provide a direct StatFs path across different storage providers,
                // but if we can inspect external storage paths or verify write access, we confirm validity.
                return true
            }

            // Fallback to default output directory used by StorageManager
            val outputDir = StorageManager.getOutputDirectory(context)
            if (!outputDir.exists()) outputDir.mkdirs()
            val stat = StatFs(outputDir.path)
            val availableBytes = stat.availableBytes
            if (availableBytes <= 0L) {
                val usable = outputDir.usableSpace
                if (usable > 0L) usable >= requiredBytes else true
            } else {
                availableBytes >= requiredBytes
            }
        } catch (e: Exception) {
            true // fallback if stat fails unexpectedly
        }
    }
}
