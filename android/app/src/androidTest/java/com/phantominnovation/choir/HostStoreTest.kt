package com.phantominnovation.choir

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = TestHostAccess()
    private val prefsName = "host_store_test"
    private val legacyName = "host_legacy_test"
    private fun store(access: HostAccess = host) = RegistrationStore(context, access, prefsName, legacyName)
    private val draft = RegistrationInput("Host test song", "entrance", "CountryWaltz", "104", "-3", listOf(DriveLink("Test score", "https://drive.google.com/file/d/host-test/view")))

    @After fun clean() {
        context.deleteSharedPreferences(prefsName)
        context.deleteSharedPreferences(legacyName)
    }

    @Test fun noWriteRouteAcceptsAViewerOrUnconfiguredSession() {
        val guest = store(PendingFirebaseHostAccess)
        val catalog = guest.load()
        val row = catalog.registrations.first()
        listOf<() -> Unit>(
            { guest.add(draft) }, { guest.update(row.id, RegistrationInput.from(row).copy(tempo = "130")) },
            { guest.delete(row.id) }, { guest.saveLinks(row.id, emptyList()) },
        ).forEach { write -> assertThrows(SecurityException::class.java) { write() } }
        assertEquals(catalog, guest.load())
        assertFalse(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).contains("catalog_edits"))
        assertFalse(HostSession("viewer", isHost = false, configured = true).canEdit)
        assertFalse(HostSession(null, isHost = true, configured = true).canEdit)
    }

    @Test fun renameAndRemoveOriginalOtherSectionsKeepEveryRegistrationAfterReload() {
        val before = store().load()
        val original = before.sections.single { it.id == "adoration" }
        val renamed = store().changeSection(original, "Adoration service", null).sections.single { it.id == original.id }
        val next = store().changeSection(renamed, renamed.title, "holy")
        assertEquals(before.registrations.size, next.registrations.size)
        assertFalse(next.sections.any { it.id == original.id })
        before.registrations.forEach { row ->
            assertEquals(if (row.sectionId == original.id) row.copy(sectionId = "holy") else row,
                next.registrations.single { it.id == row.id })
        }
        assertEquals(next, store().load())
        assertThrows(SecurityException::class.java) { store(PendingFirebaseHostAccess).changeSection(original, "Denied", null) }
    }

    @Test fun addEditMoveAndDeletePersistWithoutAlteringBackupOrReusingIdentity() {
        val before = CatalogRepository(context).load()
        val original = before.registrations.first()
        val added = store().add(draft).registrations.single { it.name == draft.name }
        assertEquals(459, store().load().registrations.size)
        assertTrue(added.id.startsWith("custom-"))
        val edited = draft.copy(name = "Renamed host song", sectionId = "mission-wedding", style = "PowerBallad", tempo = "88", transpose = "+2")
        store().update(added.id, edited)
        val fresh = store().load().registrations.single { it.id == added.id }
        assertEquals("mission-wedding", fresh.sectionId)
        assertEquals("Renamed host song", fresh.name)
        assertEquals("PowerBallad", fresh.style)
        assertEquals(88, fresh.tempo)
        assertEquals(2, fresh.transpose)
        assertEquals(draft.links, fresh.links)
        store().update(original.id, RegistrationInput.from(original).copy(name = "Renamed original", tempo = "99"))
        assertEquals(original.links, store().load().registrations.single { it.id == original.id }.links)
        store().delete(original.id)
        store().delete(added.id)
        val reloaded = store().load()
        assertEquals(457, reloaded.registrations.size)
        assertFalse(reloaded.registrations.any { it.id in setOf(added.id, original.id) })
        assertEquals(before, CatalogRepository(context).load())
    }

    @Test fun oldDriveLinksSurviveUpgradeAndHostCanRemoveThemWithoutFallback() {
        val row = CatalogRepository(context).load().registrations.first { it.links.isEmpty() }
        val url = "https://docs.google.com/document/d/existing-user-link/edit"
        val encoded = JSONArray().put(JSONObject().put("title", "Previously saved file").put("url", url)).toString()
        context.getSharedPreferences(legacyName, Context.MODE_PRIVATE).edit().putString(row.id, encoded).commit()
        val migrated = store(PendingFirebaseHostAccess).load().registrations.single { it.id == row.id }
        assertEquals(url, migrated.links.single().url)
        store().update(row.id, RegistrationInput.from(migrated).copy(tempo = "100"))
        assertEquals(migrated.links, store().load().registrations.single { it.id == row.id }.links)
        store().saveLinks(row.id, emptyList())
        assertTrue(store().load().registrations.single { it.id == row.id }.links.isEmpty())
        assertEquals(encoded, context.getSharedPreferences(legacyName, Context.MODE_PRIVATE).getString(row.id, null))
    }

    @Test fun revocationAndInvalidEditsCannotChangeStoredData() {
        val sameStore = store()
        val row = sameStore.add(draft).registrations.single { it.name == draft.name }
        val before = sameStore.load()
        assertThrows(IllegalArgumentException::class.java) { sameStore.update(row.id, draft.copy(tempo = "501")) }
        assertEquals(before, sameStore.load())
        host.signOut()
        assertThrows(SecurityException::class.java) { sameStore.update(row.id, draft.copy(name = "Not allowed")) }
        assertThrows(SecurityException::class.java) { sameStore.delete(row.id) }
        assertThrows(SecurityException::class.java) { sameStore.saveLinks(row.id, emptyList()) }
        assertEquals(before, sameStore.load())
    }

    @Test fun damagedLocalDataIsNotOverwrittenByANewSave() {
        val raw = "broken saved catalog"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("catalog_edits", raw).commit()
        assertThrows(org.json.JSONException::class.java) { store().add(draft) }
        assertEquals(raw, prefs.getString("catalog_edits", null))
    }
}
