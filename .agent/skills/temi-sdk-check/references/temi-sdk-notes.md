# Temi SDK Notes & Caveats

Temi V3 (Android 11) における公式SDKの実装に関する注意事項と仕様メモです。監査時に参照してください。

## robot.goTo(location, backwards, noRotationAtEnd)
- **noRotationAtEnd**: 目的地に到達した際、マップ保存時の初期方向へロボットを回転させるかどうかの設定。接客アプリでは到達後の向きが体験に直結するため、設計に合わせて意図的に指定する必要があります。

## onGoToLocationStatusChanged のステータス管理
ナビゲーション中は以下のステータス遷移を正確に捉える必要があります。
- `START`: ナビゲーションの開始。
- `CALCULATING`: 経路の計算中。
- `GOING`: 目的地へ移動中。
- `COMPLETE`: 目的地へ正常に到着完了。
- `ABORT`: 障害物、ユーザー操作、またはパスが見つからない等の理由でナビゲーションが中断された状態。
- `REPOSING`: ロボットがナビゲーション中の問題から復帰しようとしている状態。**この状態の時は、安易にエラー終了や次アクションへ遷移させず、適切に無視（return）等の待機処理を入れないと無限ループや不正なロジック実行の原因になります。**

## robot.askQuestion() と ASR (音声認識)
- `askQuestion()` はロボットのリスニングモードを起動します。
- ASRはネットワーク環境やユーザーの声量によって、結果が空（Empty）で返ってくる場合があります。必ずフォールバック（再質問やテキスト入力への切り替え）とタイムアウト機構を用意してください。

## robot.speak(TtsRequest)
- Temiはデフォルトで発話時に「Conversation Layer（会話レイヤー）」と呼ばれるシステムUIを画面上部に表示します。
- カスタムのアプリUI（画面全体を利用する接客UIなど）を実装している場合、この標準UIが被るとUXが損なわれます。
- 防ぐためには、`TtsRequest.create(speech, isShowOnConversationLayer = false)` のようにオプションを指定して発話させる必要があります。
