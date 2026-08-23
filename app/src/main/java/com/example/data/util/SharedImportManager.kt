package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Holds one-shot share/shortcut payloads for navigation.
 * All mutations are synchronized to avoid races between onCreate/onNewIntent
 * and the first composition that consumes the URIs.
 */
object SharedImportManager {
    const val ROUTE_UNSUPPORTED = "unsupported_share"

    private val lock = Any()
    private val _importedUris = mutableListOf<Uri>()
    @Volatile
    private var _targetRoute: String? = null

    /** Snapshot of pending URIs (do not mutate the returned list). */
    val importedUris: List<Uri>
        get() = synchronized(lock) { _importedUris.toList() }

    var targetRoute: String?
        get() = _targetRoute
        set(value) { _targetRoute = value }

    fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return

        // 1. App Shortcuts
        val shortcutRoute = intent.getStringExtra("shortcut_route")
        if (!shortcutRoute.isNullOrBlank()) {
            synchronized(lock) {
                _targetRoute = shortcutRoute
            }
            return
        }

        // 2. Share sheet
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val uris = mutableListOf<Uri>()
        when (action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }
        if (uris.isEmpty()) return

        // Take persistable grants when possible so recovered tasks still open after process death
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Not all providers support persistable grants; ignore.
            } catch (_: Exception) {
            }
        }

        val first = uris.first()
        val mime = intent.type
            ?: context.contentResolver.getType(first)
            ?: ""
        val route = resolveRoute(context, first, mime)

        synchronized(lock) {
            _importedUris.clear()
            _importedUris.addAll(uris)
            _targetRoute = route
        }
    }

    private fun resolveRoute(context: Context, uri: Uri, mimeType: String): String {
        val lowerMime = mimeType.lowercase()
        val name = StorageManager.getFileNameFromUri(context, uri)?.lowercase() ?: ""

        return when {
            lowerMime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") ||
                name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".avi") -> "video"
            lowerMime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".m4a") ||
                name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".flac") ||
                name.endsWith(".aac") -> "audio"
            lowerMime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") ||
                name.endsWith(".heic") || name.endsWith(".bmp") -> "image"
            name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx") ||
                lowerMime.contains("officedocument") || lowerMime.contains("msword") ||
                lowerMime.contains("spreadsheet") || lowerMime.contains("presentation") -> "convert"
            lowerMime == "application/pdf" || name.endsWith(".pdf") -> "files"
            else -> {
                Log.w("SharedImportManager", "Unsupported shared file: mime=$lowerMime name=$name")
                ROUTE_UNSUPPORTED
            }
        }
    }

    fun consumeUris(): List<Uri> {
        synchronized(lock) {
            val list = _importedUris.toList()
            _importedUris.clear()
            return list
        }
    }

    fun consumeRoute(): String? {
        val route = _targetRoute
        _targetRoute = null
        return route
    }
}
