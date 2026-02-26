package com.example.temiguide.ai.deepseek

import com.example.temiguide.ai.*

/**
 * DeepSeek API を使用する AiProvider 実装（スタブ）。
 *
 * 将来実装時に必要な作業:
 * TODO: OpenAI 互換 API エンドポイント (https://api.deepseek.com/v1/chat/completions) を使用
 * TODO: OpenAiProvider をベースに endpoint / model のみ差し替え
 *       - 共通の OpenAI 互換クライアントを抽出して共用することを推奨
 * TODO: DeepSeek 固有のシステムプロンプト調整
 *       - DeepSeek の Function Calling はモデルバージョンにより挙動が異なるため注意
 * TODO: AppConfig.apiKey / modelName を使用
 * TODO: deepseek-chat / deepseek-reasoner 等モデル選択への対応
 */
class DeepSeekProvider : AiProvider {

    override val providerName: String = "DeepSeek"

    override suspend fun chat(
        userText: String,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        throw NotImplementedError("DeepSeek Provider is not yet implemented")
    }

    override suspend fun chatWithFunctions(
        userText: String,
        functions: List<FunctionSpec>,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        throw NotImplementedError("DeepSeek Provider is not yet implemented")
    }

    override fun isAvailable(): Boolean = false
}
