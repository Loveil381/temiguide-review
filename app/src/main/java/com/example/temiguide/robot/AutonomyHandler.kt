package com.example.temiguide.robot

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.temiguide.persona.BehaviorScheduler
import com.example.temiguide.core.StateManager
import com.example.temiguide.voice.TtsProvider
import com.example.temiguide.utils.DevLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

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
    private val mapZones: List<MapZone>,
    private val navigationHandler: NavigationHandler? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var autonomyLoopRunnable: Runnable? = null
    var currentMode: AutonomyMode = AutonomyMode.IDLE_AT
    private var isLoopRunning = false

    var lastArrivalTime: Long = 0
    private var lastSpeakTime: Long = 0
    private var lastSpeakText: String? = null

    private var supervisorJob = SupervisorJob()
    private var coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    private val validZones get() = mapZones.filter {
        it.name.isNotBlank() && !it.name.contains("no name", ignoreCase = true)
    }

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
        val delayMillis = Random.nextLong(10000, 30000)
        
        autonomyLoopRunnable = Runnable {
            evaluateNextAction()
            scheduleNextEvaluation()
        }
        handler.postDelayed(autonomyLoopRunnable!!, delayMillis)
    }

    private fun evaluateNextAction() {
        if (!com.example.temiguide.core.AppConfig.autonomyEnabled) return
        if (stateManager.isInState(com.example.temiguide.core.AppState.Arrival::class)) return
        if (System.currentTimeMillis() - lastArrivalTime < 30_000) return
        if (!stateManager.isAutonomous()) return

        val behavior = behaviorScheduler.getNextIdleBehavior()
        if (behavior != null) {
            val zone = validZones.find { it.name == behavior.zone }
            if (zone != null) {
                DevLog.add("Autonomy", "Scheduled: ${behavior.action} at ${behavior.zone}")
                currentMode = AutonomyMode.FOLLOW_SCHEDULE
                executeMode(currentMode, zone, behavior.speakText, behavior.action)
                return
            }
        }

        val r = Random.nextFloat()
        currentMode = when {
            r < 0.2f -> AutonomyMode.WANDER
            r < 0.4f -> AutonomyMode.PATROL
            else -> AutonomyMode.IDLE_AT
        }
        DevLog.add("Autonomy", "Fallback mode: ${currentMode.name}")
        executeMode(currentMode)
    }

    /** goToLocation のラッパー：isNavigating を正しく設定 */
    private fun navigateTo(locationName: String) {
        navigationHandler?.let {
            it.isNavigating = true
            Log.d("AutonomyHandler", "isNavigating set to TRUE before goTo($locationName)")
        }
        robotController.goToLocation(locationName)
    }

    private fun executeMode(
        mode: AutonomyMode,
        targetZone: MapZone? = null,
        speakText: String? = null,
        action: String? = null
    ) {
        Log.d("AutonomyHandler", "Executing mode: $mode")
        when (mode) {
            AutonomyMode.FOLLOW_SCHEDULE -> {
                targetZone?.let {
                    navigateTo(it.name)
                    speakText?.let { t -> trySpeak(t) }
                }
            }
            AutonomyMode.WANDER -> {
                if (validZones.isNotEmpty()) navigateTo(validZones.random().name)
            }
            AutonomyMode.PATROL -> {
                if (validZones.isNotEmpty()) navigateTo(validZones.random().name)
            }
            AutonomyMode.APPROACH -> { /* handled in handlePersonDetected */ }
            AutonomyMode.IDLE_AT -> {
                robotController.turnBy(Random.nextInt(-30, 30))
            }
        }
    }

    fun handlePersonDetected(onApproach: () -> Unit) {
        if (stateManager.isAutonomous()) {
            Log.d("AutonomyHandler", "Person detected → APPROACH")
            stopAutonomyLoop()
            currentMode = AutonomyMode.APPROACH
            robotController.stopMovement()
            robotController.turnBy(0)
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
        coroutineScope.launch {
            ttsProvider?.speak(text)
        }
    }

    fun moveToZone(zoneName: String) {
        val zone = validZones.find { it.name == zoneName }
        if (zone != null) {
            Log.d("AutonomyHandler", "moveToZone: $zoneName")
            navigateTo(zone.name)
        } else {
            navigateTo(zoneName)
        }
    }

    fun destroy() {
        stopAutonomyLoop()
        supervisorJob.cancel()
        Log.d("AutonomyHandler", "Destroyed")
    }

    fun recreate() {
        supervisorJob = SupervisorJob()
        coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)
        Log.d("AutonomyHandler", "Recreated coroutine scope")
    }
}
