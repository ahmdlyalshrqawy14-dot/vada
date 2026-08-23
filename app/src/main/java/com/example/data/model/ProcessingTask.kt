package com.example.data.model

import android.net.Uri
import java.io.File
import java.util.UUID

enum class TaskType {
    VIDEO, AUDIO, IMAGE, DOCUMENT, CONVERSION
}

enum class TaskStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}

enum class ProcessorType {
    HARDWARE, SOFTWARE
}

enum class ProcessingMethodLabel {
    REAL_ENCODING, PASSTHROUGH_ONLY
}

enum class CompressionPreset {
    LIGHT, MEDIUM, HEAVY, CUSTOM
}

enum class CompressionOutcome {
    SUCCESS, MARGINAL, NO_COMPRESSION
}

data class ProcessingTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val taskType: TaskType,
    val sourceUris: List<Uri>,
    val outputFileName: String,
    val outputExtension: String,
    val progress: Float = 0f,
    val status: TaskStatus = TaskStatus.QUEUED,
    val processorType: ProcessorType = ProcessorType.SOFTWARE,
    val errorMessage: String? = null,
    val compressionSkipped: Boolean = false,
    val compressionOutcome: CompressionOutcome? = null,
    val tempFilesToClean: MutableList<File> = mutableListOf(),
    val paramsJson: String = "",
    val executeBlock: suspend (
        onProgress: suspend (Float) -> Unit,
        onProcessorChanged: (ProcessorType) -> Unit,
        onCompressionSkipped: (Boolean) -> Unit,
        onOutcomeEvaluated: (CompressionOutcome) -> Unit,
        shouldPause: suspend () -> Boolean
    ) -> File
)
