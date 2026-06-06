import { defineConfig } from "vitepress";
import llmstxt from "vitepress-plugin-llms";

export default defineConfig({
  title: "Monica",
  cleanUrls: false,
  lastUpdated: true,
  sitemap: {
    hostname: "https://Monica-Pass.github.io/Monica",
    transformItems: (items) => {
      const permalinkItemBak = [];
      const permalinks = globalThis.VITEPRESS_CONFIG?.site?.themeConfig?.permalinks;
      items.forEach((item) => {
        const permalink = permalinks?.map[item.url];
        if (permalink) permalinkItemBak.push({ url: permalink, lastmod: item.lastmod });
      });
      return [...items, ...permalinkItemBak];
    },
  },
  head: [
    ["link", { rel: "icon", type: "image/svg+xml", href: "/teek-logo-mini.svg" }],
    ["link", { rel: "icon", type: "image/png", href: "/teek-logo-mini.png" }],
    ["meta", { property: "og:type", content: "website" }],
  ],
  markdown: {
    lineNumbers: true,
    image: { lazyLoading: true },
  },
  themeConfig: {
    logo: "/themepng.png",
    socialLinks: [{ icon: "github", link: "https://github.com/Monica-Pass/Monica" }],
    search: { provider: "local" },
  },
  vite: {
    plugins: [llmstxt()],
  },
});