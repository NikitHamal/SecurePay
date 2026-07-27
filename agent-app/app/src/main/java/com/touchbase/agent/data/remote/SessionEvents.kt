package com.touchbase.agent.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide session signals.
 *
 * The network layer runs off the main thread and has no access to the navigation
 * graph, so when the server rejects our token (HTTP 401) it simply raises
 * [sessionExpired] here. The navigation host listens and bounces the agent
 * straight back to the sign-in screen instead of leaving an "Unauthorized"
 * error sitting on screen until somebody logs out by hand.
 */
object SessionEvents {

    private val _sessionExpired = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits a human readable reason each time the session is invalidated by the server. */
    val sessionExpired: SharedFlow<String> = _sessionExpired.asSharedFlow()

    fun notifySessionExpired(reason: String = DEFAULT_REASON) {
        _sessionExpired.tryEmit(reason)
    }

    const val DEFAULT_REASON = "Your session expired. Please sign in again."
}
