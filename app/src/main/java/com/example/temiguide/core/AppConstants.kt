package com.example.temiguide.core

object AppConstants {
    // Timeouts
    const val REACT_TOTAL_TIMEOUT_MS = 25_000L
    const val REACT_SINGLE_CALL_TIMEOUT_MS = 12_000L
    const val IDLE_TIMER_MS = 30_000L
    const val WATCHDOG_TIMEOUT_MS = 30_000L
    const val NAVIGATION_TIMEOUT_MS = 60_000L

    // ReAct
    const val REACT_MAX_ITERATIONS = 5

    // Locations
    const val HOME_BASE = "home base"

    // TTS Messages
    const val MSG_ACK = "はい"
    const val MSG_ERROR = "申し訳ございません、エラーが発生しました。もう一度お試しください。"
    const val MSG_TIMEOUT = "少々お待ちください、処理に時間がかかっております。"
    const val MSG_NO_INTERNET = "申し訳ございません、ただいまインターネットに接続できません。店員をお呼びしますので少々お待ちください。"
    const val MSG_THINKING_TIMEOUT = "処理がタイムアウトしました。"

    // Log Tag
    const val LOG_TAG = "TemiGuide"

    // Patrol
    const val PATROL_INTERVAL_MS = 120_000L  // 巡回地点間の待機時間（2分）
    const val PATROL_IDLE_TRIGGER_MS = 60_000L  // Idle 状態がこの時間続いたら巡回開始（1分）
    const val PATROL_SPEAK_DELAY_MS = 3_000L  // 到着後、発話までの待機
}
