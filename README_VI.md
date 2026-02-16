# Kho Mat Khau Cuc Bo Monica

[中文](README.md) | [English](README_EN.md) | [日本語](README_JA.md) | **Tiếng Việt**

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Browser-3DDC84)
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)
[![Website](https://img.shields.io/badge/Website-Monica-0A66C2)](https://joyinjoester.github.io/Monica/)

Monica la kho mat khau cuc bo tong hop **Bitwarden** va **KeePass**.
Ung dung uu tien luu tru local, giup quan ly mat khau, 2FA, ghi chu bao mat va tep dinh kem tren Android va trinh duyet.

Trang web: https://joyinjoester.github.io/Monica/

> Monica for Windows da duoc luu tru (archived). Ma nguon lich su: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)

---

## Thong Tin Cho Nguoi Dung

### Monica phu hop voi ai
- Nguoi can quan ly mat khau local-first, khong muon phu thuoc hoan toan vao cloud.
- Nguoi su dung ca du lieu Bitwarden va file KeePass (`.kdbx`).
- Nguoi dung Android hang ngay va can autofill tren trinh duyet.

### Gia tri ban nhan duoc
- Kho du lieu ma hoa local cho dang nhap, the, thong tin dinh danh, ghi chu va tep.
- Tich hop hai he sinh thai: Android co kha nang Bitwarden API/sync va doc-ghi KeePass (`.kdbx`).
- Dong bo/backup tuy chon qua ha tang WebDAV cua chinh ban.
- Quan ly TOTP tich hop trong cung mot ung dung.

### Cai dat nhanh

Android:
1. Tai APK moi nhat tai [Releases](https://github.com/JoyinJoester/Monica/releases).
2. Cai dat tren Android 8.0+ va khoi tao master password.

Tien ich trinh duyet (Chrome / Edge):
1. Build tu `Monica for Browser`.
2. Mo `chrome://extensions/` va bat Developer mode.
3. Chon Load unpacked va tro den thu muc `dist`.

---

## Trong Tam Android

### Tinh nang cot loi
- Vault local de luu tru thong tin dang nhap.
- Nhap/tich hop du lieu KeePass va Bitwarden.
- Tim kiem nhanh theo tieu de, domain va tag.
- Mo khoa bang sinh trac hoc cua he thong Android.
- Luu tru va sinh ma TOTP tap trung.

### Chi tiet trien khai
- UI: Jetpack Compose + Material 3 + Navigation Compose.
- Data layer: Room (`PasswordDatabase`) + DAO + Repository.
- Bat dong bo: Kotlin Coroutines + Flow.
- DI: Koin (khoi tao trong `MonicaApplication`).
- Bao mat: Android Keystore, EncryptedSharedPreferences, BiometricPrompt.
- Nen task: WorkManager (`AutoBackupWorker`) cho auto backup WebDAV.
- Giao thuc va tich hop: Retrofit + OkHttp (Bitwarden API), kotpass (KeePass), sardine-android (WebDAV).

### Mo hinh bao mat
- Ma hoa: AES-256-GCM (authenticated encryption).
- KDF: PBKDF2-HMAC-SHA256 (tham so lap cao).
- Bao ve local: hash master password va cai dat bao mat duoc quan ly tren thiet bi.
- Ranh gioi mang: ung dung co khai bao quyen mang, chu yeu de tich hop Bitwarden va dong bo/backup WebDAV.

---

## Ung Ho

Neu Monica huu ich cho ban, hay can nhac ung ho de duy tri phat trien va nang cap bao mat.

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica" width="320"/>
<br/>
<sub>Ho tro qua WeChat / Alipay</sub>
</div>

Nguon ung ho duoc uu tien cho:
- Tang cuong bao mat va kiem toan.
- Cai tien UX va do on dinh tren Android.
- Dong bo tinh nang da nen tang va bao tri tai lieu.

---

## Ghi Chu Cho Nha Phat Trien

### Cau truc ma nguon hien tai
- `takagi/ru/monica/ui`: man hinh va thanh phan Compose.
- `takagi/ru/monica/data`: entity Room, DAO, migration co so du lieu.
- `takagi/ru/monica/repository`: lop truy cap du lieu.
- `takagi/ru/monica/security`: ma hoa, quan ly khoa, logic xac thuc.
- `takagi/ru/monica/bitwarden`: API, crypto, mapper, sync, viewmodel.
- `takagi/ru/monica/autofill`: dich vu va luong autofill.
- `takagi/ru/monica/passkey`: Credential Provider cho Android 14+.
- `takagi/ru/monica/workers`: task nen nhu auto backup WebDAV.

### Thanh phan da duoc su dung (co the doi chieu trong repo)
- Android UI: Jetpack Compose, Material 3, Navigation Compose.
- Data va state: Room, DataStore Preferences, ViewModel.
- Bao mat: Android Keystore, EncryptedSharedPreferences, BiometricPrompt.
- Network va protocol: Retrofit, OkHttp, Kotlinx Serialization.
- Dong bo va he sinh thai: sardine-android (WebDAV), kotpass (KeePass), tich hop Bitwarden API.
- Bat dong bo va job: Coroutines, Flow, WorkManager.
- Tinh nang bo sung: CameraX + ML Kit (quet QR), Credentials API (Passkey).

### Build va dong gop
- Android Studio: phien ban stable moi nhat.
- JDK: 17+.
- Cau hinh Android: `minSdk 26`, `targetSdk 34` (xem `Monica for Android/app/build.gradle`).
- Cong nghe browser: React + TypeScript + Vite (xem `Monica for Browser/package.json`).
- Hoan nghenh dong gop qua Issue va PR.

---

## Giay Phep

Copyright (c) 2025 JoyinJoester

Monica duoc phat hanh theo [GNU General Public License v3.0](LICENSE).
