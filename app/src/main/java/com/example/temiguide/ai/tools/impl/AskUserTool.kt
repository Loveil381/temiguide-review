package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.example.temiguide.voice.temi.TemiTtsProvider
import com.robotemi.sdk.Robot

class AskUserTool(
    private val robot: Robot,
    private val ttsProvider: TemiTtsProvider,
    private val languageProvider: () -> String = { "ja-JP" }
) : TemiTool {
    override val name: String = "ask_user"
    override val description: String = "顧客に質問して回答を待つ。好みや要望を確認する時に使用。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("question", ParamType.STRING, "質問するテキスト", true)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val question = args["question"] as? String ?: return ToolResult(false, "question パラメータがありません")

        val language = languageProvider()
        
        // speak で質問を読み上げる（TTS のみ）
        // ASR の再起動は ConversationHandler の onAllSpeechComplete → robot.wakeup() が行う
        ttsProvider.speak(question, language)
        
        return ToolResult(
            success = true,
            message = "質問しました ($language): $question",
            shouldWaitForUser = true
        )
    }
}
