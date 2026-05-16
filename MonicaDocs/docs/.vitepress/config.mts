import { defineConfig } from "vitepress";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";
import llmstxt from "vitepress-plugin-llms";
import { teekConfig } from "./teekConfig";
import shared from "./locales/shared.mjs";
import zh from "./locales/zh-CN.mjs";
import en from "./locales/en-US.mjs";
import ja from "./locales/ja-JP.mjs";
import vi from "./locales/vi-VN.mjs";
import ru from "./locales/ru-RU.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.resolve(__dirname, "../public");

const repoName = process.env.GITHUB_REPOSITORY?.split("/")[1];
const basePath = repoName ? `/${repoName}/` : "/";
const hasBase = basePath !== "/";
const isBasePrefixed = (id: string) => id.startsWith(basePath);

export default defineConfig({
  extends: teekConfig,

  base: basePath,

  vite: {
    plugins: [
      llmstxt(),
      {
        name: "resolve-base-paths",
        enforce: "pre",
        config(_, { isSsrBuild }) {
          if (!hasBase) return;
          // Only externalize during client build; SSR resolves via resolveId
          if (isSsrBuild) return;
          return {
            build: { rollupOptions: { external: isBasePrefixed } },
          };
        },
        resolveId(id) {
          if (!hasBase || !isBasePrefixed(id)) return;
          // /Monica/image/afdian.svg → resolve to public/image/afdian.svg
          const relative = id.slice(basePath.length);
          const filePath = path.resolve(publicDir, relative);
          try {
            if (fs.statSync(filePath).isFile()) return filePath;
          } catch {}
          // File doesn't exist locally → virtual module exporting the path string
          return "\0base-asset:" + id;
        },
        load(id) {
          if (id.startsWith("\0base-asset:")) {
            return `export default ${JSON.stringify(id.slice("\0base-asset:".length))}`;
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