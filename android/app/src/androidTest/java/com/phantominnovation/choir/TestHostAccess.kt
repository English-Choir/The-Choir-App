package com.phantominnovation.choir

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Test sources only. No fake host provider or unlock switch is packaged in the app APK. */
class TestHostAccess : HostAccess {
    private val value = MutableStateFlow(HostSession("instrumentation-host", isHost = true, configured = true))
    override val session = value.asStateFlow()
    override fun signOut() { value.value = HostSession(configured = true) }
}
