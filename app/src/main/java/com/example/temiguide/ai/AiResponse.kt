package com.example.temiguide.ai

/**
 * AIプロバイダからの統一レスポンス。
 *
 * どのプロバイダ（Gemini, OpenAI, DeepSeek, Local）が返しても
 * 同じ構造で処理できるようにする。
 *
 * @param text AI が生成したテキスト（表示・発話用）
 * @param actions 実行すべきロボットアクションのリスト
 * @param confidence 応答の確信度 (0.0 - 1.0)
 * @param rawResponse デバッグ用の生レスポンス文字列
 * @param provider レスポンスを返したプロバイダ名
 */
data class AiResponse(
    val text: String,
    val actions: List<TemiAction> = emptyList(),
    val confidence: Float = 1.0f,
    val rawResponse: String = "",
    val provider: String = "",
    val error: String? = null
)

/**
 * ロボットが実行可能なアクションの型安全な定義。
 *
 * 現在の ActionQueue.ActionItem の文字列ベースの type 分岐を置き換える。
 * 将来のアクション追加時は、ここにサブクラスを追加するだけでよい。
 */
sealed class TemiAction {

    /** テキストを発話する */
    data class Speak(
        val text: String,
        val language: String = "ja"
    ) : TemiAction()

    /** 指定ロケーションへ移動する */
    data class Navigate(
        val destination: String,
        val announcement: String? = null
    ) : TemiAction()

    /** 絶対座標へ移動する（将来用: temi SDK goToPosition） */
    data class NavigateToPosition(
        val x: Float,
        val y: Float,
        val yaw: Float
    ) : TemiAction()

    /** 指定角度だけ回転する */
    data class TurnBy(
        val degrees: Int,
        val velocity: Float = 1.0f
    ) : TemiAction()

    /** 頭部チルト角度を変更する（将来用: temi SDK tiltAngle） */
    data class TiltHead(
        val angle: Int
    ) : TemiAction()

    /** スタッフを呼び出す */
    data class CallStaff(
        val reason: String
    ) : TemiAction()

    /** ユーザーに質問する（ASR開始） */
    data class AskQuestion(
        val text: String
    ) : TemiAction()

    /** 指定秒数待機する */
    data class Wait(
        val seconds: Int
    ) : TemiAction()

    /** 写真を撮影する（将来用） */
    object TakePhoto : TemiAction()

    /** ホームベースに帰還する */
    object GoHome : TemiAction()

    /** 複数ロケーションを巡回する（将来用） */
    data class Patrol(
        val locations: List<String>
    ) : TemiAction()

    /** 指定ゾーンに自律移動する */
    data class MoveToZone(
        val zone: String
    ) : TemiAction()

    /** ユーザーの記憶を保存する */
    data class SaveMemory(
        val key: String,
        val value: String
    ) : TemiAction()

    /** 会話を終了する */
    data class EndConversation(
        val reply: String
    ) : TemiAction()

    /** ロボットを一時停止する */
    data class Pause(
        val message: String
    ) : TemiAction()
}
