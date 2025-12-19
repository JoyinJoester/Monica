# Monica Password Manager

[![License](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20Android-lightgrey.svg)]()
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)

**Monica** is an enterprise-grade, offline-first password management solution engineered for absolute privacy and sovereignty over your digital credentials. By eschewing cloud dependencies in favor of local-only encrypted storage, Monica ensures that your sensitive data remains exclusively in your possession.

Compatible with **Windows 11** (WinUI 3) and **Android** (Jetpack Compose).

---

## 🏛️ Architecture & Security

Monica is built upon a "Privacy by Design" philosophy, utilizing industry-standard cryptographic primitives to guarantee data confidentiality and integrity.

- **Zero-Knowledge Architecture**: Your master password never leaves your device and is never stored.
- **Encryption**: The vault is secured using **AES-256-GCM** authenticated encryption.
- **Key Derivation**: Master keys are derived using **PBKDF2-HMAC-SHA256** with high iteration counts to resist brute-force attacks.
- **Sovereignty**: Data is stored locally in an encrypted SQLite/Room database. Optional synchronization is achieved via your own **WebDAV** server, keeping you in control of the infrastructure.

---

## ⚡ Core Capabilities

### 🔐 Credential Management
*   **Encrypted Vault**: Securely store passwords, banking information, and private notes.
*   **TOTP Authenticator**: Integrated Time-based One-Time Password generator for seamless 2FA.
*   **Breach Detection**: Proactive security analysis using *k-Anonymity* to check against known data breaches without exposing your passwords.

### 🔄 Cross-Platform Synchronization
*   **WebDAV Sync**: Encrypted synchronization across devices using any WebDAV-compliant provider (Nextcloud, Synology, generic NAS).
*   **Unified Experience**: Feature parity between the modern Windows desktop application and the native Android mobile app.

### �️ Advanced Features
*   **Secure Document Storage**: Encrypted storage for attachments and sensitive files.
*   **Biometric Unlock**: Support for Windows Hello and Android Biometrics for quick access.
*   **Data Portability**: Full support for importing/exporting data, ensuring no vendor lock-in.

---

## 🛠️ Technical Specifications

### Windows Client
*   **Framework**: WinUI 3 (Windows App SDK)
*   **Runtime**: .NET 8
*   **Data Access**: Entity Framework Core

### Android Client
*   **UI Toolkit**: Jetpack Compose (Material Design 3)
*   **Language**: Kotlin
*   **Architecture**: MVVM / Clean Architecture

---

## � Installation

### Windows
1. Download the latest installer (`.exe`) from the [Releases](https://github.com/JoyinJoester/Monica/releases) page.
2. Execute the installer to deploy Monica to your system.

### Android
1. Download the latest APK from the [Releases](https://github.com/JoyinJoester/Monica/releases) page.
2. Install the application on your Android device (Android 8.0+ required).

---

## 🤝 Support the Development

Monica is an open-source project driven by community support. If this tool adds value to your digital security workflow, consider supporting its continued development.

<div align="center">
<img src="image/support_author.jpg" alt="Support" width="280"/>
<br/>
<sub>Scan via WeChat / Alipay</sub>
</div>

---

## ⚖️ License

Copyright © 2025 JoyinJoester.
Distributed under the **GNU General Public License v3.0**. See `LICENSE` for more information.
