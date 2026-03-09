package com.example.temiguide.ai.tools.impl

import com.example.temiguide.core.AppConstants
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object NavigationAwaiter {
    private const val ANY_LOCATION = "__any__"
    private val pendingContinuations = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()

    suspend fun awaitArrival(location: String, timeoutMs: Long = AppConstants.NAVIGATION_TIMEOUT_MS): Boolean {
        pendingContinuations.remove(location)?.cancel()

        var continuation: CancellableContinuation<Boolean>? = null
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                continuation = cont
                pendingContinuations.put(location, cont)?.cancel()
                cont.invokeOnCancellation {
                    pendingContinuations.remove(location, cont)
                }
            }
        }

        continuation?.let { pendingContinuations.remove(location, it) }
        return result ?: false
    }

    suspend fun awaitArrival(timeoutMs: Long = AppConstants.NAVIGATION_TIMEOUT_MS): Boolean =
        awaitArrival(ANY_LOCATION, timeoutMs)

    fun onStatusChanged(location: String, status: String) {
        val result = when (status) {
            "complete" -> true
            "abort" -> false
            else -> return
        }

        resumePending(location, result)
        if (location != ANY_LOCATION) {
            resumePending(ANY_LOCATION, result)
        }
    }

    fun cancelAll() {
        pendingContinuations.entries.toList().forEach { (location, cont) ->
            if (pendingContinuations.remove(location, cont) && cont.isActive) {
                cont.resume(false)
            }
        }
    }

    private fun resumePending(location: String, result: Boolean) {
        val cont = pendingContinuations.remove(location) ?: return
        if (cont.isActive) {
            cont.resume(result)
        }
    }
}
