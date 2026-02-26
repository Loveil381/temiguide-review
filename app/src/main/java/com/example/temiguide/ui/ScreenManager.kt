package com.example.temiguide.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.temiguide.R
import com.example.temiguide.robot.RobotController
import com.example.temiguide.core.AppConstants

/**
 * Manages the UI screen states (showScreen), idle timers, and clock updates.
 */
class ScreenManager(
    private val activity: Activity,
    private val animationManager: AnimationManager
) {
    companion object {
        const val SCREEN_IDLE = 0
        const val SCREEN_GREETING = 1
        const val SCREEN_LISTENING = 2
        const val SCREEN_THINKING = 3
        const val SCREEN_GUIDANCE = 4
        const val SCREEN_NAVIGATION = 5
        const val SCREEN_ARRIVAL = 6
        const val SCREEN_STAFF = 7
    }

    private val handler = Handler(Looper.getMainLooper())
    private var idleToHomeRunnable: Runnable? = null
    private var clockRunnable: Runnable? = null
    
    // We need a reference to robotController to go home when idle timer expires
    // It will be set after initialization to avoid circular dependency if needed.
    var robotController: RobotController? = null
    var patrolManager: com.example.temiguide.robot.PatrolManager? = null
    var isNavigating: Boolean = false

    // ======== Screen containers ========
    val screenIdle by lazy { activity.findViewById<View>(R.id.screenIdle) }
    val screenGreeting by lazy { activity.findViewById<View>(R.id.screenGreeting) }
    val screenListening by lazy { activity.findViewById<View>(R.id.screenListening) }
    val screenThinking by lazy { activity.findViewById<View>(R.id.screenThinking) }
    val screenGuidance by lazy { activity.findViewById<View>(R.id.screenGuidance) }
    val screenNavigation by lazy { activity.findViewById<View>(R.id.screenNavigation) }
    val screenArrival by lazy { activity.findViewById<View>(R.id.screenArrival) }
    val screenStaffAssist by lazy { activity.findViewById<View>(R.id.screenStaffAssist) }

    val tvIdleClock by lazy { activity.findViewById<TextView>(R.id.tvIdleClock) }
    val tvIdleAmPm by lazy { activity.findViewById<TextView>(R.id.tvIdleAmPm) }

    // ======== Listening screen views ========
    val tvLiveTranscription by lazy { activity.findViewById<TextView>(R.id.tvLiveTranscription) }
    val listeningMicPane by lazy { activity.findViewById<View>(R.id.listeningMicPane) }
    val ivListeningMicIcon by lazy { activity.findViewById<android.widget.ImageView>(R.id.ivListeningMicIcon) }
    val tvListeningCountdown by lazy { activity.findViewById<TextView>(R.id.tvListeningCountdown) }

    // ======== Guidance screen views ========
    val tvGuidanceSubtitle by lazy { activity.findViewById<TextView>(R.id.tvGuidanceSubtitle) }
    val tvGuidanceDestName by lazy { activity.findViewById<TextView>(R.id.tvGuidanceDestName) }

    // ======== Navigation screen views ========
    val tvNavDestName by lazy { activity.findViewById<TextView>(R.id.tvNavDestName) }
    val tvNavEndLabel by lazy { activity.findViewById<TextView>(R.id.tvNavEndLabel) }
    val tvNavEta by lazy { activity.findViewById<TextView>(R.id.tvNavEta) }

    // ======== Arrival screen views ========
    val tvArrivalTitle by lazy { activity.findViewById<TextView>(R.id.tvArrivalTitle) }
    val tvArrivalDestName by lazy { activity.findViewById<TextView>(R.id.tvArrivalDestName) }
    val tvArrivalDesc by lazy { activity.findViewById<TextView>(R.id.tvArrivalDesc) }
    val tvArrivalCountdown by lazy { activity.findViewById<TextView>(R.id.tvArrivalCountdown) }
    val tvArrivalListeningStatus by lazy { activity.findViewById<TextView>(R.id.tvArrivalListeningStatus) }
    val ivArrivalMic by lazy { activity.findViewById<android.widget.ImageView>(R.id.ivArrivalMic) }
    val tvArrivalSection by lazy { activity.findViewById<TextView>(R.id.tvArrivalSection) }

    // ======== Staff screen views ========
    val viewStaffProgress by lazy { activity.findViewById<View>(R.id.viewStaffProgress) }

    val screens by lazy {
        listOf(
            screenIdle, screenGreeting, screenListening, screenThinking,
            screenGuidance, screenNavigation, screenArrival, screenStaffAssist
        )
    }

    fun showScreen(screenId: Int) {
        activity.runOnUiThread {
            animationManager.stopAllAnimations()

            val currentVisible = screens.filter { it.visibility == View.VISIBLE }

            val targetScreen = when (screenId) {
                SCREEN_IDLE -> screenIdle
                SCREEN_GREETING -> screenGreeting
                SCREEN_LISTENING -> screenListening
                SCREEN_THINKING -> screenThinking
                SCREEN_GUIDANCE -> screenGuidance
                SCREEN_NAVIGATION -> screenNavigation
                SCREEN_ARRIVAL -> screenArrival
                SCREEN_STAFF -> screenStaffAssist
                else -> screenIdle
            }

            if (currentVisible.contains(targetScreen) && currentVisible.size == 1) {
                // Already visible
            } else {
                currentVisible.forEach { view ->
                    if (view != targetScreen) {
                        view.animate()
                            .alpha(0f)
                            .setDuration(350)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction { view.visibility = View.GONE }
                            .start()
                    }
                }

                targetScreen.alpha = 0f
                targetScreen.translationY = 60f
                targetScreen.visibility = View.VISIBLE
                targetScreen.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .setListener(null)
                    .start()
            }

            when (screenId) {
                SCREEN_IDLE -> {
                    updateClock()
                    animationManager.startIdleAnimations()
                    startIdleTimer()
                }
                SCREEN_GREETING -> {
                    animationManager.startGreetingAnimation()
                    cancelIdleTimer()
                }
                SCREEN_LISTENING -> {
                    animationManager.startListeningAnimation()
                    cancelIdleTimer()
                }
                SCREEN_THINKING -> {
                    animationManager.startThinkingAnimation()
                    cancelIdleTimer()
                }
                SCREEN_GUIDANCE -> {
                    animationManager.startGuidanceAnimation()
                    cancelIdleTimer()
                }
                SCREEN_NAVIGATION -> {
                    animationManager.startNavigationAnimation()
                    cancelIdleTimer()
                }
                SCREEN_ARRIVAL -> {
                    animationManager.startArrivalAnimation()
                    cancelIdleTimer()
                }
                SCREEN_STAFF -> {
                    animationManager.startStaffAnimation()
                    cancelIdleTimer()
                }
            }
        }
    }

    fun showListeningScreen(text: String) {
        activity.runOnUiThread {
            tvLiveTranscription.text = text
            tvLiveTranscription.alpha = 1.0f
            tvLiveTranscription.textSize = 32f
        }
        showScreen(SCREEN_LISTENING)
    }

    fun showGuidanceScreen(subtitle: String, destName: String) {
        activity.runOnUiThread {
            tvGuidanceSubtitle.text = subtitle
            tvGuidanceDestName.text = destName.uppercase()
        }
        showScreen(SCREEN_GUIDANCE)
    }

    fun showNavigationScreen(destName: String, endLabel: String, etaSeconds: Int? = null) {
        activity.runOnUiThread {
            tvNavDestName.text = destName.uppercase()
            tvNavEndLabel.text = endLabel
            if (etaSeconds != null && etaSeconds > 0) {
                tvNavEta.text = activity.getString(R.string.nav_eta, etaSeconds.toString())
            } else {
                tvNavEta.text = "まもなく到着します"
            }
        }
        showScreen(SCREEN_NAVIGATION)
    }

    fun showArrivalScreen(title: String, destName: String, description: String, categoryText: String? = null) {
        activity.runOnUiThread {
            tvArrivalTitle.text = title
            tvArrivalDestName.text = destName
            tvArrivalDesc.text = description
            
            val cat = categoryText ?: when {
                destName.contains("メンズ") -> "メンズ"
                destName.contains("レディース") -> "レディース"
                destName.contains("キッズ") -> "キッズ"
                destName.contains("ベビー") -> "ベビー"
                else -> "ショップ"
            }
            tvArrivalSection.text = cat
            
            ivArrivalMic.visibility = View.GONE
            tvArrivalListeningStatus.text = ""
        }
        showScreen(SCREEN_ARRIVAL)
    }

    fun startIdleTimer() {
        cancelIdleTimer()
        Log.d("TemiGuide", "Starting ${AppConstants.PATROL_IDLE_TRIGGER_MS}ms idle timer for patrol")
        idleToHomeRunnable = Runnable {
            if (isNavigating) return@Runnable
            if (screenIdle.visibility != View.VISIBLE) return@Runnable
            
            if (patrolManager?.isActive() != true) {
                Log.d("TemiGuide", "Idle timeout reached, starting patrol")
                patrolManager?.startPatrol()
            }
        }
        handler.postDelayed(idleToHomeRunnable!!, AppConstants.PATROL_IDLE_TRIGGER_MS)
    }

    fun cancelIdleTimer() {
        idleToHomeRunnable?.let {
            Log.d("TemiGuide", "Cancelling idle timer")
            handler.removeCallbacks(it)
        }
        idleToHomeRunnable = null
    }

    fun resetIdleTimer() {
        if (idleToHomeRunnable != null && screenIdle.visibility == View.VISIBLE) {
            startIdleTimer()
        }
    }

    fun startClockUpdates() {
        clockRunnable = object : Runnable {
            override fun run() {
                updateClock()
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(clockRunnable!!)
    }

    private fun updateClock() {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("h:mm", Locale.getDefault())
        tvIdleClock.text = sdf.format(cal.time)
        tvIdleAmPm.text = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    }
    
    fun stopClockUpdates() {
        clockRunnable?.let { handler.removeCallbacks(it) }
        clockRunnable = null
    }
    
    // Some logic checks visibility
    fun isIdleScreenVisible(): Boolean {
        return screenIdle.visibility == View.VISIBLE
    }
}
