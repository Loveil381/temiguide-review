# Temi Guide App

アパレルショップ向けの店内案内ロボットアプリケーション（Temi V3 / Android 11 専用）。
お客様のご要望やお探しの商品を音声でヒアリングし、AI (Gemini API) が文脈を理解した上で、適切な売り場まで自律移動（ナビゲーション）でご案内します。

## 対象デバイス
- **ハードウェア**: Temi V3
- **OS**: Android 11 (API Level 30)

## 主要機能一覧
- **音声対話 (ASR & TTS)**: 日本語、英語、中国語に対応したマルチリンガル音声入力・発話機能。
- **AI案内 (Gemini API)**: お客様の曖昧な質問（例：「白シャツが欲しい」「コーディネートを教えて」）から意図を解析し、適切な対応を決定。
- **ナビゲーション機能**: Temiの自律移動機能（`robot.goTo()`）を活用し、店内マップ上の指定地点へ正確に案内。目的地到着時の自動旋回対応。
- **スタッフ呼び出し**: サイズや在庫確認など、人間のスタッフが必要な要求を検知した際に、スタッフ呼び出しフローへ移行。
- **多言語対応**: システムプロンプトによる自動言語検出と、指定言語での音声応答 (`TtsRequest.Language`)。
- **アイドル時の客引き (Cat Concierge)**: 人感センサー (`onDetectionStateChanged`) を活用し、人が近づいた際に自動で挨拶・リスニングモードへ移行。

## 技術スタック
- **言語**: Kotlin
- **SDK**: Temi SDK (`com.robotemi:sdk:1.137.1`)
- **AIモデル**: Google Gemini 2.5 Flash Lite (Retrofit / OkHttp 経由での API通信)
- **JSON解析**: Gson
- **非同期処理**: Kotlin Coroutines & Android Handler (メインスレッド)
- **UIアーキテクチャ**: Single Activity (`MainActivity.kt`) + 複数Viewコンテナ切り替え方式
- **アニメーション**: `ValueAnimator`, `ObjectAnimator`, `AnimatedVectorDrawable`

## ビルド・デプロイ手順
1. **APIキーの設定**: `local.properties` に `GEMINI_API_KEY=[あなたのGemini APIキー]` を追加します。
2. **ビルド**: Android Studio でプロジェクトを開き、`Build > Make Project` を実行するか、ターミナルで `./gradlew assembleDebug` を実行します。
3. **Temiへの接続 (ADB)**:
   - Temi本体の開発者オプションを有効にし、Wi-Fi接続経由でADBデバッグを有効化します。
   - PCのターミナルから `adb connect [TemiのIPアドレス]:5555` で接続します。
4. **インストール**: Android StudioのRunボタンを押すか、`adb install -r app/build/outputs/apk/debug/app-debug.apk` を実行します。

## プロジェクト構造
```
app/src/main/
├── java/com/example/temiguide/
│   ├── MainActivity.kt        # アプリのメインロジック（UI制御、API通信、SDKイベントハンドリング）
│   └── api/                   # RetrofitのAPIインターフェースとデータクラス
├── res/
│   ├── layout/                # 各画面（Idle, Listening, Navigation等）のXMLレイアウト
│   ├── drawable/              # アイコン、背景、アニメーション定義 (AVD) など
│   └── values/                # 文字列リソース(strings.xml)、色定義(colors.xml)など
└── AndroidManifest.xml        # KioskモードやTemi SDKのメタデータを定義
```
