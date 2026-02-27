package com.example.temiguide.robot

import android.util.Log
import android.view.View
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.temiguide.MainActivity
import com.example.temiguide.R

import com.example.temiguide.ui.ScreenManager
import com.example.temiguide.utils.ActionLogger
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.example.temiguide.ai.AiProvider
import com.example.temiguide.ai.Message
import com.example.temiguide.ai.ReActEngine
import kotlinx.coroutines.withTimeoutOrNull
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppState
import com.example.temiguide.core.AppConstants
import com.example.temiguide.core.TemiLog
import com.example.temiguide.data.AppDatabase
import com.example.temiguide.data.InteractionLog
import kotlinx.coroutines.Dispatchers

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.temiguide.voice.LiveVoiceProvider
import com.example.temiguide.voice.SttProvider
import com.example.temiguide.voice.TtsProvider

class ConversationHandler(
    private val activity: MainActivity,
    private val robotController: RobotController,
    private val screenManager: ScreenManager,
    private val dialogActionHandler: DialogActionHandler,
    private val sttProvider: SttProvider?,
    private val ttsProvider: TtsProvider?,
    private val liveVoiceProvider: LiveVoiceProvider?,
    private val aiProvider: AiProvider,
    private val stateManager: StateManager,
    private val database: AppDatabase
) {
    var detectedLanguage: String = "ja-JP"

    var reactEngine: ReActEngine? = null

    // Internal state
    private val conversationHistory = mutableListOf<Message>()
    private var currentReActJob: kotlinx.coroutines.Job? = null
    var isPaused: Boolean = false

    // Dependencies from MainActivity to reduce coupling
    var isArrivalListening = false
    var isListening = false
    var isWelcomeSpeaking = false
    var lastUserSpeech: String = ""
    var pendingAutoListen = false
    var isPersonDetected = false
    private var silenceLevel = 0
    private var asrSilenceCheckJob: kotlinx.coroutines.Job? = null
    private var asrTimeoutJob: kotlinx.coroutines.Job? = null
    
    var postSafely: ((Long, () -> Unit) -> Unit)? = null
    var returnToHome: (() -> Unit)? = null

    var personaPromptBuilder: com.example.temiguide.persona.PersonaPromptBuilder? = null
    var faceManager: FaceManager? = null
    var currentZone = "入口"

    fun stopCurrentTask() {
        currentReActJob?.cancel()
        ttsProvider?.stop()
        isPaused = true
        Log.d("TemiGuide", "ConversationHandler: paused, current task stopped")
    }

    fun resume() {
        isPaused = false
        Log.d("TemiGuide", "ConversationHandler: resumed")
    }



    fun speakAndListen(text: String) {
        // 導航中は ASR を起動しない（wakeup が goTo を abort させるため）
        val currentState = stateManager.getCurrentState()
        if (currentState is AppState.Navigating || currentState is AppState.Autonomous) {
            Log.d("TemiGuide", "speakAndListen blocked: currently in ${currentState.javaClass.simpleName}")
            return
        }
        
        if (!isArrivalListening) {
            screenManager.showScreen(ScreenManager.SCREEN_LISTENING)
            activity.runOnUiThread {
                screenManager.listeningMicPane.visibility = View.VISIBLE
                screenManager.ivListeningMicIcon.setImageResource(R.drawable.ic_speaker)
                screenManager.tvLiveTranscription.text = text
                screenManager.tvLiveTranscription.alpha = 1.0f
                screenManager.tvLiveTranscription.textSize = 32f
                screenManager.tvListeningCountdown.visibility = View.GONE
            }
        } else {
            activity.runOnUiThread {
                screenManager.tvArrivalCountdown.visibility = View.VISIBLE
                screenManager.tvArrivalListeningStatus.text = activity.getString(R.string.listening_status)
            }
        }
        isListening = true
        startAsrTimeout()
        
        if (liveVoiceProvider?.isConnected() == true) {
            liveVoiceProvider.sendText(text)
        } else if (sttProvider is com.example.temiguide.voice.temi.TemiSttProvider) {
            // 先に prompt を TTS で発話し、完了後に wakeup() で ASR を起動
            activity.lifecycleScope.launch {
                if (text.isNotBlank()) {
                    ttsProvider?.speak(text)
                }
                sttProvider.startListening(
                    language = "ja-JP",
                    prompt = "",
                    onResult = { res -> onAsrResult(res.text, res.language) },
                    onError = { error -> if (error == "empty_result") onAsrResult("") }
                )
            }
        } else {
            activity.lifecycleScope.launch {
                val success = ttsProvider?.speak(text) ?: true
                if (success) {
                    sttProvider?.startListening(
                        language = "ja-JP",
                        prompt = "",
                        onResult = { res -> onAsrResult(res.text, res.language) },
                        onError = { error -> if (error == "empty_result") onAsrResult("") }
                    )
                }
            }
        }
    }

    fun speakOnly(text: String, onDone: (() -> Unit)? = null) {
        activity.lifecycleScope.launch {
            val success = ttsProvider?.speak(text) ?: true
            if (success) {
                onDone?.invoke()
            }
        }
    }

    fun fallbackSpeak(text: String) {
        speakOnly(text)
    }

    private fun speakErrorAndReturnToIdle() {
        speakOnly(activity.getString(R.string.msg_retry)) {
            postSafely?.invoke(200) {
                screenManager.showScreen(ScreenManager.SCREEN_IDLE)
            }
        }
    }

    private fun speakErrorAndRetry() {
        speakOnly(activity.getString(R.string.msg_react_retry)) {
            postSafely?.invoke(200) {
                speakAndListen("")
            }
        }
    }

    private fun startAsrTimeout() {
        asrSilenceCheckJob?.cancel()
        asrSilenceCheckJob = activity.lifecycleScope.launch {
            var waited = 0
            while (waited < 15) {
                kotlinx.coroutines.delay(1000)
                if (!isListening) return@launch
                if (!isPersonDetected) {
                    waited++
                }
            }
            if (isListening) {
                if (silenceLevel == 0) {
                    val state = stateManager.getCurrentState()
                    if (state is AppState.Navigating || state is AppState.Autonomous) {
                        Log.d("TemiGuide", "ASR timeout speakAndListen blocked: navigating")
                        return@launch
                    }
                    com.example.temiguide.utils.DevLog.add("ASR", "無音タイムアウト1（15秒）")
                    silenceLevel = 1
                    speakAndListen(activity.getString(R.string.msg_silence_prompt))
                } else {
                    com.example.temiguide.utils.DevLog.add("ASR", "無音タイムアウト2（さらに15秒）")
                    silenceLevel = 0
                    isListening = false
                    screenManager.showScreen(ScreenManager.SCREEN_IDLE)
                    speakOnly(activity.getString(R.string.msg_silence_goodbye))
                    stopAllSpeech()
                }
            }
        }
    }

    fun onAsrResult(text: String, language: String = "ja-JP") {
        val asrResult = text
        detectedLanguage = language
        
        if (asrResult.isBlank()) {
            activity.runOnUiThread {
                if (isListening) {
                    sttProvider?.startListening(
                        language = "ja-JP", prompt = "",
                        onResult = { r -> onAsrResult(r.text, r.language) },
                        onError = { e -> if (e == "empty_result") onAsrResult("") }
                    )
                }
            }
            return
        }

        silenceLevel = 0
        asrSilenceCheckJob?.cancel()
        robotController.finishConversation()
        
        Log.d("TemiGuide", "ASR Result: $asrResult")

        activity.runOnUiThread {
            isListening = false
            screenManager.tvListeningCountdown.visibility = View.GONE
            screenManager.tvArrivalCountdown.visibility = View.GONE

            // ASR blank cases are handled silently to preserve 15s timer

            if (!isArrivalListening) {
                screenManager.tvLiveTranscription.text = asrResult
                screenManager.tvLiveTranscription.alpha = 1.0f
            }
            isArrivalListening = false
            lastUserSpeech = asrResult

            val returnKeywords = listOf("戻って", "帰って", "もういい", "ホームに戻って", "go back", "回去吧")
            if (returnKeywords.any { asrResult.contains(it, ignoreCase = true) }) {
                Log.d("TemiGuide", "Return home command detected")
                robotController.stopMovement()
                speakOnly(activity.getString(R.string.msg_returning_home)) {
                    returnToHome?.invoke()
                }
                return@runOnUiThread
            }

            screenManager.showScreen(ScreenManager.SCREEN_THINKING)
        }
        
        // Parallel AI Task
        processUserQuery(asrResult)
    }

    fun processUserQuery(query: String) {
        if (isPaused) {
            Log.d("TemiGuide", "ConversationHandler is paused (sequence playing), ignoring query")
            return
        }
        val builder = personaPromptBuilder ?: return
        val locationsList = com.example.temiguide.models.locationInfo.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val overrides = builder.buildSystemInstruction(locationsList, currentZone)
        
        conversationHistory.add(Message(role = "user", content = query))
        asrSilenceCheckJob?.cancel()

        // 打断逻辑：取消之前的 ReAct 任务并停止 TTS
        currentReActJob?.cancel()
        ttsProvider?.stop()

        currentReActJob = activity.lifecycleScope.launch {
            val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (!hasInternet) {
                Log.w("TemiGuide", "No internet connection")
                ttsProvider?.speak(AppConstants.MSG_NO_INTERNET)
                stateManager.transition(AppState.Idle)
                activity.runOnUiThread { screenManager.showScreen(ScreenManager.SCREEN_IDLE) }
                return@launch
            }

            val engine = reactEngine ?: run {
                Log.w("TemiGuide", "ReActEngine not initialized")
                speakErrorAndReturnToIdle()
                return@launch
            }

            val startTime = System.currentTimeMillis()
            var reActResult: com.example.temiguide.ai.ReActResult? = null
            
            try {
                // 思考中フェーズに入る前に「はい」と返事して安心感を与える
                ttsProvider?.speak(AppConstants.MSG_ACK, detectedLanguage)

                val languageHint = when (detectedLanguage) {
                    "zh-CN", "zh-TW" -> "\n[ユーザーは中国語を话しています。中国語（簡体字）で返答してください]"
                    "en-US" -> "\n[The user is speaking English. Please respond in English]"
                    else -> ""
                }
                val enhancedInput = query + languageHint
                val customerContext = faceManager?.getContextForAI() ?: ""

                val result = withTimeoutOrNull(AppConstants.REACT_TOTAL_TIMEOUT_MS) {
                    engine.run(enhancedInput, overrides, customerContext)
                }
                reActResult = result

                if (result == null) {
                    com.example.temiguide.utils.DevLog.add("REACT_TIMEOUT", "ReAct loop timed out (30_000ms)")
                    activity.runOnUiThread { screenManager.showScreen(ScreenManager.SCREEN_IDLE) }
                    speakErrorAndRetry()
                    return@launch
                }

                ActionLogger.logAction(activity, query, result.text, null, "ReActResponse", "Iterations: ${result.iterationCount}")

                if (!result.text.isBlank() && !result.waitingForUser) {
                    stateManager.transition(AppState.Speaking(result.text))
                    
                    if (ttsProvider is com.example.temiguide.voice.temi.TemiTtsProvider) {
                        ttsProvider.onAllSpeechComplete = {
                            val state = stateManager.getCurrentState()
                            if (state is AppState.Listening || state is AppState.Speaking) {
                                Log.d("TemiGuide", "All TTS chunks complete, calling wakeup()")
                                robotController.robot.wakeup()
                            }
                            // ★ TTS 完了直後にリスニング状態へ移行
                            Log.d("TemiGuide", "AI response spoken, transitioning to Listening")
                            stateManager.transition(AppState.Listening())
                            activity.runOnUiThread { screenManager.showScreen(ScreenManager.SCREEN_LISTENING) }
                            isListening = true
                            startAsrTimeout()
                            ttsProvider.onAllSpeechComplete = null
                        }
                    }
                    
                    ttsProvider?.speakInChunks(result.text, detectedLanguage)
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("TemiGuide", "ReAct job cancelled by new input")
            } catch (e: Exception) {
                Log.e("TemiGuide", "Error during ReAct process: ${e.message}", e)
                ActionLogger.logAction(activity, query, "", null, "error", "ReAct Error ${e.message}")
                speakErrorAndReturnToIdle()
            } finally {
                val latency = System.currentTimeMillis() - startTime
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        database.interactionLogDao().insert(
                            InteractionLog(
                                userInput = query,
                                aiResponse = reActResult?.text,
                                toolsExecuted = reActResult?.executedTools?.joinToString(","),
                                latencyMs = latency,
                                success = reActResult != null,
                                errorMessage = if (reActResult == null) "timeout" else null
                            )
                        )
                    } catch (logEx: Exception) {
                        Log.e("TemiGuide", "Failed to save interaction log", logEx)
                    }
                }
            }
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    fun stopAllSpeech() {
        ttsProvider?.stop()
        sttProvider?.stopListening()
    }
}
