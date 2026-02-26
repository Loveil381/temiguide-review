package com.example.temiguide.ai.tools.impl

import com.example.temiguide.ai.tools.ParamType
import com.example.temiguide.ai.tools.TemiTool
import com.example.temiguide.ai.tools.ToolParam
import com.example.temiguide.ai.tools.ToolResult
import com.example.temiguide.ui.ScreenManager
import com.example.temiguide.voice.temi.TemiTtsProvider

class SpeakTool(
    private val ttsProvider: TemiTtsProvider,
    private val screenManager: ScreenManager? = null,
    private val languageProvider: () -> String = { "ja-JP" }
) : TemiTool {
    override val name: String = "speak"
    override val description: String = "顧客に話しかける。案内、商品紹介、挨拶など。"
    override val parameters: List<ToolParam> = listOf(
        ToolParam("text", ParamType.STRING, "発話するテキスト", true)
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String ?: return ToolResult(false, "text パラメータがありません")
        
        // 発話中は Listening 画面に切り替え（発話テキストを表示）
        screenManager?.showListeningScreen(text)

        val success = ttsProvider.speak(text, languageProvider())
        return if (success) {
            ToolResult(true, "発話完了")
        } else {
            ToolResult(false, "発話に失敗しました")
        }
    }
}
