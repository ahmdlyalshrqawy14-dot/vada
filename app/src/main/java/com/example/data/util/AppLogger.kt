package com.example.data.util

import android.util.Log

object AppLogger {

    /**
     * Logs non-critical failures where operation can safely continue,
     * but documentation is required for debugging and diagnostics.
     */
    fun logSilentFailure(tag: String, context: String, e: Throwable) {
        Log.w(tag, "فشل غير حرج: $context (${e.javaClass.simpleName}: ${e.localizedMessage})", e)
    }

    /**
     * Logs critical or high-priority failures.
     */
    fun logError(tag: String, context: String, e: Throwable) {
        Log.e(tag, "فشل حرج: $context (${e.javaClass.simpleName}: ${e.localizedMessage})", e)
    }
}
