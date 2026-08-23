package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingTaskDao {
    @Query("SELECT * FROM pending_tasks ORDER BY createdAt ASC")
    suspend fun getAllPendingTasks(): List<PendingTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingTask(item: PendingTaskEntity)

    @Query("DELETE FROM pending_tasks WHERE taskId = :taskId")
    suspend fun deletePendingTaskById(taskId: String)

    @Query("DELETE FROM pending_tasks")
    suspend fun clearAllPendingTasks()
}
