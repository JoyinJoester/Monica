---
title: iOS 版の紹介
date: 2025-06-01 23:00:00
permalink: /ecosystem/iOSReadme
---

# Monica for iOS

> Monica のローカルファースト iOS パスワード保管庫クライアント  
> iOS 17+ · SwiftUI · MDBX Vault · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-iOS/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note 概要
Monica for iOS は、Monica パスワード保管庫エコシステムのネイティブ iOS クライアントです。<mark>ローカルファースト</mark> の設計思想を採用し、iPhone 上で安全かつ制御可能で、復元しやすいパスワード管理体験の提供に注力しています。

主な特長: `MDBX Vault`、`AutoFill`、`TOTP`、`WebDAV`
:::

Monica for iOS は、Monica パスワード保管庫エコシステムのネイティブ iOS クライアントであり、**ローカルファースト** の設計思想を採用し、iPhone 上で安全かつ制御可能で、復元しやすいパスワード管理体験の提供に注力しています。

現在のプロジェクト方針は **MDBX 優先、iOS 17+、iPhone 優先、Rust/Swift UniFFI ブリッジ** を軸としており、ローカル暗号化 Vault、ネイティブ AutoFill、TOTP、WebDAV バックアップ復元、さらに Android / MDBX データモデルと互換性のある多種別エントリ管理を重点的に進めています。

最初の公開版では、次の項目を優先して完成させる予定です。

- ローカル Vault の基本機能
- コアとなるエントリ管理
- WebDAV バックアップと復元
- できるだけ完全な AutoFill 対応

今後さらに次の機能を強化していきます。

- Passkey
- Bitwarden 互換
- 添付ファイル対応
- より多くのクラウド同期機能

---

## どんな人に向いているか

Monica for iOS は、次のようなユーザーに適しています。

- **ローカルファースト**な方法でパスワードを管理したく、重要な認証情報をサードパーティのサービスに預けたくない方
- すでに Monica Android や MDBX エコシステム内でパスワード、TOTP、セキュアノートなどを管理している方
- iOS ネイティブの **AutoFill、Face ID / Touch ID、ローカルバックアップ復元** などの機能を活用したい方
- iPhone 上でログイン項目、セキュアノート、TOTP、銀行カード、証明書メタデータなど、多種別エントリを一元管理したい方

---

## 現在の機能

### ローカル MDBX Vault

- ローカル暗号化 Vault の作成、オープン、ロックに対応
- ローカルファースト戦略を採用し、外部サービスへの依存を低減

### 多種別エントリ管理

- ログイン項目、セキュアノート、TOTP、銀行カード、証明書メタデータなどの基本 CRUD に対応
- Monica / MDBX のデータモデル思想と互換性あり

### お気に入りと検索

- お気に入り優先に対応
- お気に入りのみ表示に対応
- セッション内検索に対応
- ソフト削除からの復元に対応

### TOTP

- `otpauth://` URI のインポートに対応
- QR コードのインポートに対応
- 認証コードの生成と残り秒数の更新に対応

### iOS AutoFill

- Credential Provider Extension を通じて暗号化インデックスを読み取り
- システムへ入力可能な認証情報を返却
- パスワード自動入力シナリオにネイティブ対応

### セキュアロック解除

- Keychain、LocalAuthentication、MDBX `security_key` を組み合わせて保護
- マスターパスワードの平文は保存しない
- 可能な限り機密情報をローカルの安全境界内に限定

### WebDAV

- アップロード、ダウンロード、SHA-256 完全性検証に対応
- 復元プレビューに対応
- 復元前オープン検証に対応

### OneDrive

- MSAL と Microsoft Graph app-folder provider を統合済み
- 実アカウントと実ネットワークでの検証は継続中

### KeePass / KDBX

- 最新の KDBX3 / KDBX4 の主要な読み書きフロー互換を実装済み
- 今後さらに多くの実運用シナリオ検証を拡充予定

---

## 現在の状況

- プロジェクトは iOS クライアント開発および実機検証の段階にあります
- 署名なしシミュレータテストで `CODE_SIGNING_ALLOWED=NO` を使用した場合、App Group container が利用できないのは想定内の挙動です
- AutoFill QuickType 表示、Credential Provider、App Group、Keychain access group、TOTP カメラスキャンなどの機能は、署名済み実機環境で引き続き検証が必要です

---

## 開発情報

### ディレクトリ構成 🗂️

::: tip プロジェクト構成の概要
```text
.
├── Monica.xcodeproj/
├── App/
│   └── MonicaApp/
├── Extensions/
│   ├── MonicaAutoFillExtension/
│   ├── MonicaShareExtension/
│   └── MonicaWidgetExtension/
├── Tests/
│   └── MonicaTests/
├── Scripts/
│   ├── build-mdbx-xcframework.sh
│   └── generate-mdbx-swift-bindings.sh
├── Artifacts/
│   ├── MDBX/
│   └── MSAL/
├── Generated/
│   └── MDBXUniFFI/
└── SwiftPackages/
  ├── MSAL/
  ├── MonicaCore/
  ├── MonicaMDBX/
  ├── MonicaSecurity/
  ├── MonicaStorage/
  ├── MonicaSync/
  └── MonicaUI/
```
:::

### 技術スタック ⚙️

- **App 層**: <mark>SwiftUI</mark>、Observation、AuthenticationServices、LocalAuthentication、Keychain、WidgetKit
- **ローカル Vault**: MDBX (Rust) + UniFFI → `MonicaMDBX` (Swift)
- **コアロジック**: `MonicaCore`（TOTP、`otpauth://` 解析、安全なパスワード生成）
- **ストレージ層**: `MonicaStorage`（Vault repository、KDBX 互換、AutoFill インデックス）
- **セキュリティ層**: `MonicaSecurity`（Keychain / LocalAuthentication 境界）
- **同期層**: `MonicaSync`（WebDAV、OneDrive、Bitwarden アダプタ）

### MDBX UniFFI 🔧

Rust 側のブリッジ crate:

```text
mdbx/crates/mdbx-ios-ffi
```

Swift binding を生成:

```bash
Scripts/generate-mdbx-swift-bindings.sh
```

iOS XCFramework を生成:

```bash
Scripts/build-mdbx-xcframework.sh
```

UniFFI CLI が不足している場合:

```bash
cargo install uniffi --version 0.31.1 --locked --features cli
```

注意: `Scripts/build-mdbx-xcframework.sh` と Xcode build を並列実行しないでください。スクリプトが `MonicaMDBXGenerated.xcframework` を置き換えるため、Xcode 側で読み込みに失敗する可能性があります。

---

## 検証コマンド

::: tip テストとビルドをすばやく実行する方法

### Swift Package テスト

macOS のターミナルで次のコマンドを順に実行し、各 Swift パッケージをテストします。

```bash
cd SwiftPackages/MonicaCore && swift test
cd ../MonicaMDBX && swift test
cd ../MonicaStorage && swift test
cd ../MonicaSecurity && swift test
cd ../MonicaSync && swift test
cd ../MonicaUI && swift test
```

### iOS Simulator ビルド（署名なし）

まず利用可能なシミュレータを一覧表示します。

```bash
xcrun simctl list devices available
```

次に `<iPhone simulator UUID>` を置き換えて実行します。

```bash
xcodebuild \
  -project Monica.xcodeproj \
  -scheme Monica \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<iPhone simulator UUID>" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

### iOS Simulator XCTest

```bash
xcodebuild test \
  -project Monica.xcodeproj \
  -scheme Monica \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<iPhone simulator UUID>" \
  CODE_SIGNING_ALLOWED=NO
```

:::

---

## ロードマップ

### 初回リリースの重点項目

- MDBX ローカル Vault
- コアエントリ管理
- TOTP
- WebDAV
- iOS AutoFill

### 継続的に進める項目

- 署名済み実機での検証
- Keychain / LocalAuthentication
- App Group
- QuickType
- カメラスキャン

### 今後の機能

- Passkey
- Bitwarden 双方向同期
- 添付ファイル体験の強化
- より多くのクラウドサービス
- Widget / Live Activity など iOS ネイティブの入口

---

## 謝辞

Monica の設計、互換性対応、および一部機能の方向性は、以下の優れたオープンソースプロジェクトやソフトウェアから多くの着想と支援を得ています。

- [Bitwarden](https://bitwarden.com/) - オープンソースのパスワード管理エコシステム、Vault モデル、同期機能の重要な参考
- [KeePass](https://keepass.info/) - ローカルパスワード保管庫の思想と `.kdbx` エコシステム互換の重要な基盤
- [Keyguard](https://github.com/AChep/keyguard-app) - Android パスワードマネージャーの操作体験の参考
- [Stratum Auth](https://github.com/stratumauth/app) - 認証アプリの体験、アイコン素材、関連互換対応の参考

### ロードマップ（簡易版） 🚦

- **初回リリースの重点（必達）**: MDBX ローカル Vault、コアエントリ管理、TOTP、WebDAV、iOS AutoFill
- **直近の検証（進行中）**: 署名済み実機検証、Keychain / LocalAuthentication、App Group、QuickType、カメラスキャン
- **今後の計画（検討/スケジュール予定）**: Passkey、Bitwarden 双方向同期、添付ファイル体験、より多くのクラウドサービス、Widget / Live Activity

> 上記の項目は、そのまま issue や milestone に落とし込んで追跡・分担できます。

### 謝辞 🙏

Monica の設計、互換性対応、および一部機能の方向性は、以下の優れたオープンソースプロジェクトやソフトウェアから多くの着想と支援を得ています。

---
---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-iOS&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-iOS&Date)

---

## ライセンス

Copyright (c) 2025 JoyinJoester

Monica for iOS は [GNU General Public License v3.0](LICENSE) の下でオープンソースとして公開されています。

## サードパーティ製アイコン表記

- 本プロジェクトで使用しているサードパーティ製アイコン素材は [Stratum Auth app](https://github.com/stratumauth/app) に由来します（バージョン [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0)、ディレクトリ [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons)、GPL-3.0）
- ブランド名および Logo の商標権は、それぞれの権利者に帰属します
