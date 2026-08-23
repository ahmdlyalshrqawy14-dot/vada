package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

import com.example.data.queue.TaskQueueManager
import kotlinx.coroutines.flow.firstOrNull

import com.example.data.util.AppLogger

class ProcessingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "voda_processing_channel"
        const val NOTIFICATION_ID = 8801

        const val ACTION_START = "com.example.voda.START"
        const val ACTION_STOP = "com.example.voda.STOP"
        const val ACTION_CANCEL = "com.example.voda.CANCEL"
        const val ACTION_TOGGLE_PAUSE = "com.example.voda.TOGGLE_PAUSE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_PROCESSOR = "extra_processor"

        private const val WAKELOCK_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        private const val WAKELOCK_RENEWAL_INTERVAL_MS = 15 * 60 * 1000L // Renew every 15 minutes

        fun startService(context: Context, title: String, progress: Int, processorType: String) {
            val intent = Intent(context, ProcessingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_PROCESSOR, processorType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProcessingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var lastWakeLockRenewalTime: Long = 0L

    // Cached prefs so every progress tick does not block the service thread with runBlocking + DataStore.
    @Volatile private var cachedNotificationsEnabled: Boolean = true
    @Volatile private var cachedLangCode: String = "ar"
    @Volatile private var prefsCacheLoaded: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        refreshPrefsCache()
    }

    private fun refreshPrefsCache() {
        try {
            val prefs = com.example.data.prefs.PreferencesManager.getInstance(applicationContext)
            cachedNotificationsEnabled = kotlinx.coroutines.runBlocking {
                prefs.notificationsEnabledFlow.firstOrNull() ?: true
            }
            cachedLangCode = kotlinx.coroutines.runBlocking {
                prefs.languageCode.firstOrNull() ?: "ar"
            }
            prefsCacheLoaded = true
        } catch (e: Exception) {
            AppLogger.logSilentFailure("ProcessingForegroundService", "Failed to cache notification prefs", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            releaseWakeLock()
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voda:ProcessingWakeLock").apply {
                acquire(WAKELOCK_DURATION_MS)
            }
            lastWakeLockRenewalTime = System.currentTimeMillis()
        } catch (e: Exception) {
            // خطأ حرج: قد يؤدي إلى سكون الجهاز أثناء معالجة الملفات الكبيرة
            AppLogger.logError("ProcessingForegroundService", "فشل حجز WakeLock لمنع نوم الجهاز أثناء المعالجة", e)
        }
    }

    private fun checkAndRenewWakeLock() {
        val now = System.currentTimeMillis()
        if (wakeLock == null || wakeLock?.isHeld != true || (now - lastWakeLockRenewalTime) >= WAKELOCK_RENEWAL_INTERVAL_MS) {
            acquireWakeLock()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // خطأ غير حرج: قد يكون الـ WakeLock قد انتهى أو حُرّر مسبقاً
            AppLogger.logSilentFailure("ProcessingForegroundService", "فشل تحرير الـ WakeLock", e)
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                val activeList = TaskQueueManager.getInstance(applicationContext).activeTasks.value
                activeList.forEach { task ->
                    TaskQueueManager.getInstance(applicationContext).cancelTask(task.id)
                }
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                TaskQueueManager.getInstance(applicationContext).togglePauseActiveTask()
            }
            else -> {
                checkAndRenewWakeLock()
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Vada Processing"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val processor = intent?.getStringExtra(EXTRA_PROCESSOR) ?: "Software"

        val notification = buildNotification(title, progress, processor)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun buildNotification(title: String, progress: Int, processor: String): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, ProcessingForegroundService::class.java).apply { action = ACTION_TOGGLE_PAUSE }
        val pendingPauseIntent = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ProcessingForegroundService::class.java).apply { action = ACTION_CANCEL }
        val pendingCancelIntent = PendingIntent.getService(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!prefsCacheLoaded) {
            refreshPrefsCache()
        }
        val strings: com.example.data.i18n.AppStrings =
            if (cachedLangCode == "ar") com.example.data.i18n.ArabicStrings else com.example.data.i18n.EnglishStrings

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vada — $title")
            .setContentText("$processor • $progress%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingMainIntent)
            .addAction(android.R.drawable.ic_media_pause, strings.notificationPauseResume, pendingPauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, strings.notificationCancel, pendingCancelIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (!cachedNotificationsEnabled) {
            builder.setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setNotificationSilent()
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vada Processing Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress during media/document processing"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}
