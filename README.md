# 🗺️✨ Google Maps AI StreetView Explorer (Nano Banana 搭載)

Android 端末のセンサーと **Google Maps StreetView**、そして **Google AI Studio の最新 AI 画像生成モデル（Nano Banana / `gemini-3.1-flash-lite-image`）** を融合させた、新感覚の「AI 景色変換 ＆ 仮想世界探訪アプリ」です。

---

## 🎬 アプリ動作デモ動画 (YouTube Shorts)

[![Google Map AI Demo](https://img.youtube.com/vi/L2Y7zD4Zuac/hqdefault.jpg)](https://www.youtube.com/shorts/L2Y7zD4Zuac)

> ▶️ **[YouTube Shorts で実際のアプリ動く様子を見る](https://www.youtube.com/shorts/L2Y7zD4Zuac)** (`https://www.youtube.com/shorts/L2Y7zD4Zuac`)

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

4. **🗺️ ミニマップからの自由スポット・ジャンプ ＆ 地名検索**:
   - 画面右下のミニマップをタップすると拡大地図ダイアログが起動。
   - 地名・住所・施設名での検索バー機能 ＆ 地図上タップでのピン（📍）設置に対応。
   - 今見ているストリートビューの現在地からそのまま位置の微調整が可能です。

---

## 📋 動作環境 ＆ 開発に必要なツール

- **ビルドツール（必須）**: **Android Studio** (Jellyfish / Koala / Ladybug 以降推奨)
  > ⚠️ 本プロジェクトのビルド・実行には **Android Studio** が必要となります。事前に公式サイトよりインストールしてください。
- **OS要件**: Android 14.0 (API レベル 34) 以上
- **Java / JDK**: JDK 17 以上
- **Gradle**: 8.x 以上

---

## 🤖 Antigravity を使った開発 ＆ 不明点の質問方法

本プロジェクトは **Google Antigravity**（AI コーディングアシスタント）での開発に最適化されています。

1. **Antigravity をインストール・起動**します。
2. Antigravity 上で**本プロジェクトのルートフォルダを指定して開きます**。
3. ビルドエラーの解決、設定方法、コードのカスタマイズ、動作仕様などの不明点があれば、**「〇〇のビルドの仕方を教えて」「〇〇の機能を変更したい」と直接 Antigravity に質問・指示**してください。AI がコードの調査・修正・ビルド確認まで全自動でアシストします。

---

## 💰 API 利用料金 ＆ Google Cloud 課金について

本アプリで利用する API（Google Maps API ＆ Google AI Studio）は、**Google Cloud Platform** 上で提供されています。ご利用にあたり以下の点をご確認ください。

### 1. Google Maps API の利用 ＆ 課金について
- **Google Cloud の利用必須**: Google Maps API（Maps SDK for Android）を利用するためには、[Google Cloud Console](https://console.cloud.google.com/) でのアカウント作成および支払い登録（Cloud 課金アカウントの有効化）が必要となります。
- **課金の可能性**: Google Cloud には毎月約 $200 相当の無料枠（数万回分のリクエスト）が提供されているため一般的な使用では実質無料で収まりますが、**利用頻度やリクエスト数に応じてわずかに課金（従量課金）が発生する可能性があります**。

### 2. Google AI Studio (Gemini / Nano Banana) の料金について
- **初回お支払い登録（デポジット）**: Google AI Studio で API キーを発行して画像生成機能を利用する場合、初回に **約 2,000 円（$10~$15 前後）の支払い登録（Google Cloud 従量課金設定）** が必要となります。
- **1回あたりの画像変換コスト**: AI 画像変換 1 回あたりにかかる費用は、およそ **約 1 円程度** です。（※ご利用頻度に応じて従量課金されます）

---

## 🔑 API キーの取得と設定手順

本アプリを実行・ビルドするには、以下の **2つの API キー** が必要です。

### 1. Google Maps API キーの取得 (`MAPS_API_KEY`)
1. [Google Cloud Console](https://console.cloud.google.com/) にログインします。
2. 新規プロジェクトを作成（または既存プロジェクトを選択）し、**「Maps SDK for Android」** を有効化します。
3. **「認証情報」** 画面から API キーを発行します。

### 2. Google AI Studio API キーの取得 (`GEMINI_API_KEY`)
1. [Google AI Studio (aistudio.google.com)](https://aistudio.google.com/) にアクセスします。
2. **「Get API Key」** から API キーを発行します。（※従量課金の有効化・初回支払い登録が必要です）

---

## 📝 設定ファイル (`local.properties`) の編集

プロジェクトのルートディレクトリにある `local.properties` ファイルを開き（存在しない場合は新規作成）、取得した 2つの API キーを以下のように記述して保存します。

```properties
## local.properties (プロジェクトルート直下)

# Android SDK のパス (お使いの環境に合わせて指定)
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk

# Google Maps API キー (Maps SDK for Android 有効化済み)
MAPS_API_KEY=AIzaSyxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Google AI Studio (Gemini / Nano Banana) API キー
GEMINI_API_KEY=AIzaSyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
```

> ⚠️ **注意**: `local.properties` は API キー等の個人秘密情報を含むため、Git 等のバージョン管理システムにはコミットしないでください（`.gitignore` に含まれています）。

---

## 🚀 ビルド ＆ アプリの実行方法

### 方式 A: Android Studio から実行する場合（推奨）
1. **Android Studio** で本プロジェクトフォルダを開きます。
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
| **スポット自由ジャンプ・検索** | 右下のミニマップをタップ ➔ 地名検索または地図上タップでピン選択 ➔ `📍 この場所へ移動する` |

---

## 📄 ライセンス ＆ クレジット
- **Google Maps SDK for Android**: © Google LLC
- **Google AI Studio (Gemini Interactions API / Nano Banana)**: © Google LLC

---

## ⚠️ 免責事項 (Disclaimer)

- 本アプリケーションはオープンソース成果物として「現状のまま（As-Is）」提供されます。
- 本アプリの利用、および Google Maps API / Google AI Studio の API キー利用に伴って発生するいかなる費用・課金・損害・トラブルについても、開発者および制作者は一切の責任を負いかねます。API キーの管理・利用設定はご利用者ご自身の責任にて行なってください。
- センサー連動機能やストリートビュー機能をご利用の際は、周囲の安全に十分配慮し、歩行中や危険な場所でのスマホの注視・操作はお控えください。
