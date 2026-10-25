package com.example.ecopoints.app.data.remote

import com.example.ecopoints.app.data.local.CloudPreferences
import com.example.ecopoints.app.data.local.db.EcoDatabase
import com.example.ecopoints.app.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CloudSyncState(
    val syncing: Boolean = false,
    val message: String = "Los cambios se guardan automáticamente.",
    val lastSync: Long = 0L
)

/** Observa todas las tablas y ajustes. Serializa subidas, actualización al entrar y cierre de sesión. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CloudSyncCoordinator(
    private val database: EcoDatabase,
    private val settings: SettingsRepository,
    private val cloud: FirebaseBackupRepository,
    private val preferences: CloudPreferences,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 600L,
    private val retryMillis: Long = 5_000L
) {
    private val gate = Mutex()
    private var watcher: Job? = null
    private val _state = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()
    val configured: Boolean get() = cloud.configured

    fun start() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            settings.sessionUserId.collectLatest { userId ->
                _state.value = CloudSyncState()
                if (userId == null) return@collectLatest
                combine(
                    database.invalidationTracker.createFlow("users", "user_progress", "pets", "challenges", "achievement_unlocks", "activity_logs"),
                    settings.settings
                ) { _, _ -> Unit }.debounce(debounceMillis).collect {
                    while (currentCoroutineContext().isActive && settings.sessionUserId.first() == userId) {
                        try {
                            gate.withLock {
                                if (settings.sessionUserId.first() == userId) flushLocked(userId)
                            }
                            break
                        } catch (error: Exception) {
                            error.rethrowCancellation()
                            showPending()
                            delay(retryMillis)
                        }
                    }
                }
            }
        }
    }

    suspend fun hasPendingChanges(userId: Long): Boolean {
        val uid = checkNotNull(database.userDao().findById(userId)?.firebaseUid)
        val saved = preferences.syncedHash(uid) ?: return false
        return cloud.localFingerprint(userId) != saved
    }

    suspend fun recordConfirmed(userId: Long, hash: String? = null) {
        val uid = checkNotNull(database.userDao().findById(userId)?.firebaseUid)
        preferences.recordSync(uid, hash ?: cloud.localFingerprint(userId))
        _state.value = CloudSyncState(message = "Todos los cambios están guardados.", lastSync = preferences.lastSync(uid))
    }

    private suspend fun flushLocked(userId: Long) {
        val user = checkNotNull(database.userDao().findById(userId))
        val uid = checkNotNull(user.firebaseUid)
        check(cloud.isSignedInAs(user.email) && cloud.matchesIdentity(uid)) { "Vuelve a iniciar sesión para sincronizar." }
        while (true) {
            val current = cloud.localFingerprint(userId)
            if (preferences.syncedHash(uid) == current) {
                _state.value = CloudSyncState(message = "Todos los cambios están guardados.", lastSync = preferences.lastSync(uid))
                return
            }
            _state.value = _state.value.copy(syncing = true, message = "Guardando cambios…")
            val confirmed = cloud.saveForUser(userId)
            recordConfirmed(userId, confirmed)
            // Si hubo otro cambio durante la subida, también debe confirmarse antes de salir.
            if (cloud.localFingerprint(userId) == confirmed) return
        }
    }

    suspend fun tryFlushCurrent() {
        try {
            gate.withLock { settings.sessionUserId.first()?.let { flushLocked(it) } }
        } catch (error: Exception) {
            error.rethrowCancellation()
            showPending()
        }
    }

    /** Solo borra la sesión después de confirmar todos los cambios. Ante un fallo, conserva la sesión. */
    suspend fun finishSession() = gate.withLock {
        try {
            settings.sessionUserId.first()?.let { flushLocked(it) }
            settings.endSession()
            cloud.signOut()
        } catch (error: Exception) {
            error.rethrowCancellation()
            showPending()
            throw error
        }
    }

    suspend fun prepareAuthentication(email: String?) = gate.withLock {
        val id = settings.sessionUserId.first()
        if (id != null) {
            val user = database.userDao().findById(id)
            // Al volver a autenticar la misma cuenta se conservan sus cambios locales pendientes.
            if (email == null || !user?.email.equals(email, ignoreCase = true)) flushLocked(id)
        }
        settings.endSession()
        cloud.signOut()
    }

    suspend fun refreshCurrent() = gate.withLock {
        val id = checkNotNull(settings.sessionUserId.first())
        flushLocked(id)
        val user = checkNotNull(database.userDao().findById(id))
        cloud.readBackup(user.email)?.let { cloud.restoreForUser(id, it) }
        recordConfirmed(id)
    }

    private fun showPending() {
        _state.value = _state.value.copy(syncing = false,
            message = "Hay cambios pendientes. Se reintentará automáticamente al recuperar la conexión.")
    }
}
