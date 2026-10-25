package com.example.ecopoints.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecopoints.app.data.model.AppSettings
import com.example.ecopoints.app.data.model.ThemeMode
import com.example.ecopoints.app.data.repository.SettingsRepository
import com.example.ecopoints.app.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.example.ecopoints.app.data.remote.FirebaseBackupRepository
import com.example.ecopoints.app.data.local.CloudPreferences

data class CloudUiState(val busy: Boolean = false, val message: String? = null, val lastBackup: Long = 0L)

data class SettingsUiState(
    val userName: String = "EcoAmigo",
    val settings: AppSettings = AppSettings()
)

/** Ajustes de EcoPoints: cada cambio se guarda al instante en DataStore y se aplica en toda la app. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    userRepository: UserRepository,
    private val cloud: FirebaseBackupRepository,
    private val cloudPreferences: CloudPreferences
) : ViewModel() {
    val firebaseConfigured = cloud.configured
    private val _cloudState = MutableStateFlow(CloudUiState())
    val cloudState = _cloudState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.sessionUserId.collect { id ->
                _cloudState.value = CloudUiState(lastBackup = id?.let(cloudPreferences::lastBackup) ?: 0L)
            }
        }
    }

    fun transfer(restore: Boolean = false) {
        if (_cloudState.value.busy) return
        _cloudState.value = _cloudState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val userId = settingsRepository.sessionUserId.first()
                val message = cloud.transfer(restore)
                if (!restore && userId != null) cloudPreferences.recordBackup(userId)
                _cloudState.value = CloudUiState(message = message, lastBackup = userId?.let(cloudPreferences::lastBackup) ?: 0L)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                val message = when (error) {
                    is TimeoutCancellationException -> "La conexión tardó demasiado. Revisa tu internet y reintenta."
                    is IllegalStateException -> "Cierra sesión e inicia sesión con tu cuenta Firebase para respaldar."
                    is com.google.firebase.auth.FirebaseAuthException -> "No se pudo autenticar en Firebase. Revisa tus credenciales y que Correo/contraseña esté habilitado."
                    is com.google.firebase.firestore.FirebaseFirestoreException -> "No se pudo acceder a Firestore. Revisa internet y las reglas del proyecto."
                    else -> "No se pudo completar la operación. Revisa la configuración y el formato del respaldo."
                }
                _cloudState.value = _cloudState.value.copy(busy = false, message = message)
            }
        }
    }

    private val userName: Flow<String> = settingsRepository.sessionUserId.flatMapLatest { userId ->
        if (userId == null) flowOf("EcoAmigo") else userRepository.observeUser(userId).map { it?.name ?: "EcoAmigo" }
    }

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, userName) { settings, name ->
        SettingsUiState(userName = name, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) = save { settingsRepository.setThemeMode(mode) }

    fun setLargeText(enabled: Boolean) = save { settingsRepository.setLargeText(enabled) }

    fun setChallengeReminders(enabled: Boolean) = save { settingsRepository.setChallengeReminders(enabled) }

    fun setPetReminders(enabled: Boolean) = save { settingsRepository.setPetReminders(enabled) }

    fun setRankingVisible(visible: Boolean) = save { settingsRepository.setRankingVisible(visible) }

    fun setConfirmSpend(enabled: Boolean) = save { settingsRepository.setConfirmSpend(enabled) }

    fun setVibration(enabled: Boolean) = save { settingsRepository.setVibration(enabled) }

    fun setMapRadiusKm(radiusKm: Int) = save { settingsRepository.setMapRadiusKm(radiusKm) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
