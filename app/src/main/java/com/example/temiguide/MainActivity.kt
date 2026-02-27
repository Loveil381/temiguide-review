package com.example.temiguide

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import com.example.temiguide.models.*
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnLocationsUpdatedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnMovementStatusChangedListener
import com.robotemi.sdk.listeners.OnConversationStatusChangedListener

import com.robotemi.sdk.sequence.OnSequencePlayStatusChangedListener
import com.robotemi.sdk.face.OnFaceRecognizedListener
import com.robotemi.sdk.face.ContactModel

import com.example.temiguide.robot.ConversationHandler
import com.example.temiguide.robot.DetectionHandler
import com.example.temiguide.robot.DialogActionHandler
import com.example.temiguide.robot.NavigationHandler
import com.example.temiguide.robot.RobotController
import com.example.temiguide.robot.FaceManager
import com.example.temiguide.ui.AnimationManager
import com.example.temiguide.ui.ScreenManager

import com.example.temiguide.persona.BehaviorScheduler
import com.example.temiguide.persona.MemoryManager
import com.example.temiguide.persona.PersonaConfig
import com.example.temiguide.persona.PersonaPromptBuilder
import com.example.temiguide.robot.AutonomyHandler
import com.example.temiguide.robot.MapZone
import com.example.temiguide.core.StateManager
import com.example.temiguide.core.AppConfig
import com.example.temiguide.core.AppState
import com.example.temiguide.core.AppConstants
import com.example.temiguide.ai.AiProviderFactory
import com.example.temiguide.ai.AiProvider
import com.example.temiguide.ai.gemini.GeminiProvider
import com.example.temiguide.ai.tools.ToolRegistry
import com.example.temiguide.data.AppDatabase
import com.example.temiguide.ai.tools.impl.*
import com.example.temiguide.core.RemoteConfigManager
import com.example.temiguide.ai.ReActEngine

import com.example.temiguide.voice.LiveVoiceProvider
import com.example.temiguide.voice.SttProvider
import com.example.temiguide.voice.TtsProvider
import com.example.temiguide.core.VoiceMode
import com.example.temiguide.voice.VoiceProviderFactory
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity(),
    OnRobotReadyListener,
    OnGoToLocationStatusChangedListener,
    Robot.ConversationViewAttachesListener,
    OnLocationsUpdatedListener,
    OnDetectionStateChangedListener,
    OnMovementStatusChangedListener,
    OnConversationStatusChangedListener,
    OnSequencePlayStatusChangedListener,
    OnFaceRecognizedListener {

    private lateinit var robot: Robot
    private lateinit var robotController: RobotController
    private lateinit var animationManager: AnimationManager
    private lateinit var screenManager: ScreenManager
    private lateinit var dialogActionHandler: DialogActionHandler
    private lateinit var conversationHandler: ConversationHandler
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var detectionHandler: DetectionHandler
    private lateinit var patrolManager: com.example.temiguide.robot.PatrolManager
    private lateinit var faceManager: FaceManager
    private lateinit var voiceProviderFactory: VoiceProviderFactory
    internal var sttProvider: SttProvider? = null
    private var ttsProvider: TtsProvider? = null
    private var liveVoiceProvider: LiveVoiceProvider? = null

    private lateinit var memoryManager: MemoryManager
    private lateinit var personaConfig: PersonaConfig
    private lateinit var personaPromptBuilder: PersonaPromptBuilder
    private lateinit var behaviorScheduler: BehaviorScheduler

    companion object {
        lateinit var stateManager: StateManager
        lateinit var autonomyHandler: AutonomyHandler
    }

    // Action Runnables to prevent memory leaks
    private val actionRunnables = mutableListOf<Runnable>()

    private fun postSafely(delay: Long, action: () -> Unit) {
        val r = object : Runnable {
            override fun run() {
                actionRunnables.remove(this)
                action()
            }
        }
        actionRunnables.add(r)
        handler.postDelayed(r, delay)
    }

    private fun clearActionRunnables() {
        actionRunnables.forEach { handler.removeCallbacks(it) }
        actionRunnables.clear()
    }

    // State
    private var isFirstWelcome = true
    private var isInitialized = false
    private var pendingLocation: String? = null
    private var isGuideMode = false
    
    // Handlers
    private val handler = Handler(Looper.getMainLooper())

    // Watchdog State
    private var watchdogRunnable: Runnable? = null
    private var lastStateChangeTime: Long = 0
    private var lastObservedState: AppState? = null

    // DevMenu Trigger
    private var tapCount = 0
    private var lastTapTime: Long = 0

    // Location Info Map moved to Models.kt


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("TemiGuide", "FATAL UNCAUGHT EXCEPTION on ${thread.name}: ${throwable.message}", throwable)
            // 尝试保存崩溃日志到本地文件
            try {
                val crashFile = File(filesDir, "crash_${System.currentTimeMillis()}.txt")
                crashFile.writeText("${java.util.Date()}\n${thread.name}\n${throwable.stackTraceToString()}")
            } catch (e: Exception) {
                // ignore
            }
            // 重启 app
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        setContentView(R.layout.activity_main)

        // Firebase Initialize
        FirebaseApp.initializeApp(this)
        RemoteConfigManager.init()

        // === 1. Core 基盤 ===
        AppConfig.init(this)

        // === 基盤: Database ===
    val database = AppDatabase.getInstance(this)

    // === 2. Robot / UI ===
        robot = Robot.getInstance()
        robotController = RobotController(robot)
        
        animationManager = AnimationManager(this)
        screenManager = ScreenManager(this, animationManager)
        screenManager.robotController = robotController
        
        faceManager = FaceManager(robot)

        // === 3. Voice ===
        // voice.VoiceMode と core.VoiceMode が異なる場合があるが、ここでは AppConfig から文字列経由で取得するか、ここでは一旦 TEMI_BUILTIN にしておく
        // val mode = VoiceMode.fromString(AppConfig.voiceMode.name) - assuming AppConfig.voiceMode exists
        voiceProviderFactory = VoiceProviderFactory(robotController)
        val mode = VoiceMode.TEMI_BUILTIN
        sttProvider = voiceProviderFactory.getSttProvider(mode)
        ttsProvider = voiceProviderFactory.getTtsProvider(mode)
        liveVoiceProvider = voiceProviderFactory.getLiveProvider(mode)
        
        // === 4. AI Provider ===
        AiProviderFactory.init()
        val aiProvider = AiProviderFactory.getProvider(AppConfig.aiProvider)

        // === 5. State ===
        stateManager = StateManager()

        // === 6. Persona ===
        memoryManager = MemoryManager(this)
        personaConfig = PersonaConfig()
        personaPromptBuilder = PersonaPromptBuilder(personaConfig, memoryManager)
        behaviorScheduler = BehaviorScheduler(personaConfig)
        
        // === 7. Handlers ===
        dialogActionHandler = DialogActionHandler(this, robotController, screenManager, locationInfo, stateManager)
        conversationHandler = ConversationHandler(
            this, robotController, screenManager, dialogActionHandler, 
            sttProvider, ttsProvider, liveVoiceProvider, aiProvider, stateManager, database
        )
        navigationHandler = NavigationHandler(this, robotController, screenManager, conversationHandler, dialogActionHandler, stateManager)
        detectionHandler = DetectionHandler(this, robotController, screenManager, conversationHandler, dialogActionHandler, navigationHandler, stateManager, faceManager)
        
        patrolManager = com.example.temiguide.robot.PatrolManager(robot, ttsProvider!!, stateManager, screenManager, navigationHandler)
        screenManager.patrolManager = patrolManager

        // ===== Tool System 初始化 =====
        val toolRegistry = ToolRegistry()
        if (ttsProvider is com.example.temiguide.voice.temi.TemiTtsProvider) {
            val temiTts = ttsProvider as com.example.temiguide.voice.temi.TemiTtsProvider
            toolRegistry.register(SpeakTool(temiTts, screenManager) { conversationHandler.detectedLanguage })
            toolRegistry.register(AskUserTool(robot, temiTts) { conversationHandler.detectedLanguage })
        }
        toolRegistry.register(NavigateTool(robot, stateManager, screenManager, navigationHandler))
        toolRegistry.register(TurnTool(robot))
        toolRegistry.register(TiltHeadTool(robot))
        toolRegistry.register(GetLocationsTool(robot))
        toolRegistry.register(CallStaffTool())

        if (aiProvider is GeminiProvider) {
            aiProvider.toolRegistry = toolRegistry
            val reactEngine = ReActEngine(aiProvider, toolRegistry)
            conversationHandler.reactEngine = reactEngine
        }

        Log.d("TemiGuide", "ReAct Tool System initialized with ${toolRegistry.getAllNames().size} tools: ${toolRegistry.getAllNames()}")
        
        val mapZones = listOf(
            MapZone("甘品エリア", 1.0f, 1.0f, 0f, "甘いものが置いてあるエリア"),
            MapZone("入口", 0f, 0f, 0f, "店舗の入り口"),
            MapZone("窓際", 2.0f, 2.0f, 0f, "窓際のエリア")
        )
        autonomyHandler = AutonomyHandler(stateManager, behaviorScheduler, robotController, ttsProvider, mapZones)
        
        // Apply Config
        com.example.temiguide.utils.DevLog.add("Init", "Detection distance config is ${AppConfig.detectionDistance}m")

        conversationHandler.postSafely = { delay, action -> postSafely(delay, action) }
        conversationHandler.returnToHome = { navigationHandler.returnToHome() }
        
        navigationHandler.postSafely = { delay, action -> postSafely(delay, action) }
        detectionHandler.postSafely = { delay, action -> postSafely(delay, action) }
        detectionHandler.onPersonDetectedAutonomy = {
            autonomyHandler.handlePersonDetected {
                val greeting = personaConfig.greetingVariations.random()
                conversationHandler.speakAndListen(greeting)
            }
        }
        navigationHandler.onIdleResumed = {
            autonomyHandler.startAutonomyLoop()
        }
        navigationHandler.onArrivalCooldownStart = {
            autonomyHandler.notifyArrival()
        }

        conversationHandler.personaPromptBuilder = personaPromptBuilder
        conversationHandler.faceManager = faceManager
        dialogActionHandler.setCallbacks(
            postSafely = { delay, action -> postSafely(delay, action) },
            returnToHome = { navigationHandler.returnToHome() },
            speakAndListen = { text -> conversationHandler.speakAndListen(text) },
            speakOnly = { text, onDone -> conversationHandler.speakOnly(text, onDone) },
            onSaveMemory = { key, value -> memoryManager.saveMemory(key, value) },
            onMoveToZone = { zone -> autonomyHandler.moveToZone(zone) },
            onNavStart = { navigationHandler.retryCount = 0 }
        )

// Idle screen tap starts listening and cancels idle timer
        findViewById<View>(R.id.screenIdle).setOnClickListener {
            // ★ 解決策2: 新しい対話を始める前に、既存のTTSやNLU状態を完全にリセットする
            robotController.stopMovement()
            robotController.finishConversation()
            
            screenManager.cancelIdleTimer()

            // 全フラグリセット
            Log.d("TemiGuide", "navigationHandler.isNavigating set to FALSE via screenIdle click")
            navigationHandler.isNavigating = false
            conversationHandler.isWelcomeSpeaking = false
            conversationHandler.isListening = false
            conversationHandler.isArrivalListening = false
            conversationHandler.pendingAutoListen = false

            screenManager.showScreen(ScreenManager.SCREEN_GREETING)

            // askQuestion で挨拶＋自動ASR
            postSafely(100) {
                conversationHandler.speakAndListen(getString(R.string.msg_greeting_ask))
            }
        }

        findViewById<View>(R.id.btnStopGuidance).setOnClickListener {
            robotController.stopMovement()
            dialogActionHandler.isGuideMode = false
            navigationHandler.returnToHome()
        }
        findViewById<View>(R.id.btnCancelStaff).setOnClickListener {
            navigationHandler.returnToHome()
        }
        findViewById<View>(R.id.btnDone).setOnClickListener {
            navigationHandler.returnToHome()
        }
        findViewById<View>(R.id.btnEndSession).setOnClickListener {
            forceResetSession()
            postSafely(200) { navigationHandler.returnToHome() }
        }

        // Immersive Mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                
        // Check permissions
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }

        // Start clock updates
        screenManager.startClockUpdates()
        screenManager.showScreen(ScreenManager.SCREEN_IDLE)

        setupDevMenuTrigger()
        startWatchdog()
    }

    private fun forceResetSession() {
        robotController.stopMovement()
        robotController.finishConversation()
        navigationHandler.userEndedSession = true
        detectionHandler.clearTimers()
        
        Log.d("TemiGuide", "navigationHandler.isNavigating set to FALSE via forceResetSession")
        navigationHandler.isNavigating = false
        conversationHandler.isListening = false
        conversationHandler.isArrivalListening = false
        conversationHandler.isWelcomeSpeaking = false
        dialogActionHandler.pendingArrivalAnnouncement = null
        conversationHandler.pendingAutoListen = false
        pendingLocation = null
        dialogActionHandler.cancelAll()
        conversationHandler.stopAllSpeech()
        
        clearActionRunnables()
        
        conversationHandler.clearHistory()
        screenManager.showScreen(ScreenManager.SCREEN_IDLE)
        autonomyHandler.startAutonomyLoop()
    }

    private fun startWatchdog() {
        val r = object : Runnable {
            override fun run() {
                val currentState = stateManager.state.value
                val now = System.currentTimeMillis()
                
                if (currentState != lastObservedState) {
                    lastObservedState = currentState
                    lastStateChangeTime = now
                } else {
                    if (now - lastStateChangeTime >= AppConstants.WATCHDOG_TIMEOUT_MS) {
                        if (currentState is AppState.Thinking) {
                            com.example.temiguide.utils.DevLog.add("Watchdog", "AI推論が長時間(30s)停止しているためリセットします")
                            forceResetSession()
                            lastStateChangeTime = now
                        }
                    }
                }
                handler.postDelayed(this, 5000)
            }
        }
        watchdogRunnable = r
        handler.postDelayed(r, 5000)
    }

    private fun setupDevMenuTrigger() {
        findViewById<View>(R.id.devMenuTrigger)?.setOnClickListener {
            handleDevTap()
        }
    }

    private fun handleDevTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 3000) {
            tapCount = 0
        }
        
        tapCount++
        lastTapTime = now
        
        if (tapCount >= 5) {
            tapCount = 0
            openDevMenu()
        }
    }

    private fun openDevMenu() {
        val intent = android.content.Intent(this, com.example.temiguide.core.DevMenuActivity::class.java)
        startActivity(intent)
    }

    // ==================== Animations ====================
    // Animations moved to AnimationManager

    // ==================== Robot Lifecycle (UNCHANGED) ====================

    override fun onStart() {
        super.onStart()
        
        // --- 修正箇所：権限チェック ---
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
        
        
        robot.addOnRobotReadyListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addConversationViewAttachesListener(this)
        robot.addOnLocationsUpdatedListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnMovementStatusChangedListener(this)
        robot.addOnConversationStatusChangedListener(this)
        robot.addOnSequencePlayStatusChangedListener(this)
        robot.addOnFaceRecognizedListener(this)
        
        patrolManager.recreate()
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        robot.removeConversationViewAttachesListener(this)
        robot.removeOnLocationsUpdateListener(this)
        robot.removeOnDetectionStateChangedListener(this)
        robot.removeOnMovementStatusChangedListener(this)
        robot.removeOnConversationStatusChangedListener(this)
        robot.removeOnSequencePlayStatusChangedListener(this)
        robot.removeOnFaceRecognizedListener(this)

        screenManager.stopClockUpdates()
        clearActionRunnables()
        patrolManager.destroy()
        watchdogRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            try {
                val activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
                robotController.onStart(activityInfo)
            } catch (e: Exception) {
                Log.e("TemiGuide", "Failed to get ActivityInfo", e)
            }
            robotController.requestToBeKioskApp()
            robotController.hideTopBar()
            try {
                robotController.setAsrLanguages(listOf(SttLanguage.JA_JP, SttLanguage.EN_US, SttLanguage.ZH_CN))
            } catch (e: Exception) {
                Log.e("TemiGuide", "Failed to set ASR language", e)
            }
            
            if (!isInitialized) {
                screenManager.showScreen(ScreenManager.SCREEN_IDLE)
                autonomyHandler.startAutonomyLoop()
                
                if (isFirstWelcome) {
                    conversationHandler.isWelcomeSpeaking = true
                    conversationHandler.speakOnly(getString(R.string.msg_welcome_details)) {
                        conversationHandler.isWelcomeSpeaking = false
                    }
                    isFirstWelcome = false
                }
                isInitialized = true
            }
        }
    }

    override fun onConversationAttaches(isAttached: Boolean) {
        // No-op
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        navigationHandler.onGoToLocationStatusChanged(location, status, descriptionId, description)
    }

    override fun onLocationsUpdated(locations: List<String>) {
        // Locations updated
    }
    
    override fun onDetectionStateChanged(state: Int) {
        if (state == OnDetectionStateChangedListener.DETECTED) {
            patrolManager.stopPatrol()
        }
        detectionHandler.onDetectionStateChanged(state)
        conversationHandler.isPersonDetected = (state == 1)
    }

    override fun onMovementStatusChanged(type: String, status: String) {
        navigationHandler.onMovementStatusChanged(type, status)
    }

    override fun onConversationStatusChanged(status: Int, text: String) {
        Log.d("TemiGuide", "ConversationStatus: status=$status, text=$text")
        
        // 巡逻中有人说话，停止巡逻
        if (status == 1 || status == 2) {
            patrolManager.stopPatrol()
        }

        // 导航中或到达状态时，忽略对话事件
        val currentState = stateManager.state.value
        if (currentState is AppState.Navigating || currentState is AppState.Arrival) {
            Log.d("TemiGuide", "Ignoring conversation during ${currentState.javaClass.simpleName}, status=$status")
            robot.finishConversation()
            return
        }

        when (status) {
            1 -> {
                // ASR 开始监听
                Log.d("TemiGuide", "ASR listening started")
            }
            2 -> {
                // ASR 结果返回（来自 robot.wakeup() 触发 de 对话）
                Log.d("TemiGuide", "ASR result from wakeup: $text")
                // TemiSttProvider の AsrListener 重複を防ぐ
                if (sttProvider is com.example.temiguide.voice.temi.TemiSttProvider) {
                    (sttProvider as com.example.temiguide.voice.temi.TemiSttProvider).markAsHandled()
                }
                if (text.isNotBlank()) {
                    conversationHandler.onAsrResult(text)
                }
            }
            0 -> {
                // 对话结束
                Log.d("TemiGuide", "Conversation finished")
            }
        }
    }

    override fun onSequencePlayStatusChanged(status: Int, sequenceId: String?) {
        Log.d("TemiGuide", "Sequence status changed: $status, id=$sequenceId")
        when (status) {
            OnSequencePlayStatusChangedListener.PLAYING -> {
                Log.d("TemiGuide", "Sequence playing, pausing conversation")
                screenManager.cancelIdleTimer()
                conversationHandler.stopCurrentTask()
                stateManager.transition(AppState.Idle)  // 状態をリセット
            }
            OnSequencePlayStatusChangedListener.IDLE -> {
                Log.d("TemiGuide", "Sequence finished, resuming")
                conversationHandler.resume()
                screenManager.startIdleTimer()
            }
        }
    }

    override fun onFaceRecognized(contactModelList: List<ContactModel>) {
        val name = faceManager.onFaceRecognized(contactModelList)
        if (name != null && stateManager.state.value is AppState.Idle) {
            // リピーター検知 → 巡回停止 → 挨拶
            patrolManager.stopPatrol()
            val greetingText = "${name}様、またお越しいただきありがとうございます！何かお探しですか？"
            conversationHandler.speakAndListen(greetingText)
        }
    }


}

