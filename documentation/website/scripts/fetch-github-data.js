import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUTPUT_DIR = path.resolve(__dirname, '../public/data');
const OUTPUT_FILE = path.join(OUTPUT_DIR, 'github-data.json');

// Using the provided repo info or env vars
const REPO_OWNER = 'JoyinJoester';
const REPO_NAME = 'Monica';

async function fetchData() {
    try {
        const headers = {
            'User-Agent': 'Monica-Website-Builder',
            'Accept': 'application/vnd.github.v3+json'
        };

        // Add token if available (essential for higher rate limits in CI)
        if (process.env.GITHUB_TOKEN) {
            headers['Authorization'] = `token ${process.env.GITHUB_TOKEN}`;
        }

        console.log(`Fetching data for ${REPO_OWNER}/${REPO_NAME}...`);

        // 1. Fetch Commits (Fetch more to calculate 30-day trend)
        const commitsRes = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/commits?per_page=100`, { headers });
        if (!commitsRes.ok) throw new Error(`Commits fetch failed: ${commitsRes.status}`);
        const commitsRaw = await commitsRes.json();

        const commits = commitsRaw.map(c => ({
            sha: c.sha.substring(0, 7),
            message: c.commit.message,
            date: c.commit.author.date,
            author: c.commit.author.name,
            url: c.html_url
        }));

        // 2. Fetch Contributors
        const contribsRes = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/contributors`, { headers });
        if (!contribsRes.ok) throw new Error(`Contributors fetch failed: ${contribsRes.status}`);
        const contribsRaw = await contribsRes.json();

        // Calculate Ratio
        const totalContributions = contribsRaw.reduce((acc, curr) => acc + curr.contributions, 0);
        const contributors = contribsRaw.map(c => ({
            login: c.login,
            avatar_url: c.avatar_url,
            html_url: c.html_url,
            contributions: c.contributions,
            ratio: ((c.contributions / totalContributions) * 100).toFixed(1)
        }));

        // Ensure output dir exists
        if (!fs.existsSync(OUTPUT_DIR)) {
            fs.mkdirSync(OUTPUT_DIR, { recursive: true });
        }

        // Write File
        const data = {
            generated_at: new Date().toISOString(),
            commits,
            contributors
        };

        fs.writeFileSync(OUTPUT_FILE, JSON.stringify(data, null, 2));
        console.log(`Successfully wrote GitHub data to ${OUTPUT_FILE}`);

    } catch (error) {
        console.error('Error fetching GitHub data:', error);
        // Determine if we should fail the build or just write empty data
        // Writing empty data allows build to succeed even if API fails (e.g. rate limit)
        // But for CI, we generally want to know.
        // Let's write a fallback structure.
        if (!fs.existsSync(OUTPUT_FILE)) {
            fs.writeFileSync(OUTPUT_FILE, JSON.stringify({ commits: [], contributors: [] }));
            console.log('Wrote empty fallback data.');
        }
    }
}

fetchData();
