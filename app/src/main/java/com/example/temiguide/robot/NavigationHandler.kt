package com.example.temiguide.robot

import android.util.Log
import com.example.temiguide.MainActivity
import com.example.temiguide.R
import com.example.temiguide.models.locationInfo
import com.example.temiguide.ui.ScreenManager
import java.util.Locale
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppState

import com.example.temiguide.core.AppConstants
import com.example.temiguide.ai.tools.impl.NavigationAwaiter

class NavigationHandler(
    private val activity: MainActivity,
    private val robotController: RobotController,
    private val screenManager: ScreenManager,
    private val conversationHandler: ConversationHandler,
    private val dialogActionHandler: DialogActionHandler,
    private val stateManager: StateManager
) {

    var isNavigating = false
    var pendingArrivalLocation: String? = null
    var userEndedSession = false
    var retryCount = 0
    
    var postSafely: ((Long, () -> Unit) -> Unit)? = null
    var onIdleResumed: (() -> Unit)? = null
    var onArrivalCooldownStart: (() -> Unit)? = null

    fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        Log.d("TemiGuide", "onGoToLocationStatus: location=$location, status=$status, isNavigating=$isNavigating, isGuideMode=${dialogActionHandler.isGuideMode}, descriptionId=$descriptionId, description=$description")
        NavigationAwaiter.onStatusChanged(location, status)
        
        when (status) {
            "reposing" -> {
                return
            }
            "complete" -> {
                retryCount = 0
                if (!isNavigating && !dialogActionHandler.isGuideMode) {
                    Log.d("TemiGuide", "Ignoring goTo complete - not initiated by app: $location")
                    return
                }
                dialogActionHandler.isGuideMode = false
                Log.d("TemiGuide", "isNavigating set to FALSE at status COMPLETE (L52)")
                isNavigating = false
                
                if (location.lowercase(Locale.getDefault()) == AppConstants.HOME_BASE) {
                    screenManager.showScreen(ScreenManager.SCREEN_IDLE)
                    onIdleResumed?.invoke()
                    screenManager.cancelIdleTimer()
                } else {
                    pendingArrivalLocation = location
                    robotController.turnBy(180, 0.5f)
                }
            }
            "abort" -> {
                com.example.temiguide.utils.DevLog.add("Navigation", "STATUS_ABORT ($location)")
                if (!isNavigating && !dialogActionHandler.isGuideMode && !userEndedSession) {
                    Log.d("TemiGuide", "Ignoring goTo abort - not initiated by app: $location")
                    return
                }
                if (userEndedSession) {
                    dialogActionHandler.isGuideMode = false
                    userEndedSession = false
                    screenManager.showScreen(ScreenManager.SCREEN_IDLE)
                    onIdleResumed?.invoke()
                } else {
                    retryCount++
                    if (retryCount >= 3) {
                        dialogActionHandler.isGuideMode = false
                        Log.d("TemiGuide", "isNavigating set to FALSE at status ABORT (max retries) (L78)")
                        isNavigating = false
                        retryCount = 0
                        conversationHandler.speakOnly(activity.getString(R.string.msg_nav_give_up)) {
                            postSafely?.invoke(200) {
                                screenManager.showScreen(ScreenManager.SCREEN_IDLE)
                                onIdleResumed?.invoke()
                            }
                        }
                    } else {
                        conversationHandler.speakOnly(activity.getString(R.string.msg_nav_retry)) {
                            postSafely?.invoke(3000) {
                                robotController.goToLocation(location)
                            }
                        }
                    }
                }
            }
            "obstacle detected", "obstacle" -> {
                com.example.temiguide.utils.DevLog.add("Navigation", "STATUS_OBSTACLE_DETECTED")
            }
            "going" -> {
                Log.d("TemiGuide", "Navigation going to $location")
            }
            "calculating" -> {
                Log.d("TemiGuide", "Calculating route to $location")
            }
        }
    }
    
    fun handleArrival(location: String) {
        // Robust fallback for location name
        val locationName = locationInfo[location]
            ?: locationInfo.entries.firstOrNull { it.value.contains(location, ignoreCase = true) }?.value
            ?: locationInfo.entries.firstOrNull { it.key.contains(location, ignoreCase = true) }?.value
            ?: location.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // Prevent duplicate "コーナー"
        val title = if (locationName.endsWith("コーナー")) locationName else "${locationName}コーナー"
        val description = if (locationName.endsWith("コーナー")) "${locationName}にお連れしました。" else "${locationName}コーナーにお連れしました。"

        com.example.temiguide.utils.DevLog.add("ARRIVAL", "status=complete, location=$location")
        val currentStateName = stateManager.state.value.javaClass.simpleName
        val transOk = stateManager.transition(AppState.Arrival(location, locationName))
        com.example.temiguide.utils.DevLog.add("STATE", "$currentStateName → Arrival($location): ${if (transOk) "OK" else "DENIED, using forceTransition"}")
        if (!transOk) stateManager.forceTransition(AppState.Arrival(location, locationName))

        com.example.temiguide.utils.DevLog.add("ARRIVAL", "showing arrival screen for $locationName")
        screenManager.showArrivalScreen(title, locationName, description)
        
        if (dialogActionHandler.pendingArrivalAnnouncement != null) {
            val announcement = dialogActionHandler.pendingArrivalAnnouncement
            dialogActionHandler.pendingArrivalAnnouncement = null
            
            val arrivalText = announcement ?: activity.getString(R.string.msg_arrival)
            com.example.temiguide.utils.DevLog.add("ARRIVAL", "speaking arrival TTS: $arrivalText")
            conversationHandler.speakOnly(arrivalText) {
                val pendingCount = if (dialogActionHandler.pendingQueueCompletion != null) 1 else 0
                com.example.temiguide.utils.DevLog.add("ARRIVAL", "arrival TTS completed, pendingActions=$pendingCount")
                dialogActionHandler.pendingQueueCompletion?.invoke()
                dialogActionHandler.pendingQueueCompletion = null
                
                @Suppress("DEPRECATION")
                if (dialogActionHandler.actionQueue == null) {  // actionQueue 已削除，始終 true
                    postSafely?.invoke(10000) {
                        com.example.temiguide.utils.DevLog.add("ARRIVAL", "arrival cooldown timer finished, checking state")
                        if (stateManager.isInState(AppState.Arrival::class)) {
                            com.example.temiguide.utils.DevLog.add("ARRIVAL", "still in Arrival state, invoking onArrivalCooldownStart and returning to home")
                            onArrivalCooldownStart?.invoke()
                            stateManager.transition(AppState.Idle)
                            returnToHome()
                        } else {
                            com.example.temiguide.utils.DevLog.add("ARRIVAL", "not in Arrival state, doing nothing")
                        }
                    }
                } else {
                    com.example.temiguide.utils.DevLog.add("ARRIVAL", "actionQueue is running, not starting cooldown timer")
                }
            }
        } else {
            // pendingQueueCompletion がある場合は次の地点へ（到着後質問モードではなく）
            if (dialogActionHandler.pendingQueueCompletion != null) {
                val arrivalText = activity.getString(R.string.msg_arrival)
                com.example.temiguide.utils.DevLog.add("ARRIVAL", "speaking default arrival TTS: $arrivalText, then executing pending queue")
                conversationHandler.speakOnly(arrivalText) {
                    val pendingCount = if (dialogActionHandler.pendingQueueCompletion != null) 1 else 0
                    com.example.temiguide.utils.DevLog.add("ARRIVAL", "default arrival TTS completed, pendingActions=$pendingCount")
                    dialogActionHandler.pendingQueueCompletion?.invoke()
                    dialogActionHandler.pendingQueueCompletion = null
                }
            } else {
                // 通常の到着後フロー（質問モード）
                val arrivalSpeech = if (locationInfo.containsKey(location)) {
                    activity.getString(R.string.msg_arrival_corner_prefix) + locationInfo[location] + activity.getString(R.string.msg_arrival_corner_suffix)
                } else {
                    activity.getString(R.string.msg_arrival_more)
                }
                com.example.temiguide.utils.DevLog.add("ARRIVAL", "speaking arrival TTS (listen mode): $arrivalSpeech")
                conversationHandler.isArrivalListening = true
                conversationHandler.speakAndListen(arrivalSpeech)
                postSafely?.invoke(10000) {
                    com.example.temiguide.utils.DevLog.add("ARRIVAL", "arrival cooldown timer finished (listen mode), checking state")
                    if (stateManager.isInState(AppState.Arrival::class)) {
                        com.example.temiguide.utils.DevLog.add("ARRIVAL", "still in Arrival state, invoking onArrivalCooldownStart and returning to home")
                        onArrivalCooldownStart?.invoke()
                        stateManager.transition(AppState.Idle)
                        returnToHome()
                    }
                }
            }
        }
    }

    fun returnToHome() {
        conversationHandler.clearHistory()
        conversationHandler.pendingAutoListen = false
        dialogActionHandler.isGuideMode = false
        Log.d("TemiGuide", "isNavigating set to TRUE at returnToHome (L193)")
        isNavigating = true
        dialogActionHandler.cancelAll()
        retryCount = 0
        screenManager.cancelIdleTimer()
        robotController.finishConversation()
        val destinationName = activity.getString(R.string.msg_nav_home)
        screenManager.showNavigationScreen(destinationName, AppConstants.HOME_BASE, null)
        postSafely?.invoke(500) {
             robotController.goToLocation(AppConstants.HOME_BASE)
        }
    }

    fun onMovementStatusChanged(type: String, status: String) {
        Log.d("TemiGuide", "Movement: type=$type, status=$status")
        if (type == "turnBy") {
            when (status) {
                "complete", "abort" -> {
                    pendingArrivalLocation?.let { location ->
                        pendingArrivalLocation = null
                        Log.d("TemiGuide", "isNavigating set to FALSE after turnBy (L212)")
                        isNavigating = false
                        handleArrival(location)
                    }
                }
            }
        }
    }
}
