package com.example.temiguide.voice.temi

import com.example.temiguide.robot.RobotController
import com.example.temiguide.voice.TtsProvider
import com.example.temiguide.voice.VoiceInfo
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class TemiTtsProvider(private val robotController: RobotController) : TtsProvider, Robot.TtsListener {
    private var _isSpeaking = false
    private var isChunkMode = false
    private var pendingContinuations = mutableMapOf<String, CancellableContinuation<Boolean>>()
    var onAllSpeechComplete: (() -> Unit)? = null

    init {
        robotController.robot.addTtsListener(this)
    }

    override suspend fun speak(text: String, language: String): Boolean {
        _isSpeaking = true
        val result = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
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

        if (result == null) {
            stop()
            return false
        }

        return result
    }

    override suspend fun speakInChunks(text: String, language: String) {
        isChunkMode = true
        try {
            val chunks = splitIntoChunks(text)
            for (chunk in chunks) {
                if (chunk.isBlank()) continue
                val success = speak(chunk.trim(), language)
                if (!success) break
            }
        } finally {
            isChunkMode = false
            _isSpeaking = false
            onAllSpeechComplete?.invoke()
        }
    }

    private fun splitIntoChunks(text: String): List<String> {
        val parts = text.split(Regex("(?<=[\u3002\uFF01\uFF1F])"))

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
                if (pendingContinuations.isEmpty() && !isChunkMode) {
                    _isSpeaking = false
                    onAllSpeechComplete?.invoke()
                }
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
                if (cont != null) {
                    pendingContinuations[id] = cont
                }
            }
        }
    }
}
