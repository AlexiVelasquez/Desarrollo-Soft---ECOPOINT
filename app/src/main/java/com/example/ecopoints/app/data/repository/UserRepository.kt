package com.example.ecopoints.app.data.repository

import androidx.room.withTransaction
import com.example.ecopoints.app.data.local.db.*
import com.example.ecopoints.app.data.model.*
import com.example.ecopoints.app.data.remote.FirebaseBackupRepository
import com.example.ecopoints.app.data.remote.CloudSyncCoordinator
import com.example.ecopoints.app.data.remote.remoteMessage
import com.example.ecopoints.app.data.remote.rethrowCancellation
import com.example.ecopoints.app.domain.EcoRules
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Firebase autentica las cuentas; Room conserva la copia de trabajo del dispositivo. */
class UserRepository(
    private val database: EcoDatabase,
    private val settings: SettingsRepository,
    private val cloud: FirebaseBackupRepository,
    private val sync: CloudSyncCoordinator
) {
    private val userDao = database.userDao()

    fun observeUser(userId: Long): Flow<User?> = userDao.observeById(userId).map { it?.toDomain() }
    suspend fun getUser(userId: Long): User? = userDao.findById(userId)?.toDomain()
    suspend fun canContinue(user: User): Boolean = cloud.isSignedInAs(user.email) &&
        cloud.matchesIdentity(userDao.findById(user.id)?.firebaseUid)

    suspend fun register(
        name: String, email: String, password: String, species: String, petName: String,
        now: Long = System.currentTimeMillis()
    ): RegisterResult {
        var authenticating = false
        return try {
            sync.prepareAuthentication(null)
            authenticating = true
            val account = cloud.createAccount(name.trim(), normalize(email), password)
            val id = database.withTransaction {
                val existing = userDao.findByEmail(account.email)
                if (existing != null) {
                    check(existing.firebaseUid == null || existing.firebaseUid == account.uid)
                    userDao.updateProfile(existing.id, name.trim(), account.email)
                    userDao.linkFirebase(existing.id, account.uid)
                    existing.id
                } else createLocal(name.trim(), account.email, species, petName, now, account.uid)
            }
            val confirmed = cloud.saveForUser(id)
            sync.recordConfirmed(id, confirmed)
            settings.startSession(id)
            RegisterResult.Success(id)
        } catch (error: Exception) {
            if (authenticating) cloud.signOut()
            error.rethrowCancellation()
            if (error is FirebaseAuthUserCollisionException) RegisterResult.EmailTaken
            else RegisterResult.Error(error.remoteMessage() + " Si la cuenta ya se creó, entra desde Iniciar sesión.")
        }
    }

    suspend fun login(email: String, password: String): LoginResult {
        var authenticating = false
        return try {
            sync.prepareAuthentication(normalize(email))
            authenticating = true
            val account = cloud.signIn(normalize(email), password)
            val existing = userDao.findByEmail(account.email)
            if (existing != null) {
                check(existing.firebaseUid == null || existing.firebaseUid == account.uid)
                userDao.linkFirebase(existing.id, account.uid)
                if (sync.hasPendingChanges(existing.id)) {
                    sync.recordConfirmed(existing.id, cloud.saveForUser(existing.id))
                }
            }
            val backup = cloud.readBackup(account.email)
            val id = database.withTransaction {
                val name = backup?.profileName?.takeIf { it.isNotBlank() }
                    ?: account.name?.takeIf { it.isNotBlank() } ?: account.email.substringBefore('@')
                val localId = existing?.id ?: createLocal(name, account.email, PetSpecies.DEFAULT, "", System.currentTimeMillis(), account.uid)
                // Siempre recupera el estado de nube, incluso si había una copia local antigua.
                if (backup != null) cloud.restoreForUser(localId, backup)
                localId
            }
            if (backup == null) cloud.saveForUser(id)
            cloud.saveProfileForUser(id)
            sync.recordConfirmed(id)
            settings.startSession(id)
            LoginResult.Success(id, false)
        } catch (error: Exception) {
            if (authenticating) cloud.signOut()
            error.rethrowCancellation()
            LoginResult.Error(error.remoteMessage())
        }
    }

    suspend fun sendPasswordReset(email: String) = cloud.sendPasswordReset(normalize(email))

    suspend fun logout() {
        sync.finishSession()
    }

    suspend fun continueSession() = sync.refreshCurrent()

    sealed class UpdateProfileResult {
        data object Success : UpdateProfileResult()
        data object EmailTaken : UpdateProfileResult()
        data class Error(val message: String) : UpdateProfileResult()
    }

    suspend fun updateProfile(id: Long, name: String, email: String): UpdateProfileResult {
        val user = userDao.findById(id) ?: return UpdateProfileResult.Error("No hay una sesión activa")
        if (normalize(email) != user.email) return UpdateProfileResult.Error("El correo identifica tu cuenta Firebase y no se cambia desde este formulario.")
        return try {
            cloud.updateName(user.email, name.trim())
            userDao.updateProfile(id, name.trim(), user.email)
            UpdateProfileResult.Success
        } catch (error: Exception) {
            error.rethrowCancellation()
            UpdateProfileResult.Error(error.remoteMessage())
        }
    }

    private suspend fun createLocal(name: String, email: String, species: String, petName: String, now: Long, uid: String): Long {
        val id = userDao.insert(UserEntity(name = name, email = email, passwordHash = null, passwordSalt = null, createdAt = now, firebaseUid = uid))
        database.progressDao().upsert(ProgressEntity(id, balance = EcoRules.WELCOME_BONUS, historicalPoints = EcoRules.WELCOME_BONUS))
        database.petDao().upsert(PetEntity(id, species, petName.trim(), decayTimestamp = now))
        return id
    }

    private fun normalize(email: String) = email.trim().lowercase(java.util.Locale.ROOT)
}
