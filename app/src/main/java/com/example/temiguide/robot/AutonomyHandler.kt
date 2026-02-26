package com.example.temiguide.robot

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.temiguide.persona.BehaviorScheduler
import com.example.temiguide.core.StateManager
import com.example.temiguide.voice.TtsProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.temiguide.utils.DevLog

data class MapZone(
    val name: String,
    val x: Float,
    val y: Float,
    val yaw: Float,
    val description: String
)

enum class AutonomyMode {
    PATROL, WANDER, APPROACH, FOLLOW_SCHEDULE, IDLE_AT
}

class AutonomyHandler(
    private val stateManager: StateManager,
    private val behaviorScheduler: BehaviorScheduler,
    private val robotController: RobotController,
    private val ttsProvider: TtsProvider?,
    private val mapZones: List<MapZone>
) {
    private val handler = Handler(Looper.getMainLooper())
    private var autonomyLoopRunnable: Runnable? = null
    var currentMode: AutonomyMode = AutonomyMode.IDLE_AT
    private var isLoopRunning = false

    var lastArrivalTime: Long = 0
    private var lastSpeakTime: Long = 0
    private var lastSpeakText: String? = null

    private val validZones get() = mapZones.filter { it.name.isNotBlank() && !it.name.contains("no name", ignoreCase = true) }

    fun notifyArrival() {
        lastArrivalTime = System.currentTimeMillis()
    }

    fun startAutonomyLoop() {
        if (!com.example.temiguide.core.AppConfig.autonomyEnabled) return
        if (isLoopRunning) return
        isLoopRunning = true
        Log.d("AutonomyHandler", "Starting autonomy loop")
        scheduleNextEvaluation()
    }

    fun stopAutonomyLoop() {
        isLoopRunning = false
        autonomyLoopRunnable?.let { handler.removeCallbacks(it) }
        autonomyLoopRunnable = null
        Log.d("AutonomyHandler", "Stopped autonomy loop")
    }

    private fun scheduleNextEvaluation() {
        if (!com.example.temiguide.core.AppConfig.autonomyEnabled) return
        if (!isLoopRunning) return
        
        autonomyLoopRunnable?.let { handler.removeCallbacks(it) }
        val delayMillis = Random.nextLong(10000, 30000) // 10-30 seconds
        
        autonomyLoopRunnable = Runnable {
            evaluateNextAction()
            scheduleNextEvaluation()
        }
        
        handler.postDelayed(autonomyLoopRunnable!!, delayMillis)
    }

    private fun evaluateNextAction() {
        if (!com.example.temiguide.core.AppConfig.autonomyEnabled) return
        
        if (stateManager.isInState(com.example.temiguide.core.AppState.Arrival::class)) {
            Log.d("AutonomyHandler", "State is Arrival, skipping autonomy loop")
            return
        }

        if (System.currentTimeMillis() - lastArrivalTime < 30_000) {
            Log.d("AutonomyHandler", "In arrival cooldown, skipping action")
            return
        }

        if (!stateManager.isAutonomous()) {
            Log.d("AutonomyHandler", "State is not AUTONOMOUS, skipping action")
            return
        }

        val behavior = behaviorScheduler.getNextIdleBehavior()
        if (behavior != null) {
            val zone = validZones.find { it.name == behavior.zone }
            if (zone != null) {
                DevLog.add("Autonomy", "Next scheduled behavior: ${behavior.action} at ${behavior.zone}")
                currentMode = AutonomyMode.FOLLOW_SCHEDULE
                executeMode(currentMode, zone, behavior.speakText, behavior.action)
                return
            }
        }

        // Random fallback if no scheduled behavior matched
        val r = Random.nextFloat()
        currentMode = when {
            r < 0.2f -> AutonomyMode.WANDER
            r < 0.4f -> AutonomyMode.PATROL
            else -> AutonomyMode.IDLE_AT
        }
        
        DevLog.add("Autonomy", "Fallback mode selected: ${currentMode.name}")
        executeMode(currentMode)
    }

    private fun executeMode(mode: AutonomyMode, targetZone: MapZone? = null, speakText: String? = null, action: String? = null) {
        Log.d("AutonomyHandler", "Executing mode: \$mode")
        when (mode) {
            AutonomyMode.FOLLOW_SCHEDULE -> {
                targetZone?.let {
                    // Start movement, we would rely on GoToLocation callbacks for actual arrival to perform 'action'
                    robotController.goToLocation(it.name)
                    if (speakText != null) {
                        trySpeak(speakText)
                    }
                }
            }
            AutonomyMode.WANDER -> {
                if (validZones.isNotEmpty()) {
                    val randomZone = validZones.random()
                    robotController.goToLocation(randomZone.name)
                }
            }
            AutonomyMode.PATROL -> {
                // If Patrol wasn't defined we can simulate it with wander or specific logic
                if (validZones.isNotEmpty()) {
                    val randomZone = validZones.random()
                    robotController.goToLocation(randomZone.name)
                }
            }
            AutonomyMode.APPROACH -> {
                // Already handled in handlePersonDetected
            }
            AutonomyMode.IDLE_AT -> {
                robotController.turnBy(Random.nextInt(-30, 30))
            }
        }
    }

    fun handlePersonDetected(onApproach: () -> Unit) {
        if (stateManager.isAutonomous()) {
            Log.d("AutonomyHandler", "Person detected! Switching to APPROACH mode")
            stopAutonomyLoop() // Pause autonomy while approaching
            currentMode = AutonomyMode.APPROACH
            robotController.stopMovement()
            
            // Turn slightly to acknowledge
            robotController.turnBy(0) // Assuming auto-follow face or just stop
            // Trigger approach logic (like greetings)
            onApproach()
        }
    }

    private fun trySpeak(text: String) {
        if (!com.example.temiguide.core.AppConfig.idleTalkEnabled) return
        if (stateManager.isInState(com.example.temiguide.core.AppState.Navigating::class)) return
        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < 60_000) return
        if (text == lastSpeakText) return
        
        lastSpeakTime = now
        lastSpeakText = text
        GlobalScope.launch {
            ttsProvider?.speak(text)
        }
    }

    fun moveToZone(zoneName: String) {
        val zone = validZones.find { it.name == zoneName }
        if (zone != null) {
            Log.d("AutonomyHandler", "Function called moveToZone: \$zoneName")
            robotController.goToLocation(zone.name)
        } else {
            // Unregistered zone, just pass it to SDK and hope it matches
            robotController.goToLocation(zoneName)
        }
    }
}
