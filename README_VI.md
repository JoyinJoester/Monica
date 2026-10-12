# Kho Mat Khau Cuc Bo Monica

<div align="center">

[中文](README.md) | [English](README_EN.md) | [日本語](README_JA.md) | **Tiếng Việt** | [Русский](README_RU.md) | [黑羽川](彩蛋语言备份/README_Nya.md)

<img src="image/themepng.png" alt="Monica App Icon" width="500" />

<p><strong>Kho mat khau uu tien local, ket noi Bitwarden va KeePass</strong></p>
<p>Android · Local Vault · TOTP · WebDAV Backup</p>

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)
[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-Android/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)
[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/commits)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)
[![Telegram](https://img.shields.io/badge/Telegram-Monica%20Community-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+IZUDLL-vWOA1Y2U1)

[![Afdian](https://img.shields.io/badge/Afdian-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)
[![PayPal](https://img.shields.io/badge/PayPal-Support%20Monica-FFD140?style=flat-square&logo=paypal&logoColor=00457C)](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW)
[![Liberapay · EUR](https://img.shields.io/badge/Liberapay-EUR-F6C915?style=flat-square&logo=liberapay&logoColor=black)](https://liberapay.com/JoyinJoester)
<br>
<a href="https://trendshift.io/repositories/27059" target="_blank"><img src="https://trendshift.io/api/badge/repositories/27059" alt="JoyinJoester%2FMonica | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>

</div>

Monica la kho mat khau cuc bo tong hop **Bitwarden** va **KeePass**.
Ung dung uu tien luu tru local, giup quan ly mat khau, 2FA, ghi chu bao mat va tep dinh kem tren thiet bi Android.

Trang web: https://monica-pass.github.io/MonicaDocs/

---

## Thong Tin Cho Nguoi Dung

### Monica phu hop voi ai
- Nguoi can quan ly mat khau local-first, khong muon phu thuoc hoan toan vao cloud.
- Nguoi su dung ca du lieu Bitwarden va file KeePass (`.kdbx`).
- Nguoi can autofill trong ung dung Android va trinh duyet di dong.

### Gia tri ban nhan duoc
- Kho du lieu ma hoa local cho dang nhap, the, thong tin dinh danh, ghi chu va tep.
- Tich hop hai he sinh thai: Android co kha nang Bitwarden API/sync va doc-ghi KeePass (`.kdbx`).
- Dong bo/backup tuy chon qua ha tang WebDAV cua chinh ban.
- Quan ly TOTP tich hop trong cung mot ung dung.

### Dinh dang co so du lieu local MDBX
MDBX la dinh dang vault ma hoa, uu tien local dang duoc Monica phat trien. No khong chi la mot bang mat khau; MDBX duoc thiet ke xoay quanh thu muc long nhau, tep dinh kem, lich su commit, phat hien xung dot, chuoi xoa tombstone, khoi phuc snapshot va cac che do bao mat Tiga.

Neu ban muon tich hop MDBX vao client khac, hay bat dau voi [MDBX workspace README](mdbx/README.en.md) va [MDBX client integration guide](mdbx/CLIENT_INTEGRATION_GUIDE.md). Dac ta dinh dang day du nam trong [mdbx/docs](mdbx/docs/README.md).

### Cai dat nhanh

1. Tai APK moi nhat tai [Releases](https://github.com/Monica-Pass/Monica-for-Android/releases).
2. Cai dat tren Android 8.0+ va khoi tao master password.

### Gioi han da biet
- Do han che tuong thich he thong, Monica for Android hien tai khong the tao passkey tren mot so thiet bi Xiaomi HyperOS. Co the thu module [HyperMonica](https://github.com/Wuming155/HyperMonica) do 酷 U cung cap.

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

<br/>

<p align="center">
  <a href="https://www.paypal.com/ncp/payment/BHSYWK73CA8FW">
    <img src="https://img.shields.io/badge/PayPal-Support%20Monica-FFD140?style=for-the-badge&amp;logo=paypal&amp;logoColor=003087" alt="Hỗ trợ Monica qua PayPal" />
  </a>
  <a href="https://liberapay.com/JoyinJoester">
    <img src="https://img.shields.io/badge/Liberapay-EUR-F6C915?style=for-the-badge&amp;logo=liberapay&amp;logoColor=black" alt="Liberapay · EUR (€)" />
  </a>
</p>

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
- `mdbx`: Rust workspace va tai lieu tich hop client cho dinh dang co so du lieu local Monica MDBX.

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
- Hoan nghenh dong gop qua Issue va PR.

---

## Loi Cam On

Thiet ke, kha nang tuong thich va mot so dinh huong tinh nang cua Monica da nhan duoc nhieu cam hung va ho tro tu cac du an ma nguon mo va phan mem xuat sac sau:

- [Keyguard](https://github.com/AChep/keyguard-app) - tai lieu tham khao cho thiet ke tuong tac va trai nghiem nguoi dung cua trinh quan ly mat khau Android.
- [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer) - tham khao cho kieu thanh cuon kho mat khau tren Android.
- [Bitwarden](https://bitwarden.com/) - nguon tham khao quan trong cho he sinh thai quan ly mat khau ma nguon mo, mo hinh vault va kha nang dong bo.
- [KeePass](https://keepass.info/) - nen tang cho triet ly local vault va kha nang tuong thich voi he sinh thai `.kdbx`.
- [Stratum Auth](https://github.com/stratumauth/app) - tham khao ve trai nghiem authenticator, tai nguyen icon va cac ho tro tuong thich lien quan.
- [Steam Desktop Authenticator](https://github.com/Jessecar96/SteamDesktopAuthenticator) - tham khao ve dinh dang Steam maFile, Steam Guard va kha nang tuong thich xac nhan giao dich.
- [steamguard-cli](https://github.com/dyc3/steamguard-cli) - tham khao ve dang nhap Steam Guard, chuyen authenticator va trien khai giao thuc xac nhan.
- [AnotherVaporAuth](https://github.com/freefrank/AnotherVaporAuth) - tham khao ve Steam mobile authenticator, phe duyet dang nhap va trai nghiem xac nhan.

---

## Lien Ket Ban Be

- [Linux.do](https://linux.do)
- [TinadecOffice](https://github.com/Tinadec/TinadecOffice)

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-Android&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-Android&Date)

---

## Dong Gop Vien

![Dong Gop Vien](.github/assets/contributor-flag.svg)

---

## Cong Dong Va Ho Tro

- Nhom Telegram: [Tham gia cong dong Monica](https://t.me/+IZUDLL-vWOA1Y2U1)

---

## Giay Phep

Copyright (c) 2025 JoyinJoester

Monica duoc phat hanh theo [GNU General Public License v3.0](LICENSE).

## Ghi Chu Ve Bieu Tuong Ben Thu Ba

- Du an nay dong goi cuc bo cac tai nguyen icon tu [Stratum Auth app](https://github.com/stratumauth/app) (phien ban [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), thu muc [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0).
- Nguon icon the ngan hang/thanh toan: thu muc cuc bo [SVG Credit Card & Payment Icons](svg-credit-card-payment-icons-main) (Apache-2.0).
- Ten thuong hieu va logo thuoc quyen so huu cua cac chu so huu tuong ung.

## F-Droid contact

For F-Droid metadata or packaging issues, contact: joyin8888@foxmail.com

