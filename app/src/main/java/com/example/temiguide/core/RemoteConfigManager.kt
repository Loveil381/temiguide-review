package com.example.temiguide.core

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object RemoteConfigManager {
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // Remote Config キー
    const val KEY_SYSTEM_PROMPT = "system_prompt"
    const val KEY_IDLE_TIMER_MS = "idle_timer_ms"
    const val KEY_REACT_TIMEOUT_MS = "react_timeout_ms"
    const val KEY_MAX_SPEAK_LENGTH = "max_speak_length"
    const val KEY_PROMO_MESSAGE = "promo_message"
    const val KEY_MULTI_LOCATION_AUTO = "multi_location_auto_continue"

    fun init() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600  // 1時間ごと
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // デフォルト値
        remoteConfig.setDefaultsAsync(mapOf(
            KEY_SYSTEM_PROMPT to "",  // 空 = GeminiProvider のテンプレートを使用
            KEY_IDLE_TIMER_MS to AppConstants.IDLE_TIMER_MS,
            KEY_REACT_TIMEOUT_MS to AppConstants.REACT_TOTAL_TIMEOUT_MS,
            KEY_MAX_SPEAK_LENGTH to 50L,
            KEY_PROMO_MESSAGE to "",
            KEY_MULTI_LOCATION_AUTO to true
        ))

        // フェッチ＆アクティベート
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(AppConstants.LOG_TAG, "Remote Config fetched and activated")
            } else {
                Log.w(AppConstants.LOG_TAG, "Remote Config fetch failed, using defaults")
            }
        }
    }

    fun getString(key: String): String = remoteConfig.getString(key)
    fun getLong(key: String): Long = remoteConfig.getLong(key)
    fun getBoolean(key: String): Boolean = remoteConfig.getBoolean(key)
}
