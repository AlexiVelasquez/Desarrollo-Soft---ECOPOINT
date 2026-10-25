package com.example.ecopoints.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ecopoints.app.data.local.datastore.EcoPreferencesStore
import com.example.ecopoints.app.data.local.db.*
import com.example.ecopoints.app.data.model.LoginResult
import com.example.ecopoints.app.data.model.RegisterResult
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

/** Dos cachés vacías simulan dos dispositivos. FirebaseApp aislada conserva la sesión real del usuario. */
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val files = (1..2).map { File(context.cacheDir, "auth-smoke-" + suffix + "-" + it + ".preferences_pb") }
        val settings = files.map { file ->
            SettingsRepository(EcoPreferencesStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })))
        }
        val databases = (1..2).map { Room.inMemoryDatabaseBuilder(context, EcoDatabase::class.java).build() }
        val clouds = databases.mapIndexed { i, db -> FirebaseBackupRepository(context, db, settings[i], app) }
        val users = databases.mapIndexed { i, db -> UserRepository(db, settings[i], clouds[i]) }
        var uid: String? = null
        try {
            withTimeout(120_000) {
                val account = users[0].register("Prueba Firebase", email, password, "Koala", "Mascota QA")
                uid = auth.currentUser?.uid
                assertTrue("El registro remoto debe funcionar: " + account, account is RegisterResult.Success)
                val firstId = (account as RegisterResult.Success).userId
                assertEquals(uid, databases[0].userDao().findById(firstId)?.firebaseUid)
                assertTrue(clouds[0].isSignedInAs(email))
                assertNotNull(clouds[0].readBackup(email))
                assertNull(databases[0].userDao().findById(firstId)?.passwordHash)
                settings[0].setLargeText(true)
                databases[0].progressDao().earn(firstId, 25)
                databases[0].challengeDao().insert(ChallengeEntity(
                    userId = firstId, icon = "eco", title = "Reciclar", description = "Prueba",
                    durationDays = 2, startMillis = System.currentTimeMillis(), evidencePath = "/private/only-device-one.jpg"
                ))
                clouds[0].transfer(restore = false)
                assertTrue(clouds[0].isSignedInAs(email))
                users[0].logout()

                assertEquals(0, databases[1].userDao().count())
                assertTrue(users[1].login(email, "wrong-password") is LoginResult.Error)
                assertNull(settings[1].sessionUserId.first())
                assertNull(auth.currentUser)
                assertEquals(0, databases[1].userDao().count())

                val login = users[1].login(email, password)
                assertTrue("Debe entrar sin registrarse en el segundo dispositivo: " + login, login is LoginResult.Success)
                val secondId = (login as LoginResult.Success).userId
                assertEquals(uid, databases[1].userDao().findById(secondId)?.firebaseUid)
                assertEquals("Prueba Firebase", databases[1].userDao().findById(secondId)?.name)
                assertEquals(75, databases[1].progressDao().get(secondId)?.balance)
                assertEquals("Mascota QA", databases[1].petDao().get(secondId)?.name)
                assertTrue(settings[1].settings.first().largeText)
                assertEquals(1, databases[1].challengeDao().getAll(secondId).size)
                assertEquals("", databases[1].challengeDao().getAll(secondId).single().evidencePath)
                assertEquals(secondId, settings[1].sessionUserId.first())
                assertTrue(users[1].canContinue(checkNotNull(users[1].getUser(secondId))))

                databases[1].progressDao().earn(secondId, 10)
                users[1].logout()
                assertTrue(users[1].login(email, password) is LoginResult.Success)
                assertEquals(85, databases[1].progressDao().get(secondId)?.balance)
                assertEquals(RegisterResult.EmailTaken, users[1].register("Duplicado", email, password, "Koala", "Otra"))
                assertNull(settings[1].sessionUserId.first())
                assertNull(auth.currentUser)
                try {
                    firestore.collection("backups").document(checkNotNull(uid)).get(Source.SERVER).await()
                    fail("La lectura sin autenticación debe quedar denegada")
                } catch (error: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
                }
            }
        } finally {
            try {
                withTimeout(30_000) {
                    val cleanupUser = auth.signInWithEmailAndPassword(email, password).await().user
                    if (cleanupUser != null) {
                        firestore.collection("backups").document(cleanupUser.uid).delete().await()
                        cleanupUser.delete().await()
                    }
                }
            } finally {
                auth.signOut()
                databases.forEach { it.close() }
                scope.cancel()
                files.forEach { it.delete() }
                app.delete()
            }
        }
    }
}
