package com.example.ecopoints.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecopoints.app.data.repository.PetRepository
import com.example.ecopoints.app.data.repository.SettingsRepository
import com.example.ecopoints.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val userName: String = "",
    val email: String = "",
    val petName: String = "",
    val nameInput: String = "",
    val emailInput: String = "",
    val petNameInput: String = ""
)

sealed class ProfileEvent {
    data class Message(val text: String) : ProfileEvent()
}

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events = _events.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val userId = settingsRepository.sessionUserId.first() ?: return@launch
            val user = userRepository.getUser(userId)
            val pet = petRepository.getPet(userId)
            
            if (user != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userName = user.name,
                        email = user.email,
                        petName = pet?.name ?: "",
                        nameInput = user.name,
                        emailInput = user.email,
                        petNameInput = pet?.name ?: ""
                    )
                }
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { state ->
            if (state.isEditing) {
                // Cancel edit, revert inputs
                state.copy(
                    isEditing = false,
                    nameInput = state.userName,
                    emailInput = state.email,
                    petNameInput = state.petName
                )
            } else {
                state.copy(isEditing = true)
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(nameInput = name) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(emailInput = email) }
    }

    fun onPetNameChange(petName: String) {
        _uiState.update { it.copy(petNameInput = petName) }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val userId = settingsRepository.sessionUserId.first() ?: return@launch
            val currentState = _uiState.value
            
            if (currentState.nameInput.isBlank() || currentState.emailInput.isBlank() || currentState.petNameInput.isBlank()) {
                _events.emit(ProfileEvent.Message("Los campos no pueden estar vacíos"))
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            val updateResult = userRepository.updateProfile(userId, currentState.nameInput, currentState.emailInput)
            
            if (updateResult is UserRepository.UpdateProfileResult.EmailTaken) {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(ProfileEvent.Message("Este correo ya está en uso"))
                return@launch
            }

            petRepository.updateName(userId, currentState.petNameInput)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditing = false,
                    userName = currentState.nameInput.trim(),
                    email = currentState.emailInput.trim(),
                    petName = currentState.petNameInput.trim()
                )
            }
            _events.emit(ProfileEvent.Message("Perfil actualizado"))
        }
    }
}
