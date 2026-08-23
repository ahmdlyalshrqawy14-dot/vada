package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Registers our own androidx.startup-driven WorkManager configuration instead of relying on
 * WorkManager's default ContentProvider-based auto-initialization. This is required as soon as
 * a custom Configuration is needed (e.g. a non-default logging level for debugging chunked
 * compression retries) and is the standard place to eagerly initialize WorkManager so the first
 * VideoCompressionWorker.enqueue() call from VideoScreen doesn't race a lazy first-touch init.
 *
 * See AndroidManifest.xml: the default androidx.work.WorkManagerInitializer <provider> is
 * disabled via tools:node="remove" because Configuration.Provider takes over that role.
 */
class VadaApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Explicit eager init (rather than relying purely on first-use lazy init) so background
        // video compression jobs enqueued right after a cold start are never lost to a race
        // between WorkManager's internal database setup and the first enqueueUniqueWork() call.
        WorkManager.getInstance(this)
    }
}
