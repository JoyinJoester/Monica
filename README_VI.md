# Kho Mat Khau Cuc Bo Monica

<div align="center">

[中文](README.md) | [English](README_EN.md) | [日本語](README_JA.md) | **Tiếng Việt**

<img src="documentation/website/public/images/app_icon_android.svg" alt="Monica App Icon" width="112" />

<p><strong>Kho mat khau uu tien local, ket noi Bitwarden va KeePass</strong></p>
<p>Android / Browser · Local Vault · TOTP · WebDAV Backup</p>

[Trang web](https://joyinjoester.github.io/Monica/) · [Releases](https://github.com/JoyinJoester/Monica/releases) · [Tai lieu Android](Monica%20for%20Android/README.md) · [Tai lieu Browser](Monica%20for%20Browser/README.md)

<p>
	Lien ket ban be:
	<a href="https://linux.do" title="Linux.do">
		<img src="https://linux.do/logo-96.svg" alt="Linux.do" width="22" />
		Linux.do
	</a>
</p>

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
[![QQ Group](https://img.shields.io/badge/QQ%20Group-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

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

[![Afdian](https://img.shields.io/badge/Afdian-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)

</div>

Monica la kho mat khau cuc bo tong hop **Bitwarden** va **KeePass**.
Ung dung uu tien luu tru local, giup quan ly mat khau, 2FA, ghi chu bao mat va tep dinh kem tren Android va trinh duyet.

Trang web: https://joyinjoester.github.io/Monica/

> Monica for Windows da duoc luu tru (archived). Ma nguon lich su: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)
>
> Hien tai du an chu yeu do mot minh toi duy tri, nen thoi gian va nguon luc deu rat gioi han. Vi vay, Monica for Wear va Monica for Browser tam thoi chua the duoc cap nhat lien tuc. Trong giai doan nay, toi se tap trung uu tien cho Monica for Android, bao gom hoan thien tinh nang, cai thien trai nghiem va duy tri do on dinh. Cam on ban da thong cam va ung ho.

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

### Gioi han da biet
- Do han che tuong thich he thong, Monica for Android hien tai khong the tao passkey tren mot so thiet bi Xiaomi HyperOS.

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
- Cau hinh Android: `compileSdk 35`, `targetSdk 34`, `minSdk 26` (xem `Monica for Android/app/build.gradle`).
- Moc build Android: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00` (Material3 dong bo theo BOM).
- Nguon thong tin phien ban: `Monica for Android/gradle/libs.versions.toml` va `Monica for Android/app/build.gradle`.
- Cong nghe browser: React + TypeScript + Vite (xem `Monica for Browser/package.json`).
- Hoan nghenh dong gop qua Issue va PR.

---

## Loi Cam On

Thiet ke, kha nang tuong thich va mot so dinh huong tinh nang cua Monica da nhan duoc nhieu cam hung va ho tro tu cac du an ma nguon mo va phan mem xuat sac sau:

- [Keyguard](https://github.com/AChep/keyguard-app) - tai lieu tham khao cho thiet ke tuong tac va trai nghiem nguoi dung cua trinh quan ly mat khau Android.
- [Bitwarden](https://bitwarden.com/) - nguon tham khao quan trong cho he sinh thai quan ly mat khau ma nguon mo, mo hinh vault va kha nang dong bo.
- [KeePass](https://keepass.info/) - nen tang cho triet ly local vault va kha nang tuong thich voi he sinh thai `.kdbx`.
- [Stratum Auth](https://github.com/stratumauth/app) - tham khao ve trai nghiem authenticator, tai nguyen icon va cac ho tro tuong thich lien quan.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Monica&type=Date)](https://star-history.com/#JoyinJoester/Monica&Date)

---

## Giay Phep

Copyright (c) 2025 JoyinJoester

Monica duoc phat hanh theo [GNU General Public License v3.0](LICENSE).

## Ghi Chu Ve Bieu Tuong Ben Thu Ba

- Du an nay dong goi cuc bo cac tai nguyen icon tu [Stratum Auth app](https://github.com/stratumauth/app) (phien ban [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), thu muc [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0).
- Ten thuong hieu va logo thuoc quyen so huu cua cac chu so huu tuong ung.
