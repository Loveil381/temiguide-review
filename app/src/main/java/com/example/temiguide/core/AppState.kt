package com.example.temiguide.core

/**
 * アプリケーション全体の状態を表現する sealed class。
 * 各 Handler は StateManager.state を collect し、自分に関係する状態変化だけ処理する。
 */
sealed class AppState {

    /** 待機中（人物検知待ち） */
    object Idle : AppState()

    /** 挨拶表示中 */
    object Greeting : AppState()

    /**
     * 音声認識リスニング中
     * @param prompt ASR 開始時にロボットが読み上げるテキスト
     * @param isQueueAsk ActionQueue の ask アクションによるリスニングか
     */
    data class Listening(
        val prompt: String = "",
        val isQueueAsk: Boolean = false
    ) : AppState()

    /** AI推論中 */
    object Thinking : AppState()

    /**
     * ロボット発話中
     * @param text 発話テキスト
     * @param isWelcome 初回起動時のウェルカムメッセージか
     * @param nextState TTS 完了後に自動遷移する状態（null の場合は遷移しない）
     */
    data class Speaking(
        val text: String,
        val isWelcome: Boolean = false,
        val nextState: AppState? = null
    ) : AppState()

    /**
     * ナビゲーション移動中
     * @param destination ロケーション名（temi の goTo に渡す値）
     * @param displayName UI 表示用のロケーション名
     * @param announcement 到着時に読み上げるテキスト（キュー実行時に使用）
     */
    data class Navigating(
        val destination: String,
        val displayName: String,
        val announcement: String? = null
    ) : AppState()

    /**
     * 目的地到着
     * @param location ロケーション名
     * @param displayName UI 表示用のロケーション名
     * @param announcement 到着時読み上げテキスト
     * @param listenAfter 到着後に音声認識を開始するか
     */
    data class Arrival(
        val location: String,
        val displayName: String,
        val announcement: String? = null,
        val listenAfter: Boolean = true
    ) : AppState()

    /**
     * スタッフ呼び出し中
     * @param reason 呼び出し理由
     */
    data class StaffCall(
        val reason: String
    ) : AppState()

    /**
     * 自律行動中（将来拡張用）
     * @param task 実行中タスクの説明
     */
    data class Autonomous(
        val task: String
    ) : AppState()

    /**
     * エラー状態
     * @param message エラーメッセージ
     * @param recoverable true なら自動復帰可能
     */
    data class Error(
        val message: String,
        val recoverable: Boolean = true
    ) : AppState()

    // ==================== Utilities ====================

    /**
     * 現在の状態から [target] への遷移が許可されているかを判定する。
     *
     * ルール:
     * - 任意の状態 → Idle は常に許可（緊急リセット用）
     * - 任意の状態 → Error は常に許可
     * - 同一状態への遷移は禁止
     * - それ以外は許可マトリクスに従う
     */
    fun canTransitionTo(target: AppState): Boolean {
        // 同一状態への遷移は禁止（data class のプロパティが異なる場合は許可）
        if (this::class == target::class && this == target) return false

        // ユーザー指定の明示的な禁止ルール: 到着後、ナビ中、発話中は自律行動への遷移を禁止
        if (target is Autonomous) {
            if (this is Arrival || this is Navigating || this is Speaking) {
                return false
            }
        }

        // 任意 → Idle は常に許可
        if (target is Idle) return true

        // 任意 → Error は常に許可
        if (target is Error) return true

        return when (this) {
            is Idle       -> target is Greeting || target is Listening || target is Speaking || target is Autonomous || target is Navigating
            is Greeting   -> target is Listening || target is Speaking || target is Navigating
            is Listening  -> target is Listening || target is Thinking || target is Speaking
            is Thinking   -> target is Listening || target is Speaking || target is Navigating || target is StaffCall
            is Speaking   -> target is Listening || target is Speaking || target is Navigating || target is StaffCall || target is Autonomous
            is Navigating -> target is Speaking || target is Navigating || target is Arrival || target is Autonomous
            is Arrival    -> target is Listening || target is Speaking || target is Navigating || target is Autonomous
            is StaffCall  -> false  // StaffCall → Idle or Error のみ（上で処理済み）
            is Autonomous -> target is Listening || target is Speaking || target is Navigating || target is Autonomous || target is Idle
            is Error      -> target is Speaking  // Error → Speaking（エラーメッセージ発話）のみ
        }
    }

    /**
     * AppState を既存の SCREEN_* 定数に変換する。
     * 段階的移行のため、既存の ScreenManager.showScreen(Int) との互換性を維持。
     */
    fun screenId(): Int = when (this) {
        is Idle       -> SCREEN_IDLE
        is Greeting   -> SCREEN_GREETING
        is Listening  -> SCREEN_LISTENING
        is Thinking   -> SCREEN_THINKING
        is Speaking   -> SCREEN_LISTENING   // Speaking 中は Listening 画面を流用
        is Navigating -> SCREEN_NAVIGATION
        is Arrival    -> SCREEN_ARRIVAL
        is StaffCall  -> SCREEN_STAFF
        is Autonomous -> SCREEN_NAVIGATION  // 将来専用画面を追加
        is Error      -> SCREEN_IDLE        // エラー時は Idle に戻す
    }

    companion object {
        // 既存の SCREEN_* 定数（ScreenManager / MainActivity との互換用）
        const val SCREEN_IDLE = 0
        const val SCREEN_GREETING = 1
        const val SCREEN_LISTENING = 2
        const val SCREEN_THINKING = 3
        const val SCREEN_GUIDANCE = 4
        const val SCREEN_NAVIGATION = 5
        const val SCREEN_ARRIVAL = 6
        const val SCREEN_STAFF = 7
    }
}
