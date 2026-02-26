package com.example.temiguide

class ActionQueue(
    private val actions: MutableList<ActionItem>,
    private val onExecute: (ActionItem, () -> Unit) -> Unit
) {
    data class ActionItem(
        val type: String,
        val location: String? = null,
        val text: String? = null,
        val question: String? = null,
        val announcement: String? = null,
        val reason: String? = null,
        val seconds: Int? = null,
        val key: String? = null,
        val value: String? = null
    )

    private var currentIndex = 0
    var isRunning = false
        private set

    fun start() {
        isRunning = true
        currentIndex = 0
        executeNext()
    }

    fun executeNext() {
        if (currentIndex >= actions.size) {
            isRunning = false
            return
        }
        val action = actions[currentIndex]
        currentIndex++
        onExecute(action) { executeNext() }
    }

    fun cancel() {
        isRunning = false
        actions.clear()
    }

    fun hasNext(): Boolean = currentIndex < actions.size
    
    // ユーザーがask質問に「いいえ」と答えた場合、残りをスキップ
    fun skipRemaining() {
        currentIndex = actions.size
        isRunning = false
    }
}
