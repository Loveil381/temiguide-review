---
description: Always On QA Rules for Temi Robot App
---

# Temi Robot App QA Rules (Always On)

これらのルールは、開発およびコード改修時に常に遵守する必要があります。

1. **状態フラグのペア管理**: 状態フラグ（`isNavigating`, `isListening`, `isArrivalListening`, `isGuideMode`, `isWelcomeSpeaking`など）は必ず `set` / `reset`（または true/false）を正確にペアで管理し、状態のリークを防ぐこと。
2. **executeActionの網羅性**: `executeAction` 関数内の `when` ブランチは、すべてのアクションタイプ（`guide`, `speak`, `call_staff`, `end_conversation`, `pause`）をカバーしていること。
3. **明示的な退出パス**: 各画面（View/Layout）には、必ず明確な退出パス（タイマーによる自動遷移、ユーザー操作のボタン、タイムアウトなど）が存在すること。デッドエンド（行き止まり）画面は禁止。
4. **カウントダウン終了時の処理**: カウントダウンやタイマーが0になった時は、必ず後続のアクションを実行すること。単に View を非表示（GONE/INVISIBLE）にするだけの処理は禁止。
5. **安全なナビゲーション**: `robot.goTo()` は、必ず `isNavigating` が `false` の時のみ呼び出すこと。
6. **ハードコード禁止**: APIキーなどのシークレット情報をソースコードにハードコードしないこと。
7. **Handlerリーク防止**: `handler.postDelayed` を使用した場合は、必ず対応する `removeCallbacks`（または `removeCallbacksAndMessages`）を呼び出し、メモリリークや予期せぬ実行を防ぐこと。
