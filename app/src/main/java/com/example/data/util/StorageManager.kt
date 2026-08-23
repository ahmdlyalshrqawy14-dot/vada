package com.example.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.i18n.AppStrings
import com.example.data.i18n.StringsArabic
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object StorageManager {

    private const val TAG = "StorageManager"
    private const val APP_FOLDER_NAME = "Vada"

    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file size from uri: $uri", e)
            0L
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query display name from uri: $uri", e)
        }
        return name
    }

    fun getOutputDirectory(context: Context): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vadaFolder = File(downloadsDir, APP_FOLDER_NAME)
        if (!vadaFolder.exists()) {
            vadaFolder.mkdirs()
        }
        if (vadaFolder.exists() && vadaFolder.canWrite()) {
            return vadaFolder
        }

        // Fallback to Documents/Vada
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val vadaDocs = File(docsDir, APP_FOLDER_NAME)
        if (!vadaDocs.exists()) {
            vadaDocs.mkdirs()
        }
        if (vadaDocs.exists() && vadaDocs.canWrite()) {
            return vadaDocs
        }

        // Ultimate fallback to context external files
        val appDir = File(context.getExternalFilesDir(null), APP_FOLDER_NAME)
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    fun resolveFileCollision(targetDir: File, baseName: String, extension: String): File {
        val cleanExt = if (extension.startsWith(".")) extension else ".$extension"
        var candidate = File(targetDir, "$baseName$cleanExt")
        var count = 1
        while (candidate.exists()) {
            candidate = File(targetDir, "$baseName ($count)$cleanExt")
            count++
        }
        return candidate
    }

    sealed class SavedOutputResult {
        data class LocalFile(val file: File) : SavedOutputResult() {
            override val name: String get() = file.name
            override val pathOrUri: String get() = file.absolutePath
            override val length: Long get() = file.length()
        }
        data class SafUri(val uri: Uri, override val name: String, override val length: Long) : SavedOutputResult() {
            override val pathOrUri: String get() = uri.toString()
        }

        abstract val name: String
        abstract val pathOrUri: String
        abstract val length: Long
    }

    fun saveFinalOutput(
        context: Context,
        tempFile: File,
        desiredName: String,
        extension: String,
        customSafUri: String? = null,
        strings: AppStrings? = null
    ): SavedOutputResult {
        val str = strings ?: StringsArabic
        val cleanExt = if (extension.startsWith(".")) extension else ".$extension"
        val fullFileName = "$desiredName$cleanExt"
        val mimeType = getMimeType(extension)

        // Strategy 0: Custom SAF folder selected in settings
        if (!customSafUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customSafUri)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                if (docDir != null && docDir.exists() && docDir.canWrite()) {
                    val newFile = docDir.createFile(mimeType, fullFileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                            tempFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        val finalSize = tempFile.length()
                        return SavedOutputResult.SafUri(
                            uri = newFile.uri,
                            name = fullFileName,
                            length = finalSize
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to SAF custom directory: $customSafUri", e)
            }
        }

        // Strategy 1: MediaStore for Android 10+ (Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER_NAME")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // Clear pending flag
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)

                    // Resolve the actual saved display name (MediaStore may have renamed it on collision)
                    val actualName = try {
                        context.contentResolver.query(
                            uri,
                            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                            null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                                if (idx != -1) cursor.getString(idx) else null
                            } else null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to query actual MediaStore display name", e)
                        null
                    }

                    // Return the real content:// Uri returned by MediaStore directly, rather than
                    // guessing a local File path (which can silently point at the wrong/nonexistent
                    // file when the destination folder isn't directly writable on API 30+, or when
                    // MediaStore auto-renamed the file due to a name collision).
                    return SavedOutputResult.SafUri(
                        uri = uri,
                        name = actualName ?: fullFileName,
                        length = tempFile.length()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore insert failed on Android Q+", e)
            }
        }

        // Strategy 2: Direct file copy (Android 9 and below, or MediaStore unavailable/failed)
        var savedFile: File? = null
        try {
            val targetDir = getOutputDirectory(context)
            val finalFile = resolveFileCollision(targetDir, desiredName, extension)
            tempFile.copyTo(finalFile, overwrite = true)
            savedFile = finalFile
        } catch (e: SecurityException) {
            Log.e(TAG, "Storage permission denied during direct file copy", e)
            throw java.io.IOException(str.errorStoragePermissionDenied, e)
        } catch (e: Exception) {
            Log.e(TAG, "Direct file copy failed", e)
            if (savedFile == null) {
                throw java.io.IOException(str.errorSaveToDownloadsFailed(e.localizedMessage ?: ""))
            }
        }

        val resultFile = savedFile ?: throw java.io.IOException(str.errorSaveFinalOutputFailed)
        return SavedOutputResult.LocalFile(resultFile)
    }

    fun createTempFile(context: Context, prefix: String = "vada_", extension: String): File {
        val cleanExt = if (extension.startsWith(".")) extension else ".$extension"
        return File.createTempFile("${prefix}${System.currentTimeMillis()}_", cleanExt, context.cacheDir)
    }

    fun cleanTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("vada_") || file.name.startsWith("voda_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning temp files", e)
        }
    }

    fun getMimeType(extension: String): String {
        val ext = extension.lowercase().replace(".", "")
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "*/*"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Copies a picker/share URI into app cache so WorkManager and the queue still have a
     * readable file after the grant is revoked (GET_CONTENT is not persistable).
     */
    fun copyUriToCache(context: Context, uri: Uri, prefix: String = "vada_src_"): Pair<Uri, File> {
        val name = getFileNameFromUri(context, uri) ?: "file"
        val ext = name.substringAfterLast('.', "bin").ifBlank { "bin" }
        val dest = createTempFile(context, prefix, ext)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open source file")
        return Uri.fromFile(dest) to dest
    }
}
