package com.example.ecopoints.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ecopoints.app.data.local.db.EcoDatabase
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun upgradeFromVersionOnePreservesAccountAndBalance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schema = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("com.example.ecopoints.app.data.local.db.EcoDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            old.execSQL("INSERT INTO users (id, name, email, created_at) VALUES (1, 'Ana', 'ana@example.com', 1)")
            old.execSQL("INSERT INTO user_progress VALUES (1, 75, 100, 2, 3, '2026-10-25', 1, 1)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, EcoDatabase::class.java, name)
            .addMigrations(EcoDatabase.MIGRATION_1_2, EcoDatabase.MIGRATION_2_3).build()
        try {
            assertEquals("Ana", migrated.userDao().findById(1)?.name)
            assertEquals(75, migrated.progressDao().get(1)?.balance)
            assertEquals(null, migrated.userDao().findById(1)?.firebaseUid)
            migrated.userDao().linkFirebase(1, "migration-uid")
            assertEquals("migration-uid", migrated.userDao().findById(1)?.firebaseUid)
            assertEquals(emptyList<Any>(), migrated.activityLogDao().getAll(1))
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
