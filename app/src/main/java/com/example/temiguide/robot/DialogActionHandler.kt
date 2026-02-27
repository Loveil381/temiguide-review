package com.example.temiguide.robot

import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import com.example.temiguide.MainActivity
import com.example.temiguide.R
import com.example.temiguide.ui.ScreenManager
import com.robotemi.sdk.TtsRequest
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppState

class DialogActionHandler(
    private val activity: MainActivity,
    private val robotController: RobotController,
    private val screenManager: ScreenManager,
    private val locationInfo: Map<String, String>,
    private val stateManager: StateManager
) {

    var isGuideMode = false
    var isNavigating = false
    var isQueueAskMode = false
    var isStaffCallActive = false
    var pendingQueueCompletion: (() -> Unit)? = null
    var pendingArrivalAnnouncement: String? = null

    // Helper functions passed from MainActivity
    private var postSafelyInternal: ((Long, () -> Unit) -> Unit)? = null
    private var returnToHomeInternal: (() -> Unit)? = null
    private var speakAndListenInternal: ((String) -> Unit)? = null
    private var speakOnlyInternal: ((String, (() -> Unit)?) -> Unit)? = null

    private var onSaveMemoryInternal: ((String, String) -> Unit)? = null
    private var onMoveToZoneInternal: ((String) -> Unit)? = null
    private var onNavStartInternal: (() -> Unit)? = null

    fun setCallbacks(
        postSafely: (Long, () -> Unit) -> Unit,
        returnToHome: () -> Unit,
        speakAndListen: (String) -> Unit,
        speakOnly: (String, (() -> Unit)?) -> Unit,
        onSaveMemory: ((String, String) -> Unit)? = null,
        onMoveToZone: ((String) -> Unit)? = null,
        onNavStart: (() -> Unit)? = null
    ) {
        this.postSafelyInternal = postSafely
        this.returnToHomeInternal = returnToHome
        this.speakAndListenInternal = speakAndListen
        this.speakOnlyInternal = speakOnly
        this.onSaveMemoryInternal = onSaveMemory
        this.onMoveToZoneInternal = onMoveToZone
        this.onNavStartInternal = onNavStart
    }

    private fun postSafely(delay: Long, action: () -> Unit) {
        postSafelyInternal?.invoke(delay, action)
    }

    private fun returnToHome() {
        returnToHomeInternal?.invoke()
    }

    private fun speakAndListen(text: String) {
        speakAndListenInternal?.invoke(text)
    }

    private fun speakOnly(text: String, onDone: (() -> Unit)? = null) {
        speakOnlyInternal?.invoke(text, onDone)
    }

    private fun handleCallStaff(reply: String, ttsLang: TtsRequest.Language) {
        speakOnly(reply)
        screenManager.showScreen(MainActivity.SCREEN_STAFF)
        isStaffCallActive = true
        
        // Animate progress bar
        postSafely(500) {
            if (!isStaffCallActive) return@postSafely
            val viewStaffProgress = activity.findViewById<View>(R.id.viewStaffProgress)
            val params = viewStaffProgress.layoutParams
            val parent = viewStaffProgress.parent as? View ?: return@postSafely
            val targetWidth = (parent.width * 0.66).toInt()
            ValueAnimator.ofInt(0, targetWidth).apply {
                duration = 15000
                addUpdateListener {
                    if (!isStaffCallActive) {
                        cancel()
                        return@addUpdateListener
                    }
                    params.width = it.animatedValue as Int
                    viewStaffProgress.layoutParams = params
                }
                start()
            }
        }
        
        postSafely(30000) {
            if (!isStaffCallActive) return@postSafely
            isStaffCallActive = false
            speakOnly(activity.getString(R.string.staff_not_found)) {
                returnToHome()
            }
        }
    }

    fun cancelAll() {
        pendingQueueCompletion = null
        isQueueAskMode = false
        isStaffCallActive = false
    }
}
