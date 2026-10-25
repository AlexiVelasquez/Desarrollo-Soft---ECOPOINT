package com.example.ecopoints.app.data.repository

import com.example.ecopoints.app.data.local.db.ActivityLogEntity
import com.example.ecopoints.app.data.local.db.EcoDatabase
import kotlinx.coroutines.flow.Flow

class ActivityLogRepository(private val database: EcoDatabase) {
    private val activityLogDao = database.activityLogDao()

    fun observeLogs(userId: Long): Flow<List<ActivityLogEntity>> =
        activityLogDao.observeAll(userId)

    suspend fun addLog(
        userId: Long,
        actionType: String,
        description: String,
        pointsChanged: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        return activityLogDao.insert(
            ActivityLogEntity(
                userId = userId,
                actionType = actionType,
                description = description,
                pointsChanged = pointsChanged,
                timestamp = timestamp
            )
        )
    }
}