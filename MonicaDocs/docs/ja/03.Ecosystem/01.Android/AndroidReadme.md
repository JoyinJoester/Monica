---
title: Android 版の紹介
date: 2025-06-01 23:00:00
permalink: /ecosystem/AndroidReadme
---

# Monica for Android

> Monica のローカルパスワード保管庫 Android クライアント  
> Android 8.0+ · Jetpack Compose · MDBX / Bitwarden / KeePass · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-Android/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note 概要
Monica for Android は、Monica パスワード保管庫エコシステムのネイティブ Android クライアントです。<mark>ローカルファースト</mark> の設計思想を採用し、Android スマートフォン上で安全かつ制御可能で、復元しやすいパスワード管理体験の提供に注力しています。

主な特長: `ローカル Vault`、`Bitwarden 統合`、`KeePass 互換`、`AutoFill`、`TOTP`、`WebDAV`
:::

Monica for Android は、**Bitwarden** と **KeePass** を統合したローカルパスワード保管庫（Local Vault）です。ローカルストレージ優先を中核に、Android スマートフォンでアカウントやパスワード、2FA、セキュアノート、機密添付ファイルを一元管理でき、必要に応じて WebDAV によるバックアップ復元やクロスプラットフォームでのデータ同期も利用できます。

現在のプロジェクト方針は **ローカルファースト、Bitwarden 互換、KeePass 統合、MDBX の全面対応、Android 8.0+** を軸としており、ローカル暗号化 Vault、ネイティブ AutoFill、TOTP、生体認証、Passkey、WebDAV バックアップ復元、多種別エントリ管理を重点的に強化しています。

---

## どんな人に向いているか ✅

Monica for Android は、次のようなユーザーに適しています。

- **ローカルファースト**な方法でパスワードを管理したく、重要な認証情報をサードパーティのクラウドサービスに預けたくない方
- Bitwarden を使いつつ、KeePass（`.kdbx`）データも維持しており、単一の入口でまとめて管理したい方
- Android の日常利用の中で AutoFill、自動入力、生体認証によるロック解除、TOTP 管理を行いたい方
- 自前の WebDAV 基盤を使って、オフラインまたは低遅延でデバイス間バックアップと復元を実現したい方

## 現在の機能 🎯

### ローカル Vault

- ローカル暗号化 Vault の作成、オープン、ロックに対応
- ローカルファースト戦略を採用し、外部サービスへの依存を低減
- MDBX と従来型の暗号化データベース形式に対応

### 複数エコシステムの統合

- **Bitwarden 互換**: Bitwarden API 同期、インポート/エクスポート、アカウント連携に対応
- **KeePass 互換**: `.kdbx` ファイルのネイティブな読み書きとインポート移行に対応
- **MDBX ローカル形式**: Monica 独自のネスト型暗号化データベース形式で、バージョン履歴と競合検出をサポート

### 多種別エントリ管理

- ログイン項目、セキュアノート、銀行カード、身分証明書、TOTP などの基本 CRUD に対応
- 各エントリで添付ファイルを保存可能
- お気に入り、タグ、フォルダ分類に対応

### お気に入りと検索

- お気に入り優先ビューに対応
- タイトル、ドメイン、タグによる高速検索に対応
- 全文検索に対応

### TOTP と二段階認証

- `otpauth://` URI のインポートに対応
- QR コードのインポートに対応（カメラまたはギャラリー経由）
- 認証コードの生成とリアルタイム更新に対応

### Android ネイティブ統合

- **自動入力（AutoFill）**: システムレベルのパスワード自動入力機能を提供し、大半のアプリやブラウザに対応
- **生体認証**: Face ID / 指紋によるロック解除に対応し、BiometricPrompt を統合
- **Passkey / Credential Provider**: Android 14+ のパスキーに対応
- **バックグラウンドバックアップ**: WorkManager による定期 WebDAV 自動バックアップ

### WebDAV バックアップ復元

- アップロード、ダウンロード、完全性検証（SHA-256）に対応
- 復元前のプレビューとバージョン選択に対応
- 復元前検証に対応

## 現在の状況 ⚡

- プロジェクトは活発に開発・継続保守されています
- Android 8.0 - 15 を広くサポートしており、一部の新機能（Passkey など）は Android 14+ が必要です

## 開発情報 👨‍💻

### 技術アーキテクチャ

```text
UI 層 (Compose)
    ↓
ViewModel / ViewModel State
    ↓
Repository Pattern
    ↓
Room Database + Keystore + Biometric
    ↓
Bitwarden API / KeePass Library / MDBX FFI
    ↓
WebDAV / Encryption / Security
```

### コア技術スタック ⚙️

- **UI フレームワーク**: <mark>Jetpack Compose</mark> + Material 3 + Navigation Compose
- **データ層**: Room（SQLite ORM）+ DAO + Repository
- **状態管理**: ViewModel + Kotlin Flow + DataStore Preferences
- **依存性注入**: Koin（`MonicaApplication` でアプリを起動）
- **セキュリティ機能**: Android Keystore、EncryptedSharedPreferences、BiometricPrompt
- **バックグラウンドタスク**: WorkManager（`AutoBackupWorker` が WebDAV 自動バックアップを担当）
- **ネットワークとプロトコル**: Retrofit + OkHttp（Bitwarden API）、kotpass（KeePass）、sardine-android（WebDAV）
- **非同期フレームワーク**: Coroutines + Flow
- **スキャンと認識**: CameraX + ML Kit（QR コード）、Credentials API（Passkey）

### プロジェクトのレイヤー構成（コード構造） 📁

- `takagi/ru/monica/ui`: Compose ページと UI コンポーネント
- `takagi/ru/monica/data`: Room エンティティ、DAO、データベースマイグレーション
- `takagi/ru/monica/repository`: データアクセスのラッパーと統合処理
- `takagi/ru/monica/security`: 暗号化、鍵、認証
- `takagi/ru/monica/bitwarden`: Bitwarden API、暗号化、同期、ViewModel ロジック
- `takagi/ru/monica/autofill`: AutoFill サービスと関連フロー
- `takagi/ru/monica/passkey`: Android 14+ Credential Provider 統合
- `takagi/ru/monica/workers`: バックグラウンドタスク（WebDAV 自動バックアップなど）

### セキュリティモデル 🔐

- **暗号アルゴリズム**: AES-256-GCM（認証付き暗号）
- **鍵導出**: PBKDF2-HMAC-SHA256（高反復パラメータ）
- **ローカル保護**: マスターパスワードのハッシュと安全設定は Android Keystore が管理
- **ネットワーク境界**: Bitwarden API 同期、WebDAV バックアップ/復元、QR コード更新時にのみ使用

### ビルドと依存関係 📦

- **JDK**: 17+
- **Android Studio**: 最新安定版
- **コンパイル設定**: `compileSdk 35`、`targetSdk 34`、`minSdk 26`
- **ビルドツール**: AGP `8.6.0`、Kotlin `2.0.21`、Compose BOM `2026.03.00`
- **バージョン管理**: `gradle/libs.versions.toml` と `app/build.gradle` を参照

### クイックビルドコマンド

デバッグ APK をビルド:

```bash
./gradlew :app:assembleDebug
```

単体テストを実行:

```bash
./gradlew test
```

Android インストルメンテーションテストを実行（実機接続またはエミュレータ起動が必要）:

```bash
./gradlew connectedAndroidTest
```

---

## ロードマップ 🚦

### 安定版の重点項目

- MDBX ローカル Vault の中核機能
- Bitwarden 互換インポート/同期
- KeePass `.kdbx` の読み書き
- 多種別エントリ管理
- Android AutoFill 対応
- TOTP と QR コード
- WebDAV バックアップ復元

### 継続的に進める項目

- 生体認証体験の強化（Face / 指紋）
- Passkey の実機検証（Android 14+）
- オフライン検索性能の最適化
- MDBX への全面移行と互換性向上

### 今後の機能

- より多くのクラウド同期サービス（OneDrive、Google Drive など）
- ブラウザ拡張との連携
- iOS 版とのデータ同期
- 高度な暗号化とセキュリティ監査
- Widget とクイックアクセス

---

## 謝辞 🙏

Monica の設計、互換性対応、および一部機能の方向性は、以下の優れたオープンソースプロジェクトやソフトウェアから多くの着想と支援を得ています。

- [Keyguard](https://github.com/AChep/keyguard-app) - Android パスワードマネージャーの操作設計と体験の参考
- [Bitwarden](https://bitwarden.com/) - オープンソースのパスワード管理エコシステム、Vault モデル、同期機能の重要な参考
- [KeePass](https://keepass.info/) - ローカルパスワード保管庫という考え方と `.kdbx` エコシステム互換の重要な基盤
- [Stratum Auth](https://github.com/stratumauth/app) - 認証アプリの体験、アイコン素材、関連互換対応の参考

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-Android&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-Android&Date)

---

## ライセンス

Copyright (c) 2025 JoyinJoester

Monica for Android は [GNU General Public License v3.0](LICENSE) の下でオープンソースとして公開されています。

## サードパーティ製アイコン表記

- 本プロジェクトには [Stratum Auth app](https://github.com/stratumauth/app) のアイコン素材がローカル同梱されています（バージョン [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0)、ディレクトリ [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons)、GPL-3.0）
- ブランド名および Logo の商標権は、それぞれの権利者に帰属します
