package com.phantominnovation.choir

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

internal data class LibraryServices(val access: HostAccess, val library: RegistrationLibrary, val chords: OfflineChordsRepository, val updates: AppUpdatesRepository, val hymnPlans: HymnPlansRepository)

internal fun libraryServices(application: Application): LibraryServices {
    // The test application must never contact the live project.
    val app = if (BuildConfig.BUILD_TYPE == "qa") null else FirebaseApp.initializeApp(application)
    val access: HostAccess = if (app == null) PendingFirebaseHostAccess
        else FirebaseHostAccess(FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app))
    val local = RegistrationStore(application, access)
    val library = if (app == null) LocalRegistrationLibrary(local)
        else FirebaseRegistrationLibrary(FirebaseFirestore.getInstance(app), access, local)
    val songs = bundledChordSongs(application)
    val chords = if (app == null) ReadOnlyOfflineChords(songs)
        else FirebaseOfflineChords(FirebaseFirestore.getInstance(app), access, songs)
    val updates = if (app == null) NoAppUpdates else FirebaseAppUpdates(FirebaseFirestore.getInstance(app))
    val hymnPlans = if (app == null) ReadOnlyHymnPlans else FirebaseHymnPlans(FirebaseFirestore.getInstance(app), access, FirebaseAuth.getInstance(app))
    return LibraryServices(access, library, chords, updates, hymnPlans)
}
