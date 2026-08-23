package com.example.data.video

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.util.AppLogger

/**
 * Runtime pressure checks used by [VideoCompressionWorker] to decide whether a chunk of
 * compression should keep running or pause. This is the piece that was entirely missing from
 * the original pipeline: ProcessingForegroundService held a wakelock and ran full-tilt
 * regardless of battery level, charging state, or device thermal status.
 *
 * These are point-in-time checks intended to be polled periodically (e.g. once per second)
 * from inside the transcode loop via VideoTranscoder.transcodeSegment's `shouldPause` callback,
 * not a push/callback API — Android has no unified push API across all of these signals that
 * works consistently back to minSdk 24.
 */
object BatteryThermalGuard {

    data class Snapshot(
        val batteryPercent: Int,
        val isCharging: Boolean,
        val isThermalThrottled: Boolean,
        val lowStorage: Boolean
    )

    /** Below this battery percentage, background (non-charging) compression should pause. */
    const val LOW_BATTERY_PAUSE_THRESHOLD = 15

    fun snapshot(context: Context): Snapshot {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val percent = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        } catch (e: Exception) {
            AppLogger.logSilentFailure("BatteryThermalGuard", "Failed to read battery capacity", e)
            100
        }
        val charging = try {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            AppLogger.logSilentFailure("BatteryThermalGuard", "Failed to read charging status", e)
            false
        }
        val thermalThrottled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            } else {
                false // No thermal API before API 29; can't detect, so never pause on this signal.
            }
        } catch (e: Exception) {
            AppLogger.logSilentFailure("BatteryThermalGuard", "Failed to read thermal status", e)
            false
        }
        val lowStorage = try {
            !com.example.data.util.FileValidator.checkAvailableStorageSpace(context, 100L * 1024 * 1024)
        } catch (e: Exception) {
            AppLogger.logSilentFailure("BatteryThermalGuard", "Failed to check storage headroom", e)
            false
        }
        return Snapshot(percent, charging, thermalThrottled, lowStorage)
    }

    /**
     * Whether compression should pause right now given the caller's willingness to run on
     * battery. When [allowOnBattery] is false (user selected "compress only while charging"),
     * any non-charging state pauses. When true, only genuinely low battery, thermal throttling,
     * or low storage pause — a full battery not on charge should still be allowed to compress.
     */
    fun shouldPause(context: Context, allowOnBattery: Boolean): Boolean {
        val snap = snapshot(context)
        if (snap.isThermalThrottled) return true
        if (snap.lowStorage) return true
        if (!snap.isCharging) {
            if (!allowOnBattery) return true
            if (snap.batteryPercent < LOW_BATTERY_PAUSE_THRESHOLD) return true
        }
        return false
    }
}
