# 🗺️✨ Google Maps AI StreetView Explorer (Nano Banana 搭載)

Android 端末のセンサーと **Google Maps StreetView**、そして **Google AI Studio の最新 AI 画像生成モデル（Nano Banana / `gemini-3.1-flash-lite-image`）** を融合させた、新感覚の「AI 景色変換 ＆ 仮想世界探訪アプリ」です。

---

## 🌟 主な機能と特徴

1. **リアルタイム・ストリートビュー探訪**:
   - 端末のジャイロ・加速度センサーと同期し、スマホをかざすだけで全方位の景色を直感的に見回せます。
   - 手動のドラッグ操作や、連動の一時停止（OFF）・再開（ON）もワンタップで切り替え可能。

2. **Google AI Studio (Nano Banana) 直結 AI 景色スタイル変換**:
   - 今見ているストリートビューの範囲をピタッと高精細に切り取り！
   - **Google AI Studio** の最新モデル（`gemini-3.1-flash-lite-image`）へ直接データ送信し、景色を以下のスタイルへリアルタイム変貌させます：
     - 🏯 **昭和30年代の日本のレトロな街並み風**
     - 📜 **1920年代 大正ロマン・モノクロセピア古写真風**
     - 🌃 **2100年 サイバーパンク未来都市風**
     - 🎨 **江戸時代の浮世絵・和風絵画調**
     - 🖌️ **水彩画・ファンタジーアニメ背景風** （自由入力プロンプトも対応！）

3. **💾 加工写真のギャラリー・ダウンロード保存**:
   - AI 変換された美しい写真を、1タップでお手持ちのスマホの「写真/ギャラリー（`Pictures/GoogleMap_AI` フォルダ）」へ高画質保存。

4. **🗺️ ミニマップからの自由スポット・ジャンプ**:
   - 画面右下のミニマップをタップすると拡大地図が起動。
   - 地図上の見たい場所を指でタップしてピン（📍）を立てるだけで、その場所のストリートビューへ一発ジャンプ！移動時は自動で連動が静止するため、ゆっくり写真鑑賞や AI 変換が楽しめます。

---

## 📋 動作環境要件

- **OS**: Android 14.0 (API レベル 34) 以上
- **開発環境**: Android Studio (Jellyfish / Koala / Ladybug 以降推奨)
- **Java / JDK**: JDK 17 以上
- **Gradle**: 8.x 以上

---

## 🔑 API キーの取得と設定手順

本アプリを実行・ビルドするには、以下の **2つの API キー** が必要です。

### 1. Google Maps API キーの取得 (`MAPS_API_KEY`)
1. [Google Cloud Console](https://console.cloud.google.com/) にログインします。
2. 新規プロジェクトを作成（または既存プロジェクトを選択）し、**「Maps SDK for Android」** を有効化します。
3. **「認証情報」** 画面から API キーを発行します。

### 2. Google AI Studio API キーの取得 (`GEMINI_API_KEY`)
1. [Google AI Studio (aistudio.google.com)](https://aistudio.google.com/) にアクセスします。
2. 無料で利用可能な **「Get API Key」** ボタンを押して API キーを発行します。

---

## 📝 設定ファイル (`local.properties`) の編集

プロジェクトのルートディレクトリにある `local.properties` ファイルを開き（存在しない場合は新規作成）、取得した 2つの API キーを以下のように記述して保存します。

```properties
## local.properties (プロジェクトルート直下)

# Android SDK のパス (お使いの環境に合わせて指定)
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk

# Google Maps API キー
MAPS_API_KEY=AIzaSyxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Google AI Studio (Gemini / Nano Banana) API キー
GEMINI_API_KEY=AIzaSyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
```

> ⚠️ **注意**: `local.properties` は API キー等の個人秘密情報を含むため、Git 等のバージョン管理システムにはコミットしないでください（`.gitignore` に含まれています）。

---

## 🚀 ビルド ＆ アプリの実行方法

### 方式 A: Android Studio から実行する場合
1. Android Studio で本プロジェクトフォルダを開きます。
2. Gradle Sync が完了するのをお待ちください。
3. 実機 Android 端末（USB デバッグ有効化）またはエミュレータを接続します。
4. 画面上部の **「Run 'app'」 (Shift + F10)** ボタンを押して実行します。

### 方式 B: コマンドライン (Gradle) でビルドする場合

#### Windows (PowerShell / Command Prompt):
```powershell
.\gradlew.bat assembleDebug
```

#### macOS / Linux:
```bash
./gradlew assembleDebug
```

ビルドが完了すると、以下のパスにデバッグ用 APK が生成されます：
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 アプリの主な操作マニュアル

| 操作内容 | 画面上のアクション |
|---|---|
| **ビューの見回し** | スマホを上下左右にかざす（センサー自動連動） |
| **画面切り替え** | 画面ドラッグ操作でセンサー自動停止 |
| **AI 景色変換** | 画面下の `✨ AI景色変換加工` ボタンをタップ |
| **画像ダウンロード** | 変換完了画面下の `💾 このAI加工写真をダウンロード保存する` ボタンをタップ |
| **スポット自由ジャンプ** | 右下のミニマップをタップ ➔ 地図上をタップしてピン選択 ➔ `📍 この場所へ移動する` |

---

## 📄 ライセンス ＆ クレジット
- **Google Maps SDK for Android**: © Google LLC
- **Google AI Studio (Gemini Interactions API / Nano Banana)**: © Google LLC
