import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "zh-CN",
  description: "Monica 是一個開源的本地密碼管理器。",
  markdown: {
    container: {
      tipLabel: "提示",
      warningLabel: "警告",
      dangerLabel: "危险",
      infoLabel: "信息",
      detailsLabel: "详细信息",
    },
  },
  themeConfig: {
    darkModeSwitchLabel: "主题",
    sidebarMenuLabel: "菜单",
    returnToTopLabel: "返回顶部",
    lastUpdatedText: "上次更新时间",
    outline: { level: [2, 4], label: "本页导航" },
    docFooter: { prev: "上一页", next: "下一页" },
    nav: [
      { text: "首页", link: "/" },
      { text: "生态", link: "/ecosystem" },
      { text: "指南", link: "/guide/intro" },
      { text: "文档", link: "/reference/catalogue" },
      {
        text: "功能页",
        items: [
          { text: "归档页", link: "/archives" },
          { text: "清单页", link: "/articleOverview" },
        ],
      },
      { text: "✨ 赞赏", link: "/personal/" },
    ],
    editLink: {
      text: "在 GitHub 上编辑此页",
      pattern: "https://github.com/Monica-Pass/Monica/edit/master/docs/:path",
    },
  },
});