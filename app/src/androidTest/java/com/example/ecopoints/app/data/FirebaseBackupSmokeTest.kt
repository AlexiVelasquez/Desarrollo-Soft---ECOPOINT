package com.example.ecopoints.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ecopoints.app.data.local.CloudPreferences
import com.example.ecopoints.app.data.local.datastore.EcoPreferencesStore
import com.example.ecopoints.app.data.local.db.*
import com.example.ecopoints.app.data.model.LoginResult
import com.example.ecopoints.app.data.model.RegisterResult
import com.example.ecopoints.app.data.remote.CloudSyncCoordinator
import com.example.ecopoints.app.data.remote.FirebaseBackupRepository
import com.example.ecopoints.app.data.repository.SettingsRepository
import com.example.ecopoints.app.data.repository.UserRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Cuenta temporal y dos dispositivos simulados, sin alterar la sesión ni los datos reales. */
@RunWith(AndroidJUnit4::class)
class FirebaseBackupSmokeTest {
    @Test fun savesRestoresAndProtectsPrivateBackup() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseSmoke") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = checkNotNull(FirebaseApp.initializeApp(context)).options
        check(options.projectId == "ecopoints-3d8bb")
        val suffix = UUID.randomUUID().toString()
        val app = FirebaseApp.initializeApp(context, options, "qa-" + suffix)
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        val email = "ecopoints.qa." + suffix + "@example.com"
        val password = "Eco!" + suffix
        val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val syncScopes = (1..2).map { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        val files = (1..2).map { File(context.cacheDir, "auth-smoke-" + suffix + "-" + it + ".preferences_pb") }
        val preferenceNames = (1..2).map { "sync-smoke-" + suffix + "-" + it }
        val settings = files.map { file ->
            SettingsRepository(EcoPreferencesStore(PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { file })))
        }
        val databases = (1..2).map { Room.inMemoryDatabaseBuilder(context, EcoDatabase::class.java).build() }
        val clouds = databases.mapIndexed { i, db -> FirebaseBackupRepository(context, db, settings[i], app, writeTimeoutMillis = 5_000) }
        val syncs = databases.mapIndexed { i, db ->
            CloudSyncCoordinator(db, settings[i], clouds[i], CloudPreferences(context, preferenceNames[i]), syncScopes[i],
                debounceMillis = 100, retryMillis = 300)
        }
        val users = databases.mapIndexed { i, db -> UserRepository(db, settings[i], clouds[i], syncs[i]) }
        syncs.forEach { it.start() }
        try {
            withTimeout(180_000) {
                val account = users[0].register("Prueba Firebase", email, password, "Koala", "Mascota QA")
                assertTrue("Registro: " + account, account is RegisterResult.Success)
                val firstId = (account as RegisterResult.Success).userId
                val uid = checkNotNull(auth.currentUser).uid
                val profileDocument = firestore.collection("users").document(uid)
                assertEquals(email, profileDocument.get(Source.SERVER).await().getString("email"))

                users[0].updateProfile(firstId, "Perfil actualizado", email)
                databases[0].progressDao().earn(firstId, 25)
                databases[0].petDao().updateName(firstId, "Mascota actualizada")
                val challenge = ChallengeEntity(userId = firstId, icon = "eco", title = "Reciclar", description = "Prueba",
                    durationDays = 2, startMillis = System.currentTimeMillis(), evidencePath = "/private/device-one.jpg")
                databases[0].challengeDao().insert(challenge)
                databases[0].achievementDao().insertAll(listOf(AchievementUnlockEntity(firstId, "qa-achievement", 1L)))
                databases[0].activityLogDao().insert(ActivityLogEntity(userId = firstId, actionType = "QA", description = "Guardado automático", timestamp = 1L))
                settings[0].setLargeText(true)
                settings[0].setMapRadiusKm(3)

                // Sin pulsar ningún botón: las mutaciones llegan al servidor.
                withTimeout(20_000) {
                    while (true) {
                        val remote = clouds[0].readBackup(email)
                        if (remote?.progress?.balance == 75 && remote.pet.name == "Mascota actualizada" &&
                            remote.achievements.size == 1 && remote.activity.size == 1 && remote.settings.largeText &&
                            remote.profileName == "Perfil actualizado" && remote.settings.mapRadiusKm == 3) break
                        delay(250)
                    }
                }
                assertEquals("Mascota actualizada", profileDocument.get(Source.SERVER).await().getString("petName"))
                assertTrue(syncs[0].state.value.lastSync > 0)

                // Un cambio inmediatamente antes de salir debe confirmarse antes de cerrar.
                databases[0].progressDao().earn(firstId, 5)
                users[0].logout()
                assertNull(settings[0].sessionUserId.first())
                assertNull(auth.currentUser)
                assertTrue(users[1].login(email, "wrong-password") is LoginResult.Error)
                assertEquals(0, databases[1].userDao().count())
                val login = users[1].login(email, password)
                assertTrue("Segundo dispositivo: " + login, login is LoginResult.Success)
                val secondId = (login as LoginResult.Success).userId
                assertEquals(80, databases[1].progressDao().get(secondId)?.balance)
                assertEquals("Mascota actualizada", databases[1].petDao().get(secondId)?.name)
                assertEquals("Perfil actualizado", databases[1].userDao().findById(secondId)?.name)
                assertEquals(1, databases[1].achievementDao().getAll(secondId).size)
                assertEquals(1, databases[1].activityLogDao().getAll(secondId).size)
                assertTrue(settings[1].settings.first().largeText)
                assertEquals("", databases[1].challengeDao().getAll(secondId).single().evidencePath)

                databases[1].progressDao().earn(secondId, 10)
                settings[1].setLargeText(false)
                databases[1].petDao().updateName(secondId, "Mascota segundo dispositivo")
                users[1].logout()

                // La caché antigua del primer dispositivo no puede ocultar la actualización remota.
                assertTrue(users[0].login(email, password) is LoginResult.Success)
                assertEquals(90, databases[0].progressDao().get(firstId)?.balance)
                assertFalse(settings[0].settings.first().largeText)
                assertEquals("Mascota segundo dispositivo", databases[0].petDao().get(firstId)?.name)

                // Sin red, salir falla y conserva tanto la sesión como el cambio pendiente.
                firestore.disableNetwork().await()
                databases[0].progressDao().earn(firstId, 7)
                try {
                    users[0].logout()
                    fail("No debe cerrar la sesión mientras el cambio no está confirmado")
                } catch (_: TimeoutCancellationException) {
                    assertEquals(firstId, settings[0].sessionUserId.first())
                    assertNotNull(auth.currentUser)
                    assertEquals(97, databases[0].progressDao().get(firstId)?.balance)
                }
                firestore.enableNetwork().await()
                users[0].logout()
                assertTrue(users[1].login(email, password) is LoginResult.Success)
                assertEquals(97, databases[1].progressDao().get(secondId)?.balance)

                users[1].logout()
                try {
                    profileDocument.get(Source.SERVER).await()
                    fail("El perfil no permite lectura anónima")
                } catch (error: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
                }
            }
        } finally {
            syncScopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
            try {
                withTimeout(30_000) {
                    firestore.enableNetwork().await()
                    val cleanupUser = auth.signInWithEmailAndPassword(email, password).await().user
                    firestore.waitForPendingWrites().await()
                    if (cleanupUser != null) {
                        try {
                            firestore.collection("backups").document(cleanupUser.uid).delete().await()
                            firestore.collection("users").document(cleanupUser.uid).delete().await()
                        } finally { cleanupUser.delete().await() }
                    }
                }
            } finally {
                auth.signOut()
                databases.forEach { it.close() }
                dataScope.cancel()
                files.forEach { it.delete() }
                preferenceNames.forEach { context.deleteSharedPreferences(it) }
                app.delete()
            }
        }
    }
}
