import { defineConfig } from "vitepress";
import { teekConfig } from "./teekConfig";
import shared from "./locales/shared.mjs";
import zh from "./locales/zh-CN.mjs";
import en from "./locales/en-US.mjs";
import ja from "./locales/ja-JP.mjs";
import vi from "./locales/vi-VN.mjs";
import ru from "./locales/ru-RU.mjs";

export default defineConfig({
  extends: teekConfig,
  
  base:'/Monica/',

  ...shared,

  locales: {
    root: { 
      label: "简体中文", 
      lang: "zh-CN",
      ...zh 
    },
    en: { 
      label: "English", 
      lang: "en-US", 
      link: "/en/",
      ...en 
    },
    ja: { 
      label: "日本語", 
      lang: "ja-JP", 
      link: "/ja/",
      ...ja 
    },
    vi: { 
      label: "Tiếng Việt", 
      lang: "vi-VN", 
      link: "/vi/",
      ...vi 
    },
    ru: { 
      label: "Русский", 
      lang: "ru-RU", 
      link: "/ru/",
      ...ru 
    },
  },
});