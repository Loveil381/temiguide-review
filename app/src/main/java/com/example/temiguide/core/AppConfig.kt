package com.example.temiguide.core

import android.content.Context
import android.content.SharedPreferences
import com.example.temiguide.BuildConfig
import com.google.firebase.ai.type.ThinkingLevel
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * アプリケーション全体の設定を管理するオブジェクト。
 *
 * 各プロパティは [SharedPreferences] に即座に永続化される委譲プロパティで実装。
 * [init] をアプリ起動時（Application.onCreate or MainActivity.onCreate）に呼び出すこと。
 */
object AppConfig {

    private const val PREFS_NAME = "temiguide_config"
    private lateinit var prefs: SharedPreferences

    /**
     * 初期化。Context から SharedPreferences を取得する。
     * 二重呼び出しは無視される。
     */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== AI Provider ====================

    /** 使用する AI プロバイダ */
    var aiProvider: AiProviderType
        get() = AiProviderType.fromString(prefs.getString("ai_provider", AiProviderType.GEMINI.name)!!)
        set(value) = prefs.edit().putString("ai_provider", value.name).apply()

    /** API キー */
    var apiKey: String by stringPref("api_key", BuildConfig.GEMINI_API_KEY)

    /** モデル名 */
    var modelName: String by stringPref("model_name", "gemini-2.5-flash")

    /** システムプロンプト（空文字の場合はデフォルトを使用） */
    var systemPrompt: String by stringPref("system_prompt", "")

    /** Temperature (0.0 - 2.0) */
    var temperature: Float by floatPref("temperature", 0.7f)

    /** 最大トークン数 */
    var maxTokens: Int by intPref("max_tokens", 1024)

    /** Thinking Level (MINIMAL, LOW, MEDIUM, HIGH) */
    var thinkingLevel: String by stringPref("thinking_level", "LOW")

    /**
     * ThinkingLevel enum を取得する。
     */
    fun getThinkingLevelEnum(): ThinkingLevel = when (thinkingLevel) {
        "MINIMAL" -> ThinkingLevel.MINIMAL
        "LOW" -> ThinkingLevel.LOW
        "MEDIUM" -> ThinkingLevel.MEDIUM
        "HIGH" -> ThinkingLevel.HIGH
        else -> ThinkingLevel.LOW
    }

    // ==================== Voice ====================

    /** 音声モード */
    var voiceMode: VoiceMode
        get() = VoiceMode.fromString(prefs.getString("voice_mode", VoiceMode.TEMI_BUILTIN.name)!!)
        set(value) = prefs.edit().putString("voice_mode", value.name).apply()

    // ==================== Run Mode ====================

    /** 実行モード */
    var runMode: RunMode
        get() = RunMode.fromString(prefs.getString("run_mode", RunMode.DEMO.name)!!)
        set(value) = prefs.edit().putString("run_mode", value.name).apply()

    // ==================== Robot ====================

    /** ロボット名 */
    var robotName: String by stringPref("robot_name", "てみちゃん")

    /** パーソナリティ */
    var personality: String by stringPref("personality", "friendly")

    /** 言語 */
    var language: String by stringPref("language", "ja")

    /** アイドルタイムアウト秒数 */
    var idleTimeoutSec: Int by intPref("idle_timeout_sec", 30)

    /** 人物検知を有効にするか */
    var detectionEnabled: Boolean by boolPref("detection_enabled", true)

    // ==================== Future Flags ====================

    /** ビジョン機能を有効にするか（将来用） */
    var visionEnabled: Boolean by boolPref("vision_enabled", false)

    /** 自律行動を有効にするか */
    var autonomyEnabled: Boolean by boolPref("autonomy_enabled", false)

    /** 独り言を有効にするか */
    var idleTalkEnabled: Boolean by boolPref("idle_talk_enabled", false)

    /** 人物検知距離 (m) */
    var detectionDistance: Float by floatPref("detection_distance", 1.5f)

    /** Fleet管理を有効にするか（将来用） */
    var fleetEnabled: Boolean by boolPref("fleet_enabled", false)

    // ==================== Utilities ====================

    /**
     * 全設定をデバッグ用文字列として出力する。
     */
    fun toDebugString(): String = buildString {
        appendLine("=== AppConfig ===")
        appendLine("aiProvider=$aiProvider, modelName=$modelName")
        appendLine("apiKey=${if (apiKey.isNotBlank()) "***SET***" else "EMPTY"}")
        appendLine("temperature=$temperature, maxTokens=$maxTokens")
        appendLine("voiceMode=$voiceMode, runMode=$runMode")
        appendLine("robotName=$robotName, personality=$personality, language=$language")
        appendLine("idleTimeoutSec=$idleTimeoutSec, detectionEnabled=$detectionEnabled, detectionDistance=$detectionDistance")
        appendLine("visionEnabled=$visionEnabled, autonomyEnabled=$autonomyEnabled, idleTalkEnabled=$idleTalkEnabled, fleetEnabled=$fleetEnabled")
    }

    /**
     * 全設定をデフォルト値にリセットする。
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    // ==================== Delegate Helpers ====================

    private fun stringPref(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            prefs.getString(key, default) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            prefs.edit().putString(key, value).apply()
        }
    }

    private fun intPref(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            prefs.getInt(key, default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun floatPref(key: String, default: Float) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float =
            prefs.getFloat(key, default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            prefs.edit().putFloat(key, value).apply()
        }
    }

    private fun boolPref(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            prefs.getBoolean(key, default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }
}

// ==================== Enums ====================

enum class AiProviderType {
    GEMINI, OPENAI, DEEPSEEK, LOCAL;

    companion object {
        fun fromString(value: String): AiProviderType =
            entries.firstOrNull { it.name == value } ?: GEMINI
    }
}

enum class VoiceMode {
    TEMI_BUILTIN, GEMINI_LIVE, CUSTOM;

    companion object {
        fun fromString(value: String): VoiceMode =
            entries.firstOrNull { it.name == value } ?: TEMI_BUILTIN
    }
}

enum class RunMode {
    DEMO, PRODUCTION, DEBUG;

    companion object {
        fun fromString(value: String): RunMode =
            entries.firstOrNull { it.name == value } ?: DEMO
    }
}
