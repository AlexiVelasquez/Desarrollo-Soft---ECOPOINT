package com.example.ecopoints.app.data.local

import android.content.Context

/** SharedPreferences para una preferencia simple. Nunca guarda la contraseña de Firebase. */
class CloudPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_preferences", Context.MODE_PRIVATE)
    fun lastBackup(userId: Long): Long = preferences.getLong("last_backup_$userId", 0L)

    fun recordBackup(userId: Long) {
        preferences.edit().putLong("last_backup_$userId", System.currentTimeMillis()).apply()
    }
}
