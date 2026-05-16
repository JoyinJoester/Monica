import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "en-US",
  description: "Monica is an open-source, local-first password manager.",
  markdown: {
    container: {
      tipLabel: "TIP",
      warningLabel: "WARNING",
      dangerLabel: "DANGER",
      infoLabel: "INFO",
      detailsLabel: "Details",
    },
  },
  themeConfig: {
    darkModeSwitchLabel: "Appearance",
    sidebarMenuLabel: "Menu",
    returnToTopLabel: "Return to top",
    lastUpdatedText: "Last updated",
    outline: { level: [2, 4], label: "On this page" },
    docFooter: { prev: "Previous page", next: "Next page" },
    nav: [
      { text: "Home", link: "/en/" },
      { text: "Guide", link: "/en/guide/intro" },
      { text: "Docs", link: "/en/reference/catalogue" },
      {
        text: "Features",
        items: [
          { text: "Archives", link: "/en/archives" },
          { text: "Overview", link: "/en/articleOverview" },
        ],
      },
      { text: "✨ Sponsor", link: "/en/personal/" },
    ],
    editLink: {
      text: "Edit this page on GitHub",
      pattern: "https://github.com/JoyinJoester/Monica/edit/master/docs/:path",
    },
  },
});