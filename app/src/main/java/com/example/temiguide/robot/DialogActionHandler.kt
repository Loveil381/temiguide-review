package com.example.temiguide.robot

import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import com.example.temiguide.ActionQueue
import com.example.temiguide.MainActivity
import com.example.temiguide.R
import com.example.temiguide.ui.ScreenManager
import com.robotemi.sdk.TtsRequest
import com.example.temiguide.ai.AiResponse
import com.example.temiguide.ai.TemiAction
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppState

class DialogActionHandler(
    private val activity: MainActivity,
    private val robotController: RobotController,
    private val screenManager: ScreenManager,
    private val locationInfo: Map<String, String>,
    private val stateManager: StateManager
) {

    var actionQueue: ActionQueue? = null
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

    fun executeAction(response: AiResponse, conversationHistory: MutableList<Any>, resetAutoListen: () -> Unit) {
        val actions = response.actions.toMutableList()
        if (actions.isNotEmpty()) {
            val hasSpeech = actions.any { it is TemiAction.Speak || it is TemiAction.AskQuestion || (it is TemiAction.Navigate && !it.announcement.isNullOrBlank()) }
            if (!hasSpeech && response.text.isNotBlank()) {
                actions.add(0, TemiAction.Speak(response.text))
            }
            executeTemiActions(actions, conversationHistory, resetAutoListen)
            return
        }

        // Fallback for empty actions: just speak
        speakAndListen(response.text.ifBlank { activity.getString(R.string.msg_retry) })
    }

    private fun executeTemiActions(
        actions: List<TemiAction>,
        conversationHistory: MutableList<Any>,
        resetAutoListen: () -> Unit
    ) {
        if (actions.isEmpty()) return
        
        fun executeNext(index: Int) {
            if (index >= actions.size) return
            val action = actions[index]
            val next = { executeNext(index + 1) }

            when (action) {
                is TemiAction.Speak -> {
                    speakOnly(action.text) { next() }
                }
                is TemiAction.Navigate -> {
                    // Fallback resolution matching NavigationHandler logic
                    val navKey = locationInfo.entries.firstOrNull { it.value.equals(action.destination, ignoreCase = true) }?.key
                        ?: locationInfo.entries.firstOrNull { it.value.contains(action.destination, ignoreCase = true) }?.key
                        ?: locationInfo.entries.firstOrNull { it.key.contains(action.destination, ignoreCase = true) }?.key
                        ?: robotController.robot.locations.firstOrNull { it.contains(action.destination, ignoreCase = true) }
                        ?: action.destination

                    if (locationInfo.containsKey(navKey) || navKey in locationInfo.values || robotController.robot.locations.contains(navKey)) {
                        conversationHistory.clear()
                        resetAutoListen()
                        isGuideMode = true
    
                        pendingQueueCompletion = next
                        pendingArrivalAnnouncement = action.announcement
    
                        val startMsg = activity.getString(R.string.msg_guide_start)
                        val displayName = locationInfo[navKey] ?: navKey.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        com.example.temiguide.utils.DevLog.add("NAVIGATION", "Showing guidance screen for $displayName")
                        screenManager.showGuidanceScreen(startMsg, displayName)
    
                        speakOnly(startMsg) {
                            com.example.temiguide.utils.DevLog.add("NAVIGATION", "TTS '$startMsg' completed. Switching to navigation screen")
                            screenManager.showNavigationScreen(displayName, navKey)
        
                            postSafely(500) {
                                if (isNavigating) return@postSafely
                                isNavigating = true
                                onNavStartInternal?.invoke()
                                val navState = AppState.Navigating(navKey, displayName, action.announcement)
                                val ok = stateManager.transition(navState)
                                com.example.temiguide.utils.DevLog.add("STATE", "${stateManager.state.value.javaClass.simpleName} → Navigating($navKey): ${if (ok) "OK" else "DENIED, using forceTransition"}")
                                if (!ok) stateManager.forceTransition(navState)
                                robotController.goToLocation(navKey)
                                com.example.temiguide.utils.DevLog.add("ARRIVAL", "calling goTo($navKey)")
                            }
                        }
                    } else {
                        speakOnly(activity.getString(R.string.msg_location_not_found)) { next() }
                    }
                    // Loop breaks here for Navigate implicitly, as we don't call next()
                }
                is TemiAction.Wait -> {
                    screenManager.showScreen(MainActivity.SCREEN_IDLE)
                    postSafely(action.seconds * 1000L) { next() }
                }
                is TemiAction.AskQuestion -> {
                    speakAndListen(action.text)
                }
                is TemiAction.GoHome -> {
                    returnToHome()
                }
                is TemiAction.TurnBy -> {
                    robotController.turnBy(action.degrees, action.velocity)
                    next() 
                }
                is TemiAction.TiltHead -> {
                    robotController.tiltAngle(action.angle)
                    next()
                }
                is TemiAction.Patrol -> {
                    if (action.locations.size >= 3) {
                        robotController.patrol(action.locations)
                    }
                }
                is TemiAction.EndConversation -> {
                    conversationHistory.clear()
                    resetAutoListen()
                    speakOnly(action.reply) {
                        postSafely(200) { returnToHome() }
                    }
                }
                is TemiAction.Pause -> {
                    robotController.stopMovement()
                    isNavigating = false
                    isGuideMode = false
                    speakOnly(action.message) {
                        postSafely(5000) {
                            speakAndListen(activity.getString(R.string.msg_pause_ready))
                        }
                    }
                }
                is TemiAction.CallStaff -> {
                    conversationHistory.clear()
                    resetAutoListen()
                    handleCallStaff(action.reason, TtsRequest.Language.JA_JP)
                }
                is TemiAction.MoveToZone -> {
                    onMoveToZoneInternal?.invoke(action.zone)
                }
                is TemiAction.SaveMemory -> {
                    onSaveMemoryInternal?.invoke(action.key, action.value)
                    next()
                }
                is TemiAction.TakePhoto -> {
                    // TODO: implement
                    next()
                }
                is TemiAction.NavigateToPosition -> {
                    // TODO: implement
                    next()
                }
            }
        }
        
        executeNext(0)
    }

    private fun executeQueueItem(
        item: ActionQueue.ActionItem, 
        onComplete: () -> Unit,
        conversationHistory: MutableList<Any>, 
        resetAutoListen: () -> Unit
    ) {
        when (item.type) {
            "speak" -> {
                screenManager.showListeningScreen(item.text ?: "")
                speakOnly(item.text ?: "") { onComplete() }
            }
            "guide" -> {
                val dest = item.location ?: ""
                val navKey = locationInfo.entries.firstOrNull { it.value.equals(dest, ignoreCase = true) }?.key
                    ?: locationInfo.entries.firstOrNull { it.value.contains(dest, ignoreCase = true) }?.key
                    ?: locationInfo.entries.firstOrNull { it.key.contains(dest, ignoreCase = true) }?.key
                    ?: robotController.robot.locations.firstOrNull { it.contains(dest, ignoreCase = true) }
                    ?: dest

                if (navKey.isNotBlank() && (locationInfo.containsKey(navKey) || robotController.robot.locations.contains(navKey))) {
                    // ナビ開始 → 到着後に announcement を話す → onComplete
                    pendingQueueCompletion = onComplete
                    pendingArrivalAnnouncement = item.announcement
                    isGuideMode = true
                    
                    val displayName = locationInfo[navKey] ?: navKey.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    val startMsg = activity.getString(R.string.msg_guide_start)
                    com.example.temiguide.utils.DevLog.add("NAVIGATION", "Showing guidance screen for $displayName")
                    screenManager.showGuidanceScreen(startMsg, displayName)
                    
                    speakOnly(startMsg) {
                        com.example.temiguide.utils.DevLog.add("ARRIVAL", "speaking next nav TTS: $startMsg")
                        com.example.temiguide.utils.DevLog.add("ARRIVAL", "next TTS completed, switching screen to $displayName")
                        screenManager.showNavigationScreen(displayName, navKey)
                        isNavigating = true
                        onNavStartInternal?.invoke()
                        val navState = AppState.Navigating(navKey, displayName, item.announcement)
                        val ok = stateManager.transition(navState)
                        com.example.temiguide.utils.DevLog.add("STATE", "→ Navigating($navKey): ${if (ok) "OK" else "DENIED, using forceTransition"}")
                        if (!ok) stateManager.forceTransition(navState)
                        com.example.temiguide.utils.DevLog.add("ARRIVAL", "calling goTo($navKey), state=${stateManager.state.value.javaClass.simpleName}")
                        robotController.goToLocation(navKey)
                    }
                } else {
                    speakOnly(activity.getString(R.string.msg_location_not_found)) { onComplete() }
                }
            }
            "ask" -> {
                pendingQueueCompletion = onComplete
                isQueueAskMode = true
                speakAndListen(item.question ?: "")
            }
            "wait_seconds" -> {
                postSafely((item.seconds ?: 3) * 1000L) { onComplete() }
            }
            "call_staff" -> {
                handleCallStaff(item.reason ?: activity.getString(R.string.staff_calling), TtsRequest.Language.JA_JP)
                // call_staffは終端アクション
            }
            "end_conversation" -> {
                conversationHistory.clear()
                screenManager.showScreen(MainActivity.SCREEN_IDLE)
                postSafely(500) { returnToHome() }
            }
            "pause" -> {
                robotController.stopMovement()
                isNavigating = false
                speakAndListen(item.text ?: activity.getString(R.string.msg_pause_waiting))
            }
            "save_memory" -> {
                if (item.key != null && item.value != null) {
                    onSaveMemoryInternal?.invoke(item.key, item.value)
                }
                onComplete() // Continue queue since saving is instant
            }
            "move_to_zone" -> {
                if (item.location != null) {
                    onMoveToZoneInternal?.invoke(item.location)
                }
                onComplete()
            }
        }
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
        actionQueue?.cancel()
        actionQueue = null
        pendingQueueCompletion = null
        isQueueAskMode = false
        isStaffCallActive = false
    }
}
