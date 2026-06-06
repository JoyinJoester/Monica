<script setup lang="ts" name="ArchivesDashboard">
import { computed } from "vue";
import { useData, withBase } from "vitepress";
import { usePosts } from "vitepress-theme-teek";

type ArchivePost = {
  title?: string;
  url?: string;
  date?: string;
};

type MonthGroup = {
  month: string;
  count: number;
  posts: ArchivePost[];
};

type YearGroup = {
  year: string;
  count: number;
  months: MonthGroup[];
};

const posts = usePosts();
const { lang } = useData();

const localeText = {
  zh: {
    kicker: "归档",
    title: "文档归档",
    description: "按更新时间浏览所有已发布文档，并优先展示最近更新。",
    statsLabel: "归档统计",
    documents: "文档",
    years: "年份",
    latestUpdate: "最新更新",
    latestUpdates: "最近更新",
    allDocuments: "全部文档",
    items: "条目",
    documentsUnit: "篇文档",
    noDate: "无日期",
    untitled: "未命名文档",
  },
  en: {
    kicker: "Archives",
    title: "Documentation Archive",
    description: "Browse all published documentation by update time, with recent changes surfaced first.",
    statsLabel: "Archive statistics",
    documents: "Documents",
    years: "Years",
    latestUpdate: "Latest update",
    latestUpdates: "Latest Updates",
    allDocuments: "All Documents",
    items: "items",
    documentsUnit: "documents",
    noDate: "No date",
    untitled: "Untitled",
  },
  ja: {
    kicker: "アーカイブ",
    title: "ドキュメントアーカイブ",
    description: "公開済みドキュメントを更新日時順に確認し、最近の更新を先に表示します。",
    statsLabel: "アーカイブ統計",
    documents: "ドキュメント",
    years: "年",
    latestUpdate: "最新更新",
    latestUpdates: "最近の更新",
    allDocuments: "すべてのドキュメント",
    items: "件",
    documentsUnit: "件",
    noDate: "日付なし",
    untitled: "無題",
  },
  vi: {
    kicker: "Lưu trữ",
    title: "Kho tài liệu",
    description: "Duyệt toàn bộ tài liệu đã xuất bản theo thời gian cập nhật, ưu tiên các thay đổi mới nhất.",
    statsLabel: "Thống kê lưu trữ",
    documents: "Tài liệu",
    years: "Năm",
    latestUpdate: "Cập nhật mới nhất",
    latestUpdates: "Cập nhật gần đây",
    allDocuments: "Tất cả tài liệu",
    items: "mục",
    documentsUnit: "tài liệu",
    noDate: "Không có ngày",
    untitled: "Chưa có tiêu đề",
  },
  ru: {
    kicker: "Архив",
    title: "Архив документации",
    description: "Просматривайте опубликованную документацию по времени обновления, начиная с последних изменений.",
    statsLabel: "Статистика архива",
    documents: "Документы",
    years: "Годы",
    latestUpdate: "Последнее обновление",
    latestUpdates: "Последние обновления",
    allDocuments: "Все документы",
    items: "элементов",
    documentsUnit: "документов",
    noDate: "Без даты",
    untitled: "Без названия",
  },
};

const localeKey = computed(() => {
  const value = lang.value.toLowerCase();
  if (value.startsWith("zh")) return "zh";
  if (value.startsWith("ja")) return "ja";
  if (value.startsWith("vi")) return "vi";
  if (value.startsWith("ru")) return "ru";
  return "en";
});

const text = computed(() => localeText[localeKey.value]);
const dateLocale = computed(() => lang.value || "en-US");

const sortedPosts = computed<ArchivePost[]>(() => posts.value.sortPostsByDate ?? []);
const totalDocs = computed(() => sortedPosts.value.length);
const latestDocs = computed(() => sortedPosts.value.slice(0, 8));

const yearGroups = computed<YearGroup[]>(() => {
  const grouped = posts.value.groupPostsByYearMonth ?? {};
  const yearCounts = posts.value.groupPostsByYear ?? {};

  return Object.entries(grouped)
    .map(([year, months]) => {
      const monthGroups = Object.entries(months as Record<string, ArchivePost[]>)
        .map(([month, monthPosts]) => ({
          month: month === "NaN" ? text.value.noDate : month.padStart(2, "0"),
          count: monthPosts.length,
          posts: monthPosts,
        }))
        .sort((a, b) => Number.parseInt(b.month, 10) - Number.parseInt(a.month, 10));

      return {
        year: year === "NaN" ? text.value.noDate : year,
        count: yearCounts[year]?.length ?? monthGroups.reduce((total, item) => total + item.count, 0),
        months: monthGroups,
      };
    })
    .sort((a, b) => {
      if (a.year === text.value.noDate) return 1;
      if (b.year === text.value.noDate) return -1;
      return Number.parseInt(b.year, 10) - Number.parseInt(a.year, 10);
    });
});

const yearCount = computed(() => yearGroups.value.filter(item => item.year !== text.value.noDate).length);
const latestDate = computed(() => sortedPosts.value.find(item => item.date)?.date ?? "");

function postUrl(post: ArchivePost) {
  return post.url ? withBase(post.url) : "#";
}

function postTitle(post: ArchivePost) {
  return post.title || text.value.untitled;
}

function formatDate(value?: string) {
  if (!value) return text.value.noDate;

  return new Intl.DateTimeFormat(dateLocale.value, {
    year: "numeric",
    month: "short",
    day: "2-digit",
  }).format(new Date(value));
}

function formatMonth(year: string, month: string) {
  if (year === text.value.noDate || month === text.value.noDate) return text.value.noDate;

  return `${year}-${month}`;
}

function formatShortDate(value?: string) {
  if (!value) return "--";

  return new Intl.DateTimeFormat(dateLocale.value, {
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}
</script>

<template>
  <section class="archives-dashboard" aria-labelledby="archives-dashboard-title">
    <header class="archives-dashboard__hero">
      <div>
        <span class="archives-dashboard__kicker">{{ text.kicker }}</span>
        <h1 id="archives-dashboard-title">{{ text.title }}</h1>
        <p>{{ text.description }}</p>
      </div>

      <div class="archives-dashboard__stats" :aria-label="text.statsLabel">
        <div>
          <strong>{{ totalDocs }}</strong>
          <span>{{ text.documents }}</span>
        </div>
        <div>
          <strong>{{ yearCount }}</strong>
          <span>{{ text.years }}</span>
        </div>
        <div>
          <strong>{{ formatDate(latestDate) }}</strong>
          <span>{{ text.latestUpdate }}</span>
        </div>
      </div>
    </header>

    <div class="archives-dashboard__grid">
      <aside class="archives-dashboard__latest" aria-labelledby="archives-latest-title">
        <h2 id="archives-latest-title">{{ text.latestUpdates }}</h2>

        <a v-for="post in latestDocs" :key="post.url || post.title" :href="postUrl(post)" class="archives-latest-item">
          <span>{{ formatDate(post.date) }}</span>
          <strong>{{ postTitle(post) }}</strong>
        </a>
      </aside>

      <div class="archives-dashboard__timeline" aria-labelledby="archives-timeline-title">
        <div class="archives-dashboard__section-header">
          <h2 id="archives-timeline-title">{{ text.allDocuments }}</h2>
          <span>{{ totalDocs }} {{ text.items }}</span>
        </div>

        <section v-for="group in yearGroups" :key="group.year" class="archives-year">
          <div class="archives-year__header">
            <h3>{{ group.year }}</h3>
            <span>{{ group.count }} {{ text.documentsUnit }}</span>
          </div>

          <div class="archives-months">
            <section v-for="month in group.months" :key="`${group.year}-${month.month}`" class="archives-month">
              <div class="archives-month__header">
                <strong>{{ formatMonth(group.year, month.month) }}</strong>
                <span>{{ month.count }}</span>
              </div>

              <a
                v-for="post in month.posts"
                :key="post.url || post.title"
                class="archives-doc"
                :href="postUrl(post)"
              >
                <time>{{ formatShortDate(post.date) }}</time>
                <span>{{ postTitle(post) }}</span>
              </a>
            </section>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.archives-dashboard {
  margin: 0 auto 28px;
  width: min(100%, 1120px);
}

.archives-dashboard__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 22px;
  align-items: end;
  margin-bottom: 18px;
  padding: 24px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: linear-gradient(135deg, var(--vp-c-bg-soft), var(--vp-c-bg));
}

.archives-dashboard__kicker {
  display: block;
  margin-bottom: 8px;
  color: var(--vp-c-brand-1);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  text-transform: uppercase;
}

.archives-dashboard__hero h1 {
  margin: 0;
  border: 0;
  padding: 0;
  font-size: 30px;
  line-height: 1.2;
}

.archives-dashboard__hero p {
  margin: 10px 0 0;
  max-width: 620px;
  color: var(--vp-c-text-2);
  font-size: 14px;
  line-height: 1.7;
}

.archives-dashboard__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 10px;
}

.archives-dashboard__stats div {
  padding: 12px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg);
}

.archives-dashboard__stats strong,
.archives-dashboard__stats span {
  display: block;
}

.archives-dashboard__stats strong {
  color: var(--vp-c-text-1);
  font-size: 18px;
  line-height: 1.3;
}

.archives-dashboard__stats span {
  margin-top: 2px;
  color: var(--vp-c-text-3);
  font-size: 12px;
}

.archives-dashboard__grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.archives-dashboard__latest,
.archives-dashboard__timeline {
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
}

.archives-dashboard__latest {
  position: sticky;
  top: calc(var(--vp-nav-height) + 16px);
  padding: 16px;
}

.archives-dashboard__latest h2,
.archives-dashboard__section-header h2 {
  margin: 0;
  border: 0;
  padding: 0;
  font-size: 16px;
  line-height: 1.4;
}

.archives-latest-item {
  display: block;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--vp-c-text-1);
  background: var(--vp-c-bg);
  text-decoration: none;
}

.archives-latest-item:hover {
  border-color: var(--vp-c-brand-1);
}

.archives-latest-item span,
.archives-latest-item strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archives-latest-item span {
  color: var(--vp-c-text-3);
  font-size: 12px;
}

.archives-latest-item strong {
  margin-top: 3px;
  font-size: 13px;
  line-height: 1.45;
}

.archives-dashboard__timeline {
  padding: 16px;
}

.archives-dashboard__section-header,
.archives-year__header,
.archives-month__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.archives-dashboard__section-header {
  margin-bottom: 14px;
}

.archives-dashboard__section-header span,
.archives-year__header span,
.archives-month__header span {
  color: var(--vp-c-text-3);
  font-size: 12px;
  white-space: nowrap;
}

.archives-year + .archives-year {
  margin-top: 18px;
}

.archives-year__header {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.archives-year__header h3 {
  margin: 0;
  font-size: 20px;
  line-height: 1.3;
}

.archives-months {
  display: grid;
  gap: 10px;
}

.archives-month {
  padding: 12px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg);
}

.archives-month__header {
  margin-bottom: 8px;
}

.archives-month__header strong {
  color: var(--vp-c-brand-1);
  font-size: 13px;
}

.archives-doc {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 10px;
  align-items: baseline;
  padding: 7px 0;
  color: var(--vp-c-text-1);
  text-decoration: none;
}

.archives-doc + .archives-doc {
  border-top: 1px solid var(--vp-c-divider);
}

.archives-doc:hover span {
  color: var(--vp-c-brand-1);
}

.archives-doc time {
  color: var(--vp-c-text-3);
  font-size: 12px;
  line-height: 1.5;
}

.archives-doc span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.5;
}

:global(.tk-article-page.tk-archives) {
  width: min(1220px, 100%);
}

:global(.tk-archives__header),
:global(.tk-archives > .vp-doc),
:global(.tk-archives__timeline) {
  display: none;
}

@media (max-width: 900px) {
  .archives-dashboard__hero,
  .archives-dashboard__grid {
    grid-template-columns: 1fr;
  }

  .archives-dashboard__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .archives-dashboard__latest {
    position: static;
  }
}

@media (max-width: 640px) {
  .archives-dashboard__hero {
    padding: 18px;
  }

  .archives-dashboard__hero h1 {
    font-size: 24px;
  }

  .archives-dashboard__stats {
    grid-template-columns: 1fr;
  }
}
</style>
