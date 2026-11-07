package com.phantominnovation.choir

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class LibraryState(val catalog: Catalog? = null, val error: Boolean = false, val syncMessage: String? = null)
data class WriteResult(val error: String? = null) { val saved get() = error == null }

class ChoirViewModel(
    application: Application,
    private val access: HostAccess,
    private val store: RegistrationLibrary,
    private val chords: OfflineChordsRepository = ReadOnlyOfflineChords(bundledChordSongs(application)),
    private val updates: AppUpdatesRepository = NoAppUpdates,
    private val hymnPlans: HymnPlansRepository = ReadOnlyHymnPlans,
) : AndroidViewModel(application) {
    private constructor(application: Application, services: LibraryServices) : this(application, services.access, services.library, services.chords, services.updates, services.hymnPlans)
    constructor(application: Application) : this(application, libraryServices(application))
    constructor(application: Application, access: HostAccess, store: RegistrationStore) : this(application, access, LocalRegistrationLibrary(store))

    private val mutableState = MutableStateFlow(LibraryState())
    private val edits = Mutex()
    val state = mutableState.asStateFlow()
    val hostSession = access.session
    private val mutableChords = MutableStateFlow(ChordState())
    val chordState = mutableChords.asStateFlow()
    private val mutableUpdates = MutableStateFlow(AppUpdateState())
    val updateState = mutableUpdates.asStateFlow()
    private val mutableUpdatePrompt = MutableStateFlow<AppRelease?>(null)
    val automaticUpdatePrompt = mutableUpdatePrompt.asStateFlow()
    private var promptedVersion: Long? = null
    private val mutableHymnPlans = MutableStateFlow(HymnPlansState())
    val hymnPlanState = mutableHymnPlans.asStateFlow()
    private var updateCheck: Job? = null
    private var observer: Job? = null
    private val editingOriginals = mutableMapOf<String, Registration>()
    fun beginEdit(row: Registration) { editingOriginals[row.id] = row }

    init {
        reload()
        viewModelScope.launch {
            hymnPlans.observe().catch { emit(mutableHymnPlans.value.copy(message = "Hymn list sync unavailable")) }
                .collect { mutableHymnPlans.value = it }
        }
        viewModelScope.launch {
            chords.observe().catch { emit(mutableChords.value.copy(message = "Chord sync unavailable")) }
                .collect { mutableChords.value = it }
        }
    }

    suspend fun saveChordMode(row: Registration, mode: ChordMode, expectedRevision: Long): WriteResult = edits.withLock {
        if (!access.session.value.canEdit) return@withLock WriteResult("Host sign-in is required to change chord availability.")
        try {
            withContext(Dispatchers.IO) { chords.setMode(row, mode, expectedRevision) }
            WriteResult()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: SecurityException) { WriteResult(error.message) }
        catch (error: IllegalArgumentException) { WriteResult(error.message) }
        catch (_: Exception) { WriteResult("Could not save chord availability. Check your connection and host access, then try again.") }
    }

    fun reload() {
        observer?.cancel()
        observer = viewModelScope.launch {
            store.observe().flowOn(Dispatchers.IO)
                .catch { emit(LibraryState(error = true)) }
                .collect { mutableState.value = it }
        }
    }

    fun signOut() = access.signOut()
    suspend fun saveHymnPlan(plan: HymnPlan, expectedRevision: Long): WriteResult = edits.withLock {
        try {
            val sections = mutableState.value.catalog?.sections ?: error("Library not loaded")
            validateHymnPlan(plan, sections)
            hymnPlans.save(plan, expectedRevision)
            WriteResult()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: IllegalArgumentException) { WriteResult(error.message) }
        catch (error: SecurityException) { WriteResult(error.message) }
        catch (_: Exception) { WriteResult("Couldn't save the hymn list. Connect to the internet and try again.") }
    }
    fun dismissUpdatePrompt() { mutableUpdatePrompt.value = null }
    fun checkForUpdate(automatic: Boolean = false) {
        if (updateCheck?.isActive == true) return
        mutableUpdates.value = AppUpdateState(checking = true)
        updateCheck = viewModelScope.launch {
            try {
                val release = withTimeout(15_000) { updates.latest() }
                mutableUpdates.value = AppUpdateState(checked = true, release = release)
                if (automatic && release != null && release.isNewerThan(BuildConfig.VERSION_CODE)
                    && release.versionCode != promptedVersion) {
                    promptedVersion = release.versionCode
                    mutableUpdatePrompt.value = release
                }
            } catch (_: TimeoutCancellationException) {
                mutableUpdates.value = AppUpdateState(error = "The update check timed out. Check your connection and try again.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUpdates.value = AppUpdateState(error = "Couldn't check for updates. Check your connection and try again.")
            }
        }
    }
    suspend fun signIn(email: String, password: String) = access.signIn(email, password)
    suspend fun resetPassword(email: String) = access.resetPassword(email)
    fun refreshHost() { viewModelScope.launch { access.refresh() } }
    override fun onCleared() { access.close(); super.onCleared() }

    private suspend fun write(action: suspend () -> Catalog): WriteResult = edits.withLock {
        if (!access.session.value.canEdit) return@withLock WriteResult("Host sign-in is required to make changes.")
        try {
            val next = withContext(Dispatchers.IO) { action() }
            mutableState.value = LibraryState(next)
            WriteResult()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: SecurityException) { WriteResult(error.message ?: "Host access is required.") }
        catch (error: IllegalArgumentException) { WriteResult(error.message ?: "Check the registration details.") }
        catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
            WriteResult(when (error.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Host permission was denied. Sign in again or refresh host access."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "Connect to the internet to save changes."
                else -> error.cause?.message ?: "Changes couldn't be saved to Firebase. Please try again."
            })
        }
        catch (_: IOException) { WriteResult("Changes couldn't be saved. Please try again.") }
        catch (_: Exception) { WriteResult("Changes couldn't be saved. Your current library has been kept.") }
    }

    suspend fun saveRegistration(id: String?, input: RegistrationInput): WriteResult =
        write { if (id == null) store.add(input) else store.update(id, input, editingOriginals[id]) }

    suspend fun addSection(title: String): WriteResult = write { store.addSection(title) }
    suspend fun changeSection(section: ChoirSection, title: String, moveTo: String?): WriteResult =
        write { store.changeSection(section, title, moveTo) }

    suspend fun deleteRegistration(id: String): WriteResult = write { store.delete(id, editingOriginals[id]) }
    suspend fun saveLinks(id: String, links: List<DriveLink>): Boolean = write { store.saveLinks(id, links, editingOriginals[id]) }.saved
}
