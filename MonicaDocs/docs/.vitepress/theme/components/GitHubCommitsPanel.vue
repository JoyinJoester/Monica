<script setup lang="ts" name="GitHubCommitsPanel">
import { computed, onMounted, ref } from "vue";
import { useData, withBase } from "vitepress";

type ActivityDay = {
  date: string;
  count: number;
};

type LatestCommit = {
  sha: string;
  shortSha: string;
  message: string;
  authorName: string;
  authorAvatar: string;
  date: string;
  url: string;
};

type GitHubCommitsData = {
  repo: string;
  repoUrl: string;
  generatedAt: string;
  range: {
    from: string;
    to: string;
  };
  activity: ActivityDay[];
  latest: LatestCommit[];
};

type CalendarCell = ActivityDay & {
  isPadding?: boolean;
  level: number;
};

const data = ref<GitHubCommitsData | null>(null);
const loading = ref(true);
const failed = ref(false);
const { lang } = useData();

const localeText = {
  zh: {
    title: "GitHub \u63d0\u4ea4",
    fallbackDescription: "Monica-Pass/Monica \u7684\u63d0\u4ea4\u6d3b\u8dc3\u5ea6",
    commitsInLastYear: "\u8fc7\u53bb\u4e00\u5e74\u63d0\u4ea4",
    updated: "\u66f4\u65b0\u4e8e",
    loading: "\u6b63\u5728\u52a0\u8f7d GitHub \u63d0\u4ea4...",
    unavailable: "GitHub \u63d0\u4ea4\u6570\u636e\u6682\u4e0d\u53ef\u7528\u3002",
    noData: "\u6682\u65e0\u63d0\u4ea4\u6d3b\u8dc3\u6570\u636e\u3002",
    less: "\u5c11",
    more: "\u591a",
    weekdayMon: "\u4e00",
    weekdayWed: "\u4e09",
    weekdayFri: "\u4e94",
    commits: "\u6b21\u63d0\u4ea4",
  },
  en: {
    title: "GitHub Commits",
    fallbackDescription: "Commit activity for Monica-Pass/Monica",
    commitsInLastYear: "commits in the last year",
    updated: "Updated",
    loading: "Loading GitHub commits...",
    unavailable: "GitHub commit data is unavailable.",
    noData: "No commit activity data yet.",
    less: "Less",
    more: "More",
    weekdayMon: "Mon",
    weekdayWed: "Wed",
    weekdayFri: "Fri",
    commits: "commits",
  },
  ja: {
    title: "GitHub \u30b3\u30df\u30c3\u30c8",
    fallbackDescription: "Monica-Pass/Monica \u306e\u30b3\u30df\u30c3\u30c8\u30a2\u30af\u30c6\u30a3\u30d3\u30c6\u30a3",
    commitsInLastYear: "\u904e\u53bb1\u5e74\u9593\u306e\u30b3\u30df\u30c3\u30c8",
    updated: "\u66f4\u65b0",
    loading: "GitHub \u30b3\u30df\u30c3\u30c8\u3092\u8aad\u307f\u8fbc\u307f\u4e2d...",
    unavailable: "GitHub \u30b3\u30df\u30c3\u30c8\u30c7\u30fc\u30bf\u3092\u5229\u7528\u3067\u304d\u307e\u305b\u3093\u3002",
    noData: "\u30b3\u30df\u30c3\u30c8\u30a2\u30af\u30c6\u30a3\u30d3\u30c6\u30a3\u30c7\u30fc\u30bf\u306f\u307e\u3060\u3042\u308a\u307e\u305b\u3093\u3002",
    less: "\u5c11",
    more: "\u591a",
    weekdayMon: "\u6708",
    weekdayWed: "\u6c34",
    weekdayFri: "\u91d1",
    commits: "\u30b3\u30df\u30c3\u30c8",
  },
  vi: {
    title: "Commit GitHub",
    fallbackDescription: "Ho\u1ea1t \u0111\u1ed9ng commit c\u1ee7a Monica-Pass/Monica",
    commitsInLastYear: "commit trong n\u0103m qua",
    updated: "C\u1eadp nh\u1eadt",
    loading: "\u0110ang t\u1ea3i commit GitHub...",
    unavailable: "D\u1eef li\u1ec7u commit GitHub hi\u1ec7n kh\u00f4ng kh\u1ea3 d\u1ee5ng.",
    noData: "Ch\u01b0a c\u00f3 d\u1eef li\u1ec7u ho\u1ea1t \u0111\u1ed9ng commit.",
    less: "\u00cdt",
    more: "Nhi\u1ec1u",
    weekdayMon: "T2",
    weekdayWed: "T4",
    weekdayFri: "T6",
    commits: "commit",
  },
  ru: {
    title: "\u041a\u043e\u043c\u043c\u0438\u0442\u044b GitHub",
    fallbackDescription: "\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c \u043a\u043e\u043c\u043c\u0438\u0442\u043e\u0432 Monica-Pass/Monica",
    commitsInLastYear: "\u043a\u043e\u043c\u043c\u0438\u0442\u043e\u0432 \u0437\u0430 \u043f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0439 \u0433\u043e\u0434",
    updated: "\u041e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u043e",
    loading: "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430 \u043a\u043e\u043c\u043c\u0438\u0442\u043e\u0432 GitHub...",
    unavailable: "\u0414\u0430\u043d\u043d\u044b\u0435 \u043a\u043e\u043c\u043c\u0438\u0442\u043e\u0432 GitHub \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u044b.",
    noData: "\u0414\u0430\u043d\u043d\u044b\u0445 \u043e\u0431 \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u0438 \u043a\u043e\u043c\u043c\u0438\u0442\u043e\u0432 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442.",
    less: "\u041c\u0435\u043d\u044c\u0448\u0435",
    more: "\u0411\u043e\u043b\u044c\u0448\u0435",
    weekdayMon: "\u041f\u043d",
    weekdayWed: "\u0421\u0440",
    weekdayFri: "\u041f\u0442",
    commits: "\u043a\u043e\u043c\u043c\u0438\u0442\u043e\u0432",
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

const activity = computed(() => data.value?.activity ?? []);
const latest = computed(() => data.value?.latest ?? []);
const maxCount = computed(() => Math.max(...activity.value.map(item => item.count), 0));
const totalCommits = computed(() => activity.value.reduce((total, item) => total + item.count, 0));

const calendarCells = computed<CalendarCell[]>(() => {
  if (!activity.value.length) return [];

  const firstDate = new Date(`${activity.value[0].date}T00:00:00Z`);
  const leadingDays = firstDate.getUTCDay();
  const padding = Array.from({ length: leadingDays }, (_, index) => ({
    date: `padding-${index}`,
    count: 0,
    isPadding: true,
    level: 0,
  }));

  return padding.concat(activity.value.map(item => ({
    ...item,
    level: getLevel(item.count, maxCount.value),
  })));
});

const monthLabels = computed(() => {
  const labels: { index: number; label: string }[] = [];
  let previousMonth = "";

  calendarCells.value.forEach((item, index) => {
    if (index % 7 !== 0) return;

    if (item.isPadding) return;

    const date = new Date(`${item.date}T00:00:00Z`);
    const month = date.toLocaleString(dateLocale.value, { month: "short", timeZone: "UTC" });
    if (month === previousMonth) return;

    previousMonth = month;
    labels.push({ index: Math.floor(index / 7), label: month });
  });

  return labels;
});

onMounted(async () => {
  try {
    const response = await fetch(withBase("/github-commits.json"));
    if (!response.ok) throw new Error(`Failed to load GitHub commits: ${response.status}`);

    data.value = await response.json();
  } catch (error) {
    failed.value = true;
    console.error(error);
  } finally {
    loading.value = false;
  }
});

function getLevel(count: number, max: number) {
  if (count <= 0 || max <= 0) return 0;
  if (count >= Math.ceil(max * 0.75)) return 4;
  if (count >= Math.ceil(max * 0.5)) return 3;
  if (count >= Math.ceil(max * 0.25)) return 2;
  return 1;
}

function formatDate(value: string) {
  if (!value) return "";

  return new Intl.DateTimeFormat(dateLocale.value, {
    year: "numeric",
    month: "short",
    day: "2-digit",
  }).format(new Date(value));
}

function formatGeneratedAt(value: string) {
  if (!value) return "";

  return new Intl.DateTimeFormat(dateLocale.value, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function commitTitle(day: CalendarCell) {
  if (day.isPadding) return "";

  return `${day.date}: ${day.count} ${text.value.commits}`;
}
</script>

<template>
  <section class="github-commits-panel" aria-labelledby="github-commits-title">
    <div class="github-commits-panel__header">
      <div>
        <h2 id="github-commits-title">{{ text.title }}</h2>
        <p v-if="data">
          <a :href="data.repoUrl" target="_blank" rel="noreferrer">{{ data.repo }}</a>
          · {{ totalCommits }} {{ text.commitsInLastYear }}
        </p>
        <p v-else>{{ text.fallbackDescription }}</p>
      </div>
      <span v-if="data?.generatedAt" class="github-commits-panel__updated">
        {{ text.updated }} {{ formatGeneratedAt(data.generatedAt) }}
      </span>
    </div>

    <div v-if="loading" class="github-commits-panel__state">{{ text.loading }}</div>
    <div v-else-if="failed || !data" class="github-commits-panel__state">
      {{ text.unavailable }}
    </div>

    <template v-else>
      <div v-if="calendarCells.length" class="github-activity" aria-label="GitHub commit activity calendar">
        <div class="github-activity__months" :style="{ width: `${Math.ceil(calendarCells.length / 7) * 14}px` }">
          <span
            v-for="month in monthLabels"
            :key="`${month.label}-${month.index}`"
            :style="{ gridColumnStart: month.index + 1 }"
          >
            {{ month.label }}
          </span>
        </div>

        <div class="github-activity__body">
          <div class="github-activity__weekdays" aria-hidden="true">
            <span>{{ text.weekdayMon }}</span>
            <span>{{ text.weekdayWed }}</span>
            <span>{{ text.weekdayFri }}</span>
          </div>

          <div class="github-activity__grid">
            <span
              v-for="day in calendarCells"
              :key="day.date"
              class="github-activity__cell"
              :class="[
                `github-activity__cell--level-${day.level}`,
                { 'github-activity__cell--padding': day.isPadding },
              ]"
              :title="commitTitle(day)"
              :aria-label="day.isPadding ? undefined : commitTitle(day)"
            />
          </div>
        </div>

        <div class="github-activity__legend" aria-hidden="true">
          <span>{{ text.less }}</span>
          <i class="github-activity__cell github-activity__cell--level-0"></i>
          <i class="github-activity__cell github-activity__cell--level-1"></i>
          <i class="github-activity__cell github-activity__cell--level-2"></i>
          <i class="github-activity__cell github-activity__cell--level-3"></i>
          <i class="github-activity__cell github-activity__cell--level-4"></i>
          <span>{{ text.more }}</span>
        </div>
      </div>
      <div v-else class="github-commits-panel__state">{{ text.noData }}</div>

      <div class="github-commits-list">
        <a
          v-for="commit in latest"
          :key="commit.sha"
          class="github-commit"
          :href="commit.url"
          target="_blank"
          rel="noreferrer"
        >
          <img
            v-if="commit.authorAvatar"
            class="github-commit__avatar"
            :src="commit.authorAvatar"
            :alt="commit.authorName"
            loading="lazy"
          />
          <span v-else class="github-commit__avatar github-commit__avatar--fallback" aria-hidden="true">
            {{ commit.authorName.slice(0, 1).toUpperCase() }}
          </span>

          <span class="github-commit__content">
            <strong>{{ commit.message }}</strong>
            <span>{{ commit.authorName }} · {{ formatDate(commit.date) }}</span>
          </span>

          <code>{{ commit.shortSha }}</code>
        </a>
      </div>
    </template>
  </section>
</template>

<style scoped>
.github-commits-panel {
  margin: 0 auto 28px;
  padding: 18px;
  width: min(100%, 1120px);
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
}

.github-commits-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.github-commits-panel__header h2 {
  margin: 0;
  border: 0;
  padding: 0;
  font-size: 18px;
  line-height: 1.35;
}

.github-commits-panel__header p {
  margin: 4px 0 0;
  color: var(--vp-c-text-2);
  font-size: 13px;
  line-height: 1.5;
}

.github-commits-panel__updated {
  flex: 0 0 auto;
  color: var(--vp-c-text-3);
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
}

.github-commits-panel__state {
  padding: 26px 0;
  color: var(--vp-c-text-2);
  text-align: center;
}

.github-activity {
  overflow-x: auto;
  padding-bottom: 4px;
}

.github-activity__months {
  display: grid;
  grid-auto-columns: 14px;
  grid-auto-flow: column;
  margin-left: 34px;
  min-width: max-content;
  color: var(--vp-c-text-3);
  font-size: 11px;
  line-height: 18px;
}

.github-activity__months span {
  grid-row: 1;
}

.github-activity__body {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: max-content;
}

.github-activity__weekdays {
  display: grid;
  grid-template-rows: repeat(7, 12px);
  gap: 2px;
  width: 26px;
  color: var(--vp-c-text-3);
  font-size: 10px;
  line-height: 12px;
}

.github-activity__weekdays span:nth-child(1) {
  grid-row: 2;
}

.github-activity__weekdays span:nth-child(2) {
  grid-row: 4;
}

.github-activity__weekdays span:nth-child(3) {
  grid-row: 6;
}

.github-activity__grid {
  display: grid;
  grid-auto-columns: 12px;
  grid-auto-flow: column;
  grid-template-rows: repeat(7, 12px);
  gap: 2px;
  min-width: max-content;
}

.github-activity__cell {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  outline: 1px solid rgba(27, 31, 35, 0.04);
}

.github-activity__cell--level-0 {
  background: #ebedf0;
}

.github-activity__cell--level-1 {
  background: #9be9a8;
}

.github-activity__cell--level-2 {
  background: #40c463;
}

.github-activity__cell--level-3 {
  background: #30a14e;
}

.github-activity__cell--level-4 {
  background: #216e39;
}

:global(.dark) .github-activity__cell--level-0 {
  background: #2d333b;
}

:global(.dark) .github-activity__cell--level-1 {
  background: #0e4429;
}

:global(.dark) .github-activity__cell--level-2 {
  background: #006d32;
}

:global(.dark) .github-activity__cell--level-3 {
  background: #26a641;
}

:global(.dark) .github-activity__cell--level-4 {
  background: #39d353;
}

.github-activity__cell--padding {
  visibility: hidden;
}

.github-activity__legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 10px;
  color: var(--vp-c-text-3);
  font-size: 11px;
}

.github-commits-list {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}

.github-commit {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  color: var(--vp-c-text-1);
  background: var(--vp-c-bg);
  text-decoration: none;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.github-commit:hover {
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-bg-alt);
}

.github-commit__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.github-commit__avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--vp-c-text-2);
  background: var(--vp-c-default-soft);
  font-size: 13px;
}

.github-commit__content {
  min-width: 0;
}

.github-commit__content strong,
.github-commit__content span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.github-commit__content strong {
  font-size: 13px;
  line-height: 1.45;
}

.github-commit__content span {
  color: var(--vp-c-text-2);
  font-size: 12px;
  line-height: 1.45;
}

.github-commit code {
  color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
  font-size: 12px;
}

@media (max-width: 640px) {
  .github-commits-panel {
    padding: 14px;
  }

  .github-commits-panel__header {
    display: block;
  }

  .github-commits-panel__updated {
    display: block;
    margin-top: 6px;
    white-space: normal;
  }

  .github-commit {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .github-commit__avatar {
    width: 28px;
    height: 28px;
  }

  .github-commit code {
    grid-column: 2;
    width: max-content;
  }
}
</style>
