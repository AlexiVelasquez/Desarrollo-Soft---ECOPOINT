package com.example.ecopoints.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ecopoints.app.data.local.datastore.EcoPreferencesStore
import com.example.ecopoints.app.data.local.db.EcoDatabase
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

/** Prueba remota optativa: crea y elimina exclusivamente su cuenta y su respaldo temporales. */
@RunWith(AndroidJUnit4::class)
class FirebaseBackupSmokeTest {
    @Test fun savesRestoresAndProtectsPrivateBackup() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseSmoke") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = checkNotNull(FirebaseApp.initializeApp(context))
        check(app.options.projectId == "ecopoints-3d8bb")
        val auth = FirebaseAuth.getInstance(app)
        check(auth.currentUser == null) { "La prueba requiere que no exista una sesión Firebase activa" }
        val firestore = FirebaseFirestore.getInstance(app)
        val suffix = UUID.randomUUID().toString()
        val email = "ecopoints.qa.$suffix@example.com"
        val password = "Eco!$suffix"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferencesFile = File(context.cacheDir, "firebase-smoke-$suffix.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile })
        val settings = SettingsRepository(EcoPreferencesStore(store))
        val database = Room.inMemoryDatabaseBuilder(context, EcoDatabase::class.java).build()
        var uid: String? = null
        try {
            uid = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user).uid
            auth.signOut()
            val users = UserRepository(database, settings)
            val account = users.register("Prueba Firebase", email, password, "Koala", "Mascota QA")
            val localId = (account as RegisterResult.Success).userId
            settings.setLargeText(true)
            val repository = FirebaseBackupRepository(context, database, settings)
            repository.transfer(password, createAccount = false, restore = false)
            database.progressDao().earn(localId, 25)
            database.petDao().updateName(localId, "Nombre modificado")
            settings.setLargeText(false)
            repository.transfer(password, createAccount = false, restore = true)
            assertEquals(50, database.progressDao().get(localId)?.balance)
            assertEquals("Mascota QA", database.petDao().get(localId)?.name)
            assertTrue(settings.settings.first().largeText)
            assertNull(auth.currentUser)
            try {
                firestore.collection("backups").document(uid).get(Source.SERVER).await()
                fail("La lectura sin autenticación debe quedar denegada")
            } catch (error: FirebaseFirestoreException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
            }
        } finally {
            try {
                if (uid != null) {
                    withTimeout(30_000) {
                        auth.signInWithEmailAndPassword(email, password).await()
                        firestore.collection("backups").document(uid).delete().await()
                        checkNotNull(auth.currentUser).delete().await()
                    }
                }
            } finally {
                auth.signOut()
                database.close()
                scope.cancel()
                preferencesFile.delete()
            }
        }
    }
}
