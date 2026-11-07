package com.phantominnovation.choir

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class ChordMode(val value: String, val label: String) {
    AUTOMATIC("automatic", "Automatic"), ENABLED("enabled", "Enabled"), DISABLED("disabled", "Disabled");
    companion object { fun from(value: String?) = entries.firstOrNull { it.value == value } ?: AUTOMATIC }
}

data class ChordSong(val sectionId: String, val name: String, val aliases: List<String> = emptyList())
data class ChordOverride(val mode: ChordMode = ChordMode.AUTOMATIC, val revision: Long = 0, val appendPending: Boolean = false)
data class ChordState(
    val songs: List<ChordSong> = emptyList(), val overrides: Map<String, ChordOverride> = emptyMap(),
    val message: String? = "Loading chord availability…",
) {
    fun forCatalog(catalog: Catalog?) = if (catalog == null || catalog.sectionSettings.isEmpty()) this else
        copy(songs = songs.map { it.copy(sectionId = resolveSection(it.sectionId, catalog.sectionSettings)) })
    private val songIndex by lazy {
        songs.flatMap { song -> song.nameKeys().map { key -> (song.sectionId to key) to song } }
            .groupBy({ it.first }, { it.second })
    }
    fun matchingSongs(row: Registration): List<ChordSong> = songIndex[row.sectionId to chordKey(row.name)].orEmpty()
    fun matches(row: Registration): Boolean = matchingSongs(row).isNotEmpty()
    fun setting(row: Registration) = overrides[row.id] ?: ChordOverride()
    fun available(row: Registration) = when (setting(row).mode) {
        ChordMode.AUTOMATIC -> matches(row)
        ChordMode.ENABLED -> true
        ChordMode.DISABLED -> false
    }
}

internal fun ChordSong.nameKeys() = (listOf(name) + aliases).flatMap(::chordNames).map(::chordKey).filter { it.isNotEmpty() }.toSet()

// Only section tags and printing instructions are ignored; musical variants (Dm, waltz) stay distinct.
fun chordKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("\\s*\\((?:lhm|holy|log|comm|communion|offertory|recc|recessional|proclamation|thanks|reprint[^)]*)\\)"), "")
    .replace("&", "and").replace(Regex("[^a-z0-9]"), "")

private fun chordNames(name: String): List<String> {
    val alternate = Regex("^(.*?)\\s*\\(([^)]+)\\)\\s*$").matchEntire(name)
    return if (alternate != null && alternate.groupValues[2].trim().split(Regex("\\s+")).size >= 2 &&
        !alternate.groupValues[2].startsWith("reprint", true))
        listOf(name, alternate.groupValues[1].trim(), alternate.groupValues[2].trim()) else listOf(name)
}

fun bundledChordSongs(context: Context): List<ChordSong> {
    val sections = JSONObject(context.assets.open("reviewed_chords.json").bufferedReader().use { it.readText() }).getJSONArray("sections")
    return buildList {
        for (i in 0 until sections.length()) {
            val section = sections.getJSONObject(i)
            val songs = section.getJSONArray("songs")
            for (j in 0 until songs.length()) {
                val song = songs.getJSONObject(j)
                val aliases = song.optJSONArray("aliases")
                add(ChordSong(section.getString("id"), song.getString("name"),
                    if (aliases == null) emptyList() else (0 until aliases.length()).map { aliases.getString(it) }))
            }
        }
    }
}

interface OfflineChordsRepository {
    fun observe(): Flow<ChordState>
    suspend fun setMode(row: Registration, mode: ChordMode, expectedRevision: Long)
}

class ReadOnlyOfflineChords(private val songs: List<ChordSong> = emptyList()) : OfflineChordsRepository {
    override fun observe() = MutableStateFlow(ChordState(songs, message = "Chord list saved on this device"))
    override suspend fun setMode(row: Registration, mode: ChordMode, expectedRevision: Long) {
        throw SecurityException("Connect Firebase to change chord availability.")
    }
}

class FirebaseOfflineChords(
    private val db: FirebaseFirestore, private val access: HostAccess, private val bundled: List<ChordSong>,
) : OfflineChordsRepository {
    override fun observe() = callbackFlow {
        var state = ChordState(bundled)
        var catalogMessage: String? = state.message
        var overrideMessage: String? = null
        var healthMessage: String? = null
        fun publish() { trySend(state.copy(message = listOfNotNull(healthMessage, catalogMessage, overrideMessage).joinToString(" · ").ifEmpty { null })) }
        publish()
        val catalogListener = db.collection("chordCatalog").document("current").addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (error != null || doc == null) catalogMessage = "Chord sync unavailable · showing saved list"
            else if (!doc.exists()) catalogMessage = "Using supplied chord list · Google Sheets sync awaiting setup"
            else try {
                val sections = doc.get("sections") as List<*>
                val songs = sections.flatMap { value ->
                    val section = value as Map<*, *>
                    (section["songs"] as List<*>).map { entry ->
                        val song = entry as Map<*, *>
                        ChordSong(section["id"] as String, song["name"] as String,
                            (song["aliases"] as? List<*>)?.map { it as String } ?: emptyList())
                    }
                }
                state = state.copy(songs = songs)
                val stamp = doc.getTimestamp("syncedAt")?.toDate()?.let {
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(it)
                }
                catalogMessage = if (doc.metadata.isFromCache) "Chord list saved offline"
                    else if (doc.getBoolean("syncEnabled") != true) "Chord list imported · automatic sheet sync awaiting setup"
                    else "Chord list synced${stamp?.let { " · $it" }.orEmpty()}"
            } catch (_: Exception) { catalogMessage = "Chord list couldn't be refreshed · showing saved list" }
            publish()
        }
        val overrideListener = db.collection("chordOverrides").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) overrideMessage = "Host chord settings unavailable"
            else {
                state = state.copy(overrides = snapshot.documents.associate { it.id to ChordOverride(
                    ChordMode.from(it.getString("mode")), it.getLong("revision") ?: 0, it.getBoolean("appendPending") == true) })
                overrideMessage = if (snapshot.metadata.isFromCache) "Saved host settings" else null
            }
            publish()
        }
        val healthListener = db.collection("chordSync").document("status").addSnapshotListener { doc, _ ->
            if (doc?.exists() == true) {
                val lastSuccess = doc.getTimestamp("lastSuccessAt")?.toDate()?.time
                healthMessage = if (doc.getString("status") == "error") "Google Sheets sync needs attention"
                    else if (lastSuccess != null && System.currentTimeMillis() - lastSuccess > 45 * 60 * 1000) "Google Sheets sync is delayed"
                    else null
                publish()
            }
        }
        awaitClose { catalogListener.remove(); overrideListener.remove(); healthListener.remove() }
    }

    override suspend fun setMode(row: Registration, mode: ChordMode, expectedRevision: Long) {
        val session = access.session.value
        if (!session.canEdit) throw SecurityException("Host sign-in is required to change chord availability.")
        val uid = requireNotNull(session.userId)
        val ref = db.collection("chordOverrides").document(row.id)
        db.runTransaction { transaction ->
            val host = transaction.get(db.collection("hosts").document(uid))
            val registration = transaction.get(db.collection("registrations").document(row.id))
            val previous = transaction.get(ref)
            if (host.getBoolean("active") != true || !access.session.value.canEdit || access.session.value.userId != uid)
                throw SecurityException("Host access has ended. Sign in again.")
            require(registration.exists() && registration.getBoolean("deleted") != true) { "This registration no longer exists." }
            require(registration.getString("name") == row.name) {
                "This registration changed. Close these controls and try again."
            }
            require((previous.getLong("revision") ?: 0) == expectedRevision) { "Another host changed this setting. Close these controls and try again." }
            transaction.set(ref, mapOf("mode" to mode.value, "revision" to expectedRevision + 1,
                "appendPending" to (mode == ChordMode.ENABLED), "name" to row.name, "sectionId" to requireNotNull(registration.getString("sectionId")),
                "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
    }
}
