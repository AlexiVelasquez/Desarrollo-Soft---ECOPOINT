package com.example.ecopoints.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos SQLite de EcoPoints (Room). Reemplaza al almacenamiento en SharedPreferences:
 * cuentas, progreso, mascota, retos y logros viven aquí; los ajustes viven en DataStore.
 */
@Database(
    entities = [
        UserEntity::class,
        ProgressEntity::class,
        PetEntity::class,
        ChallengeEntity::class,
        AchievementUnlockEntity::class,
        ActivityLogEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class EcoDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun progressDao(): ProgressDao
    abstract fun petDao(): PetDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun achievementDao(): AchievementDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        const val NAME = "ecopoints.db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `firebase_uid` TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_firebase_uid` ON `users` (`firebase_uid`)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `activity_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, `action_type` TEXT NOT NULL, `description` TEXT NOT NULL, `points_changed` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_logs_user_id` ON `activity_logs` (`user_id`)")
            }
        }

        fun create(context: Context): EcoDatabase =
            Room.databaseBuilder(context.applicationContext, EcoDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
