package com.example.temiguide.persona

import android.content.Context
import android.os.Handler
import android.os.Looper

class MemoryManager(private val context: Context) {

    private val shortTermHistory = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var historyClearTimer: Runnable? = null
    
    // Shared preferences for simple "long term memory"
    private val prefs = context.getSharedPreferences("RobotMemory", Context.MODE_PRIVATE)

    fun addInteraction(userText: String, aiResponse: String) {
        shortTermHistory.add("User: \$userText\nAI: \$aiResponse")
        
        // Retain max 20 messages to avoid huge context in prompt
        if (shortTermHistory.size > 20) {
            shortTermHistory.removeAt(0)
        }

        resetClearTimer()
    }

    fun getRecentContext(): String {
        if (shortTermHistory.isEmpty()) return "なし"
        return shortTermHistory.joinToString("\n\n")
    }

    fun saveMemory(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun recallMemory(key: String): String? {
        return prefs.getString(key, null)
    }
    
    fun getAllLongTermMemory(): String {
        val allEntries = prefs.all
        if (allEntries.isEmpty()) return "なし"
        return allEntries.entries.joinToString("\n") { "- \${it.key}: \${it.value}" }
    }

    private fun resetClearTimer() {
        historyClearTimer?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            shortTermHistory.clear()
        }
        historyClearTimer = runnable
        // Clear short term context after 15 minutes
        handler.postDelayed(runnable, 15 * 60 * 1000L)
    }
}
