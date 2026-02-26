package com.example.temiguide.ai.openai

import com.example.temiguide.ai.*

/**
 * OpenAI API を使用する AiProvider 実装（スタブ）。
 *
 * 将来実装時に必要な作業:
 * TODO: Retrofit で OpenAI Chat Completions API (https://api.openai.com/v1/chat/completions) を呼ぶ
 * TODO: tools パラメータで Function Calling を実装
 *       - FunctionSpec → OpenAI tools JSON 形式に変換
 *       - response.choices[0].message.tool_calls を TemiAction に変換
 * TODO: AppConfig.apiKey / modelName を使用
 * TODO: conversationHistory を OpenAI messages 形式 (role/content) に変換
 * TODO: streaming レスポンスへの対応（オプション）
 * TODO: エラーハンドリング（レート制限 429、認証エラー 401 等）
 */
class OpenAiProvider : AiProvider {

    override val providerName: String = "OpenAI"

    override suspend fun chat(
        userText: String,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        throw NotImplementedError("OpenAI Provider is not yet implemented")
    }

    override suspend fun chatWithFunctions(
        userText: String,
        functions: List<FunctionSpec>,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        throw NotImplementedError("OpenAI Provider is not yet implemented")
    }

    override fun isAvailable(): Boolean = false
}
