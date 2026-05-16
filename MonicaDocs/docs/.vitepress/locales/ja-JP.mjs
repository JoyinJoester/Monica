import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "ja-JP",
  description: "Monicaはオープンソースのローカルパスワードマネージャーです。",
  markdown: {
    container: {
      tipLabel: "ヒント",
      warningLabel: "警告",
      dangerLabel: "危険",
      infoLabel: "情報",
      detailsLabel: "詳細情報",
    },
  },
  themeConfig: {
    darkModeSwitchLabel: "テーマ",
    sidebarMenuLabel: "メニュー",
    returnToTopLabel: "トップへ戻る",
    lastUpdatedText: "最終更新日時",
    outline: { level: [2, 4], label: "このページのナビゲーション" },
    docFooter: { prev: "前のページ", next: "次のページ" },
    nav: [
      { text: "ホーム", link: "/ja/" },
      { text: "ガイド", link: "/ja/guide/intro" },
      { text: "ドキュメント", link: "/ja/reference/catalogue" },
      {
        text: "機能ページ",
        items: [
          { text: "アーカイブ", link: "/ja/archives" },
          { text: "一覧ページ", link: "/ja/articleOverview" },
        ],
      },
      { text: "✨ 支援", link: "/ja/personal/" },
    ],
    editLink: {
      text: "GitHub でこのページを編集",
      pattern: "https://github.com/JoyinJoester/Monica/edit/master/docs/:path",
    },
  },
});