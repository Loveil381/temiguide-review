package com.example.temiguide.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 開発者メニュー用のログ記録ユーティリティ。
 * 直近50件のログをメモリ上に保持する。
 */
object DevLog {
    private const val MAX_LOGS = 50
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /**
     * ログを追加する。
     */
    @Synchronized
    fun add(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLog = "[$timestamp] $tag: $message"
        
        Log.d("DevLog", formattedLog)
        
        logs.add(0, formattedLog) // 最新を上に
        if (logs.size > MAX_LOGS) {
            logs.removeAt(logs.size - 1)
        }
    }

    /**
     * 全ログを取得する。
     */
    @Synchronized
    fun getLogs(): List<String> = logs.toList()

    /**
     * ログをクリアする。
     */
    @Synchronized
    fun clear() {
        logs.clear()
    }
}
