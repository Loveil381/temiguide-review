# Temi SDK リファレンス（開発時参照用）

## 公式リポジトリ
- SDK: https://github.com/robotemi/sdk
- Sample App: https://github.com/robotemi/sdk/tree/master/sample
- Robot.kt API: https://github.com/robotemi/sdk/blob/master/sdk/src/main/java/com/robotemi/sdk/Robot.kt

## 音声関連 API の正しい使い方

### wakeup()
- 1回呼ぶと1回のASRセッションが始まる（1回だけ聞く）
- ASR結果を受け取った後、再度リスニングしたい場合は再呼び出しが必要
- TTS中に呼ぶと競合する。TTS完了後に呼ぶこと
- 参照: Issue #427

### cancelAllTtsRequests()
- 現在再生中のTTSを停止し、TTSキューを空にする
- **対話セッション自体は終了しない**（finishConversation との重要な違い）
- 用途: ユーザーが停止ボタンを押した時など、TTSだけを即座に止めたい場合
- finishConversation() はセッション全体を終了するが、cancelAllTtsRequests() はTTS再生のみを制御する
- 参照: 公式SDKドキュメント「Stops currently processed TTS request and empty the queue」

### speak(TtsRequest)
- isShowOnConversationLayer = false にすること（自アプリで管理する場合）
- 会話セッション中に呼ぶ場合は先に finishConversation() すること
- 参照: Issue #146, #439

### askQuestion(String)
- 質問を話した後に自動でASRを開始する、**対話ループの推奨手段**（公式Issue #427）
- onAsrResult で結果を受け取る
- speak + wakeup の組み合わせは「発話後にASRが不要な場面」や「ASRのみを単独で起動する場面」に限定して使用すること
- speak + wakeup 間でTemi内蔵NLUが介入し、デフォルトメッセージが表示される既知問題あり（Issue #439、未解決）

### finishConversation()
- 現在の会話セッションを終了する
- **askQuestion() の後に speak() を呼ぶ場合のみ**、先に finishConversation() が必要（公式Issue #146）
- askQuestion を使っていない状態で speak() の前に呼ぶと、対話状態機がリセットされ、内蔵NLUが介入する原因になる
- 連続呼び出ししても安全（エラーにならない）
- 主な使用場面: セッション終了時、対話→ナビゲーションモード切替時

### onAsrResult(asrResult: String, sttLanguage: SttLanguage)
- wakeup/askQuestion 後のユーザー発話結果
- 空文字列 = タイムアウト（ユーザーが何も言わなかった）
- 結果受信後、Temiはリスニングを停止する → 再開にはwakeup()が必要

### onTtsStatusChanged(ttsRequest: TtsRequest)
- Status.COMPLETED でTTS完了を検知
- ここで次のwakeup()を呼ぶのが推奨パターン（チェーン方式）

### onConversationStatusChanged(status: Int, text: String)
- Temi内部の会話状態変化通知
- 自アプリで会話管理する場合はログのみに留める（複雑なロジックを入れない）

## ナビゲーション API

### goTo(location, ...)
- noRotationAtEnd: Boolean? → trueで到着時の回転スキップ（v135+）
- isNavigating フラグで二重呼び出しを防ぐこと
- onGoToLocationStatusChanged でステータス監視

### 到着ステータス値
- "complete" → 到着完了
- "abort" → 中断（障害物、ユーザー停止）
- "going" → 移動中
- "start" → 開始
- "calculating" → 経路計算中
- "reposing" → 位置補正中

## よくあるトラブルと解決策

| 症状 | 原因 | 対策 |
|------|------|------|
| speak()しても話さない | 会話セッション中 | finishConversation()してからspeak() |
| wakeup()してもASR来ない | TTS中にwakeup | onTtsStatusChanged COMPLETEDで呼ぶ |
| デフォルトメッセージが出る | speak+wakeup間で内蔵NLUが介入（Issue #439未解決） | askQuestion()で対話ループを管理する。speakOnly時はisShowOnConversationLayer=false |
| 2回目のタップで反応しない | フラグ未リセット | btnEndSession/復帰時に全フラグリセット |
| goTo中に再度goTo | isNavigating未チェック | ガード条件を追加 |

## 参照Issue一覧
- #24: WakeupListener and TtsListener clarification
- #146: ASR Listener, TtsRequest not executing → finishConversation() first
- #279: Temi STT problem → ASR is part of voice interaction
- #427: How to trigger ASR without "Hey Temi" → wakeup() + re-call pattern
- #439: Default message instead of custom speak → isShowOnConversationLayer=false
- #473: Robot.wakeup not working → SDK version compatibility
- #494: Q&A through SDK → don't override conversation layer unnecessarily
- #473についての補足: wakeup()が動作しない場合はSDKバージョンとtemiランチャーバージョンの互換性を確認（SDK 1.132+ / Launcher 132+が必要）
