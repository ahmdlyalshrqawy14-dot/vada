package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_items")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileType: String, // VIDEO, AUDIO, IMAGE, DOCUMENT, CONVERSION
    val operationName: String,
    val originalSizeBytes: Long,
    val processedSizeBytes: Long,
    // outputPath can store either a local filesystem absolute path or a SAF tree content Uri (content://...)
    val outputPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val processorType: String, // HARDWARE, SOFTWARE
    val status: String = "COMPLETED", // COMPLETED, FAILED
    val errorMessage: String? = null,
    val compressionOutcome: String? = null // SUCCESS, MARGINAL, NO_COMPRESSION
)

