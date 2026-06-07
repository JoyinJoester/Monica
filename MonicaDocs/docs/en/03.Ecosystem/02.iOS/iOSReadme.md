---
title: iOS Introduction
date: 2025-06-01 23:00:00
permalink: /ecosystem/iOSReadme
---

# Monica for iOS

> Monica’s local-first password vault client for iOS  
> iOS 17+ · SwiftUI · MDBX Vault · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-iOS/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note Abstract
Monica for iOS is the native iOS client in the Monica password vault ecosystem. It follows a <mark>local-first</mark> philosophy and focuses on delivering a secure, controllable, and recoverable password management experience on iPhone.

Key capabilities: `MDBX Vault`, `AutoFill`, `TOTP`, `WebDAV`.
:::

Monica for iOS is the native iOS client in the Monica password vault ecosystem. It follows a **local-first** design philosophy and focuses on providing a secure, controllable, and recoverable password management experience on iPhone.

The current product direction is built around **MDBX first, iOS 17+, iPhone first, and Rust/Swift UniFFI bridging**, with development focused on local encrypted vaults, native AutoFill, TOTP, WebDAV backup and restore, and multi-type item management compatible with Android / MDBX data models.

The first public release will prioritize:

- Foundational local Vault capabilities
- Core item management
- WebDAV backup and restore
- More complete AutoFill support

After that, the roadmap will continue with:

- Passkey
- Bitwarden compatibility
- Attachment support
- More cloud sync capabilities

---

## Who Is It For

Monica for iOS is a good fit for users who:

- Want a **local-first** password management solution and do not want core credentials entrusted to third-party services.
- Already manage passwords, TOTP, private notes, and related data in the Monica Android or MDBX ecosystem.
- Need native iOS capabilities such as **AutoFill, Face ID / Touch ID, and local backup/restore**.
- Want to manage multiple item types on iPhone in one place, including logins, secure notes, TOTP, bank cards, and identity document metadata.

---

## Current Features

### Local MDBX Vault

- Create, open, and lock local encrypted vaults
- Follow a local-first strategy to reduce dependence on external services

### Multi-Type Item Management

- Basic CRUD for logins, secure notes, TOTP, bank cards, identity document metadata, and more
- Compatible with Monica / MDBX data model design

### Favorites and Search

- Favorites-first support
- Favorites-only view
- In-session search
- Soft-delete recovery

### TOTP

- Import via `otpauth://` URI
- QR-code import
- Code generation with remaining-seconds refresh

### iOS AutoFill

- Read encrypted indexes through the Credential Provider Extension
- Return fillable credentials to the system
- Provide native support for password AutoFill scenarios

### Secure Unlock

- Combines Keychain, LocalAuthentication, and MDBX `security_key`
- Does not store the master password in plaintext
- Keeps sensitive information within the local security boundary whenever possible

### WebDAV

- Upload, download, and SHA-256 integrity verification
- Restore preview
- Open-before-restore validation

### OneDrive

- MSAL and the Microsoft Graph app-folder provider are already integrated
- Real-account and live-network validation is still in progress

### KeePass / KDBX

- Modern KDBX3 / KDBX4 primary read/write flows are already compatible
- More real-world scenario validation will continue later

---

## Current Status

- The project is still in active iOS client development and on-device validation.
- In unsigned simulator testing, App Group containers being unavailable when using `CODE_SIGNING_ALLOWED=NO` is expected behavior.
- AutoFill QuickType display, Credential Provider, App Group, Keychain access groups, TOTP camera scanning, and similar capabilities still need further validation in a signed physical-device environment.

---

## Development Notes

### Directory Structure 🗂️

::: tip Project Layout Overview
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

### Tech Stack ⚙️

- **App layer**: <mark>SwiftUI</mark>, Observation, AuthenticationServices, LocalAuthentication, Keychain, WidgetKit
- **Local Vault**: MDBX (Rust) + UniFFI → `MonicaMDBX` (Swift)
- **Core logic**: `MonicaCore` (TOTP, `otpauth://` parsing, secure password generation)
- **Storage layer**: `MonicaStorage` (vault repository, KDBX compatibility, AutoFill index)
- **Security layer**: `MonicaSecurity` (Keychain / LocalAuthentication boundary)
- **Sync layer**: `MonicaSync` (WebDAV, OneDrive, Bitwarden adapters)

### MDBX UniFFI 🔧

Rust-side bridge crate:

```text
mdbx/crates/mdbx-ios-ffi
```

Generate Swift bindings:

```bash
Scripts/generate-mdbx-swift-bindings.sh
```

Build the iOS XCFramework:

```bash
Scripts/build-mdbx-xcframework.sh
```

If the UniFFI CLI is missing:

```bash
cargo install uniffi --version 0.31.1 --locked --features cli
```

Note: do not run `Scripts/build-mdbx-xcframework.sh` in parallel with an Xcode build. The script replaces `MonicaMDBXGenerated.xcframework` and may cause Xcode reads to fail.

---

## Verification Commands

::: tip How to Quickly Run Tests and Builds

### Swift Package Tests

Run the following commands one by one in a macOS terminal to test each Swift package:

```bash
cd SwiftPackages/MonicaCore && swift test
cd ../MonicaMDBX && swift test
cd ../MonicaStorage && swift test
cd ../MonicaSecurity && swift test
cd ../MonicaSync && swift test
cd ../MonicaUI && swift test
```

### iOS Simulator Build (Unsigned)

First list available simulators:

```bash
xcrun simctl list devices available
```

Then replace `<iPhone simulator UUID>` and run:

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

## Roadmap

### First Release Priorities

- MDBX local Vault
- Core item management
- TOTP
- WebDAV
- iOS AutoFill

### In Ongoing Development

- Signed physical-device validation
- Keychain / LocalAuthentication
- App Group
- QuickType
- Camera scanning

### Future Capabilities

- Passkey
- Bitwarden two-way sync
- Attachment experience
- More cloud services
- Native iOS entry points such as Widget / Live Activity

---

## Acknowledgements

Monica’s design, compatibility work, and parts of its feature direction were inspired and supported by the following excellent open-source projects and software:

- [Bitwarden](https://bitwarden.com/) - An important reference for open-source password management ecosystems, vault models, and sync capabilities
- [KeePass](https://keepass.info/) - A key foundation for the local vault philosophy and `.kdbx` ecosystem compatibility
- [Keyguard](https://github.com/AChep/keyguard-app) - Reference for Android password manager interaction design and UX
- [Stratum Auth](https://github.com/stratumauth/app) - Reference for authenticator UX, icon assets, and related compatibility support

### Roadmap Summary 🚦

- **First-release must-haves**: MDBX local Vault, core item management, TOTP, WebDAV, iOS AutoFill
- **Near-term validation in progress**: signed physical-device validation, Keychain / LocalAuthentication, App Group, QuickType, camera scanning
- **Future plans (exploration / scheduling)**: Passkey, Bitwarden two-way sync, attachment experience, more cloud services, Widget / Live Activity

> These items can be turned directly into issues or milestones for easier tracking and assignment.

### Acknowledgements 🙏

Monica’s design, compatibility work, and parts of its feature direction were inspired and supported by the following excellent open-source projects and software:

---
---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-iOS&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-iOS&Date)

---

## License

Copyright (c) 2025 JoyinJoester

Monica for iOS is open-sourced under the [GNU General Public License v3.0](LICENSE).

## Third-Party Icon Attribution

- This project uses third-party icon assets from the [Stratum Auth app](https://github.com/stratumauth/app) (version [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), directories [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0)
- Trademark rights for brand names and logos belong to their respective owners
