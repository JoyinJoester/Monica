---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Monica Pass"
  text: "Secure. Local. Open Source."
  tagline: "Monica is an open-source local password manager. Manage passwords, 2FA, and secure notes with all data stored entirely on your local device."
  image:
    src: /themepng.png
    alt: Monica App Icon
  actions:
    - theme: brand
      text: Download for Android
      link: https://github.com/JoyinJoester/Monica/releases
    - theme: alt
      text: Browser Extension
      link: https://github.com/JoyinJoester/Monica/tree/main/Monica%20for%20Browser
    - theme: alt
      text: Documentation
      link: /en/reference/catalogue

features:
  - title: Password Manager
    details: Supports auto-fill and multi-field storage. Protected by AES-256 encryption, ensuring data only exists on your local device.
    icon: 🔑
  - title: 2FA Authenticator
    details: Built-in TOTP authenticator with QR code scanning. Supports automatic copying of the next code before expiration.
    icon: 🕒
  - title: Cards & Notes
    details: Securely store bank cards, ID photos, and private notes. Keep everything under your full control.
    icon: 💳
  - title: Material Design 3
    details: Follows Google Material Design 3 guidelines. Features dynamic color themes and dark mode with a clean, ad-free interface.
    icon: 🎨
  - title: Hardware-Level Keystore
    details: Keys are protected by the Android Keystore System (TEE) to provide a hardware-based security boundary for your encrypted data.
    icon: 🛡️
  - title: Zero-Knowledge Architecture
    details: Operates fully offline by default without requiring network permissions, ensuring that even developers cannot access your data.
    icon: 🚫
---