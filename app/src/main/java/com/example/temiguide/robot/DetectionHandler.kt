package com.example.temiguide.robot

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.example.temiguide.MainActivity
import com.example.temiguide.R
import com.example.temiguide.ui.ScreenManager
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppState


class DetectionHandler(
    private val activity: MainActivity,
    private val robotController: RobotController,
    private val screenManager: ScreenManager,
    private val conversationHandler: ConversationHandler,
    private val dialogActionHandler: DialogActionHandler,
    private val navigationHandler: NavigationHandler,
    private val stateManager: StateManager,
    private val faceManager: FaceManager
) {

    var peopleDetected = false
    private val handler = Handler(Looper.getMainLooper())
    private var noPersonTimerRunnable: Runnable? = null
    
    var postSafely: ((Long, () -> Unit) -> Unit)? = null
    var onPersonDetectedAutonomy: (() -> Unit)? = null

    fun onDetectionStateChanged(state: Int) {
        Log.d("TemiGuide", "Detection State: \$state")
        val isDetected = state == 1
        peopleDetected = isDetected
        
        if (isDetected) {
            faceManager.startRecognition()
            onPersonDetectedAutonomy?.invoke()
        } else {
            faceManager.stopRecognition()
            faceManager.reset()
        }
        
        // Show greeting when person detected on idle screen
        if (isDetected && screenManager.isIdleScreenVisible() && !dialogActionHandler.isGuideMode && !navigationHandler.isNavigating) {
            screenManager.resetIdleTimer()
            screenManager.showScreen(MainActivity.SCREEN_GREETING)
            postSafely?.invoke(4000) {
                if (!conversationHandler.isWelcomeSpeaking) {
                    screenManager.cancelIdleTimer()
                    if (conversationHandler.isArrivalListening) {
                        activity.runOnUiThread {
                            screenManager.tvArrivalCountdown.visibility = View.VISIBLE
                            screenManager.tvArrivalListeningStatus.text = activity.getString(R.string.listening_status)
                        }
                    }
                    conversationHandler.speakAndListen(activity.getString(R.string.msg_prompt))
                }
            }
        }
        
        if (dialogActionHandler.isGuideMode && !isDetected) {
             if (noPersonTimerRunnable == null) {
                 noPersonTimerRunnable = Runnable {
                     if (!peopleDetected && dialogActionHandler.isGuideMode) {
                        conversationHandler.speakOnly(activity.getString(R.string.msg_no_person)) {
                            robotController.stopMovement()
                            dialogActionHandler.isGuideMode = false
                            navigationHandler.returnToHome()
                        }
                     }
                 }
                 conversationHandler.speakOnly(activity.getString(R.string.msg_person_question))
                 handler.postDelayed(noPersonTimerRunnable!!, 15000)
             }
        } else {
            noPersonTimerRunnable?.let { handler.removeCallbacks(it) }
            noPersonTimerRunnable = null
        }
    }
    
    fun clearTimers() {
        noPersonTimerRunnable?.let { handler.removeCallbacks(it) }
        noPersonTimerRunnable = null
    }
}
