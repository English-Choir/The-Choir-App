package com.phantominnovation.choir

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

/** Cloud documents overlay the backup. Tombstones prevent deleted backup rows from returning. */
class FirebaseRegistrationLibrary(
    private val db: FirebaseFirestore,
    private val access: HostAccess,
    private val local: RegistrationStore,
) : RegistrationLibrary {
    private val collection = db.collection("registrations")
    @Volatile private var current: Catalog? = null
    @Volatile private var revisions: Map<String, Long> = emptyMap()

    override fun observe() = callbackFlow {
        val base = local.load()
        current = base
        trySend(LibraryState(base, syncMessage = "Connecting to the shared library…"))
        var rowSnapshot: QuerySnapshot? = null
        var sectionSnapshot: QuerySnapshot? = null
        var settingsSnapshot: QuerySnapshot? = null
        fun publish() {
            val snapshot = rowSnapshot ?: return
            val sectionDocs = sectionSnapshot ?: return
            val settingsDocs = settingsSnapshot ?: return
            try {
                val settings = settingsDocs.documents.associate { doc -> doc.id to SectionSetting(
                    requireNotNull(doc.getString("title")), doc.getBoolean("removed") == true,
                    doc.getString("moveTo").orEmpty(), doc.getLong("revision") ?: 0) }
                val custom = sectionDocs.documents.map { doc ->
                    require(doc.id.startsWith("section-") && doc.getBoolean("other") == true)
                    ChoirSection(doc.id, requireNotNull(doc.getString("title")), true)
                }.sortedBy { it.title.lowercase(Locale.ROOT) }
                val rows = base.registrations.associateByTo(linkedMapOf()) { it.id }
                val versions = mutableMapOf<String, Long>()
                snapshot.documents.forEach { document ->
                    versions[document.id] = requireNotNull(document.getLong("revision"))
                    if (document.getBoolean("deleted") == true) rows.remove(document.id)
                    else rows[document.id] = cloudRegistration(document.id, requireNotNull(document.data))
                }
                val sections = base.sections + custom
                // Independent listeners can deliver a registration before its new section.
                val missing = rows.values.map { it.sectionId }.distinct().filter { id -> sections.none { it.id == id } }
                val complete = sections + missing.map { ChoirSection(it, "Other section · syncing…", true) }
                val order = complete.mapIndexed { index, section -> section.id to index }.toMap()
                val catalog = Catalog(complete, rows.values.sortedWith(compareBy({ order.getValue(it.sectionId) }, { it.name.lowercase(Locale.ROOT) })))
                    .withSectionSettings(settings)
                revisions = versions
                current = catalog
                trySend(LibraryState(catalog, syncMessage = if (snapshot.metadata.isFromCache || sectionDocs.metadata.isFromCache || settingsDocs.metadata.isFromCache) "Offline · showing saved registrations" else null))
            } catch (_: Exception) {
                trySend(LibraryState(current ?: base, syncMessage = "Some cloud data couldn't be read. Your saved library is still available."))
            }
        }
        val listener = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) trySend(LibraryState(current ?: base, syncMessage = "Showing saved registrations. Cloud connection unavailable."))
            else { rowSnapshot = snapshot; publish() }
        }
        val sectionListener = db.collection("sections").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) trySend(LibraryState(current ?: base, syncMessage = "Sections couldn't be refreshed. Connect to try again."))
            else { sectionSnapshot = snapshot; publish() }
        }
        val settingsListener = db.collection("sectionSettings").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) trySend(LibraryState(current ?: base, syncMessage = "Section settings couldn't be refreshed. Connect to try again."))
            else { settingsSnapshot = snapshot; publish() }
        }
        awaitClose { listener.remove(); sectionListener.remove(); settingsListener.remove() }
    }

    private fun catalog() = requireNotNull(current) { "Wait for the library to load, then try again." }

    override suspend fun addSection(title: String): Catalog {
        val session = access.session.value
        if (!session.canEdit) throw SecurityException("Host sign-in is required to add sections.")
        val section = newOtherSection(title, catalog().sections)
        val uid = requireNotNull(session.userId)
        val ref = db.collection("sections").document(section.id)
        db.runTransaction { transaction ->
            val host = transaction.get(db.collection("hosts").document(uid))
            val existing = transaction.get(ref)
            if (host.getBoolean("active") != true || !access.session.value.canEdit || access.session.value.userId != uid)
                throw SecurityException("Host access has ended. Sign in again.")
            require(!existing.exists()) { "A section with this name already exists." }
            transaction.set(ref, mapOf("id" to section.id, "title" to section.title, "other" to true,
                "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
        val before = catalog()
        val next = before.copy(sections = before.sections.filterNot { it.id == section.id } + section)
        current = next
        return next
    }

    override suspend fun add(input: RegistrationInput): Catalog {
        val row = input.toRegistration("custom-${UUID.randomUUID()}", catalog())
        return save(row, deleted = false, creating = true)
    }

    override suspend fun changeSection(section: ChoirSection, title: String, moveTo: String?): Catalog {
        val before = catalog()
        val renamed = renamedSection(section, title, before.sections)
        require(before.sections.find { it.id == section.id } == section) { "This section changed. Reopen its controls." }
        require(moveTo == null || before.sections.any { it.id == moveTo && it.id != section.id }) { "Choose a destination section." }
        val session = access.session.value
        if (!session.canEdit) throw SecurityException("Host sign-in is required to change sections.")
        val uid = requireNotNull(session.userId)
        val expected = before.sectionSettings[section.id]?.revision ?: 0
        val setting = SectionSetting(renamed.title, moveTo != null, moveTo.orEmpty(), expected + 1)
        db.runTransaction { transaction ->
            val host = transaction.get(db.collection("hosts").document(uid))
            val ref = db.collection("sectionSettings").document(section.id)
            val old = transaction.get(ref)
            val destination = moveTo?.let { transaction.get(db.collection("sectionSettings").document(it)) }
            if (host.getBoolean("active") != true || !access.session.value.canEdit || access.session.value.userId != uid)
                throw SecurityException("Host access has ended. Sign in again.")
            require((old.getLong("revision") ?: 0) == expected && old.getBoolean("removed") != true) { "This section changed. Reopen its controls." }
            require(destination?.getBoolean("removed") != true) { "The destination was removed. Choose another section." }
            transaction.set(ref, mapOf("title" to setting.title, "removed" to setting.removed, "moveTo" to setting.moveTo,
                "revision" to setting.revision, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
        val next = catalog().withSectionSettings(catalog().sectionSettings + (section.id to setting))
        current = next
        return next
    }

    override suspend fun update(id: String, input: RegistrationInput, expected: Registration?): Catalog {
        require(catalog().registrations.any { it.id == id }) { "This registration no longer exists." }
        return save(input.toRegistration(id, catalog()), deleted = false, expected = expected)
    }

    override suspend fun delete(id: String, expected: Registration?): Catalog = save(
        catalog().registrations.singleOrNull { it.id == id } ?: throw IllegalArgumentException("This registration no longer exists."), deleted = true, expected = expected,
    )

    override suspend fun saveLinks(id: String, links: List<DriveLink>, expected: Registration?): Catalog {
        val row = catalog().registrations.singleOrNull { it.id == id } ?: throw IllegalArgumentException("This registration no longer exists.")
        return update(id, RegistrationInput.from(row).copy(links = links), expected)
    }

    private suspend fun save(row: Registration, deleted: Boolean, creating: Boolean = false, expected: Registration? = null): Catalog {
        val session = access.session.value
        if (!session.canEdit) throw SecurityException("Host sign-in is required to make changes.")
        val expectedRevision = revisions[row.id] ?: 0L
        val document = collection.document(row.id)
        // Transactions fail offline instead of queueing a mutation after sign-out or permission removal.
        db.runTransaction { transaction ->
            val host = transaction.get(db.collection("hosts").document(requireNotNull(session.userId)))
            val existing = transaction.get(document)
            if (!access.session.value.canEdit || access.session.value.userId != session.userId || host.getBoolean("active") != true) {
                throw SecurityException("Host access has ended. Sign in again.")
            }
            val remoteRevision = existing.getLong("revision") ?: 0L
            require(remoteRevision == expectedRevision && (!creating || !existing.exists())) {
                "This registration changed on another device. Close the editor, refresh the library and try again."
            }
            require(existing.getBoolean("deleted") != true) { "This registration was removed on another device." }
            if (expected != null && existing.exists()) {
                val remote = cloudRegistration(row.id, requireNotNull(existing.data))
                require(remote.copy(sectionId = resolveSection(remote.sectionId, catalog().sectionSettings)) == expected) {
                    "This registration changed while you were editing. Close the editor and try again."
                }
            }
            transaction.set(document, row.cloudData(deleted, expectedRevision + 1, requireNotNull(session.userId)))
        }.await()
        // Publish only after the server has accepted the transaction.
        val before = catalog()
        val rows = before.registrations.filterNot { it.id == row.id } + if (deleted) emptyList() else listOf(row)
        val next = before.copy(registrations = rows.sortedWith(compareBy({ it.sectionId }, { it.name.lowercase(Locale.ROOT) })))
        current = next
        revisions = revisions + (row.id to expectedRevision + 1)
        return next
    }
}

internal fun Registration.cloudData(deleted: Boolean, revision: Long, uid: String): Map<String, Any> = mapOf(
    "id" to id, "sectionId" to sectionId, "name" to name, "style" to style,
    "tempo" to tempo, "transpose" to transpose,
    "links" to links.map { mapOf("title" to it.title, "url" to it.url) },
    "deleted" to deleted, "revision" to revision, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp(),
)

private fun cloudRegistration(id: String, data: Map<String, Any>): Registration {
    require(data["id"] == id)
    val links = (data["links"] as List<*>).map { value ->
        val link = value as Map<*, *>
        DriveLink(link["title"] as String, requireNotNull(validDriveUrl(link["url"] as String)))
    }
    return Registration(id, data["sectionId"] as String, data["name"] as String, data["style"] as String,
        (data["tempo"] as Number).toInt(), (data["transpose"] as Number).toInt(), links)
}
