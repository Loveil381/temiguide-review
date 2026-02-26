package com.example.temiguide.ai.local

import com.example.temiguide.ai.*

/**
 * ローカル推論エンジンを使用する AiProvider 実装（スタブ）。
 *
 * 将来実装時に必要な作業:
 * TODO: llama.cpp Android バインディング or ONNX Runtime でローカル推論
 * TODO: モデルファイルの assets or sdcard からのロード
 *       - temi ロボットのストレージ容量を考慮（1-3B パラメータ推奨）
 * TODO: Function Calling 非対応モデルの場合のフォールバック
 *       - JSON 出力をシステムプロンプトで強制し、手動パースする
 * TODO: 推論速度の最適化（量子化モデル Q4_K_M 等を使用）
 * TODO: メモリ使用量のモニタリング（temi のメモリは限られている）
 * TODO: バックグラウンドスレッドでの推論実行
 */
class LocalProvider : AiProvider {

    override val providerName: String = "Local"

    override suspend fun chat(
        userText: String,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        throw NotImplementedError("Local Provider is not yet implemented")
    }

    override suspend fun chatWithFunctions(
        userText: String,
        functions: List<FunctionSpec>,
        conversationHistory: List<Message>,
        systemPromptOverride: String?
    ): AiResponse {
        throw NotImplementedError("Local Provider is not yet implemented")
    }

    override fun isAvailable(): Boolean = false
}
