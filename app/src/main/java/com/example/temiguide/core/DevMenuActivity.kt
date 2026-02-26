package com.example.temiguide.core

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.temiguide.R
import com.example.temiguide.ai.AiProviderFactory
import com.example.temiguide.utils.DevLog
import com.robotemi.sdk.Robot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 開発者専用の設定メニュー。
 * AppConfig の値をリアルタイムに閲覧・編集できる。
 */
class DevMenuActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stateManager: StateManager // 実際は DI またはシングルトン経由

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_menu)

        // MainActivity からグローバルな StateManager を取得
        stateManager = com.example.temiguide.MainActivity.stateManager
        
        DevLog.add("DevMenu", "Menu opened.")

        setupProviders()
        setupInputs()
        setupSwitches()
        setupSeekBar()
        setupButtons()
        
        startStateUpdateLoop()
    }

    private fun setupProviders() {
        val spinnerAi = findViewById<Spinner>(R.id.spinnerAiProvider)
        val aiOptions = AiProviderType.entries.map { it.name }
        spinnerAi.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, aiOptions)
        spinnerAi.setSelection(AiProviderType.entries.indexOf(AppConfig.aiProvider))

        val spinnerVoice = findViewById<Spinner>(R.id.spinnerVoiceMode)
        val voiceOptions = VoiceMode.entries.map { it.name }
        spinnerVoice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceOptions)
        spinnerVoice.setSelection(VoiceMode.entries.indexOf(AppConfig.voiceMode))

        val spinnerThinking = findViewById<Spinner>(R.id.spinnerThinkingLevel)
        val thinkingOptions = listOf("MINIMAL", "LOW", "MEDIUM", "HIGH")
        spinnerThinking.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, thinkingOptions)
        spinnerThinking.setSelection(thinkingOptions.indexOf(AppConfig.thinkingLevel))
    }

    private fun setupInputs() {
        findViewById<EditText>(R.id.editApiKey).setText(AppConfig.apiKey)
        findViewById<EditText>(R.id.editModelName).setText(AppConfig.modelName)
    }

    private fun setupSwitches() {
        findViewById<Switch>(R.id.switchIdleTalk).isChecked = AppConfig.idleTalkEnabled
        findViewById<Switch>(R.id.switchAutonomy).isChecked = AppConfig.autonomyEnabled
    }

    private fun setupSeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.seekDetectionDistance)
        val txtValue = findViewById<TextView>(R.id.txtDetectionDistance)
        
        // 0.5m ~ 3.0m (Step 0.5m)
        // SeekBar (0~5) -> 0.5, 1.0, 1.5, 2.0, 2.5, 3.0
        val currentProgress = ((AppConfig.detectionDistance - 0.5f) / 0.5f).toInt()
        seekBar.progress = currentProgress
        txtValue.text = "検知距離: ${AppConfig.detectionDistance}m"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.5f + (progress * 0.5f)
                txtValue.text = "検知距離: ${value}m"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnSaveAndExit).setOnClickListener {
            saveSettings()
            finish()
        }

        findViewById<Button>(R.id.btnTestAi).setOnClickListener {
            testAi()
        }

        findViewById<Button>(R.id.btnTestTts).setOnClickListener {
            testTts()
        }

        findViewById<Button>(R.id.btnTestNav).setOnClickListener {
            testNav()
        }
    }

    private fun saveSettings() {
        AppConfig.aiProvider = AiProviderType.valueOf(findViewById<Spinner>(R.id.spinnerAiProvider).selectedItem.toString())
        AppConfig.voiceMode = VoiceMode.valueOf(findViewById<Spinner>(R.id.spinnerVoiceMode).selectedItem.toString())
        // ThinkingLevel is currently disabled in GeminiProvider due to backend limitations, 
        // but we still save the preference for future use.
        AppConfig.thinkingLevel = findViewById<Spinner>(R.id.spinnerThinkingLevel).selectedItem.toString()
        AppConfig.apiKey = findViewById<EditText>(R.id.editApiKey).text.toString()
        AppConfig.modelName = findViewById<EditText>(R.id.editModelName).text.toString()
        AppConfig.idleTalkEnabled = findViewById<Switch>(R.id.switchIdleTalk).isChecked
        AppConfig.autonomyEnabled = findViewById<Switch>(R.id.switchAutonomy).isChecked
        
        val progress = findViewById<SeekBar>(R.id.seekDetectionDistance).progress
        AppConfig.detectionDistance = 0.5f + (progress * 0.5f)
        
        DevLog.add("DevMenu", "Settings saved and applied.")
    }

    private fun startStateUpdateLoop() {
        handler.post(object : Runnable {
            override fun run() {
                findViewById<TextView>(R.id.txtCurrentState).text = "State: ${stateManager.state.value.stateName()}"
                
                val logText = DevLog.getLogs().joinToString("\n")
                findViewById<TextView>(R.id.txtLogs).text = logText
                
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun testAi() {
        DevLog.add("DevMenu", "Testing AI with 'こんにちは'...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AiProviderFactory.init() // Ensure providers are registered
                val provider = AiProviderFactory.getProvider(AppConfig.aiProvider)
                val response = provider.chat("こんにちは", emptyList())
                DevLog.add("DevMenu", "AI Response: ${response.text}")
            } catch (e: Exception) {
                DevLog.add("DevMenu", "AI Error: ${e.message}")
            }
        }
    }

    private fun testTts() {
        DevLog.add("DevMenu", "Testing TTS...")
        Robot.getInstance().speak(com.robotemi.sdk.TtsRequest.create("テスト音声です", false))
    }

    private fun testNav() {
        val locations = Robot.getInstance().locations
        DevLog.add("DevMenu", "Saved Locations: ${locations.joinToString(", ")}")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
