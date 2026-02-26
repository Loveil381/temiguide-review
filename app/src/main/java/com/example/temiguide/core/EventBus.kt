package com.example.temiguide.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ワンショットイベントを配信するバス。
 *
 * [SharedFlow] ベースのため、複数の Collector が同じイベントを受信できる。
 * replay = 0 なので、Collector が遅れて登録した場合は過去のイベントを受信しない。
 */
object EventBus {

    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** イベントを購読するための SharedFlow */
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    /**
     * サスペンド関数としてイベントを発行する。
     * Coroutine 内から呼び出すこと。
     */
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    /**
     * 非サスペンド版。Coroutine 外（コールバック等）から呼び出す場合に使用。
     * 内部で IO ディスパッチャ上の Coroutine を起動する。
     */
    fun emitNow(event: AppEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            _events.emit(event)
        }
    }
}

/**
 * アプリケーション内で発生するワンショットイベント。
 *
 * 状態（AppState）とは異なり、イベントは「発生したこと」を通知するだけで、
 * 持続的な状態を表さない。複数の Observer が同時に処理できる。
 */
sealed class AppEvent {
    /** 人物が検知された */
    object UserDetected : AppEvent()

    /** 音声認識結果が返された */
    data class AsrResult(val text: String, val language: String = "ja") : AppEvent()

    /** AI推論結果が返された */
    data class AiResponseReceived(val response: com.example.temiguide.ai.AiResponse) : AppEvent()

    /** ナビゲーションが完了した */
    data class NavigationComplete(val location: String) : AppEvent()

    /** ナビゲーションが中断された */
    data class NavigationAborted(val location: String, val reason: String) : AppEvent()

    /** TTS発話が完了した */
    object SpeakComplete : AppEvent()

    /** エラーが発生した */
    data class ErrorOccurred(val message: String, val cause: Throwable? = null) : AppEvent()

    /** 強制リセット（セッション終了ボタン等） */
    object ForceReset : AppEvent()

    /** ActionQueue の ask 質問に対するユーザー回答 */
    data class QueueAskAnswer(val accepted: Boolean) : AppEvent()
}
