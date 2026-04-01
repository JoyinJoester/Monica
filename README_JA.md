# Monica ローカルパスワード保管庫

<div align="center">

[中文](README.md) | [English](README_EN.md) | **日本語** | [Tiếng Việt](README_VI.md)

<img src="documentation/website/public/images/app_icon.webp" alt="Monica App Icon" width="112" />

<p><strong>Bitwarden と KeePass をつなぐローカル優先のパスワード保管庫</strong></p>
<p>Android / Browser · Local Vault · TOTP · WebDAV Backup</p>

[サイト](https://joyinjoester.github.io/Monica/) · [Releases](https://github.com/JoyinJoester/Monica/releases) · [Android ドキュメント](Monica%20for%20Android/README.md) · [Browser ドキュメント](Monica%20for%20Browser/README.md)

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Monica/total?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![License](https://img.shields.io/github/license/JoyinJoester/Monica?style=flat-square)](LICENSE)
[![Website](https://img.shields.io/badge/Website-Monica-0A66C2?style=flat-square)](https://joyinjoester.github.io/Monica/)
[![Last Commit](https://img.shields.io/github/last-commit/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/commits)
[![Commit Activity](https://img.shields.io/github/commit-activity/m/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/graphs/commit-activity)

[![Stars](https://img.shields.io/github/stars/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/stargazers)
[![Forks](https://img.shields.io/github/forks/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/network/members)
[![Issues](https://img.shields.io/github/issues/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/issues)
[![Pull Requests](https://img.shields.io/github/issues-pr/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/pulls)
[![QQ グループ](https://img.shields.io/badge/QQ%20Group-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Browser-3DDC84?style=flat-square)
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success?style=flat-square)
![Local First](https://img.shields.io/badge/Architecture-Local%20First-2F855A?style=flat-square)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9.3-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
![Repo Size](https://img.shields.io/github/repo-size/JoyinJoester/Monica?style=flat-square)
![Language Count](https://img.shields.io/github/languages/count/JoyinJoester/Monica?style=flat-square)

[![Android CI](https://github.com/JoyinJoester/Monica/actions/workflows/Android.yml/badge.svg)](https://github.com/JoyinJoester/Monica/actions/workflows/Android.yml)
[![Browser CI](https://github.com/JoyinJoester/Monica/actions/workflows/Browser-CI.yml/badge.svg)](https://github.com/JoyinJoester/Monica/actions/workflows/Browser-CI.yml)
[![Website Deploy](https://github.com/JoyinJoester/Monica/actions/workflows/deploy-website.yml/badge.svg)](https://github.com/JoyinJoester/Monica/actions/workflows/deploy-website.yml)
[![CodeQL](https://github.com/JoyinJoester/Monica/actions/workflows/CodeQL.yml/badge.svg)](https://github.com/JoyinJoester/Monica/actions/workflows/CodeQL.yml)
[![Top Language](https://img.shields.io/github/languages/top/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica)
[![State-of-the-art Shitcode](https://img.shields.io/static/v1?label=State-of-the-art&message=Shitcode&color=7B5804&style=flat-square)](https://github.com/trekhleb/state-of-the-art-shitcode)

[![愛発電](https://img.shields.io/badge/愛発電-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)

</div>

Monica は **Bitwarden** と **KeePass** を統合するローカルパスワード保管庫です。
ローカル優先の保存を中心に、Android とブラウザでパスワード、2FA、セキュアノート、添付ファイルを一元管理できます。

サイト: https://joyinjoester.github.io/Monica/

> Monica for Windows はアーカイブ済みです。過去コード: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)
>
> 現在このプロジェクトは主に私一人で保守しているため、使える時間とリソースに限りがあります。そのため、Monica for Wear と Monica for Browser は当面のあいだ継続的な更新が難しい状況です。現段階では Monica for Android の機能改善、使い勝手の向上、安定性の維持に注力していきます。ご理解とご支援に感謝します。

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

### 既知の制限
- システム互換性の都合により、Monica for Android は一部の Xiaomi HyperOS 端末でパスキーを作成できません。

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
- Android 設定: `compileSdk 35`, `targetSdk 34`, `minSdk 26`（`Monica for Android/app/build.gradle`）。
- Android ビルド基準: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00`（Material3 は BOM に追従）。
- バージョンの一次情報: `Monica for Android/gradle/libs.versions.toml` と `Monica for Android/app/build.gradle`。
- ブラウザ技術スタック: React + TypeScript + Vite（`Monica for Browser/package.json`）。
- Issue / PR でのコントリビューション歓迎。

---

## 謝辞

Monica の設計、互換性対応、そして一部の機能方針は、以下の優れたオープンソースプロジェクトやソフトウェアから多くの着想と支援を受けています。

- [Keyguard](https://github.com/AChep/keyguard-app) - Android 向けパスワードマネージャーの操作設計と UX の参考。
- [Bitwarden](https://bitwarden.com/) - オープンソースのパスワード管理エコシステム、Vault モデル、同期機能における重要な参考。
- [KeePass](https://keepass.info/) - ローカル Vault という思想と `.kdbx` エコシステム互換性の基盤。
- [Stratum Auth](https://github.com/stratumauth/app) - 認証アプリ体験、アイコン資産、関連互換対応の参考。

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Monica&type=Date)](https://star-history.com/#JoyinJoester/Monica&Date)

---

## ライセンス

Copyright (c) 2025 JoyinJoester

Monica は [GNU General Public License v3.0](LICENSE) で公開されています。

## サードパーティアイコン表記

- 本プロジェクトには [Stratum Auth app](https://github.com/stratumauth/app) のアイコン資産をローカル同梱しています（バージョン [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0)、ディレクトリ [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons)、GPL-3.0）。
- ブランド名およびロゴの商標権は各権利者に帰属します。
