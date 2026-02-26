package com.example.temiguide.robot

import com.robotemi.sdk.Robot
import com.robotemi.sdk.face.ContactModel
import com.example.temiguide.utils.DevLog

class FaceManager(private val robot: Robot) {

    // 直近に認識した顧客情報
    var lastRecognizedName: String? = null
        private set
    var isReturningCustomer: Boolean = false
        private set

    // 認識済みの顧客をトラッキング（同じ人に何度も挨拶しないため）
    private val recentlyGreeted = mutableMapOf<String, Long>()  // userId -> timestamp
    private val GREET_COOLDOWN_MS = 300_000L  // 5分間は同じ人に再挨拶しない

    fun startRecognition() {
        try {
            robot.startFaceRecognition(withSdkFaces = true)
            DevLog.add("FaceManager", "Face recognition started")
        } catch (e: Exception) {
            DevLog.add("FaceManager", "Failed to start face recognition: ${e.message}")
        }
    }

    fun stopRecognition() {
        try {
            robot.stopFaceRecognition()
            DevLog.add("FaceManager", "Face recognition stopped")
        } catch (e: Exception) {
            DevLog.add("FaceManager", "Failed to stop face recognition: ${e.message}")
        }
    }

    /**
     * 顔認識コールバックから呼ばれる。
     * @return 挨拶すべき顧客名（null = 挨拶不要 or 未登録）
     */
    fun onFaceRecognized(contacts: List<ContactModel>): String? {
        if (contacts.isEmpty()) {
            lastRecognizedName = null
            isReturningCustomer = false
            return null
        }

        val contact = contacts.first()
        val userId = contact.userId ?: return null
        val name = contact.firstName ?: contact.lastName ?: return null

        // クールダウンチェック
        val now = System.currentTimeMillis()
        val lastGreetTime = recentlyGreeted[userId]
        if (lastGreetTime != null && (now - lastGreetTime) < GREET_COOLDOWN_MS) {
            DevLog.add("FaceManager", "Face recognized: $name (cooldown, skip greeting)")
            return null
        }

        lastRecognizedName = name
        isReturningCustomer = true
        recentlyGreeted[userId] = now

        DevLog.add("FaceManager", "Face recognized: $name (greeting)")
        return name
    }

    /**
     * AI の system prompt に注入するコンテキスト
     */
    fun getContextForAI(): String {
        return if (isReturningCustomer && lastRecognizedName != null) {
            "現在の顧客は「${lastRecognizedName}」様です（リピーター）。名前で呼びかけ、以前のご利用に感謝してください。"
        } else {
            ""
        }
    }

    fun reset() {
        lastRecognizedName = null
        isReturningCustomer = false
    }
}
