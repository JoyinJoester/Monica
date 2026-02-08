# Monica ローカルパスワード保管庫

[中文](README.md) | [English](README_EN.md) | **日本語** | [Tiếng Việt](README_VI.md)

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Browser-3DDC84)
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)
[![Website](https://img.shields.io/badge/Website-Monica-0A66C2)](https://joyinjoester.github.io/Monica/)

Monica は **Bitwarden** と **KeePass** を統合するローカルパスワード保管庫です。
ローカル優先の保存を中心に、Android とブラウザでパスワード、2FA、セキュアノート、添付ファイルを一元管理できます。

サイト: https://joyinjoester.github.io/Monica/

> Monica for Windows はアーカイブ済みです。過去コード: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)

---

## まずユーザー向け情報

### Monica が向いているユーザー
- クラウド依存ではなく、ローカル優先のパスワード管理を求めるユーザー。
- Bitwarden データと KeePass (`.kdbx`) の両方を扱うユーザー。
- Android を日常利用しつつ、ブラウザ自動入力も使いたいユーザー。

### できること
- ログイン情報、カード情報、個人情報、ノート、添付ファイルをローカル暗号化保管。
- Android で Bitwarden API/同期機能と KeePass (`.kdbx`) 読み書きを両対応。
- 自前 WebDAV 基盤による任意の同期・バックアップ。
- アプリ内での TOTP 管理とコード生成。

### クイックインストール

Android:
1. [Releases](https://github.com/JoyinJoester/Monica/releases) から最新 APK を取得。
2. Android 8.0+ にインストールし、マスターパスワードを初期設定。

ブラウザ拡張 (Chrome / Edge):
1. `Monica for Browser` をビルド。
2. `chrome://extensions/` でデベロッパーモードを有効化。
3. 「パッケージ化されていない拡張機能を読み込む」で `dist` を選択。

---

## Android 重点

### 主な機能
- ローカル Vault による資格情報保管。
- KeePass / Bitwarden との統合インポート。
- タイトル・ドメイン・タグでの高速検索。
- Android の生体認証によるロック解除。
- TOTP の一元保存と生成。

### 実装ポイント
- UI: Jetpack Compose + Material 3 + Navigation Compose。
- データ層: Room（`PasswordDatabase`）+ DAO + Repository。
- 非同期: Kotlin Coroutines + Flow。
- DI: Koin（`MonicaApplication` で初期化）。
- セキュリティ: Android Keystore、EncryptedSharedPreferences、BiometricPrompt。
- バックグラウンド処理: WorkManager（`AutoBackupWorker`）で WebDAV 自動バックアップ。
- プロトコル/連携: Retrofit + OkHttp（Bitwarden API）、kotpass（KeePass）、sardine-android（WebDAV）。

### セキュリティモデル
- 暗号化: AES-256-GCM（認証付き暗号）。
- KDF: PBKDF2-HMAC-SHA256（高反復パラメータ）。
- ローカル保護: マスターパスワードのハッシュと安全設定は端末内で管理。
- ネットワーク境界: アプリはネットワーク権限を宣言し、主に Bitwarden 連携と WebDAV 同期/バックアップに使用。

---

## サポート

Monica が役に立った場合は、継続開発とセキュリティ強化への支援をご検討ください。

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica" width="320"/>
<br/>
<sub>WeChat / Alipay で支援</sub>
</div>

支援金の主な用途:
- セキュリティ強化と監査。
- Android UX と安定性改善。
- クロスプラットフォーム整合性とドキュメント整備。

---

## 開発者向け情報

### プロジェクト層（現行コード）
- `takagi/ru/monica/ui`: Compose 画面とコンポーネント。
- `takagi/ru/monica/data`: Room エンティティ、DAO、DB マイグレーション。
- `takagi/ru/monica/repository`: データアクセスのラッパー。
- `takagi/ru/monica/security`: 暗号化、鍵管理、認証関連ロジック。
- `takagi/ru/monica/bitwarden`: API、暗号、マッパー、同期、ViewModel。
- `takagi/ru/monica/autofill`: 自動入力サービスとフロー。
- `takagi/ru/monica/passkey`: Android 14+ Credential Provider 実装。
- `takagi/ru/monica/workers`: WebDAV 自動バックアップ等のバックグラウンド処理。

### 現在使用中の主要コンポーネント（リポジトリで検証可能）
- Android UI: Jetpack Compose, Material 3, Navigation Compose。
- データ/状態: Room, DataStore Preferences, ViewModel。
- セキュリティ: Android Keystore, EncryptedSharedPreferences, BiometricPrompt。
- 通信/プロトコル: Retrofit, OkHttp, Kotlinx Serialization。
- 同期/エコシステム: sardine-android (WebDAV), kotpass (KeePass), Bitwarden API 連携。
- 非同期/ジョブ: Coroutines, Flow, WorkManager。
- 追加機能: CameraX + ML Kit（QR スキャン）, Credentials API（Passkey）。

### ビルドとコントリビューション
- Android Studio: 最新安定版。
- JDK: 17+。
- Android 設定: `minSdk 26`, `targetSdk 34`（`Monica for Android/app/build.gradle`）。
- ブラウザ技術スタック: React + TypeScript + Vite（`Monica for Browser/package.json`）。
- Issue / PR でのコントリビューション歓迎。

---

## ライセンス

Copyright (c) 2025 JoyinJoester

Monica は [GNU General Public License v3.0](LICENSE) で公開されています。
