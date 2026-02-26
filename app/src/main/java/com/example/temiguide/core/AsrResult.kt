package com.example.temiguide.core

/**
 * ASR (音声認識) の結果を保持するデータクラス
 * @param text 認識されたテキスト
 * @param language 検出された言語コード ("ja-JP", "en-US", "zh-CN" 等)
 */
data class AsrResult(
    val text: String,
    val language: String = "ja-JP"
)
