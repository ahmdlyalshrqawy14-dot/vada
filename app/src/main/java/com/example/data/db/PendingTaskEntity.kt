package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_tasks")
data class PendingTaskEntity(
    @PrimaryKey
    val taskId: String,
    val title: String,
    val subtitle: String,
    val taskType: String,
    val sourceUris: String,
    val outputFileName: String,
    val outputExtension: String,
    val createdAt: Long = System.currentTimeMillis(),
    val paramsJson: String = ""
)
