package com.example.ecopoints.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SharedPreferences para una preferencia simple. Nunca guarda la contraseña de Firebase. */
class CloudPreferences(context: Context, name: String = "cloud_preferences") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun syncedHash(uid: String): String? = preferences.getString("synced_hash_$uid", null)
    fun lastSync(uid: String): Long = preferences.getLong("last_sync_$uid", 0L)

    suspend fun recordSync(uid: String, hash: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString("synced_hash_$uid", hash)
            .putLong("last_sync_$uid", System.currentTimeMillis()).commit()) {
            "No se pudo guardar el estado de sincronización"
        }
    }
}
