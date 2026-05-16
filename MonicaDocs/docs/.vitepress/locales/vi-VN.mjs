import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "vi-VN",
  description: "Monica là một trình quản lý mật khẩu cục bộ mã nguồn mở.",
  markdown: {
    container: {
      tipLabel: "Gợi ý",
      warningLabel: "Cảnh báo",
      dangerLabel: "Nguy hiểm",
      infoLabel: "Thông tin",
      detailsLabel: "Thông tin chi tiết",
    },
  },
  themeConfig: {
    darkModeSwitchLabel: "Giao diện",
    sidebarMenuLabel: "Menu",
    returnToTopLabel: "Cuộn về đầu trang",
    lastUpdatedText: "Cập nhật lần cuối vào",
    outline: { level: [2, 4], label: "Mục lục trang này" },
    docFooter: { prev: "Trang trước", next: "Trang sau" },
    nav: [
      { text: "Trang chủ", link: "/vi/" },
      { text: "Hướng dẫn", link: "/vi/guide/intro" },
      { text: "Tài liệu", link: "/vi/reference/catalogue" },
      {
        text: "Trang chức năng",
        items: [
          { text: "Lưu trữ", link: "/vi/archives" },
          { text: "Danh sách bài viết", link: "/vi/articleOverview" },
        ],
      },
      { text: "✨ Tài trợ", link: "/vi/personal/" },
    ],
    editLink: {
      text: "Chỉnh sửa trang này trên GitHub",
      pattern: "https://github.com/JoyinJoester/Monica/edit/master/docs/:path",
    },
  },
});