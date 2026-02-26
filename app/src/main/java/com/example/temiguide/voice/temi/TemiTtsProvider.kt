package com.example.temiguide.voice.temi

import com.example.temiguide.robot.RobotController
import com.example.temiguide.voice.TtsProvider
import com.example.temiguide.voice.VoiceInfo
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TemiTtsProvider(private val robotController: RobotController) : TtsProvider, Robot.TtsListener {
    private var _isSpeaking = false
    private var pendingContinuations = mutableMapOf<String, CancellableContinuation<Boolean>>()

    init {
        robotController.robot.addTtsListener(this)
    }

    override suspend fun speak(text: String, language: String): Boolean {
        _isSpeaking = true
        return suspendCancellableCoroutine<Boolean> { cont ->
            val sdkLanguage = when (language) {
                "zh-CN" -> TtsRequest.Language.ZH_CN
                "zh-TW" -> TtsRequest.Language.ZH_TW
                "en-US" -> TtsRequest.Language.EN_US
                "ja-JP" -> TtsRequest.Language.JA_JP
                else -> TtsRequest.Language.JA_JP
            }
            val ttsRequest = TtsRequest.create(text, isShowOnConversationLayer = false, language = sdkLanguage)
            val id = ttsRequest.id.toString()
            
            pendingContinuations[id] = cont
            cont.invokeOnCancellation { 
                pendingContinuations.remove(id)
                stop() 
            }
            robotController.robot.speak(ttsRequest)
        }
    }

    override suspend fun speakInChunks(text: String, language: String) {
        // 句読点で分割（。！？、で区切る。ただし短すぎる断片は結合）
        val chunks = splitIntoChunks(text)
        for (chunk in chunks) {
            if (chunk.isBlank()) continue
            speak(chunk.trim(), language)
        }
    }

    private fun splitIntoChunks(text: String): List<String> {
        // 。！？で分割。、は分割しない（短すぎるため）
        val parts = text.split(Regex("(?<=[。！？])"))
        
        // 短すぎる断片（10文字未満）は前の断片と結合
        val result = mutableListOf<String>()
        var buffer = ""
        for (part in parts) {
            buffer += part
            if (buffer.length >= 10) {
                result.add(buffer)
                buffer = ""
            }
        }
        if (buffer.isNotBlank()) {
            if (result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + buffer
            } else {
                result.add(buffer)
            }
        }
        return result
    }

    override fun stop() {
        if (_isSpeaking) {
            _isSpeaking = false
            robotController.robot.cancelAllTtsRequests()
            robotController.finishConversation()
            
            val conts = pendingContinuations.values.toList()
            pendingContinuations.clear()
            conts.forEach { 
                if (it.isActive) {
                    it.resume(false) 
                }
            }
        }
    }

    override fun isSpeaking(): Boolean = _isSpeaking

    override fun setVoice(voiceId: String) {}

    override fun availableVoices(): List<VoiceInfo> = listOf(
        VoiceInfo("temi", "Temi Generic", "ja-JP", "female")
    )

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        val id = ttsRequest.id.toString()
        val cont = pendingContinuations.remove(id)
        
        when (ttsRequest.status) {
            TtsRequest.Status.COMPLETED -> {
                if (pendingContinuations.isEmpty()) _isSpeaking = false
                if (cont != null && cont.isActive) {
                    cont.resume(true)
                }
            }
            TtsRequest.Status.ERROR, TtsRequest.Status.CANCELED, TtsRequest.Status.NOT_ALLOWED -> {
                if (pendingContinuations.isEmpty()) _isSpeaking = false
                if (cont != null && cont.isActive) {
                    cont.resume(false)
                }
            }
            else -> {
                // Return to map if not finished
                if (cont != null) {
                    pendingContinuations[id] = cont
                }
            }
        }
    }
}
