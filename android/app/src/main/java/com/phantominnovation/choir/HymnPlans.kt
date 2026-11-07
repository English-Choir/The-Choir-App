package com.phantominnovation.choir

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class HymnPlan(val slot: String, val title: String = "Hymn list $slot", val text: String = "",
    val choices: Map<Int, String> = emptyMap(), val revision: Long = 0)
data class HymnPlansState(val plans: List<HymnPlan> = (1..3).map { HymnPlan(it.toString()) },
    val loaded: Boolean = false, val message: String? = "Loading shared hymn lists…")

fun validateHymnPlan(plan: HymnPlan, sections: List<ChoirSection>) {
    require(plan.slot in listOf("1", "2", "3")) { "Choose one of the three hymn lists." }
    require(plan.title.trim().isNotEmpty() && plan.title.length <= 80) { "Enter a list title up to 80 characters." }
    require(plan.text.length <= 20_000 && parseHymnList(plan.text, sections).size <= 200) { "Use up to 200 hymns and 20,000 characters per list." }
    require(plan.choices.size <= 200 && plan.choices.all { (position, id) -> position in 1..200 && id.length <= 100 }) { "Invalid registration choices." }
}

interface HymnPlansRepository {
    fun observe(): Flow<HymnPlansState>
    suspend fun save(plan: HymnPlan, expectedRevision: Long)
}
object ReadOnlyHymnPlans : HymnPlansRepository {
    override fun observe() = MutableStateFlow(HymnPlansState(loaded = true, message = "Shared lists unavailable in this build"))
    override suspend fun save(plan: HymnPlan, expectedRevision: Long) { throw SecurityException("Connect Firebase to save shared lists.") }
}

/** Guest writes can append lines or change choices, but cannot erase or rewrite existing hymns. */
fun isGuestHymnChange(oldTitle: String, oldText: String, plan: HymnPlan): Boolean =
    plan.title == oldTitle && (plan.text == oldText ||
        (plan.text.isNotBlank() && (oldText.isEmpty() || plan.text.startsWith("$oldText\n"))))

class FirebaseHymnPlans(private val db: FirebaseFirestore, private val access: HostAccess,
    private val auth: FirebaseAuth) : HymnPlansRepository {
    override fun observe() = callbackFlow {
        var state = HymnPlansState()
        val listener = db.collection("hymnLists").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(state.copy(message = "Hymn list sync unavailable · connect to refresh"))
            } else try {
                val rows = snapshot.documents.filter { it.id in listOf("1", "2", "3") }.associate { doc ->
                    val choices = (doc.get("choices") as? Map<*, *>)?.mapNotNull { (key, value) ->
                        val position = (key as? String)?.toIntOrNull()
                        if (position != null && position in 1..200 && value is String && value.length <= 100) position to value else null
                    }?.toMap().orEmpty()
                    doc.id to HymnPlan(doc.id, doc.getString("title") ?: "Hymn list ${doc.id}",
                        doc.getString("text").orEmpty(), choices, doc.getLong("revision") ?: 0)
                }
                val loaded = !snapshot.metadata.isFromCache || rows.isNotEmpty()
                state = HymnPlansState((1..3).map { rows[it.toString()] ?: HymnPlan(it.toString()) }, loaded,
                    if (!loaded) "Connect once to load the shared hymn lists"
                    else if (snapshot.metadata.isFromCache) "Saved offline · connect to sync changes" else "Synced across all devices")
                trySend(state)
            } catch (_: Exception) { trySend(state.copy(message = "Hymn lists couldn't be refreshed")) }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun save(plan: HymnPlan, expectedRevision: Long) {
        require(plan.slot in listOf("1", "2", "3"))
        // Guests get an app-only identity silently, without needing a host account.
        val uid = (auth.currentUser ?: auth.signInAnonymously().await().user)?.uid
            ?: throw SecurityException("Couldn't connect. Please try again.")
        val updated = plan.copy(title = plan.title.trim(), text = plan.text.trim())
        val ref = db.collection("hymnLists").document(plan.slot)
        db.runTransaction { transaction ->
            val host = transaction.get(db.collection("hosts").document(uid))
            val old = transaction.get(ref)
            val isHost = host.getBoolean("active") == true && access.session.value.canEdit && access.session.value.userId == uid
            if (!isHost && !isGuestHymnChange(old.getString("title") ?: "Hymn list ${plan.slot}", old.getString("text").orEmpty(), updated))
                throw SecurityException("Only hosts can remove hymns, rename or clear a list. You can add hymns and choose registrations.")
            require((old.getLong("revision") ?: 0) == expectedRevision) { "Someone changed this list. Close and reopen it to use the latest version." }
            transaction.set(ref, mapOf("title" to updated.title, "text" to updated.text,
                "choices" to plan.choices.mapKeys { it.key.toString() }, "revision" to expectedRevision + 1,
                "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
    }
}
