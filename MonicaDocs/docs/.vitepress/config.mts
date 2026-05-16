import { defineConfig } from "vitepress";
import llmstxt from "vitepress-plugin-llms";
import { teekConfig } from "./teekConfig";
import shared from "./locales/shared.mjs";
import zh from "./locales/zh-CN.mjs";
import en from "./locales/en-US.mjs";
import ja from "./locales/ja-JP.mjs";
import vi from "./locales/vi-VN.mjs";
import ru from "./locales/ru-RU.mjs";

const repoName = process.env.GITHUB_REPOSITORY?.split("/")[1];
const basePath = repoName ? `/${repoName}/` : "/";
const hasBase = basePath !== "/";
const isBasePrefixed = (id: string) => id.startsWith(basePath);

const VIRTUAL_PREFIX = "\0base-asset:";

export default defineConfig({
  extends: teekConfig,

  base: basePath,

  vite: {
    plugins: [
      llmstxt(),
      {
        name: "resolve-base-paths",
        enforce: "pre",
        resolveId(id) {
          if (hasBase && isBasePrefixed(id)) {
            return VIRTUAL_PREFIX + id;
          }
        },
        load(id) {
          if (id.startsWith(VIRTUAL_PREFIX)) {
            return `export default ${JSON.stringify(id.slice(VIRTUAL_PREFIX.length))}`;
          }
        },
      },
    ],
  },

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