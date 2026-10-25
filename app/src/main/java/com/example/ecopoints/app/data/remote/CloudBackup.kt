package com.example.ecopoints.app.data.remote

import com.example.ecopoints.app.data.local.db.*
import com.example.ecopoints.app.data.model.AppSettings
import com.example.ecopoints.app.data.model.PetSpecies

/** Contrato versionado. Nunca contiene contraseñas, hashes, tokens ni fotos privadas. */
data class CloudBackup(
    val version: Int,
    val email: String,
    val progress: ProgressEntity,
    val pet: PetEntity,
    val challenges: List<ChallengeEntity>,
    val achievements: List<AchievementUnlockEntity>,
    val activity: List<ActivityLogEntity>,
    val settings: AppSettings
) {
    fun validate(expectedEmail: String) {
        require(version == 1) { "Versión de respaldo incompatible" }
        require(email.equals(expectedEmail, ignoreCase = true)) { "El respaldo pertenece a otra cuenta" }
        require(progress.balance >= 0 && progress.historicalPoints >= progress.balance)
        require(progress.streak >= 0 && progress.bestStreak >= progress.streak)
        require(progress.feedCount >= 0 && progress.playCount >= 0)
        require(pet.species in PetSpecies.ALL && pet.hunger in 0..100 && pet.happiness in 0..100)
        require(pet.name.length in 1..100)
        require(challenges.size <= 1000 && achievements.size <= 1000 && activity.size <= 5000)
        require(challenges.all { it.durationDays > 0 && it.title.isNotBlank() })
        require(settings.mapRadiusKm in listOf(1, 3, 5))
    }
}
