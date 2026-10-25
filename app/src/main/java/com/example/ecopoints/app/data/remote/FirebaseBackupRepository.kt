package com.example.ecopoints.app.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.example.ecopoints.app.data.local.db.EcoDatabase
import com.example.ecopoints.app.data.repository.SettingsRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
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

data class FirebaseAccount(val uid: String, val email: String, val name: String?)

/** Authentication mantiene la sesión; Firestore guarda el respaldo privado de ese mismo UID. */
class FirebaseBackupRepository(
    context: Context,
    private val database: EcoDatabase,
    private val settings: SettingsRepository,
    firebaseApp: FirebaseApp? = FirebaseApp.initializeApp(context.applicationContext)
) {
    private val app = firebaseApp
    val configured: Boolean get() = app != null
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance(checkNotNull(app) {
        "Falta configurar Firebase. Agrega app/google-services.json y recompila."
    })
    private val firestore get() = FirebaseFirestore.getInstance(checkNotNull(app))
    private val mutex = Mutex()
    private val gson = Gson()

    fun isSignedInAs(email: String): Boolean = configured && auth.currentUser?.email.equals(email, ignoreCase = true)
    fun signOut() { if (configured) auth.signOut() }
    fun matchesIdentity(uid: String?): Boolean = uid != null && configured && auth.currentUser?.uid == uid

    suspend fun createAccount(name: String, email: String, password: String): FirebaseAccount = withTimeout(30_000) {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        FirebaseAccount(user.uid, checkNotNull(user.email), user.displayName)
    }

    suspend fun signIn(email: String, password: String): FirebaseAccount = withTimeout(30_000) {
        val user = checkNotNull(auth.signInWithEmailAndPassword(email, password).await().user)
        FirebaseAccount(user.uid, checkNotNull(user.email), user.displayName)
    }

    suspend fun sendPasswordReset(email: String) = withTimeout(30_000) {
        auth.setLanguageCode("es")
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    suspend fun updateName(email: String, name: String) = withTimeout(30_000) {
        check(isSignedInAs(email)) { "Vuelve a iniciar sesión para actualizar tu perfil." }
        checkNotNull(auth.currentUser).updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
    }

    private fun document(email: String) = firestore.collection("backups").document(
        checkNotNull(auth.currentUser?.takeIf { it.email.equals(email, ignoreCase = true) }) {
            "Vuelve a iniciar sesión con esta cuenta."
        }.uid
    )

    suspend fun readBackup(email: String): CloudBackup? = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val remote = document(email).get(Source.SERVER).await()
            if (!remote.exists()) return@withTimeout null
            require(remote.getLong("version") == 1L) { "Versión de respaldo incompatible" }
            val payload = checkNotNull(remote.getString("payload")) { "El respaldo no contiene datos" }
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Respaldo demasiado grande" }
            gson.fromJson(payload, CloudBackup::class.java).also { it.validate(email) }
        }
    }

    suspend fun restoreForUser(userId: Long, backup: CloudBackup) {
        val user = checkNotNull(database.userDao().findById(userId))
        check(isSignedInAs(user.email)) { "Vuelve a iniciar sesión con esta cuenta." }
        check(matchesIdentity(user.firebaseUid)) { "Vuelve a iniciar sesión para vincular esta cuenta." }
        backup.validate(user.email)
        database.withTransaction {
            backup.profileName?.takeIf { it.isNotBlank() }?.let { database.userDao().updateProfile(userId, it, user.email) }
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
    }

    suspend fun saveForUser(userId: Long) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val user = checkNotNull(database.userDao().findById(userId))
            check(matchesIdentity(user.firebaseUid)) { "Vuelve a iniciar sesión para vincular esta cuenta." }
            val target = document(user.email)
            val preferences = settings.settings.first()
            val backup = database.withTransaction {
                CloudBackup(
                    1, user.email,
                    checkNotNull(database.progressDao().get(userId)), checkNotNull(database.petDao().get(userId)),
                    database.challengeDao().getAll(userId).map { it.copy(evidencePath = "", remindedDeadline = 0) },
                    database.achievementDao().getAll(userId), database.activityLogDao().getAll(userId),
                    preferences, user.name
                )
            }
            backup.validate(user.email)
            val payload = gson.toJson(backup)
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Respaldo demasiado grande" }
            target.set(mapOf("version" to 1, "payload" to payload, "updatedAt" to FieldValue.serverTimestamp())).await()
            Unit
        }
    }

    suspend fun transfer(restore: Boolean): String = mutex.withLock {
        val userId = checkNotNull(settings.sessionUserId.first()) { "Inicia sesión primero" }
        val user = checkNotNull(database.userDao().findById(userId))
        if (restore) {
            val backup = checkNotNull(readBackup(user.email)) { "No hay respaldo para esta cuenta" }
            check(settings.sessionUserId.first() == userId) { "La sesión cambió. Reintenta." }
            restoreForUser(userId, backup)
            "Respaldo restaurado. Las fotos de evidencia permanecen en su dispositivo original."
        } else {
            saveForUser(userId)
            "Respaldo guardado en Firebase"
        }
    }

    companion object { const val MAX_BYTES = 800_000 }
}
