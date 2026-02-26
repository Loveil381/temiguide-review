package com.example.temiguide.robot

import android.content.pm.ActivityInfo
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest

class RobotController(val robot: Robot) {

    fun onStart(activityInfo: ActivityInfo) {
        robot.onStart(activityInfo)
    }

    fun requestToBeKioskApp() {
        robot.requestToBeKioskApp()
    }

    fun hideTopBar() {
        robot.hideTopBar()
    }

    fun setAsrLanguages(languages: List<SttLanguage>) {
        robot.setAsrLanguages(languages)
    }

    fun askQuestion(text: String) {
        robot.askQuestion(text)
    }

    fun speak(text: String, isShowOnConversationLayer: Boolean = false, onDone: (() -> Unit)? = null) {
        val ttsRequest = TtsRequest.create(
            speech = text,
            isShowOnConversationLayer = isShowOnConversationLayer
        )
        robot.speak(ttsRequest)
        // Note: The onDone callback cannot be directly attached to the TtsRequest here 
        // because the SDK relies on the global OnTtsStatusChangedListener in MainActivity 
        // to report completion. We will let MainActivity handle the mapping for now.
    }

    fun finishConversation() {
        robot.finishConversation()
    }

    fun goToLocation(location: String) {
        robot.goTo(location)
    }

    fun stopMovement() {
        robot.stopMovement()
    }

    fun turnBy(degrees: Int, velocity: Float = 1.0f) {
        robot.turnBy(degrees, velocity)
    }

    fun tiltAngle(angle: Int, velocity: Float = 1.0f) {
        robot.tiltAngle(angle, velocity)
    }

    fun patrol(locations: List<String>) {
        robot.patrol(locations, false, 1, 3)
    }
}
