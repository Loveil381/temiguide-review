---
name: temi-sdk-check
description: robot.goTo(), robot.speak(), robot.askQuestion(), turnBy()等のTemi SDK呼び出しの監査時に使用
---

# Temi SDK Check Skill

`robot.goTo()`, `robot.speak()`, `robot.askQuestion()`, `turnBy()` など、Temi SDK固有のAPI呼び出しの安全面・仕様準拠を監査する際に使用するスキルです。

## 検査項目

- **goTo() パラメータ正当性**: `goTo()` メソッド呼び出し時の `noRotationAtEnd` パラメータが要件に応じて正しく設定されているか（目的地到着時の向きの制御）。
- **onGoToLocationStatusChanged の全status処理**: ナビゲーションにおける全てのステータス (`start`, `calculating`, `going`, `complete`, `abort`, `reposing`) が適切にハンドルされているか確認する。
- **reposing の return 処理**: ロボットが障害物回避などで `reposing` 状態になった際、不正なロジック進行を防ぐための適切な return 処理（または待機処理）が実装されているか。
- **ASKQuestion 後の ASR タイムアウト処理**: `askQuestion()` 呼び出し後、ユーザーからの応答がない（ASR無音）場合のタイムアウト復帰処理が実装されているか。
- **speak() の UI制御確認**: `robot.speak()` 呼び出し時、システムデフォルトの会話レイヤーUIを抑制するために `isShowOnConversationLayer=false` が指定されているか確認する（カスタムUIと競合しないかの確認）。

## リファレンス

Temi SDK の仕様や注意事項の詳細については、同梱の `references/temi-sdk-notes.md` を参照してください。
