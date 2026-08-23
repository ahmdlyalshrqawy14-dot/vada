package com.example.data.db

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(item: HistoryEntity): Long = historyDao.insertHistory(item)

    suspend fun deleteById(id: Long) = historyDao.deleteHistoryById(id)

    suspend fun delete(item: HistoryEntity) = historyDao.deleteHistoryById(item.id)

    suspend fun clearAll() = historyDao.clearAllHistory()
}
