package com.example.data.db

class PendingTaskRepository(private val pendingTaskDao: PendingTaskDao) {
    suspend fun getAll(): List<PendingTaskEntity> = pendingTaskDao.getAllPendingTasks()

    suspend fun insert(item: PendingTaskEntity) = pendingTaskDao.insertPendingTask(item)

    suspend fun deleteById(taskId: String) = pendingTaskDao.deletePendingTaskById(taskId)

    suspend fun clearAll() = pendingTaskDao.clearAllPendingTasks()
}
