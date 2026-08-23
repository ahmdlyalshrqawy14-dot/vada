package com.example.data.model

/**
 * How Office → PDF is produced on-device.
 * PRINT: system print engine (text-friendly, balanced size after optimize)
 * SCREENSHOT: internal page bitmaps (smaller files, raster-only)
 */
enum class ConversionMode {
    PRINT,
    SCREENSHOT;

    companion object {
        fun fromName(raw: String?): ConversionMode {
            return try {
                valueOf((raw ?: "PRINT").uppercase())
            } catch (_: Exception) {
                PRINT
            }
        }
    }
}
