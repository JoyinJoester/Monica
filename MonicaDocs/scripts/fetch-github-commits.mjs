import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const docsRoot = path.resolve(__dirname, "../docs");
const outputDir = path.join(docsRoot, "public");
const outputFile = path.join(outputDir, "github-commits.json");

const repoOwner = process.env.GITHUB_COMMITS_REPO_OWNER || "Monica-Pass";
const repoName = process.env.GITHUB_COMMITS_REPO_NAME || "Monica";
const repo = `${repoOwner}/${repoName}`;
const repoUrl = `https://github.com/${repo}`;
const apiBase = `https://api.github.com/repos/${repo}`;
const latestLimit = 10;

const headers = {
  Accept: "application/vnd.github+json",
  "User-Agent": "MonicaDocs-GitHub-Commits-Updater",
  "X-GitHub-Api-Version": "2022-11-28",
};

if (process.env.GITHUB_TOKEN) {
  headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
}

const today = startOfUtcDay(new Date());
const from = addDays(today, -364);
const activity = createActivityRange(from, today);
const activityByDate = new Map(activity.map((day) => [day.date, day]));
const latest = [];

try {
  const until = addDays(today, 1);

  for (let page = 1; ; page += 1) {
    const params = new URLSearchParams({
      per_page: "100",
      page: String(page),
      since: from.toISOString(),
      until: until.toISOString(),
    });
    const commits = await fetchJson(`${apiBase}/commits?${params}`);
    if (!Array.isArray(commits) || commits.length === 0) break;

    for (const commit of commits) {
      const commitDate = commit.commit?.author?.date || commit.commit?.committer?.date;
      const dayKey = toDateKey(commitDate);

      if (activityByDate.has(dayKey)) {
        activityByDate.get(dayKey).count += 1;
      }

      if (latest.length < latestLimit) {
        latest.push(toLatestCommit(commit));
      }
    }

    if (commits.length < 100) break;
  }

  const data = {
    repo,
    repoUrl,
    generatedAt: new Date().toISOString(),
    range: {
      from: formatDate(from),
      to: formatDate(today),
    },
    activity,
    latest,
  };

  fs.mkdirSync(outputDir, { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(data, null, 2)}\n`, "utf8");
  console.log(`Wrote GitHub commit data to ${path.relative(process.cwd(), outputFile)}`);
} catch (error) {
  console.error(error);
  process.exitCode = 1;
}

async function fetchJson(url) {
  const response = await fetch(url, { headers });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub API request failed: ${response.status} ${response.statusText}\n${body}`);
  }

  return response.json();
}

function toLatestCommit(commit) {
  const sha = commit.sha || "";
  const author = commit.author || commit.commit?.author || {};
  const commitAuthor = commit.commit?.author || {};

  return {
    sha,
    shortSha: sha.slice(0, 7),
    message: firstLine(commit.commit?.message || ""),
    authorName: author.login || commitAuthor.name || "Unknown",
    authorAvatar: author.avatar_url || "",
    date: commitAuthor.date || commit.commit?.committer?.date || "",
    url: commit.html_url || `${repoUrl}/commit/${sha}`,
  };
}

function firstLine(value) {
  return value.split(/\r?\n/, 1)[0];
}

function createActivityRange(start, end) {
  const days = [];

  for (let date = new Date(start); date <= end; date = addDays(date, 1)) {
    days.push({ date: formatDate(date), count: 0 });
  }

  return days;
}

function startOfUtcDay(value) {
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth(), value.getUTCDate()));
}

function addDays(value, days) {
  const date = new Date(value);
  date.setUTCDate(date.getUTCDate() + days);
  return date;
}

function toDateKey(value) {
  return value ? formatDate(new Date(value)) : "";
}

function formatDate(value) {
  return value.toISOString().slice(0, 10);
}
