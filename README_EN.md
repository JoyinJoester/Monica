# Monica Local Password Vault

<div align="center">

[中文](README.md) | **English** | [日本語](README_JA.md) | [Tiếng Việt](README_VI.md) | [Русский](README_RU.md) | [黑羽川](readme_Nya.md)

<img src="image/themepng.png" alt="Monica App Icon" width="500" />

<p><strong>A local-first password vault that bridges Bitwarden and KeePass</strong></p>
<p>Android / Browser · Local Vault · TOTP · WebDAV Backup</p>

<p>
	Friend Link:
	<a href="https://linux.do" title="Linux.do">
		<img src="https://www.google.com/s2/favicons?domain=linux.do&sz=64" alt="Linux.do" width="22" />
		Linux.do
	</a>
</p>

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Monica/total?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![Last Commit](https://img.shields.io/github/last-commit/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/commits)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

[![Afdian](https://img.shields.io/badge/Afdian-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)
<br>
<a href="https://trendshift.io/repositories/27059" target="_blank"><img src="https://trendshift.io/api/badge/repositories/27059" alt="JoyinJoester%2FMonica | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>

</div>

Monica is a local password vault that aggregates **Bitwarden** and **KeePass**.
It is built around local-first storage and helps you manage passwords, 2FA, secure notes, and sensitive attachments across Android and browser clients.

This project is AI-assisted. Please build a habit of backing up regularly to avoid data loss. Data privacy is priceless, so do not keep all of your data in a single app.

Website: https://joyinjoester.github.io/Monica/

> Monica for Windows is archived. Historical code: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)
>
> Monica for Browser is archived. The new Monica Extension is currently being rewritten and under active development — stay tuned.
>
> Since the project is currently maintained mostly by me alone, both time and bandwidth are limited. Because of that, Monica for Wear cannot be updated continuously for the time being. My current focus will stay on improving features, experience, and stability for Monica for Android. Thanks for your understanding and support.

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

### Known limitation
- Due to system compatibility constraints, Monica for Android currently cannot create passkeys on some Xiaomi HyperOS devices.

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
- Android config: `compileSdk 35`, `targetSdk 34`, `minSdk 26` (see `Monica for Android/app/build.gradle`).
- Android build baseline: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00` (Material3 aligned by BOM).
- Version source of truth: `Monica for Android/gradle/libs.versions.toml` and `Monica for Android/app/build.gradle`.
- Browser tech stack: React + TypeScript + Vite (see `Monica for Browser/package.json`).
- Contributions via Issues and PRs are welcome.

---

## Acknowledgments

Monica's design, compatibility work, and several feature directions have been inspired and supported by the following excellent open-source projects and software:

- [Keyguard](https://github.com/AChep/keyguard-app) - reference for Android password manager interaction design and UX.
- [Bitwarden](https://bitwarden.com/) - an important reference for the open-source password management ecosystem, vault model, and sync capabilities.
- [KeePass](https://keepass.info/) - a foundational influence for the local vault philosophy and `.kdbx` ecosystem compatibility.
- [Stratum Auth](https://github.com/stratumauth/app) - reference for authenticator experience, icon resources, and related compatibility support.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Monica&type=Date)](https://star-history.com/#JoyinJoester/Monica&Date)

---

## License

Copyright (c) 2025 JoyinJoester

Monica is released under [GNU General Public License v3.0](LICENSE).

## Third-Party Icon Attribution

- This project locally bundles icon assets from the [Stratum Auth app](https://github.com/stratumauth/app) (version [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), directories [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0).
- Brand names and logos remain the property of their respective owners.
