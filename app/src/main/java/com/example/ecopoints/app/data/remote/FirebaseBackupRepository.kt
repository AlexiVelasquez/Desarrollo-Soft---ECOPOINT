package com.example.ecopoints.app.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.example.ecopoints.app.data.local.db.EcoDatabase
import com.example.ecopoints.app.data.repository.SettingsRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Copias manuales autenticadas. Room sigue siendo la fuente de datos durante el uso sin red. */
class FirebaseBackupRepository(
    context: Context,
    private val database: EcoDatabase,
    private val settings: SettingsRepository
) {
    private val app = FirebaseApp.initializeApp(context.applicationContext)
    val configured: Boolean get() = app != null
    private val mutex = Mutex()
    private val gson = Gson()

    suspend fun transfer(password: String, createAccount: Boolean, restore: Boolean): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            withTimeout(30_000) {
                val firebase = checkNotNull(app) { "Falta app/google-services.json. Configura Firebase y recompila." }
                val userId = checkNotNull(settings.sessionUserId.first()) { "Inicia sesión primero" }
                val user = checkNotNull(database.userDao().findById(userId))
                require(password.length >= 6) { "La contraseña de Firebase debe tener al menos 6 caracteres" }
                require(!restore || !createAccount) { "Inicia sesión en una cuenta existente para restaurar" }
                val auth = FirebaseAuth.getInstance(firebase)
                try {
                    val credential = if (createAccount) {
                        auth.createUserWithEmailAndPassword(user.email, password).await()
                    } else {
                        auth.signInWithEmailAndPassword(user.email, password).await()
                    }
                    val uid = checkNotNull(credential.user).uid
                    val document = FirebaseFirestore.getInstance(firebase).collection("backups").document(uid)
                    if (restore) {
                        val remote = document.get(Source.SERVER).await()
                        val payload = checkNotNull(remote.getString("payload")) { "No hay respaldo para esta cuenta" }
                        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Respaldo demasiado grande" }
                        val backup = gson.fromJson(payload, CloudBackup::class.java)
                        backup.validate(user.email)
                        check(settings.sessionUserId.first() == userId) { "La sesión cambió. Reintenta." }
                        database.withTransaction {
                            database.progressDao().upsert(backup.progress.copy(userId = userId))
                            database.petDao().upsert(backup.pet.copy(userId = userId))
                            database.challengeDao().deleteAll(userId)
                            backup.challenges.forEach {
                                database.challengeDao().insert(it.copy(id = 0, userId = userId, evidencePath = "", remindedDeadline = 0))
                            }
                            database.achievementDao().deleteAll(userId)
                            database.achievementDao().insertAll(backup.achievements.map { it.copy(userId = userId) })
                            database.activityLogDao().deleteAll(userId)
                            backup.activity.forEach { database.activityLogDao().insert(it.copy(id = 0, userId = userId)) }
                        }
                        settings.restorePreferences(backup.settings)
                        "Respaldo restaurado. Las fotos de evidencia permanecen en su dispositivo original."
                    } else {
                        val preferences = settings.settings.first()
                        val backup = database.withTransaction {
                            CloudBackup(
                                1, user.email,
                                checkNotNull(database.progressDao().get(userId)),
                                checkNotNull(database.petDao().get(userId)),
                                database.challengeDao().getAll(userId).map { it.copy(evidencePath = "", remindedDeadline = 0) },
                                database.achievementDao().getAll(userId),
                                database.activityLogDao().getAll(userId), preferences
                            )
                        }
                        backup.validate(user.email)
                        val payload = gson.toJson(backup)
                        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Respaldo demasiado grande" }
                        document.set(mapOf("version" to 1, "payload" to payload, "updatedAt" to FieldValue.serverTimestamp())).await()
                        "Respaldo guardado en Firebase"
                    }
                } finally {
                    auth.signOut()
                }
            }
        }
    }

    companion object { const val MAX_BYTES = 800_000 }
}
