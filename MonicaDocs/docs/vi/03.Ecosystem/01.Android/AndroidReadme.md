---
title: Giới thiệu phiên bản Android
date: 2025-06-01 23:00:00
permalink: /ecosystem/AndroidReadme
---

# Monica for Android

> Ứng dụng khách Android cho kho mật khẩu cục bộ của Monica  
> Android 8.0+ · Jetpack Compose · MDBX / Bitwarden / KeePass · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-Android/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note Tóm tắt
Monica for Android là ứng dụng khách Android gốc trong hệ sinh thái kho mật khẩu Monica. Ứng dụng theo triết lý <mark>ưu tiên cục bộ</mark>, tập trung cung cấp trải nghiệm quản lý mật khẩu an toàn, có thể kiểm soát và có thể khôi phục trên điện thoại Android.

Năng lực chính: `Local Vault`, `tích hợp Bitwarden`, `tương thích KeePass`, `AutoFill`, `TOTP`, `WebDAV`.
:::

Monica for Android là kho mật khẩu cục bộ kết hợp **Bitwarden** và **KeePass**. Với lưu trữ cục bộ làm trọng tâm, ứng dụng giúp bạn quản lý tài khoản, mật khẩu, 2FA, ghi chú riêng tư và tệp đính kèm nhạy cảm trên Android, đồng thời hỗ trợ sao lưu, khôi phục và đồng bộ dữ liệu đa nền tảng qua WebDAV khi cần.

Hướng phát triển hiện tại xoay quanh **ưu tiên cục bộ, tương thích Bitwarden, tích hợp KeePass, áp dụng đầy đủ MDBX và Android 8.0+**. Trọng tâm là kho mã hóa cục bộ, AutoFill gốc, TOTP, sinh trắc học, Passkey, sao lưu/khôi phục WebDAV và quản lý nhiều loại mục.

---

## Phù hợp với ai ✅

Monica for Android phù hợp với người dùng:

- Muốn quản lý mật khẩu theo mô hình **ưu tiên cục bộ** và không muốn gửi dữ liệu cốt lõi cho dịch vụ đám mây bên thứ ba.
- Đang dùng Bitwarden nhưng vẫn duy trì dữ liệu KeePass (`.kdbx`) và cần một điểm quản lý thống nhất.
- Cần AutoFill, mở khóa sinh trắc học và quản lý TOTP trong sử dụng Android hằng ngày.
- Muốn dùng hạ tầng WebDAV tự quản để sao lưu và khôi phục dữ liệu giữa nhiều thiết bị, kể cả khi ngoại tuyến hoặc độ trễ thấp.

## Tính năng hiện tại 🎯

### Local Vault

- Tạo, mở và khóa kho mã hóa cục bộ
- Theo chiến lược ưu tiên cục bộ để giảm phụ thuộc vào dịch vụ bên ngoài
- Hỗ trợ MDBX và các định dạng cơ sở dữ liệu mã hóa truyền thống

### Tích hợp nhiều hệ sinh thái

- **Tương thích Bitwarden**: hỗ trợ đồng bộ API Bitwarden, nhập/xuất và liên kết tài khoản
- **Tương thích KeePass**: hỗ trợ đọc/ghi tệp `.kdbx` gốc, nhập và di trú dữ liệu
- **Định dạng MDBX cục bộ**: định dạng cơ sở dữ liệu mã hóa lồng nhau do Monica phát triển, hỗ trợ lịch sử phiên bản và phát hiện xung đột

### Quản lý nhiều loại mục

- CRUD cơ bản cho mục đăng nhập, ghi chú an toàn, thẻ ngân hàng, giấy tờ định danh, TOTP và các loại khác
- Mỗi mục có thể lưu tệp đính kèm
- Hỗ trợ yêu thích, nhãn và phân loại bằng thư mục

### Yêu thích và tìm kiếm

- Chế độ xem ưu tiên mục yêu thích
- Tra cứu nhanh theo tiêu đề, tên miền và nhãn
- Hỗ trợ tìm kiếm toàn văn

### TOTP và xác minh hai bước

- Nhập qua URI `otpauth://`
- Nhập mã QR bằng camera hoặc thư viện ảnh
- Tạo mã xác minh và làm mới theo thời gian thực

### Tích hợp gốc Android

- **AutoFill**: điền thông tin đăng nhập cấp hệ thống, tương thích với phần lớn ứng dụng và trình duyệt
- **Sinh trắc học**: Face ID / vân tay qua `BiometricPrompt`
- **Passkey / Credential Provider**: hỗ trợ Passkey trên Android 14+
- **Sao lưu nền**: tự động sao lưu WebDAV định kỳ bằng `WorkManager`

### Sao lưu và khôi phục WebDAV

- Hỗ trợ tải lên, tải xuống và kiểm tra toàn vẹn SHA-256
- Xem trước bản khôi phục và chọn phiên bản
- Kiểm tra trước khi khôi phục

## Trạng thái hiện tại ⚡

- Dự án đang được phát triển tích cực và bảo trì liên tục
- Hỗ trợ rộng rãi thiết bị Android 8.0 đến Android 15; một số tính năng mới như Passkey cần Android 14+

## Thông tin phát triển 👨‍💻

### Kiến trúc kỹ thuật

```text
UI Layer (Compose)
    →
ViewModel / ViewModel State
    →
Repository Pattern
    →
Room Database + Keystore + Biometric
    →
Bitwarden API / KeePass Library / MDBX FFI
    →
WebDAV / Encryption / Security
```

### Công nghệ cốt lõi ⚙️

- **Khung UI**: <mark>Jetpack Compose</mark> + Material 3 + Navigation Compose
- **Lớp dữ liệu**: Room (SQLite ORM) + DAO + Repository
- **Quản lý trạng thái**: ViewModel + Kotlin Flow + DataStore Preferences
- **Dependency injection**: Koin (khởi động trong `MonicaApplication`)
- **Bảo mật**: Android Keystore, EncryptedSharedPreferences, BiometricPrompt
- **Tác vụ nền**: WorkManager (`AutoBackupWorker` dùng cho sao lưu WebDAV tự động)
- **Mạng và giao thức**: Retrofit + OkHttp (Bitwarden API), kotpass (KeePass), sardine-android (WebDAV)
- **Bất đồng bộ**: Coroutines + Flow
- **Quét và nhận dạng**: CameraX + ML Kit (mã QR), Credentials API (Passkey)

### Phân lớp dự án 📁

- `takagi/ru/monica/ui`: trang Compose và thành phần UI
- `takagi/ru/monica/data`: entity Room, DAO, di trú cơ sở dữ liệu
- `takagi/ru/monica/repository`: đóng gói truy cập dữ liệu và tích hợp
- `takagi/ru/monica/security`: mã hóa, khóa và xác thực
- `takagi/ru/monica/bitwarden`: API Bitwarden, mã hóa, đồng bộ và logic ViewModel
- `takagi/ru/monica/autofill`: dịch vụ và luồng AutoFill
- `takagi/ru/monica/passkey`: tích hợp Credential Provider cho Android 14+
- `takagi/ru/monica/workers`: tác vụ nền như sao lưu WebDAV tự động

### Mô hình bảo mật 🔐

- **Thuật toán mã hóa**: AES-256-GCM (mã hóa có xác thực)
- **Dẫn xuất khóa**: PBKDF2-HMAC-SHA256 với số vòng lặp cao
- **Bảo vệ cục bộ**: băm mật khẩu chính và cấu hình bảo mật được quản lý bởi Android Keystore
- **Ranh giới mạng**: chỉ dùng cho đồng bộ Bitwarden API, sao lưu/khôi phục WebDAV và cập nhật mã QR

### Xây dựng và phụ thuộc 📦

- **JDK**: 17+
- **Android Studio**: bản ổn định mới nhất
- **Cấu hình biên dịch**: `compileSdk 35`, `targetSdk 34`, `minSdk 26`
- **Công cụ build**: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00`
- **Quản lý phiên bản**: xem `gradle/libs.versions.toml` và `app/build.gradle`

### Lệnh build nhanh

Build APK debug:

```bash
./gradlew :app:assembleDebug
```

Chạy unit test:

```bash
./gradlew test
```

Chạy Android instrumentation test (cần thiết bị hoặc giả lập):

```bash
./gradlew connectedAndroidTest
```

---

## Lộ trình 🚦

### Trọng tâm bản ổn định

- Năng lực Local Vault MDBX cốt lõi
- Nhập/đồng bộ tương thích Bitwarden
- Đọc/ghi KeePass `.kdbx`
- Quản lý nhiều loại mục
- Hỗ trợ Android AutoFill
- TOTP và mã QR
- Sao lưu/khôi phục WebDAV

### Đang tiếp tục

- Cải thiện trải nghiệm sinh trắc học (Face / vân tay)
- Kiểm thử Passkey trên thiết bị thật (Android 14+)
- Tối ưu hiệu năng tìm kiếm ngoại tuyến
- Di trú và tương thích MDBX đầy đủ

### Năng lực tiếp theo

- Thêm dịch vụ đồng bộ đám mây (OneDrive, Google Drive, v.v.)
- Liên kết tiện ích trình duyệt
- Đồng bộ dữ liệu với bản iOS
- Mã hóa nâng cao và kiểm toán bảo mật
- Widget và truy cập nhanh

---

## Lời cảm ơn 🙏

Thiết kế, khả năng tương thích và một phần định hướng tính năng của Monica được truyền cảm hứng và hỗ trợ bởi các dự án/phần mềm mã nguồn mở sau:

- [Keyguard](https://github.com/AChep/keyguard-app) - tham khảo về thiết kế tương tác và trải nghiệm của trình quản lý mật khẩu Android
- [Bitwarden](https://bitwarden.com/) - tham khảo quan trọng về hệ sinh thái quản lý mật khẩu mã nguồn mở, mô hình vault và đồng bộ
- [KeePass](https://keepass.info/) - nền tảng quan trọng cho triết lý kho cục bộ và tương thích hệ sinh thái `.kdbx`
- [Stratum Auth](https://github.com/stratumauth/app) - tham khảo về trải nghiệm trình xác thực, tài nguyên biểu tượng và hỗ trợ tương thích

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-Android&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-Android&Date)

---

## Giấy phép

Copyright (c) 2025 JoyinJoester

Monica for Android được phát hành mã nguồn mở theo [GNU General Public License v3.0](LICENSE).

## Ghi chú biểu tượng bên thứ ba

- Dự án đóng gói tài nguyên biểu tượng từ [Stratum Auth app](https://github.com/stratumauth/app) (phiên bản [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), thư mục [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0)
- Quyền thương hiệu của tên thương hiệu và logo thuộc về chủ sở hữu tương ứng
