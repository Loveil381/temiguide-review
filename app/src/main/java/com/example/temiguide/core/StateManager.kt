package com.example.temiguide.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * アプリケーション全体の状態を一元管理する。
 *
 * 各 Handler は [state] を collect して自分に関係する状態変化だけ処理する。
 * 状態遷移は [transition] を通じて行い、許可マトリクスに違反する遷移はログに記録して無視する。
 */
class StateManager {

    private val _state = MutableStateFlow<AppState>(AppState.Idle)

    /** 現在の状態を公開する StateFlow */
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    fun getCurrentState(): AppState = _state.value

    /** 直前の状態（デバッグ / DevMenu 表示用） */
    var previousState: AppState? = null
        private set

    /**
     * 状態遷移を試みる。
     *
     * [AppState.canTransitionTo] による許可チェックを行い、
     * 不正な遷移の場合はログを出力して false を返す（クラッシュさせない）。
     *
     * @param newState 遷移先の状態
     * @return 遷移が成功した場合は true
     */
    fun transition(newState: AppState): Boolean {
        val current = _state.value

        if (!current.canTransitionTo(newState)) {
            Log.w(
                TAG,
                "Transition DENIED: ${current.stateName()} -> ${newState.stateName()}"
            )
            return false
        }

        Log.d(TAG, "Transition: ${current.stateName()} -> ${newState.stateName()}")
        com.example.temiguide.utils.DevLog.add("State", "Transition: ${current.stateName()} -> ${newState.stateName()}")
        previousState = current
        _state.value = newState
        return true
    }

    /**
     * 許可マトリクスを無視して強制遷移する。
     * 緊急リセットやエラー復帰など、通常フローでは使わないこと。
     */
    fun forceTransition(newState: AppState) {
        val current = _state.value
        Log.w(TAG, "FORCE Transition: ${current.stateName()} -> ${newState.stateName()}")
        com.example.temiguide.utils.DevLog.add("State", "FORCE: ${current.stateName()} -> ${newState.stateName()}")
        previousState = current
        _state.value = newState
    }

    /**
     * Idle に強制遷移し、履歴をクリアする。
     */
    fun reset() {
        Log.d(TAG, "Reset to Idle")
        previousState = _state.value
        _state.value = AppState.Idle
    }

    /**
     * 現在の状態が指定した型のいずれかであるかを判定する。
     */
    fun isInState(vararg stateClasses: kotlin.reflect.KClass<out AppState>): Boolean {
        return stateClasses.any { it.isInstance(_state.value) }
    }

    /**
     * 現在の状態を指定した型に安全キャストする。
     * 型が一致しない場合は null を返す。
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : AppState> currentAs(): T? {
        return state.value as? T
    }

    // ==================== Backward Compatibility Helpers ====================
    
    /** 旧 RobotState.AUTONOMOUS 相当 */
    fun isAutonomous(): Boolean {
        val s = _state.value
        return s is AppState.Idle || s is AppState.Autonomous
    }

    /** 旧 RobotState.GUIDING 相当 */
    fun isGuiding(): Boolean {
        val s = _state.value
        return s is AppState.Navigating || s is AppState.Arrival
    }

    /** 旧 RobotState.CONVERSING 相当 */
    fun isConversing(): Boolean {
        val s = _state.value
        return s is AppState.Listening || s is AppState.Speaking || s is AppState.Thinking || s is AppState.Greeting
    }

    companion object {
        private const val TAG = "StateManager"
    }
}

/**
 * AppState の表示名を返すユーティリティ。
 * デバッグログや DevMenu で使用する。
 */
fun AppState.stateName(): String = when (this) {
    is AppState.Idle       -> "Idle"
    is AppState.Greeting   -> "Greeting"
    is AppState.Listening  -> "Listening(prompt='${prompt.take(20)}', queueAsk=$isQueueAsk)"
    is AppState.Thinking   -> "Thinking"
    is AppState.Speaking   -> "Speaking(text='${text.take(20)}', welcome=$isWelcome)"
    is AppState.Navigating -> "Navigating(dest='$destination')"
    is AppState.Arrival    -> "Arrival(loc='$location')"
    is AppState.StaffCall  -> "StaffCall(reason='${reason.take(20)}')"
    is AppState.Autonomous -> "Autonomous(task='${task.take(20)}')"
    is AppState.Error      -> "Error(msg='${message.take(30)}')"
}
