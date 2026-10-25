package com.example.ecopoints.app.data

import com.example.ecopoints.app.data.local.db.PetEntity
import com.example.ecopoints.app.data.local.db.ProgressEntity
import com.example.ecopoints.app.data.model.AppSettings
import com.example.ecopoints.app.data.remote.CloudBackup
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBackupTest {
    private val backup = CloudBackup(
        1, "ana@example.com", ProgressEntity(1, balance = 50, historicalPoints = 50),
        PetEntity(1, "Koala", "Pepe"), emptyList(), emptyList(), emptyList(), AppSettings()
    )

    @Test fun jsonRoundTripPreservesProgressAndSettings() {
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(backup), CloudBackup::class.java)
        restored.validate("ana@example.com")
        assertEquals(backup, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnotherAccountsBackup() { backup.validate("otro@example.com") }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompatibleVersion() { backup.copy(version = 2).validate(backup.email) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeBalance() {
        backup.copy(progress = backup.progress.copy(balance = -1)).validate(backup.email)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPetStats() {
        backup.copy(pet = backup.pet.copy(hunger = 101)).validate(backup.email)
    }
}
