package com.example.data.model

import org.json.JSONObject

/**
 * Serializable processing options so a queued task can be rebuilt after process death
 * with the same preset/trim/rotate/format the user actually chose — not a silent copy.
 */
data class TaskParams(
    val preset: String = CompressionPreset.MEDIUM.name,
    val muteAudio: Boolean = false,
    val quality: Int = 65,
    val maxDimension: Int = 1080,
    val videoBitrateKbps: Int = 2000,
    val rotateDegrees: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
    val combineToPdf: Boolean = false,
    val outputFormat: String? = null,
    val pdfOperation: String? = null,
    val splitMode: String? = null,
    val rangeText: String? = null,
    val extractFromVideo: Boolean = false,
    val isCustom: Boolean = false,
    val conversionMode: String = "PRINT"
) {
    fun toJson(): String = JSONObject().apply {
        put("preset", preset)
        put("muteAudio", muteAudio)
        put("quality", quality)
        put("maxDimension", maxDimension)
        put("videoBitrateKbps", videoBitrateKbps)
        put("rotateDegrees", rotateDegrees)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        put("combineToPdf", combineToPdf)
        put("outputFormat", outputFormat ?: JSONObject.NULL)
        put("pdfOperation", pdfOperation ?: JSONObject.NULL)
        put("splitMode", splitMode ?: JSONObject.NULL)
        put("rangeText", rangeText ?: JSONObject.NULL)
        put("extractFromVideo", extractFromVideo)
        put("isCustom", isCustom)
        put("conversionMode", conversionMode)
    }.toString()

    companion object {
        fun fromJson(raw: String?): TaskParams {
            if (raw.isNullOrBlank()) return TaskParams()
            return try {
                val o = JSONObject(raw)
                TaskParams(
                    preset = o.optString("preset", CompressionPreset.MEDIUM.name),
                    muteAudio = o.optBoolean("muteAudio", false),
                    quality = o.optInt("quality", 65),
                    maxDimension = o.optInt("maxDimension", 1080),
                    videoBitrateKbps = o.optInt("videoBitrateKbps", 2000),
                    rotateDegrees = o.optInt("rotateDegrees", 0),
                    trimStartMs = o.optLong("trimStartMs", 0L),
                    trimEndMs = o.optLong("trimEndMs", -1L),
                    combineToPdf = o.optBoolean("combineToPdf", false),
                    outputFormat = o.optString("outputFormat").takeIf { it.isNotBlank() && it != "null" },
                    pdfOperation = o.optString("pdfOperation").takeIf { it.isNotBlank() && it != "null" },
                    splitMode = o.optString("splitMode").takeIf { it.isNotBlank() && it != "null" },
                    rangeText = o.optString("rangeText").takeIf { it.isNotBlank() && it != "null" },
                    extractFromVideo = o.optBoolean("extractFromVideo", false),
                    isCustom = o.optBoolean("isCustom", false),
                    conversionMode = o.optString("conversionMode", "PRINT").ifBlank { "PRINT" }
                )
            } catch (_: Exception) {
                TaskParams()
            }
        }
    }
}
