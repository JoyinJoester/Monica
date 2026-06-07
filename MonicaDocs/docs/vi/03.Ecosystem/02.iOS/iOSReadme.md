---
title: Giới thiệu phiên bản iOS
date: 2025-06-01 23:00:00
permalink: /ecosystem/iOSReadme
---

# Monica for iOS

> Ứng dụng khách kho mật khẩu iOS ưu tiên cục bộ của Monica  
> iOS 17+ · SwiftUI · MDBX Vault · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-iOS/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note Tóm tắt
Monica for iOS là ứng dụng khách iOS gốc trong hệ sinh thái kho mật khẩu Monica. Ứng dụng theo triết lý <mark>ưu tiên cục bộ</mark>, tập trung cung cấp trải nghiệm quản lý mật khẩu an toàn, có thể kiểm soát và có thể khôi phục trên iPhone.

Năng lực chính: `MDBX Vault`, `AutoFill`, `TOTP`, `WebDAV`.
:::

Monica for iOS là ứng dụng khách iOS gốc trong hệ sinh thái kho mật khẩu Monica. Ứng dụng theo triết lý thiết kế **ưu tiên cục bộ**, tập trung cung cấp trải nghiệm quản lý mật khẩu an toàn, có thể kiểm soát và có thể khôi phục trên iPhone.

Hướng phát triển hiện tại lấy **MDBX ưu tiên, iOS 17+, iPhone ưu tiên và cầu nối Rust/Swift UniFFI** làm trục chính. Trọng tâm gồm kho mã hóa cục bộ, AutoFill gốc, TOTP, sao lưu/khôi phục WebDAV và quản lý nhiều loại mục tương thích với mô hình dữ liệu Android / MDBX.

Phiên bản công khai đầu tiên sẽ ưu tiên:

- Năng lực Local Vault cơ bản
- Quản lý mục cốt lõi
- Sao lưu và khôi phục WebDAV
- Hỗ trợ AutoFill hoàn chỉnh hơn

Sau đó sẽ tiếp tục hoàn thiện:

- Passkey
- Tương thích Bitwarden
- Hỗ trợ tệp đính kèm
- Nhiều năng lực đồng bộ đám mây hơn

---

## Phù hợp với ai

Monica for iOS phù hợp với người dùng:

- Muốn dùng giải pháp quản lý mật khẩu **ưu tiên cục bộ** và không muốn giao dữ liệu đăng nhập cốt lõi cho dịch vụ bên thứ ba.
- Đã quản lý mật khẩu, TOTP, ghi chú riêng tư và dữ liệu liên quan trong Monica Android hoặc hệ sinh thái MDBX.
- Cần các năng lực gốc của iOS như **AutoFill, Face ID / Touch ID và sao lưu/khôi phục cục bộ**.
- Muốn quản lý nhiều loại mục trên iPhone, gồm đăng nhập, ghi chú an toàn, TOTP, thẻ ngân hàng và siêu dữ liệu giấy tờ định danh.

---

## Tính năng hiện tại

### Local MDBX Vault

- Tạo, mở và khóa kho mã hóa cục bộ
- Theo chiến lược ưu tiên cục bộ để giảm phụ thuộc vào dịch vụ bên ngoài

### Quản lý nhiều loại mục

- CRUD cơ bản cho đăng nhập, ghi chú an toàn, TOTP, thẻ ngân hàng, siêu dữ liệu giấy tờ định danh và các loại khác
- Tương thích với tư duy mô hình dữ liệu Monica / MDBX

### Yêu thích và tìm kiếm

- Hỗ trợ ưu tiên mục yêu thích
- Hỗ trợ chỉ xem mục yêu thích
- Hỗ trợ tìm kiếm trong phiên
- Hỗ trợ khôi phục sau khi xóa mềm

### TOTP

- Nhập qua URI `otpauth://`
- Nhập mã QR
- Tạo mã xác minh và làm mới số giây còn lại

### iOS AutoFill

- Đọc chỉ mục mã hóa thông qua Credential Provider Extension
- Trả về thông tin đăng nhập có thể điền cho hệ thống
- Cung cấp hỗ trợ gốc cho các tình huống tự động điền mật khẩu

### Mở khóa an toàn

- Kết hợp Keychain, LocalAuthentication và MDBX `security_key`
- Không lưu mật khẩu chính ở dạng rõ
- Cố gắng giữ thông tin nhạy cảm trong ranh giới bảo mật cục bộ

### WebDAV

- Hỗ trợ tải lên, tải xuống và kiểm tra toàn vẹn SHA-256
- Hỗ trợ xem trước khôi phục
- Hỗ trợ mở kiểm tra trước khi khôi phục

### OneDrive

- Đã tích hợp MSAL và Microsoft Graph app-folder provider
- Kiểm thử bằng tài khoản thật và mạng thật vẫn đang tiếp tục

### KeePass / KDBX

- Đã hoàn tất khả năng tương thích luồng đọc/ghi chính cho KDBX3 / KDBX4 hiện đại
- Sẽ tiếp tục mở rộng kiểm thử trong nhiều tình huống thực tế hơn

---

## Trạng thái hiện tại

- Dự án vẫn ở giai đoạn phát triển ứng dụng khách iOS và kiểm thử trên thiết bị thật.
- Khi kiểm thử giả lập không ký với `CODE_SIGNING_ALLOWED=NO`, App Group container không khả dụng là hiện tượng dự kiến.
- AutoFill QuickType, Credential Provider, App Group, Keychain access group, quét TOTP bằng camera và các năng lực tương tự vẫn cần tiếp tục xác minh trong môi trường thiết bị thật đã ký.

---

## Thông tin phát triển

### Cấu trúc thư mục 🗂️

::: tip Tổng quan bố cục dự án
```text
.
├── Monica.xcodeproj/
├── App/
│   └── MonicaApp/
├── Extensions/
│   ├── MonicaAutoFillExtension/
│   ├── MonicaShareExtension/
│   └── MonicaWidgetExtension/
├── Tests/
│   └── MonicaTests/
├── Scripts/
│   ├── build-mdbx-xcframework.sh
│   └── generate-mdbx-swift-bindings.sh
├── Artifacts/
│   ├── MDBX/
│   └── MSAL/
├── Generated/
│   └── MDBXUniFFI/
└── SwiftPackages/
    ├── MSAL/
    ├── MonicaCore/
    ├── MonicaMDBX/
    ├── MonicaSecurity/
    ├── MonicaStorage/
    ├── MonicaSync/
    └── MonicaUI/
```
:::

### Công nghệ ⚙️

- **Lớp App**: <mark>SwiftUI</mark>, Observation, AuthenticationServices, LocalAuthentication, Keychain, WidgetKit
- **Local Vault**: MDBX (Rust) + UniFFI → `MonicaMDBX` (Swift)
- **Logic cốt lõi**: `MonicaCore` (TOTP, phân tích `otpauth://`, tạo mật khẩu an toàn)
- **Lớp lưu trữ**: `MonicaStorage` (vault repository, tương thích KDBX, chỉ mục AutoFill)
- **Lớp bảo mật**: `MonicaSecurity` (ranh giới Keychain / LocalAuthentication)
- **Lớp đồng bộ**: `MonicaSync` (WebDAV, OneDrive, adapter Bitwarden)

### MDBX UniFFI 🔧

Crate cầu nối phía Rust:

```text
mdbx/crates/mdbx-ios-ffi
```

Tạo Swift binding:

```bash
Scripts/generate-mdbx-swift-bindings.sh
```

Tạo iOS XCFramework:

```bash
Scripts/build-mdbx-xcframework.sh
```

Nếu thiếu UniFFI CLI:

```bash
cargo install uniffi --version 0.31.1 --locked --features cli
```

Lưu ý: không chạy song song `Scripts/build-mdbx-xcframework.sh` với Xcode build. Script sẽ thay thế `MonicaMDBXGenerated.xcframework` và có thể khiến Xcode đọc thất bại.

---

## Lệnh kiểm thử

::: tip Cách chạy nhanh kiểm thử và build

### Kiểm thử Swift Package

Chạy lần lượt các lệnh sau trong terminal macOS để kiểm thử từng Swift package:

```bash
cd SwiftPackages/MonicaCore && swift test
cd ../MonicaMDBX && swift test
cd ../MonicaStorage && swift test
cd ../MonicaSecurity && swift test
cd ../MonicaSync && swift test
cd ../MonicaUI && swift test
```

### Build iOS Simulator (không ký)

Trước tiên liệt kê các simulator khả dụng:

```bash
xcrun simctl list devices available
```

Sau đó thay `<iPhone simulator UUID>` và chạy:

```bash
xcodebuild \
  -project Monica.xcodeproj \
  -scheme Monica \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<iPhone simulator UUID>" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

### iOS Simulator XCTest

```bash
xcodebuild test \
  -project Monica.xcodeproj \
  -scheme Monica \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<iPhone simulator UUID>" \
  CODE_SIGNING_ALLOWED=NO
```

:::

---

## Lộ trình

### Trọng tâm phát hành đầu tiên

- Local Vault MDBX
- Quản lý mục cốt lõi
- TOTP
- WebDAV
- iOS AutoFill

### Đang tiếp tục

- Xác minh trên thiết bị thật đã ký
- Keychain / LocalAuthentication
- App Group
- QuickType
- Quét bằng camera

### Năng lực tiếp theo

- Passkey
- Đồng bộ hai chiều Bitwarden
- Trải nghiệm tệp đính kèm
- Thêm dịch vụ đám mây
- Lối vào gốc iOS như Widget / Live Activity

---

## Lời cảm ơn

Thiết kế, khả năng tương thích và một phần định hướng tính năng của Monica được truyền cảm hứng và hỗ trợ bởi các dự án/phần mềm mã nguồn mở sau:

- [Bitwarden](https://bitwarden.com/) - tham khảo quan trọng về hệ sinh thái quản lý mật khẩu mã nguồn mở, mô hình vault và đồng bộ
- [KeePass](https://keepass.info/) - nền tảng quan trọng cho triết lý kho cục bộ và tương thích hệ sinh thái `.kdbx`
- [Keyguard](https://github.com/AChep/keyguard-app) - tham khảo về thiết kế tương tác và UX của trình quản lý mật khẩu Android
- [Stratum Auth](https://github.com/stratumauth/app) - tham khảo về trải nghiệm trình xác thực, tài nguyên biểu tượng và hỗ trợ tương thích

### Lộ trình tóm tắt 🚦

- **Bắt buộc cho bản đầu tiên**: Local Vault MDBX, quản lý mục cốt lõi, TOTP, WebDAV, iOS AutoFill
- **Đang xác minh gần hạn**: thiết bị thật đã ký, Keychain / LocalAuthentication, App Group, QuickType, quét camera
- **Kế hoạch tương lai (khám phá / lên lịch)**: Passkey, đồng bộ hai chiều Bitwarden, trải nghiệm tệp đính kèm, nhiều dịch vụ đám mây hơn, Widget / Live Activity

> Có thể chuyển trực tiếp các mục trên thành issue hoặc milestone để dễ theo dõi và phân công.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-iOS&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-iOS&Date)

---

## Giấy phép

Copyright (c) 2025 JoyinJoester

Monica for iOS được phát hành mã nguồn mở theo [GNU General Public License v3.0](LICENSE).

## Ghi chú biểu tượng bên thứ ba

- Dự án sử dụng tài nguyên biểu tượng bên thứ ba từ [Stratum Auth app](https://github.com/stratumauth/app) (phiên bản [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), thư mục [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0)
- Quyền thương hiệu của tên thương hiệu và logo thuộc về chủ sở hữu tương ứng
