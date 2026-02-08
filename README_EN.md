# Monica Local Password Vault

[中文](README.md) | **English** | [日本語](README_JA.md) | [Tiếng Việt](README_VI.md)

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Browser-3DDC84)
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)
[![Website](https://img.shields.io/badge/Website-Monica-0A66C2)](https://joyinjoester.github.io/Monica/)

Monica is a local password vault that aggregates **Bitwarden** and **KeePass**.
It is built around local-first storage and helps you manage passwords, 2FA, secure notes, and sensitive attachments across Android and browser clients.

Website: https://joyinjoester.github.io/Monica/

> Monica for Windows is archived. Historical code: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)

---

## User First

### Who Monica is for
- Users who want local-first password management instead of fully hosted cloud storage.
- Users who work with both Bitwarden data and KeePass (`.kdbx`) files.
- Users who need Android daily usage and browser autofill at the same time.

### What you get
- Encrypted local vault for logins, cards, identity data, notes, and attachments.
- Dual ecosystem integration: Android includes Bitwarden API/sync capability and KeePass (`.kdbx`) read/write support.
- Optional sync/backup through your own WebDAV infrastructure.
- Built-in TOTP management in the same app.

### Quick install

Android:
1. Download the latest APK from [Releases](https://github.com/JoyinJoester/Monica/releases).
2. Install on Android 8.0+ and initialize your master password.

Browser extension (Chrome / Edge):
1. Build from `Monica for Browser`.
2. Open `chrome://extensions/` and enable Developer mode.
3. Load unpacked and choose the `dist` folder.

---

## Android Focus

### Core capabilities
- Local vault for credential storage.
- Aggregated import/integration with KeePass and Bitwarden.
- Fast search by title, domain, and tags.
- Biometric unlock with Android system capability.
- Unified TOTP storage and generation.

### Implementation details
- UI: Jetpack Compose + Material 3 + Navigation Compose.
- Data: Room (`PasswordDatabase`) + DAO + Repository.
- Concurrency: Kotlin Coroutines + Flow.
- DI: Koin (initialized in `MonicaApplication`).
- Security: Android Keystore, EncryptedSharedPreferences, BiometricPrompt.
- Background tasks: WorkManager (`AutoBackupWorker`) for automatic WebDAV backup.
- Protocols and integrations: Retrofit + OkHttp (Bitwarden API), kotpass (KeePass), sardine-android (WebDAV).

### Security model
- Encryption: AES-256-GCM (authenticated encryption).
- KDF: PBKDF2-HMAC-SHA256 (high iteration parameters).
- Local protection: master password hash and security settings are managed locally.
- Network boundary: the app declares network permissions, mainly for Bitwarden integration and WebDAV sync/backup.

---

## Support

If Monica helps your workflow, consider supporting development and security improvements.

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica" width="320"/>
<br/>
<sub>Scan with WeChat or Alipay</sub>
</div>

Your support mainly funds:
- Security hardening and audits.
- Android UX and stability improvements.
- Cross-platform consistency and documentation maintenance.

---

## Developer Notes

### Project layers (current code layout)
- `takagi/ru/monica/ui`: Compose screens and components.
- `takagi/ru/monica/data`: Room entities, DAO, database migrations.
- `takagi/ru/monica/repository`: data access wrappers.
- `takagi/ru/monica/security`: encryption, key handling, authentication-related logic.
- `takagi/ru/monica/bitwarden`: API, crypto, mapper, sync, and viewmodel logic.
- `takagi/ru/monica/autofill`: Autofill services and flows.
- `takagi/ru/monica/passkey`: Android 14+ credential provider implementation.
- `takagi/ru/monica/workers`: background tasks such as automatic WebDAV backup.

### Mature components currently used (verifiable in repo)
- Android UI: Jetpack Compose, Material 3, Navigation Compose.
- Data and state: Room, DataStore Preferences, ViewModel.
- Security: Android Keystore, EncryptedSharedPreferences, BiometricPrompt.
- Network and protocol: Retrofit, OkHttp, Kotlinx Serialization.
- Sync and ecosystem: sardine-android (WebDAV), kotpass (KeePass), Bitwarden API integration.
- Async and jobs: Coroutines, Flow, WorkManager.
- Additional capabilities: CameraX + ML Kit (QR scan), Credentials API (Passkey).

### Build and contribution
- Android Studio: latest stable.
- JDK: 17+.
- Android config: `minSdk 26`, `targetSdk 34` (see `Monica for Android/app/build.gradle`).
- Browser tech stack: React + TypeScript + Vite (see `Monica for Browser/package.json`).
- Contributions via Issues and PRs are welcome.

---

## License

Copyright (c) 2025 JoyinJoester

Monica is released under [GNU General Public License v3.0](LICENSE).
