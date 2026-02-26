package com.example.temiguide.ai.tools.impl

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.example.temiguide.core.AppConstants
import kotlin.coroutines.resume

object NavigationAwaiter {
    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    suspend fun awaitArrival(timeoutMs: Long = AppConstants.NAVIGATION_TIMEOUT_MS): Boolean {
        // Cancel any previous waiting
        pendingContinuation?.cancel()
        pendingContinuation = null

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                pendingContinuation = cont
                cont.invokeOnCancellation {
                    if (pendingContinuation == cont) {
                        pendingContinuation = null
                    }
                }
            }
        }
        
        pendingContinuation = null
        return result ?: false // timeout->false
    }

    fun onStatusChanged(location: String, status: String) {
        val cont = pendingContinuation ?: return
        when (status) {
            "complete" -> {
                pendingContinuation = null
                if (cont.isActive) cont.resume(true)
            }
            "abort" -> {
                pendingContinuation = null
                if (cont.isActive) cont.resume(false)
            }
            // Ignore other statuses
        }
    }
}
