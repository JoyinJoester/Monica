# Monica Password Manager 🔐

[中文](README_ZH.md) | **English**

<div align="center">

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Material Design 3](https://img.shields.io/badge/Material%20Design%203-757575?style=for-the-badge&logo=material-design&logoColor=white)

**A secure, feature-rich password manager built with modern Android technologies**

</div>

---

## ✨ Features

### 🔑 Password Management
- **Secure Storage**: All passwords encrypted with AES-256
- **Easy Organization**: Categories, favorites, and search functionality
- **Password Generator**: Create strong, random passwords
- **Chrome Import**: Import passwords from Chrome CSV export
- **Quick Copy**: One-tap copy for username/password

### 🛡️ TOTP Two-Factor Authentication
- **QR Scanner**: Scan QR codes to add TOTP accounts
- **Manual Entry**: Support for manual secret key input
- **Real-time Codes**: Auto-refreshing verification codes
- **Progress Indicator**: Visual countdown for code expiration
- **Multi-Account**: Manage multiple 2FA accounts

### 📄 Secure Document Storage
- **Encrypted Storage**: Store sensitive documents securely
- **Image Support**: Save ID cards, licenses, certificates
- **Local Download**: Certificate images can be downloaded and saved locally
- **Rich Notes**: Add descriptions and tags
- **Quick View**: Fast preview and access

### 💳 Bank Card Management
- **Card Information**: Store card numbers, CVV, expiry dates
- **Multiple Banks**: Support for all major banks
- **Secure Display**: Masked card numbers for privacy
- **Quick Copy**: Easy access to card details

### 🔒 Security Features
- **Numeric PIN**: 6-digit numeric master password
- **Security Questions**: Password recovery via security questions
- **AES-256 Encryption**: Military-grade encryption for all data
- **Local Storage**: All data stored locally on your device
- **Screenshot Protection**: Prevent unauthorized screenshots
- **Auto-lock**: Automatic timeout protection

### 🌍 Internationalization
- **Multi-language**: English, 中文, Tiếng Việt
- **Auto-detection**: Follows system language settings
- **Easy Switching**: Change language anytime in settings

### 🎨 Modern UI/UX
- **Material Design 3**: Latest Material You design language
- **Dark Mode**: Beautiful dark theme support
- **Smooth Animations**: Polished transitions and interactions
- **Responsive Design**: Optimized for all screen sizes

---

## 📸 Screenshots

<div align="center">

| Login | Password List | TOTP Codes |
|:---:|:---:|:---:|
| ![Login](screenshots/login.png) | ![Passwords](screenshots/passwords.png) | ![TOTP](screenshots/totp.png) |

| Documents | Bank Cards | Settings |
|:---:|:---:|:---:|
| ![Documents](screenshots/documents.png) | ![Cards](screenshots/cards.png) | ![Settings](screenshots/settings.png) |

</div>

---

## 🚀 Installation

### Requirements
- **Android 8.0** (API 26) or higher
- **Minimum RAM**: 2GB
- **Storage**: ~50MB

### Download
1. Download the latest APK from [Releases](https://github.com/JoyinJoester/Monica/releases)
2. Enable "Install from Unknown Sources" in Android settings
3. Install the APK file
4. Launch Monica and set up your master password

### Build from Source
```bash
# Clone the repository
git clone https://github.com/JoyinJoester/Monica.git
cd Monica

# Build the project
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

---

## 📖 Usage Guide

### First Time Setup
1. **Create Master Password**: Set a 6-digit numeric PIN (remember it!)
2. **Security Questions**: Answer 3 security questions for password recovery
3. **Start Adding Items**: Begin storing your passwords and data

### Adding Passwords
1. Tap the **"+"** button on the Password screen
2. Enter website, username, and password
3. Optionally add notes and tags
4. Save and done!

### Setting up TOTP
1. Navigate to **TOTP** tab
2. Tap **"+"** button
3. **Scan QR Code** or enter secret key manually
4. Name the account and save
5. View real-time verification codes

### Importing from Chrome
1. Export passwords from Chrome as CSV
2. Go to **Settings** → **Import Data**
3. Select the CSV file
4. Review and confirm import
5. All passwords imported!

### Password Recovery
1. On login screen, tap **"Forgot Password?"**
2. Answer your security questions correctly
3. Set a new master password
4. Your data remains encrypted and safe

---

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (SQLite)
- **Encryption**: AES-256
- **Navigation**: Jetpack Navigation Compose
- **Dependency Injection**: Manual DI
- **Camera**: CameraX (QR scanning)
- **TOTP**: Custom implementation (RFC 6238)

### Key Libraries
```gradle
androidx.compose.ui:ui:1.5.4
androidx.room:room-runtime:2.6.1
androidx.camera:camera-camera2:1.3.1
androidx.security:security-crypto:1.1.0-alpha06
com.google.zxing:core:3.5.2
```

---

## 🔐 Security & Privacy

### Encryption
- **AES-256-GCM**: All sensitive data encrypted with AES-256 in GCM mode
- **Key Derivation**: Master password hashed with PBKDF2
- **Secure Storage**: Android Keystore for encryption keys

### Privacy
- ✅ **100% Offline**: No internet permission, all data stays local
- ✅ **No Analytics**: No tracking, no telemetry
- ✅ **No Ads**: Completely ad-free
- ✅ **Open Source**: Code is transparent and auditable
- ✅ **No Cloud**: Your data never leaves your device

### Best Practices
1. **Strong Master Password**: Use a unique 6-digit PIN
2. **Regular Backups**: Export your data regularly
3. **Security Questions**: Choose answers only you would know
4. **Keep Updated**: Install updates for security patches

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### Ways to Contribute
- 🐛 **Report Bugs**: Open an issue with detailed steps to reproduce
- 💡 **Suggest Features**: Share your ideas in discussions
- 🌍 **Translations**: Help translate to more languages
- 📝 **Documentation**: Improve README and guides
- 💻 **Code**: Submit pull requests for bug fixes or features

### Development Setup
1. Fork the repository
2. Clone your fork: `git clone https://github.com/YourUsername/Monica.git`
3. Create a branch: `git checkout -b feature/your-feature`
4. Make changes and test thoroughly
5. Commit: `git commit -m "Add your feature"`
6. Push: `git push origin feature/your-feature`
7. Open a Pull Request

### Code Standards
- Follow Kotlin coding conventions
- Use meaningful variable/function names
- Add comments for complex logic
- Test on multiple Android versions
- Ensure Material Design 3 compliance


---

## 📄 License

```
GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007

Copyright (C) 2025 JoyinJoester

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
```

---

## 💖 Support the Author

If you find Monica useful, please consider supporting the development:

<div align="center">

<img src="support_author.jpg" alt="Support Author" width="300"/>

**Scan QR code to support via WeChat/Alipay**

</div>

Your support helps me:
- 🚀 Develop new features
- 🐛 Fix bugs faster
- 📱 Support more platforms
- 🌍 Add more languages
- 💡 Maintain the project

---

## 📞 Contact & Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/JoyinJoester/Monica/issues)
- **Email**: lichaoran8@gmail.com
- **GitHub**: [@JoyinJoester](https://github.com/JoyinJoester)

---

## ⭐ Star History

If you like Monica, please give it a star! ⭐

It helps others discover this project and motivates me to keep improving it.

---

## 🙏 Acknowledgments

- **Material Design 3**: For the beautiful design system
- **Jetpack Compose**: For modern declarative UI
- **ZXing**: For QR code scanning
- **Open Source Community**: For inspiration and support

---

<div align="center">

**Made with ❤️ by JoyinJoester**

[⬆ Back to Top](#monica-password-manager-)

</div>
