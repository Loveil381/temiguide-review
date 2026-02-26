package com.example.temiguide.ai

/**
 * AI プロバイダの統一インターフェース。
 *
 * Gemini, OpenAI, DeepSeek, Local など異なるバックエンドを
 * 同一のインターフェースで利用可能にする。
 * プロバイダの追加時はこのインターフェースを実装するだけでよい。
 */
interface AiProvider {

    /** プロバイダの表示名（ログ・DevMenu 表示用） */
    val providerName: String

    /**
     * 通常のチャットリクエストを送信する。
     *
     * @param userText ユーザーの入力テキスト
     * @param conversationHistory 会話履歴
     * @param systemPromptOverride 一時的なシステムプロンプトの上書き
     * @return AI からのレスポンス
     */
    suspend fun chat(
        userText: String,
        conversationHistory: List<Message>,
        systemPromptOverride: String? = null
    ): AiResponse

    /**
     * Function Calling 付きのチャットリクエストを送信する。
     *
     * @param userText ユーザーの入力テキスト
     * @param functions 利用可能なファンクション定義のリスト
     * @param conversationHistory 会話履歴
     * @param systemPromptOverride 一時的なシステムプロンプトの上書き
     * @return AI からのレスポンス（actions に Function Call 結果が含まれる）
     */
    suspend fun chatWithFunctions(
        userText: String,
        functions: List<FunctionSpec>,
        conversationHistory: List<Message>,
        systemPromptOverride: String? = null
    ): AiResponse

    /**
     * このプロバイダが利用可能かを返す。
     * APIキーが設定済みか、ネットワーク接続可能かなどを判定する。
     */
    fun isAvailable(): Boolean
}

// ==================== 会話メッセージ ====================

/**
 * プロバイダ中立な会話メッセージ。
 * 各プロバイダ実装内で SDK 固有の形式に変換する。
 */
data class Message(
    val role: String,       // "user", "assistant", "system"
    val content: String
)

// ==================== Function Spec ====================

/**
 * プロバイダ中立なファンクション定義。
 *
 * Gemini の FunctionDeclaration や OpenAI の tools JSON への変換は
 * 各プロバイダ実装内部で行う。
 */
data class FunctionSpec(
    val name: String,
    val description: String,
    val parameters: Map<String, ParamSpec>
)

/**
 * ファンクションパラメータの定義。
 *
 * @param type パラメータ型 ("string", "integer", "number", "boolean", "array", "object")
 * @param description パラメータの説明
 * @param required 必須パラメータか
 * @param items type="array" の場合の要素定義
 * @param properties type="object" の場合のプロパティ定義
 */
data class ParamSpec(
    val type: String,
    val description: String,
    val required: Boolean = true,
    val items: ParamSpec? = null,
    val properties: Map<String, ParamSpec>? = null
)
