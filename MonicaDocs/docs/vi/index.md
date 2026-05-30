---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Monica Pass"
  text: "An toàn · Cục bộ · Mã nguồn mở"
  tagline: "Monica là một trình quản lý mật khẩu cục bộ mã nguồn mở. Quản lý mật khẩu, mã xác thực 2FA và ghi chú mã hóa của bạn, tất cả dữ liệu được lưu trữ hoàn toàn trên thiết bị cục bộ và không đi qua bất kỳ đám mây nào."
  image:
    src: /themepng.png
    alt: Biểu tượng ứng dụng Monica
  actions:
    - theme: brand
      text: Tải về cho Android
      link: https://github.com/JoyinJoester/Monica/releases
    - theme: alt
      text: Tiện ích mở rộng trình duyệt
      link: https://github.com/JoyinJoester/Monica/tree/main/Monica%20for%20Browser
    - theme: alt
      text: Tài liệu dự án
      link: /vi/reference/catalogue

features:
  - title: Quản lý mật khẩu
    details: Hỗ trợ tự động điền và lưu trữ nhiều trường dữ liệu. Sử dụng mã hóa AES-256 cường độ cao, đảm bảo dữ liệu chỉ tồn tại trên thiết bị cục bộ của bạn.
    icon: 🔑
  - title: Trình xác thực 2FA
    details: Tích hợp trình xác thực TOTP, hỗ trợ quét mã QR để thêm mới. Tự động đếm ngược và sao chép chỉ với một chạm trước khi mã hết hạn.
    icon: 🕒
  - title: Thẻ và Ghi chú
    details: Lưu trữ an toàn thẻ ngân hàng, giấy tờ tùy thân và ghi chú bí mật. Toàn bộ thông tin nhạy cảm đều do bạn hoàn toàn kiểm soát.
    icon: 💳
  - title: Material Design 3
    details: Tuân thủ nghiêm ngặt quy chuẩn thiết kế Google Material Design 3. Hỗ trợ phối màu động (Dynamic Color) và chế độ tối, giao diện thuần khiết không quảng cáo.
    icon: 🎨
  - title: Keystore cấp phần cứng
    details: Khóa mã hóa được bảo vệ bởi Android Keystore (TEE), cung cấp ranh giới cách ly an toàn dựa trên phần cứng cho dữ liệu mã hóa của bạn.
    icon: 🛡️
  - title: Kiến trúc Zero-Knowledge
    details: Hoạt động hoàn toàn ngoại tuyến theo mặc định và không cần quyền mạng. Đảm bảo ngay cả nhà phát triển cũng không thể truy cập hoặc thu thập dữ liệu của bạn.
    icon: 🚫
---