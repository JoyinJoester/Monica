---
title: Android Introduction
date: 2025-06-01 23:00:00
permalink: /ecosystem/AndroidReadme
---

# Monica for Android

> Monica’s local password vault client for Android  
> Android 8.0+ · Jetpack Compose · MDBX / Bitwarden / KeePass · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-Android/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note Abstract
Monica for Android is the native Android client in the Monica password vault ecosystem. It follows a <mark>local-first</mark> philosophy and focuses on delivering a secure, controllable, and recoverable password management experience on Android phones.

Key capabilities: `Local Vault`, `Bitwarden aggregation`, `KeePass compatibility`, `AutoFill`, `TOTP`, `WebDAV`.
:::

Monica for Android is a local password vault that brings together **Bitwarden** and **KeePass**. With local storage as its core principle, it helps you manage accounts, passwords, 2FA, private notes, and sensitive attachments from one place on Android, while also supporting optional WebDAV backup, restore, and cross-platform data sync.

The current product direction centers on **local-first, Bitwarden compatibility, KeePass aggregation, full MDBX adoption, and Android 8.0+**. Development is focused on local encrypted vaults, native AutoFill, TOTP, biometrics, Passkey, WebDAV backup and restore, and multi-type item management.

---

## Who Is It For? ✅

Monica for Android is a good fit for users who:

- Want a **local-first** password management solution and do not want core credentials hosted with third-party cloud services.
- Use Bitwarden while also maintaining KeePass (`.kdbx`) data and want one unified entry point.
- Need AutoFill, biometric unlock, and TOTP management in day-to-day Android usage.
- Want to use their own WebDAV infrastructure for offline-friendly or low-latency backup and restore across devices.

## Current Features 🎯

### Local Vault

- Create, open, and lock local encrypted vaults
- Follow a local-first strategy to reduce reliance on external services
- Support both MDBX and traditional encrypted database formats

### Multi-Ecosystem Aggregation

- **Bitwarden compatibility**: supports Bitwarden API sync, import/export, and account association
- **KeePass compatibility**: native read/write support for `.kdbx` files plus import and migration
- **MDBX local format**: Monica’s self-developed nested encrypted database format with version history and conflict detection

### Multi-Type Item Management

- Basic CRUD for logins, secure notes, bank cards, identity documents, TOTP, and more
- Attachment storage for each item
- Favorites, tags, and folder-based organization

### Favorites and Search

- Favorites-first views
- Fast lookup by title, domain, and tag
- Full-text search support

### TOTP and Secondary Verification

- Import via `otpauth://` URI
- QR-code import from camera or gallery
- Code generation with real-time refresh

### Native Android Integration

- **AutoFill**: system-level credential filling compatible with most apps and browsers
- **Biometrics**: Face ID / fingerprint unlock via `BiometricPrompt`
- **Passkey / Credential Provider**: Passkey support on Android 14+
- **Background backup**: scheduled WebDAV auto backup using `WorkManager`

### WebDAV Backup and Restore

- Upload, download, and integrity verification with SHA-256
- Restore preview and version selection
- Pre-restore validation

## Current Status ⚡

- The project is under active development and ongoing maintenance
- Broad device support from Android 8.0 through Android 15; some newer features such as Passkey require Android 14+

## Development Notes 👨‍💻

### Technical Architecture

```text
UI Layer (Compose)
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

### Core Tech Stack ⚙️

- **UI framework**: <mark>Jetpack Compose</mark> + Material 3 + Navigation Compose
- **Data layer**: Room (SQLite ORM) + DAO + Repository
- **State management**: ViewModel + Kotlin Flow + DataStore Preferences
- **Dependency injection**: Koin (application starts in `MonicaApplication`)
- **Security capabilities**: Android Keystore, EncryptedSharedPreferences, BiometricPrompt
- **Background jobs**: WorkManager (`AutoBackupWorker` for automated WebDAV backups)
- **Networking and protocols**: Retrofit + OkHttp (Bitwarden API), kotpass (KeePass), sardine-android (WebDAV)
- **Async framework**: Coroutines + Flow
- **Scanning and recognition**: CameraX + ML Kit (QR codes), Credentials API (Passkey)

### Project Layers (Code Structure) 📁

- `takagi/ru/monica/ui`: Compose pages and UI components
- `takagi/ru/monica/data`: Room entities, DAO, database migrations
- `takagi/ru/monica/repository`: data access encapsulation and aggregation
- `takagi/ru/monica/security`: encryption, keys, and authentication
- `takagi/ru/monica/bitwarden`: Bitwarden API, encryption, sync, and ViewModel logic
- `takagi/ru/monica/autofill`: AutoFill services and flows
- `takagi/ru/monica/passkey`: Android 14+ Credential Provider integration
- `takagi/ru/monica/workers`: background tasks such as automated WebDAV backup

### Security Model 🔐

- **Encryption algorithm**: AES-256-GCM (authenticated encryption)
- **Key derivation**: PBKDF2-HMAC-SHA256 with high iteration parameters
- **Local protection**: master password hash and security settings are managed through Android Keystore
- **Network boundary**: used only for Bitwarden API sync, WebDAV backup/restore, and QR-code updates

### Build and Dependencies 📦

- **JDK**: 17+
- **Android Studio**: latest stable version
- **Build config**: `compileSdk 35`, `targetSdk 34`, `minSdk 26`
- **Build tools**: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00`
- **Version management**: see `gradle/libs.versions.toml` and `app/build.gradle`

### Quick Build Commands

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

Run Android instrumentation tests (requires a connected device or emulator):

```bash
./gradlew connectedAndroidTest
```

---

## Roadmap 🚦

### Stable Release Priorities

- MDBX local Vault core capabilities
- Bitwarden-compatible import and sync
- KeePass `.kdbx` read/write support
- Multi-type item management
- Android AutoFill support
- TOTP and QR codes
- WebDAV backup and restore

### In Ongoing Development

- Deeper biometric experience improvements (Face / fingerprint)
- Passkey device validation (Android 14+)
- Offline search performance optimization
- Full MDBX migration and compatibility

### Future Capabilities

- More cloud sync services (OneDrive, Google Drive, etc.)
- Browser extension integration
- Data sync with the iOS version
- Advanced encryption and security auditing
- Widgets and quick access

---

## Acknowledgements 🙏

Monica’s design, compatibility work, and parts of its feature direction were inspired and supported by the following excellent open-source projects and software:

- [Keyguard](https://github.com/AChep/keyguard-app) - Reference for password manager interaction design and user experience on Android
- [Bitwarden](https://bitwarden.com/) - An important reference for open-source password management ecosystems, vault models, and sync capabilities
- [KeePass](https://keepass.info/) - A key foundation for the local vault philosophy and `.kdbx` ecosystem compatibility
- [Stratum Auth](https://github.com/stratumauth/app) - Reference for authenticator UX, icon assets, and related compatibility support

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-Android&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-Android&Date)

---

## License

Copyright (c) 2025 JoyinJoester

Monica for Android is open-sourced under the [GNU General Public License v3.0](LICENSE).

## Third-Party Icon Attribution

- This project bundles icon assets from the [Stratum Auth app](https://github.com/stratumauth/app) (version [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), directories [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0)
- Trademark rights for brand names and logos belong to their respective owners
