package com.phantominnovation.choir

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Roles are provisioned by the project owner. Clients cannot create or change host documents. */
class FirebaseHostAccess(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : HostAccess {
    private val value = MutableStateFlow(HostSession(configured = true))
    override val session = value.asStateFlow()
    private var roleListener: ListenerRegistration? = null
    private var roleGeneration = 0L
    private val authListener = FirebaseAuth.AuthStateListener { observeRole() }

    init { auth.addAuthStateListener(authListener) }

    @Synchronized private fun observeRole() {
        roleListener?.remove()
        val generation = ++roleGeneration
        val user = auth.currentUser
        if (user?.isAnonymous == true) {
            value.value = HostSession(configured = true)
            return
        }
        value.value = HostSession(userId = user?.uid, email = user?.email, configured = true, checking = user != null)
        if (user == null) return
        roleListener = db.collection("hosts").document(user.uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { document, error ->
                synchronized(this@FirebaseHostAccess) {
                if (generation != roleGeneration || auth.currentUser?.uid != user.uid) return@synchronized
                val confirmed = error == null && document != null && !document.metadata.isFromCache
                val host = confirmed && document?.getBoolean("active") == true
                value.value = HostSession(user.uid, host, configured = true, email = user.email,
                    message = when {
                        error != null -> "Host access couldn't be verified. Connect to the internet and try again."
                        !confirmed -> "Connect to the internet to verify host access."
                        !host -> "This account doesn't have host permission."
                        else -> null
                    })
                }
            }
    }

    override suspend fun signIn(email: String, password: String): String? {
        if (email.isBlank() || password.isEmpty()) return "Enter your host email and app password."
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { authError(error) }
    }

    override suspend fun resetPassword(email: String): String? {
        if (email.isBlank()) return "Enter your host email first."
        return try { auth.sendPasswordResetEmail(email.trim()).await(); null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { authError(error) }
    }

    override suspend fun refresh() {
        value.value = value.value.copy(isHost = false, checking = auth.currentUser != null, message = null)
        try {
            auth.currentUser?.reload()?.await()
            auth.currentUser?.getIdToken(true)?.await()
            observeRole()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: FirebaseAuthInvalidUserException) { signOut() }
        catch (_: Exception) {
            value.value = value.value.copy(isHost = false, checking = false, message = "Connect to the internet to verify host access.")
        }
    }

    @Synchronized override fun signOut() {
        roleGeneration++
        roleListener?.remove()
        auth.signOut()
        value.value = HostSession(configured = true)
    }

    @Synchronized override fun close() { roleGeneration++; roleListener?.remove(); auth.removeAuthStateListener(authListener) }

    private fun authError(error: Exception) = when (error) {
        is FirebaseNetworkException -> "Couldn't connect. Check your internet connection."
        is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later."
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Couldn't sign in. Check your host email and app password."
        else -> "Sign-in couldn't be completed. Please try again."
    }
}
