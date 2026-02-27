# Temi Guide — AI 店舗案内ロボットアプリ

Temi V3 ロボット上で動作する AI 接客・店舗案内アプリケーション。  
Gemini API による自然言語対話、ReAct エンジンによるツール実行、自動巡回パトロール機能を搭載。

## 技術スタック

| カテゴリ | 技術 |
|---------|------|
| 言語 | Kotlin |
| ロボットSDK | Temi SDK 1.137.1 |
| AI | Google Gemini (Firebase AI Logic) |
| アーキテクチャ | Single Activity + Handler 分離 + StateManager |
| AI推論 | ReAct Engine (Reasoning + Acting ループ) |
| 音声 | Temi 内蔵 ASR/TTS (`wakeup()` + `onConversationStatusChanged`) |
| DB | Room (対話ログ保存) |
| 非同期 | Kotlin Coroutines + lifecycleScope |
| UI | FrameLayout View 切替 + AnimatedVectorDrawable |
| ビルド | Gradle KTS + KSP |

## 主な機能

- **AI 対話**: Gemini API を用いた多言語自然言語対話（日本語・英語・中国語）
- **ReAct ツール実行**: speak, navigate, ask_user, turn, tilt_head, get_available_locations, call_staff の 7 ツール
- **店舗案内ナビゲーション**: Temi の `goTo()` による自律移動 + 到着後 180° ターン
- **自動巡回パトロール**: アイドル時に登録地点を巡回し、プロモーション発話
- **顔認識**: リピーター検知 + パーソナライズ挨拶
- **ペルソナシステム**: 猫コンシェルジュキャラクター + 時間帯別エネルギー + 記憶機能
- **Kiosk モード**: 全画面専有の店舗サイネージ

## セットアップ

1. `local.properties` に API キーを追加:

GEMINI_API_KEY=your_gemini_api_key_here

2. `app/google-services.json` を Firebase Console からダウンロードして配置
3. Android Studio で **Build → Make Project**
4. Temi に ADB 接続して **Run**

## プロジェクト構造

```
app/src/main/java/com/example/temiguide/
├── MainActivity.kt          # エントリポイント + SDK リスナー
├── ai/
│   ├── AiProvider.kt        # AI プロバイダインターフェース
│   ├── AiProviderFactory.kt # プロバイダファクトリ
│   ├── ReActEngine.kt       # ReAct 推論ループ
│   ├── gemini/GeminiProvider.kt # Gemini (Firebase AI) 実装
│   └── tools/               # ツールシステム
│       ├── TemiTool.kt      # ツールインターフェース
│       ├── ToolRegistry.kt  # ツール登録管理
│       └── impl/            # 各ツール実装
├── robot/
│   ├── ConversationHandler.kt # 対話制御 (ASR/TTS/AI)
│   ├── NavigationHandler.kt   # ナビゲーション状態管理
│   ├── PatrolManager.kt       # 自動巡回パトロール
│   ├── DetectionHandler.kt    # 人物検知処理
│   ├── DialogActionHandler.kt # 案内アクション (staff call 等)
│   ├── RobotController.kt      # SDK ラッパー
│   ├── FaceManager.kt          # 顔認識管理
│   └── AutonomyHandler.kt      # 自律行動制御
├── core/
│   ├── AppState.kt           # 状態定義 (sealed class)
│   ├── StateManager.kt       # 状態遷移管理
│   ├── AppConfig.kt          # SharedPreferences 設定
│   └── AppConstants.kt       # 定数定義
├── ui/
│   ├── ScreenManager.kt      # 画面切替 + アイドルタイマー
│   └── AnimationManager.kt   # アニメーション制御
├── voice/
│   ├── temi/TemiTtsProvider.kt # Temi TTS 実装
│   └── temi/TemiSttProvider.kt # Temi ASR 実装
├── persona/                  # ペルソナ・記憶システム
├── data/                     # Room DB (対話ログ)
└── models/                   # データモデル
```

## 状態遷移

Idle → Greeting → Listening → Thinking → Speaking → Listening (ループ) → Navigating → Arrival → Idle
→ StaffCall → Idle
Idle → Autonomous (巡回) → Idle

## 音声対話フロー

TTS 発話完了 → onAllSpeechComplete → robot.wakeup() → onConversationStatusChanged(status=2) → ASR テキスト取得 → ConversationHandler.onAsrResult() → ReActEngine.run() → Tool 実行 → TTS 発話 → ループ

## セキュリティ

- API キーは `local.properties` に保管（`.gitignore` 対象）
- `google-services.json` は `.gitignore` 対象
- `BuildConfig.GEMINI_API_KEY` 経由でコード内参照

## ライセンス

Private — All rights reserved.
