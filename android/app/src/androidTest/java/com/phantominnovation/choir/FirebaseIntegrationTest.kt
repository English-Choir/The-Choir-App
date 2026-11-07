package com.phantominnovation.choir

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** All identities, credentials and admin fixture calls stay in the local demo emulators. */
class FirebaseIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val firestorePort get() = InstrumentationRegistry.getArguments().getString("firestorePort")?.toInt() ?: 8080
    private val authPort get() = InstrumentationRegistry.getArguments().getString("authPort")?.toInt() ?: 9099

    private fun emulatorAdmin(path: String, body: JSONObject) {
        val connection = URL("http://10.0.2.2:$firestorePort/v1/projects/demo-the-choir/databases/(default)/documents/$path").openConnection() as HttpURLConnection
        connection.requestMethod = "PATCH"
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.doOutput = true
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        check(connection.responseCode in 200..299) { "Emulator fixture write failed" }
        connection.disconnect()
    }

    @Test fun actualFirebaseSignInCloudCrudRevocationAndOfflineSafety() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseEmulators") == "true")
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-the-choir").setApplicationId("1:123456789:android:0000000000000000")
            .setApiKey("emulator-only-not-a-real-api-key").build(), "integration-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2",authPort) }
        val db = FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2",firestorePort)
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
        val access = FirebaseHostAccess(auth,db)
        val local = RegistrationStore(context,access,"cloud_integration_edits","cloud_integration_links")
        val library = FirebaseRegistrationLibrary(db,access,local)
        val observed = mutableListOf<LibraryState>()
        val job = launch { library.observe().collect { observed.add(it) } }
        try {
            withTimeout(15_000) { while (observed.none { it.catalog != null && it.syncMessage == null }) delay(50) }
            val originalCount = observed.last().catalog!!.registrations.size
            val email = "host-${UUID.randomUUID()}@example.test"
            val password = "Only-for-local-emulator-123!"
            auth.createUserWithEmailAndPassword(email,password).await()
            val uid = auth.currentUser!!.uid
            access.signOut()
            assertNull(access.signIn(email,password))
            withTimeout(10_000) { access.session.first { it.userId == uid && !it.checking } }
            assertFalse(access.session.value.canEdit)
            assertTrue(runCatching { library.add(RegistrationInput(name="Denied",style="Waltz")) }.exceptionOrNull() is SecurityException)
            assertTrue(runCatching { library.addSection("Denied section") }.exceptionOrNull() is SecurityException)
            withContext(Dispatchers.IO) {
                emulatorAdmin("hosts/$uid", JSONObject("""{"fields":{"active":{"booleanValue":true}}}"""))
            }
            withTimeout(10_000) { access.session.first { it.canEdit } }
            val custom = library.addSection("Integration Christmas").sections.single { it.title == "Integration Christmas" }
            assertTrue(custom.other)
            assertTrue(runCatching { library.addSection(" integration   CHRISTMAS ") }.isFailure)
            val input = RegistrationInput(name="Cloud hymn ${UUID.randomUUID()}",style="CountryWaltz",tempo="90",transpose="-2")
            val added = library.add(input).registrations.single { it.name == input.name }
            val edited = library.update(added.id,RegistrationInput.from(added).copy(sectionId=custom.id,tempo="120"),added)
                .registrations.single { it.id == added.id }
            assertEquals(custom.id,edited.sectionId)
            assertEquals(120,edited.tempo)
            // A stale editor must not overwrite a newer version already observed on this device.
            assertTrue(runCatching { library.update(added.id,RegistrationInput.from(added).copy(tempo="130"),added) }.isFailure)
            var linked = library.saveLinks(edited.id,listOf(DriveLink("Score","https://drive.google.com/file/d/test/view")),edited)
                .registrations.single { it.id == added.id }
            assertEquals(1,linked.links.size)
            val chords = FirebaseOfflineChords(db,access,emptyList())
            chords.setMode(linked,ChordMode.ENABLED,0)
            val chordDoc = db.collection("chordOverrides").document(linked.id).get().await()
            assertEquals("enabled",chordDoc.getString("mode"))
            assertEquals(true,chordDoc.getBoolean("appendPending"))
            assertTrue(runCatching { chords.setMode(linked,ChordMode.DISABLED,0) }.isFailure)
            chords.setMode(linked,ChordMode.DISABLED,1)
            assertFalse(chords.observe().first { it.overrides[linked.id]?.revision == 2L }.available(linked))
            chords.setMode(linked,ChordMode.AUTOMATIC,2)
            assertEquals("automatic",db.collection("chordOverrides").document(linked.id).get().await().getString("mode"))
            // A separate unauthenticated Firebase client represents another installation.
            val guestApp = FirebaseApp.initializeApp(context, app.options, "hymn-guest-${UUID.randomUUID()}")
            val guestAuth = FirebaseAuth.getInstance(guestApp).apply { useEmulator("10.0.2.2",authPort) }
            val guestDb = FirebaseFirestore.getInstance(guestApp).apply {
                useEmulator("10.0.2.2",firestorePort)
                // Match the real app's disk cache; eager memory GC drops rows after a listener closes.
                firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()
            }
            try {
                val guestLibrary = FirebaseRegistrationLibrary(guestDb,PendingFirebaseHostAccess,local)
                val shared = withTimeout(10_000) { guestLibrary.observe().first { it.syncMessage == null } }.catalog!!
                assertTrue(shared.sections.contains(custom))
                assertEquals(custom.id,shared.registrations.single { it.id == linked.id }.sectionId)
                assertTrue(runCatching { guestLibrary.addSection("Guest section") }.exceptionOrNull() is SecurityException)
                val plans = FirebaseHymnPlans(db,access,auth)
                val guestPlans = FirebaseHymnPlans(guestDb,PendingFirebaseHostAccess,guestAuth)
                val plan = HymnPlan("1", "Sunday Mass", "Entrance: ${linked.name}", mapOf(1 to linked.id))
                plans.save(plan,0)
                val fromOtherDevice = withTimeout(10_000) { guestPlans.observe().first { it.plans[0].revision == 1L } }
                assertEquals(plan.text, fromOtherDevice.plans[0].text)
                assertEquals(plan.choices, fromOtherDevice.plans[0].choices)
                val appended = plan.copy(text = plan.text + "\nTrust in me")
                guestPlans.save(appended,1)
                assertTrue(guestAuth.currentUser!!.isAnonymous)
                val chosen = appended.copy(choices = appended.choices + (2 to linked.id))
                guestPlans.save(chosen,2)
                val received = withTimeout(10_000) { plans.observe().first { it.plans[0].revision == 3L } }.plans[0]
                assertEquals(chosen.text,received.text)
                assertEquals(chosen.choices,received.choices)
                assertTrue(runCatching { guestPlans.save(HymnPlan("1"),3) }.exceptionOrNull() is SecurityException)
                assertTrue(runCatching { guestPlans.save(chosen.copy(text="Replacement"),3) }.exceptionOrNull() is SecurityException)
                assertTrue(runCatching { guestPlans.save(chosen,2) }.isFailure)
                val guestAccess = FirebaseHostAccess(guestAuth,guestDb)
                try {
                    withTimeout(5_000) { guestAccess.session.first { it.configured && !it.checking } }
                    assertFalse(guestAccess.session.value.canEdit)
                    assertNull(guestAccess.session.value.userId)
                    assertNull(guestAccess.session.value.message)
                } finally { guestAccess.close() }
                assertTrue(runCatching { plans.save(plan.copy(title="Stale"),0) }.isFailure)
                // As in the app, receive the latest watch snapshot before checking its offline cache.
                val guestSynced = withTimeout(10_000) { guestPlans.observe().first { it.plans[0].revision == 3L } }
                assertEquals(chosen.text,guestSynced.plans[0].text)
                guestDb.disableNetwork().await()
                val cached = withTimeout(5_000) { guestLibrary.observe().first { it.catalog?.sections?.contains(custom) == true } }.catalog!!
                assertEquals(custom.id,cached.registrations.single { it.id == linked.id }.sectionId)
                val offline = withTimeout(5_000) { guestPlans.observe().first { it.loaded } }
                assertEquals(chosen.text,offline.plans[0].text)
                plans.save(HymnPlan("1"),3)
                guestDb.enableNetwork().await()
                val cleared = withTimeout(10_000) { guestPlans.observe().first { it.plans[0].revision == 4L } }
                assertTrue(cleared.plans[0].text.isEmpty())
                assertTrue(cleared.plans[0].choices.isEmpty())
                val renamed = custom.copy(title = "Christmas service")
                library.changeSection(custom, renamed.title)
                val renamedRemote = withTimeout(10_000) {
                    guestLibrary.observe().first { it.catalog?.sections?.contains(renamed) == true }
                }.catalog!!
                assertEquals(linked, renamedRemote.registrations.single { it.id == linked.id })
                assertTrue(runCatching { guestLibrary.changeSection(renamed, "Denied") }.exceptionOrNull() is SecurityException)
                library.changeSection(renamed, renamed.title, "holy")
                val moved = withTimeout(10_000) {
                    guestLibrary.observe().first { it.catalog?.sectionSettings?.get(custom.id)?.removed == true }
                }.catalog!!
                assertFalse(moved.sections.any { it.id == custom.id })
                val kept = moved.registrations.single { it.id == linked.id }
                assertEquals(linked.copy(sectionId = "holy"), kept)
                val matched = ChordState(listOf(ChordSong(custom.id, linked.name))).forCatalog(moved)
                assertTrue(matched.available(kept))
                chords.setMode(kept, ChordMode.ENABLED, 3)
                linked = library.update(kept.id, RegistrationInput.from(kept).copy(tempo = "121"), kept)
                    .registrations.single { it.id == kept.id }
                assertEquals("holy", linked.sectionId)
                guestDb.disableNetwork().await()
                val movedOffline = withTimeout(5_000) {
                    guestLibrary.observe().first { it.catalog?.sectionSettings?.get(custom.id)?.removed == true }
                }.catalog!!
                assertFalse(movedOffline.sections.any { it.id == custom.id })
                assertEquals("holy", movedOffline.registrations.single { it.id == linked.id }.sectionId)
                guestDb.enableNetwork().await()
            } finally { guestDb.terminate().await(); guestDb.clearPersistence().await(); guestApp.delete() }
            library.delete(linked.id,linked)
            assertTrue(runCatching { chords.setMode(linked,ChordMode.ENABLED,3) }.isFailure)
            val reopened = FirebaseRegistrationLibrary(db,access,local).observe().first { it.syncMessage == null }
            assertFalse(reopened.catalog!!.registrations.any { it.id == added.id })
            assertEquals(originalCount,reopened.catalog!!.registrations.size)

            db.disableNetwork().await()
            assertTrue(runCatching { library.add(input.copy(name="Offline write")) }.isFailure)
            db.enableNetwork().await()
            withContext(Dispatchers.IO) {
                emulatorAdmin("hosts/$uid", JSONObject("""{"fields":{"active":{"booleanValue":false}}}"""))
            }
            withTimeout(10_000) { access.session.first { !it.canEdit } }
            assertTrue(runCatching { library.add(input.copy(name="Revoked write")) }.exceptionOrNull() is SecurityException)
            access.signOut()
            assertNull(access.session.value.userId)
            assertFalse(access.session.value.canEdit)
        } finally {
            job.cancelAndJoin(); access.close(); auth.signOut(); db.terminate().await(); app.delete()
            context.deleteSharedPreferences("cloud_integration_edits")
            context.deleteSharedPreferences("cloud_integration_links")
        }
    }
}
