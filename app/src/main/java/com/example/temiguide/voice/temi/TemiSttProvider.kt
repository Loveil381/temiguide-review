package com.example.temiguide.voice.temi

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
        robotController.askQuestion(prompt)
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
        if (!_isListening) return
        _isListening = false
        
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
}
