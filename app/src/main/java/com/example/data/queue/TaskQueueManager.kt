package com.example.data.queue

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.HistoryEntity
import com.example.data.db.HistoryRepository
import com.example.data.db.PendingTaskEntity
import com.example.data.db.PendingTaskRepository
import com.example.data.i18n.getAppStrings
import com.example.data.model.ProcessingTask
import com.example.data.model.ProcessorType
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.prefs.PreferencesManager
import com.example.data.util.AppLogger
import com.example.data.util.FileValidator
import com.example.data.util.StorageManager
import com.example.service.ProcessingForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.withContext

sealed class DynamicIslandState {
    object Idle : DynamicIslandState()
    data class Processing(val title: String, val progress: Float, val processorType: ProcessorType) : DynamicIslandState()
    data class Success(val title: String, val fileName: String, val message: String? = null, val isWarning: Boolean = false) : DynamicIslandState()
    data class Error(val title: String, val message: String) : DynamicIslandState()
}

class TaskQueueManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: TaskQueueManager? = null

        // فاصل مسارات الـ URIs عند تخزينها كنص واحد في PendingTaskEntity.sourceUris
        private const val PENDING_TASK_URI_DELIMITER = "\n"

        fun getInstance(context: Context): TaskQueueManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TaskQueueManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // Determine max concurrent tasks: half of available processors, safely bounded between 1 and 2
    val maxConcurrentTasks: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2)

    private val scope = CoroutineScope(Dispatchers.Default)
    private val historyRepository = HistoryRepository(AppDatabase.getInstance(context).historyDao())
    private val pendingTaskRepository = PendingTaskRepository(AppDatabase.getInstance(context).pendingTaskDao())

    private val _queueList = MutableStateFlow<List<ProcessingTask>>(emptyList())
    val queueList: StateFlow<List<ProcessingTask>> = _queueList.asStateFlow()

    private val _activeTasks = MutableStateFlow<List<ProcessingTask>>(emptyList())
    val activeTasks: StateFlow<List<ProcessingTask>> = _activeTasks.asStateFlow()

    // Backward compatibility: always tracks the head of _activeTasks (updates when activeTasks change).
    private val _activeTask = MutableStateFlow<ProcessingTask?>(null)
    val activeTask: StateFlow<ProcessingTask?> = _activeTask.asStateFlow()

    private val _islandState = MutableStateFlow<DynamicIslandState>(DynamicIslandState.Idle)
    val islandState: StateFlow<DynamicIslandState> = _islandState.asStateFlow()

    private val executionJobs = ConcurrentHashMap<String, Job>()
    private val taskPauseFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val _isPausedFlow = MutableStateFlow(false)

    private fun syncActiveTaskHead() {
        _activeTask.value = _activeTasks.value.firstOrNull()
    }

    init {
        // عند إقلاع التطبيق (أول استدعاء لـ getInstance): استرجع أي مهام كانت بانتظار الدور
        // ولم تبدأ تنفيذها قبل إغلاق التطبيق، لتفادي فقدانها بصمت.
        scope.launch(Dispatchers.IO) {
            restorePendingTasksFromDb()
        }
    }

    /**
     * يستعيد المهام المعلّقة (queueList) المحفوظة في قاعدة البيانات من جلسة سابقة، ويضيفها
     * تلقائياً إلى _queueList، وينبّه المستخدم بعددها.
     *
     * ملاحظة مهمة: لا يمكن استرجاع إعدادات المعالجة الأصلية (كالضغط أو الصيغة المستهدفة) التي
     * اختارها المستخدم، لأنها كانت جزءاً من دالة executeBlock التي لا يمكن تسلسلها إلى قاعدة
     * بيانات. لذلك تُعاد هذه المهام بمنطق تنفيذ بديل (نسخ الملف المصدر كما هو) حتى لا تُفقد
     * بيانات المستخدم بالكامل بصمت، مع الإبقاء على العنوان واسم الملف الناتج والامتداد الأصليين.
     */
    private suspend fun restorePendingTasksFromDb() {
        try {
            val savedEntities = pendingTaskRepository.getAll()
            if (savedEntities.isEmpty()) return

            val recoveredTasks = savedEntities.mapNotNull { entity -> buildRecoveredTask(entity) }
            if (recoveredTasks.isEmpty()) return

            _queueList.value = _queueList.value + recoveredTasks

            withContext(Dispatchers.Main) {
                val lang = try {
                    PreferencesManager.getInstance(context).languageFlow.firstOrNull() ?: "ar"
                } catch (_: Exception) { "ar" }
                val msg = if (lang == "en") {
                    "Restored ${recoveredTasks.size} pending task(s)"
                } else {
                    "تم استرجاع ${recoveredTasks.size} مهمة كانت معلّقة"
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }

            processNextIfNeeded()
        } catch (e: Exception) {
            AppLogger.logSilentFailure("TaskQueueManager", "فشل استرجاع المهام المعلقة من قاعدة البيانات", e)
        }
    }

    /**
     * يبني مهمة [ProcessingTask] قابلة للتنفيذ من سجل [PendingTaskEntity] محفوظ مسبقاً.
     * يُعيد null إذا كانت بيانات السجل غير صالحة (نوع مهمة غير معروف أو لا توجد أي URIs صالحة).
     */
    private fun buildRecoveredTask(entity: PendingTaskEntity): ProcessingTask? {
        val taskType = try {
            TaskType.valueOf(entity.taskType)
        } catch (e: Exception) {
            AppLogger.logSilentFailure("TaskQueueManager", "نوع مهمة غير معروف عند استرجاعها: ${entity.taskType}", e)
            return null
        }

        val uris = entity.sourceUris.split(PENDING_TASK_URI_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull { raw ->
                try {
                    Uri.parse(raw)
                } catch (e: Exception) {
                    AppLogger.logSilentFailure("TaskQueueManager", "تعذر تحليل URI محفوظ عند استرجاع مهمة معلقة: $raw", e)
                    null
                }
            }
        if (uris.isEmpty()) return null

        val tempFiles = mutableListOf<File>()
        val paramsJson = entity.paramsJson

        return ProcessingTask(
            id = entity.taskId,
            title = entity.title,
            subtitle = entity.subtitle,
            taskType = taskType,
            sourceUris = uris,
            outputFileName = entity.outputFileName,
            outputExtension = entity.outputExtension,
            tempFilesToClean = tempFiles,
            paramsJson = paramsJson,
            executeBlock = { onProgress, onProcessorChanged, onSkipped, onOutcome, shouldPause ->
                onProgress(0.05f)
                val rebuilt = TaskRebuilder.rebuildExecute(
                    context = context,
                    taskType = taskType,
                    uris = uris,
                    params = com.example.data.model.TaskParams.fromJson(paramsJson),
                    outputExtension = entity.outputExtension
                )
                tempFiles.add(rebuilt)
                onProgress(1.0f)
                rebuilt
            }
        )
    }

    private fun persistPendingTask(task: ProcessingTask) {
        scope.launch(Dispatchers.IO) {
            try {
                pendingTaskRepository.insert(
                    PendingTaskEntity(
                        taskId = task.id,
                        title = task.title,
                        subtitle = task.subtitle,
                        taskType = task.taskType.name,
                        sourceUris = task.sourceUris.joinToString(PENDING_TASK_URI_DELIMITER) { it.toString() },
                        outputFileName = task.outputFileName,
                        outputExtension = task.outputExtension,
                        paramsJson = task.paramsJson
                    )
                )
            } catch (e: Exception) {
                AppLogger.logSilentFailure("TaskQueueManager", "فشل حفظ المهمة المعلقة في قاعدة البيانات: ${task.id}", e)
            }
        }
    }

    private fun removePendingTask(taskId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                pendingTaskRepository.deleteById(taskId)
            } catch (e: Exception) {
                AppLogger.logSilentFailure("TaskQueueManager", "فشل حذف المهمة المعلقة من قاعدة البيانات: $taskId", e)
            }
        }
    }

    fun addTask(task: ProcessingTask) {
        _queueList.value = _queueList.value + task
        persistPendingTask(task)
        processNextIfNeeded()
    }

    fun cancelTask(taskId: String) {
        val activeTaskToCancel = _activeTasks.value.find { it.id == taskId }
        if (activeTaskToCancel != null) {
            taskPauseFlows[taskId]?.value = false
            taskPauseFlows.remove(taskId)
            executionJobs.remove(taskId)?.cancel()

            _activeTasks.value = _activeTasks.value.filter { it.id != taskId }
            syncActiveTaskHead()
            cleanupTaskTemps(activeTaskToCancel)

            updateIslandAndForegroundService()
            processNextIfNeeded()
        } else {
            val taskToCancel = _queueList.value.find { it.id == taskId }
            if (taskToCancel != null) {
                cleanupTaskTemps(taskToCancel)
                _queueList.value = _queueList.value.filter { it.id != taskId }
                removePendingTask(taskId)
            }
        }
    }

    fun togglePauseActiveTask(taskId: String? = null) {
        val target = if (taskId != null) {
            _activeTasks.value.find { it.id == taskId }
        } else {
            _activeTasks.value.firstOrNull()
        } ?: return

        val pauseFlow = taskPauseFlows.getOrPut(target.id) { MutableStateFlow(false) }

        if (target.status == TaskStatus.RUNNING) {
            pauseFlow.value = true
            _activeTasks.value = _activeTasks.value.map {
                if (it.id == target.id) it.copy(status = TaskStatus.PAUSED) else it
            }
            syncActiveTaskHead()
        } else if (target.status == TaskStatus.PAUSED) {
            pauseFlow.value = false
            _activeTasks.value = _activeTasks.value.map {
                if (it.id == target.id) it.copy(status = TaskStatus.RUNNING) else it
            }
            syncActiveTaskHead()
        }
    }

    private fun getPauseFlowForTask(taskId: String): MutableStateFlow<Boolean> {
        return taskPauseFlows.getOrPut(taskId) { MutableStateFlow(false) }
    }

    private fun updateIslandAndForegroundService() {
        val currentActive = _activeTasks.value
        val totalQueueCount = currentActive.size + _queueList.value.size

        if (currentActive.isEmpty()) {
            if (_islandState.value is DynamicIslandState.Processing) {
                _islandState.value = DynamicIslandState.Idle
            }
            ProcessingForegroundService.stopService(context)
            return
        }

        val primary = currentActive.first()
        val summaryTitle = if (currentActive.size > 1) {
            "جاري معالجة ${currentActive.size} من $totalQueueCount ملفات"
        } else {
            primary.title
        }

        val avgProgress = currentActive.map { it.progress }.average().toFloat()

        _islandState.value = DynamicIslandState.Processing(
            title = summaryTitle,
            progress = avgProgress,
            processorType = primary.processorType
        )

        ProcessingForegroundService.startService(
            context = context,
            title = summaryTitle,
            progress = (avgProgress * 100).toInt().coerceIn(0, 100),
            processorType = primary.processorType.name
        )
    }

    private fun processNextIfNeeded() {
        val currentActive = _activeTasks.value
        if (currentActive.size >= maxConcurrentTasks || _queueList.value.isEmpty()) return

        val hasActiveVideo = currentActive.any { it.taskType == TaskType.VIDEO }

        // Find next eligible task from queue
        // If there's an active video task, we only pick lightweight non-video tasks (IMAGE, DOCUMENT, AUDIO, CONVERSION)
        val eligibleTask = _queueList.value.firstOrNull { task ->
            if (hasActiveVideo) {
                task.taskType != TaskType.VIDEO
            } else {
                true
            }
        } ?: return

        _queueList.value = _queueList.value.filter { it.id != eligibleTask.id }
        removePendingTask(eligibleTask.id)

        val runningTask = eligibleTask.copy(status = TaskStatus.RUNNING)
        _activeTasks.value = _activeTasks.value + runningTask
        syncActiveTaskHead()
        taskPauseFlows[runningTask.id] = MutableStateFlow(false)

        updateIslandAndForegroundService()

        val job = scope.launch {
            runTask(runningTask)
        }
        executionJobs[runningTask.id] = job

        // If there is still capacity, attempt to schedule another compatible task
        if (_activeTasks.value.size < maxConcurrentTasks && _queueList.value.isNotEmpty()) {
            processNextIfNeeded()
        }
    }

    private suspend fun runTask(task: ProcessingTask) = withContext(Dispatchers.IO) {
        var sourceOriginalSize = 0L
        try {
            task.sourceUris.forEach { uri ->
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        sourceOriginalSize += pfd.statSize
                    }
                } catch (e: Exception) {
                    // تسجيل الفشل ومحاولة الاستعلام البديل لضمان حساب المساحة بشكل سليم
                    AppLogger.logSilentFailure("TaskQueueManager", "فشل قراءة حجم الملف عبر FileDescriptor: $uri", e)
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                                sourceOriginalSize += cursor.getLong(sizeIndex)
                            }
                        }
                    } catch (cursorEx: Exception) {
                        AppLogger.logSilentFailure("TaskQueueManager", "فشل قراءة حجم الملف عبر Query Cursor: $uri", cursorEx)
                    }
                }
            }

            val prefs = PreferencesManager.getInstance(context)
            val customSafUri = prefs.customSafUriFlow.firstOrNull()
            val currentLang = prefs.languageFlow.firstOrNull() ?: "ar"
            val appStrings = getAppStrings(currentLang)

            // Verify available storage space before starting processing (both cache for temp files and actual destination for final output)
            val estimatedRequired = (sourceOriginalSize * 1.2).toLong().coerceAtLeast(15 * 1024 * 1024L)
            if (!FileValidator.checkAvailableStorageSpace(context, estimatedRequired)) {
                throw IllegalStateException(appStrings.errorInsufficientCacheStorage(StorageManager.formatFileSize(estimatedRequired)))
            }
            if (!FileValidator.checkDestinationStorageSpace(context, estimatedRequired, customSafUri)) {
                throw IllegalStateException(appStrings.errorInsufficientStorage(StorageManager.formatFileSize(estimatedRequired)))
            }

            var currentTask = task.copy(status = TaskStatus.RUNNING)
            _activeTasks.value = _activeTasks.value.map { if (it.id == currentTask.id) currentTask else it }
            syncActiveTaskHead()

            var lastProgressPercent = -1
            var lastNotificationTime = 0L

            var taskSkippedCompression = currentTask.compressionSkipped
            var taskOutcome = currentTask.compressionOutcome

            val taskPauseFlow = getPauseFlowForTask(currentTask.id)

            val tempResultFile = currentTask.executeBlock(
                { progress ->
                    if (!coroutineContext.isActive) {
                        throw CancellationException("Task cancelled")
                    }
                    // Suspends in place until unpaused - no blocking wrapper, no risk of tying
                    // up a thread pool worker thread while the user has paused the task.
                    taskPauseFlow.first { !it }
                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    if (percent != lastProgressPercent && (now - lastNotificationTime >= 200 || percent == 100)) {
                        lastProgressPercent = percent
                        lastNotificationTime = now
                        currentTask = currentTask.copy(progress = progress)
                        _activeTasks.value = _activeTasks.value.map { if (it.id == currentTask.id) currentTask else it }
                        syncActiveTaskHead()
                        updateIslandAndForegroundService()
                    }
                },
                { newProcessor ->
                    currentTask = currentTask.copy(processorType = newProcessor)
                    _activeTasks.value = _activeTasks.value.map { if (it.id == currentTask.id) currentTask else it }
                    syncActiveTaskHead()
                    updateIslandAndForegroundService()
                },
                { skipped ->
                    taskSkippedCompression = skipped
                    currentTask = currentTask.copy(compressionSkipped = skipped)
                    _activeTasks.value = _activeTasks.value.map { if (it.id == currentTask.id) currentTask else it }
                    syncActiveTaskHead()
                },
                { outcome ->
                    taskOutcome = outcome
                    currentTask = currentTask.copy(compressionOutcome = outcome)
                    _activeTasks.value = _activeTasks.value.map { if (it.id == currentTask.id) currentTask else it }
                    syncActiveTaskHead()
                },
                { taskPauseFlow.value }
            )

            // Save final file to Downloads/Vada or custom SAF directory
            val savedOutput = StorageManager.saveFinalOutput(
                context,
                tempResultFile,
                currentTask.outputFileName,
                currentTask.outputExtension,
                customSafUri,
                appStrings
            )

            // Record in Room Database
            historyRepository.insert(
                HistoryEntity(
                    fileName = savedOutput.name,
                    fileType = currentTask.taskType.name,
                    operationName = currentTask.title,
                    originalSizeBytes = if (sourceOriginalSize > 0) sourceOriginalSize else tempResultFile.length(),
                    processedSizeBytes = savedOutput.length,
                    outputPath = savedOutput.pathOrUri,
                    processorType = currentTask.processorType.name,
                    status = if (taskSkippedCompression) "COMPLETED_WITHOUT_COMPRESSION" else "COMPLETED",
                    compressionOutcome = taskOutcome?.name ?: currentTask.compressionOutcome?.name
                )
            )

            currentTask = currentTask.copy(
                status = TaskStatus.COMPLETED,
                progress = 1.0f,
                compressionSkipped = taskSkippedCompression,
                compressionOutcome = taskOutcome
            )

            _activeTasks.value = _activeTasks.value.filter { it.id != currentTask.id }
            syncActiveTaskHead()
            executionJobs.remove(currentTask.id)
            taskPauseFlows.remove(currentTask.id)
            cleanupTaskTemps(currentTask)

            val noticeMsg = if (taskSkippedCompression) {
                appStrings.videoSavedWithoutCompressionNotice
            } else {
                null
            }

            _islandState.value = DynamicIslandState.Success(
                title = currentTask.title,
                fileName = savedOutput.name,
                message = noticeMsg,
                isWarning = taskSkippedCompression
            )

            updateIslandAndForegroundService()

            if (taskSkippedCompression) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        appStrings.videoSavedWithoutCompressionNotice,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Auto dismiss island after 3.5s if still success
            delay(3500)
            if (_islandState.value is DynamicIslandState.Success) {
                if (_activeTasks.value.isEmpty()) {
                    _islandState.value = DynamicIslandState.Idle
                } else {
                    updateIslandAndForegroundService()
                }
            }

            processNextIfNeeded()

        } catch (e: CancellationException) {
            // User cancelled: do NOT record as FAILED or show error island.
            cleanupTaskTemps(task)
            _activeTasks.value = _activeTasks.value.filter { it.id != task.id }
            syncActiveTaskHead()
            executionJobs.remove(task.id)
            taskPauseFlows.remove(task.id)
            if (_islandState.value is DynamicIslandState.Processing) {
                _islandState.value = DynamicIslandState.Idle
            }
            updateIslandAndForegroundService()
            processNextIfNeeded()
        } catch (e: Exception) {
            Log.e("TaskQueueManager", "Task failed: ${task.title}", e)
            val errorMsg = e.localizedMessage ?: "Processing error occurred"

            historyRepository.insert(
                HistoryEntity(
                    fileName = task.outputFileName + "." + task.outputExtension,
                    fileType = task.taskType.name,
                    operationName = task.title,
                    originalSizeBytes = sourceOriginalSize,
                    processedSizeBytes = 0L,
                    outputPath = "",
                    processorType = task.processorType.name,
                    status = "FAILED",
                    errorMessage = errorMsg
                )
            )

            cleanupTaskTemps(task)
            _activeTasks.value = _activeTasks.value.filter { it.id != task.id }
            syncActiveTaskHead()
            executionJobs.remove(task.id)
            taskPauseFlows.remove(task.id)

            _islandState.value = DynamicIslandState.Error(task.title, errorMsg)
            updateIslandAndForegroundService()

            delay(4000)
            if (_islandState.value is DynamicIslandState.Error) {
                if (_activeTasks.value.isEmpty()) {
                    _islandState.value = DynamicIslandState.Idle
                } else {
                    updateIslandAndForegroundService()
                }
            }

            processNextIfNeeded()
        }
    }

    private fun cleanupTaskTemps(task: ProcessingTask) {
        try {
            task.tempFilesToClean.forEach { file ->
                if (file.exists()) file.delete()
            }
            task.tempFilesToClean.clear()
        } catch (e: Exception) {
            Log.e("TaskQueueManager", "Failed to cleanup task temp files", e)
        }
    }
}
