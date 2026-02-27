package com.example.temiguide.voice.temi

import android.util.Log
import com.example.temiguide.robot.RobotController
import com.example.temiguide.voice.SttProvider
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.example.temiguide.core.AsrResult

class TemiSttProvider(private val robotController: RobotController) : SttProvider, Robot.AsrListener {
    private var onResultCallback: ((AsrResult) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var _isListening = false

    init {
        robotController.robot.addAsrListener(this)
    }

    override fun startListening(language: String, prompt: String, onResult: (AsrResult) -> Unit, onError: (String) -> Unit) {
        onResultCallback = onResult
        onErrorCallback = onError
        _isListening = true
        
        // wakeup() で ASR を起動。結果は主に MainActivity.onConversationStatusChanged(status=2) で処理される。
        // prompt がある場合は speakAndListen 側で TTS 発話済みなので、ここでは ASR 起動のみ。
        Log.d("TemiGuide", "TemiSttProvider: calling wakeup() for ASR")
        robotController.robot.wakeup()
    }

    override fun stopListening() {
        if (_isListening) {
            _isListening = false
            robotController.finishConversation()
        }
    }

    override fun isListening(): Boolean = _isListening

    override fun supportedLanguages(): List<String> = listOf("ja-JP", "en-US", "zh-CN")

    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        // wakeup() の ASR 結果主要由 MainActivity.onConversationStatusChanged(status=2) 处理。
        // 这里的 AsrListener 回调会在同一结果上重复触发，因此只在 _isListening 为 true 时处理，
        // 并且 onConversationStatusChanged 已经在处理前将 _isListening 设为 false。
        if (!_isListening) {
            Log.d("TemiGuide", "TemiSttProvider.onAsrResult: skipped (already handled by onConversationStatusChanged)")
            return
        }
        _isListening = false
        
        Log.d("TemiGuide", "TemiSttProvider.onAsrResult: '$asrResult', lang=$sttLanguage")

        if (asrResult.isBlank()) {
            onErrorCallback?.invoke("empty_result")
        } else {
            val langCode = when (sttLanguage) {
                SttLanguage.ZH_CN -> "zh-CN"
                SttLanguage.ZH_TW -> "zh-TW"
                SttLanguage.EN_US -> "en-US"
                SttLanguage.JA_JP -> "ja-JP"
                else -> "ja-JP"
            }
            onResultCallback?.invoke(AsrResult(text = asrResult, language = langCode))
        }
    }

    /** MainActivity.onConversationStatusChanged から呼び出し、重複処理を防ぐ */
    fun markAsHandled() {
        _isListening = false
    }
}
