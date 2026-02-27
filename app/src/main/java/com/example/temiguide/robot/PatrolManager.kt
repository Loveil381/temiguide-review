package com.example.temiguide.robot

import android.util.Log
import com.robotemi.sdk.Robot
import com.example.temiguide.core.AppConstants
import com.example.temiguide.core.AppState
import com.example.temiguide.core.StateManager
import com.example.temiguide.voice.TtsProvider
import com.example.temiguide.ui.ScreenManager
import com.example.temiguide.ai.tools.impl.NavigationAwaiter
import com.example.temiguide.utils.DevLog
import kotlinx.coroutines.*

/**
 * 主動巡回（パトロール）営業モードを管理するクラス
 */
class PatrolManager(
    private val robot: Robot,
    private val ttsProvider: TtsProvider,
    private val stateManager: StateManager,
    private val screenManager: ScreenManager,
    private val navigationHandler: NavigationHandler
) {
    private var patrolJob: Job? = null
    private var isPatrolling: Boolean = false
    private var supervisorJob = SupervisorJob()
    private var coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    // 巡回地点ごとのプロモーションメッセージ
    private val promoMessages: Map<String, String> = mapOf(
        // 例: "インナー" to "本日インナー全品20%オフです！"
    )

    fun startPatrol() {
        if (isPatrolling) return
        
        // ホームベース以外の登録地点を取得
        val locations = robot.locations.filter { it != AppConstants.HOME_BASE }
        if (locations.isEmpty()) {
            Log.w("PatrolManager", "巡回可能な地点が登録されていません")
            return
        }

        isPatrolling = true
        Log.d("PatrolManager", "Patrol started with ${locations.size} locations")
        DevLog.add("Patrol", "Started with ${locations.size} locations")

        patrolJob = coroutineScope.launch {
            try {
                while (isActive && isPatrolling) {
                    val shuffledLocations = locations.shuffled()
                    for (location in shuffledLocations) {
                        if (!isActive || !isPatrolling) break

                        // 状態チェック: Idle 以外なら巡回終了
                        val currentState = stateManager.state.value
                        if (currentState !is AppState.Idle && currentState !is AppState.Autonomous) {
                            Log.d("PatrolManager", "Patrol interrupted: current state is ${currentState.javaClass.simpleName}")
                            stopPatrol()
                            return@launch
                        }

                        Log.d("PatrolManager", "Patrol: heading to $location")
                        DevLog.add("Patrol", "Heading to $location")
                        
                        stateManager.transition(AppState.Autonomous("巡回中: $location"))

                        // 移動開始
                        navigationHandler.isNavigating = true
                        Log.d("TemiGuide", "Patrol: goTo($location) called, waiting for arrival...")
                        robot.goTo(location)
                        
                        // 到着待ち
                        val arrived = NavigationAwaiter.awaitArrival(AppConstants.NAVIGATION_TIMEOUT_MS)
                        Log.d("TemiGuide", "Patrol: arrival result for $location = $arrived")

                        if (!arrived || !isPatrolling) {
                            Log.d("PatrolManager", "Patrol stopped or failed during movement to $location")
                            break
                        }

                        // 到着後プロモーション発話
                        delay(AppConstants.PATROL_SPEAK_DELAY_MS)
                        if (!isPatrolling) break
                        
                        val message = promoMessages[location] 
                            ?: "こちらは${location}コーナーです。ぜひご覧ください。"
                        
                        ttsProvider.speak(message, "ja-JP")

                        // 次の地点まで待機
                        stateManager.transition(AppState.Idle)
                        Log.d("PatrolManager", "Patrol: waiting at $location for next move")
                        delay(AppConstants.PATROL_INTERVAL_MS)

                        // 待機後、誰かが話しかけてきていないかチェック
                        if (stateManager.state.value !is AppState.Idle) {
                            Log.d("PatrolManager", "Patrol finished: user interaction detected")
                            stopPatrol()
                            return@launch
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d("PatrolManager", "Patrol coroutine cancelled")
            } catch (e: Exception) {
                Log.e("PatrolManager", "Patrol error: ${e.message}", e)
            } finally {
                isPatrolling = false
                if (stateManager.state.value is AppState.Autonomous) {
                    stateManager.transition(AppState.Idle)
                }
            }
        }
    }

    fun stopPatrol() {
        if (!isPatrolling) return
        isPatrolling = false
        patrolJob?.cancel()
        patrolJob = null
        robot.stopMovement()
        Log.d("PatrolManager", "Patrol stopped")
        DevLog.add("Patrol", "Stopped")
    }

    /** Activity.onStop() で呼び出して全コルーチンを停止する */
    fun destroy() {
        stopPatrol()
        supervisorJob.cancel()
        Log.d("PatrolManager", "PatrolManager destroyed, all coroutines cancelled")
    }

    /** Activity.onStart() で呼び出してスコープを再構築する */
    fun recreate() {
        supervisorJob = SupervisorJob()
        coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)
        Log.d("PatrolManager", "PatrolManager recreated")
    }

    fun isActive(): Boolean = isPatrolling
}
