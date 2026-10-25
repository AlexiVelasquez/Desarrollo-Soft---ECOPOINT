package com.example.ecopoints.app.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.example.ecopoints.app.data.local.db.EcoDatabase
import com.example.ecopoints.app.data.repository.SettingsRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Date
import java.security.MessageDigest

data class FirebaseAccount(val uid: String, val email: String, val name: String?)

/** Authentication mantiene la sesión; Firestore guarda el respaldo privado de ese mismo UID. */
class FirebaseBackupRepository(
    context: Context,
    private val database: EcoDatabase,
    private val settings: SettingsRepository,
    firebaseApp: FirebaseApp? = FirebaseApp.initializeApp(context.applicationContext),
    private val writeTimeoutMillis: Long = 30_000
) {
    private val app = firebaseApp
    val configured: Boolean get() = app != null
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance(checkNotNull(app) {
        "Falta configurar Firebase. Agrega app/google-services.json y recompila."
    })
    private val firestore get() = FirebaseFirestore.getInstance(checkNotNull(app))
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

    /** Perfil legible en Firestore, siempre con el mismo UID que Authentication. */
    private fun profileData(user: com.example.ecopoints.app.data.local.db.UserEntity,
                            pet: com.example.ecopoints.app.data.local.db.PetEntity): Map<String, Any> {
        check(matchesIdentity(user.firebaseUid) && isSignedInAs(user.email))
        val createdAt = checkNotNull(auth.currentUser?.metadata).creationTimestamp
        return mapOf(
            "uid" to checkNotNull(user.firebaseUid),
            "name" to user.name,
            "email" to user.email,
            "petSpecies" to pet.species,
            "petName" to pet.name,
            "createdAt" to Timestamp(Date(createdAt)),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    /** Vincula también los usuarios existentes al entrar, sin reemplazar su respaldo. */
    suspend fun saveProfileForUser(userId: Long) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val user = checkNotNull(database.userDao().findById(userId))
            val pet = checkNotNull(database.petDao().get(userId))
            val profile = profileData(user, pet)
            firestore.collection("users").document(checkNotNull(user.firebaseUid)).set(profile).await()
            Unit
        }
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
            val localChallenges = database.challengeDao().getAll(userId)
            backup.profileName?.takeIf { it.isNotBlank() }?.let { database.userDao().updateProfile(userId, it, user.email) }
            database.progressDao().upsert(backup.progress.copy(userId = userId))
            database.petDao().upsert(backup.pet.copy(userId = userId))
            database.challengeDao().deleteAll(userId)
            backup.challenges.forEach {
                val localEvidence = localChallenges.firstOrNull { local -> local.title == it.title &&
                    local.startMillis == it.startMillis && local.completedMillis == it.completedMillis }
                    ?.evidencePath?.takeIf { path -> path.isNotEmpty() && java.io.File(path).exists() }.orEmpty()
                database.challengeDao().insert(it.copy(id = 0, userId = userId, evidencePath = localEvidence, remindedDeadline = 0))
            }
            database.achievementDao().deleteAll(userId)
            database.achievementDao().insertAll(backup.achievements.map { it.copy(userId = userId) })
            database.activityLogDao().deleteAll(userId)
            backup.activity.forEach { database.activityLogDao().insert(it.copy(id = 0, userId = userId)) }
        }
        settings.restorePreferences(backup.settings)
    }

    private suspend fun localBackup(userId: Long): CloudBackup {
        val preferences = settings.settings.first()
        return database.withTransaction {
            val user = checkNotNull(database.userDao().findById(userId))
            CloudBackup(
                1, user.email,
                checkNotNull(database.progressDao().get(userId)), checkNotNull(database.petDao().get(userId)),
                database.challengeDao().getAll(userId).map { it.copy(evidencePath = "", remindedDeadline = 0) },
                database.achievementDao().getAll(userId), database.activityLogDao().getAll(userId),
                preferences, user.name
            )
        }
    }

    private fun fingerprint(backup: CloudBackup): String = MessageDigest.getInstance("SHA-256")
        .digest(gson.toJson(backup).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun localFingerprint(userId: Long): String = fingerprint(localBackup(userId))

    /** Devuelve la huella de la versión confirmada por el servidor. */
    suspend fun saveForUser(userId: Long): String = withContext(Dispatchers.IO) {
        withTimeout(writeTimeoutMillis) {
            val user = checkNotNull(database.userDao().findById(userId))
            check(matchesIdentity(user.firebaseUid)) { "Vuelve a iniciar sesión para vincular esta cuenta." }
            val target = document(user.email)
            val backup = localBackup(userId)
            backup.validate(user.email)
            val payload = gson.toJson(backup)
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Respaldo demasiado grande" }
            // Un registro correcto confirma perfil y respaldo en una sola escritura atómica.
            val profile = profileData(user.copy(name = backup.profileName ?: user.name), backup.pet)
            firestore.batch()
                .set(firestore.collection("users").document(checkNotNull(user.firebaseUid)), profile)
                .set(target, mapOf("version" to 1, "payload" to payload, "updatedAt" to FieldValue.serverTimestamp()))
                .commit().await()
            fingerprint(backup)
        }
    }

    companion object { const val MAX_BYTES = 800_000 }
}
