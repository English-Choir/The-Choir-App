package com.phantominnovation.choir

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostSession(
    val userId: String? = null, val isHost: Boolean = false, val configured: Boolean = false,
    val email: String? = null, val checking: Boolean = false, val message: String? = null,
) {
    val canEdit: Boolean get() = configured && isHost && !userId.isNullOrBlank()
}

/** Firebase will supply verified identity/role state here; never infer a role from UI preferences. */
interface HostAccess {
    val session: StateFlow<HostSession>
    fun signOut()
    suspend fun signIn(email: String, password: String): String? = "Firebase sign-in isn't configured yet."
    suspend fun resetPassword(email: String): String? = "Firebase sign-in isn't configured yet."
    suspend fun refresh() = Unit
    fun close() = Unit
}

/** Fail closed when the Firebase configuration is absent. */
object PendingFirebaseHostAccess : HostAccess {
    override val session: StateFlow<HostSession> = MutableStateFlow(HostSession()).asStateFlow()
    override fun signOut() = Unit
}
