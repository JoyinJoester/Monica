/* main.js */

// 1. Lightbox Logic
export function initLightbox() {
    const lightbox = document.getElementById('lightbox');
    const lightboxImg = document.getElementById('lightbox-img');

    if (!lightbox || !lightboxImg) return;

    // Find all images that should have lightbox behavior
    // This includes images in markdown body and specific showcase images
    const images = document.querySelectorAll('.markdown-body img, .glass-card[src], .app-mockup');

    images.forEach(img => {
        img.style.cursor = 'zoom-in';
        img.addEventListener('click', (e) => {
            e.preventDefault(); // Prevent default link behavior if wrapped
            e.stopPropagation();
            lightboxImg.src = img.src;
            lightbox.classList.add('active');
        });
    });

    // Close behavior
    lightbox.addEventListener('click', () => {
        lightbox.classList.remove('active');
    });
}

// 2. Header Scroll Effect (Glass gets stronger on scroll)
function initScrollEffect() {
    const header = document.querySelector('header');
    if (!header) return;

    window.addEventListener('scroll', () => {
        if (window.scrollY > 50) {
            header.style.background = 'var(--glass-bg)';
            header.style.boxShadow = '0 4px 20px rgba(0,0,0,0.1)';
            header.style.backdropFilter = 'blur(20px)';
        } else {
            header.style.background = 'transparent';
            header.style.boxShadow = 'none';
            header.style.backdropFilter = 'blur(12px)';
        }
    });
}

// 3. I18n Logic
const LangManager = {
    currentLang: localStorage.getItem('monica_lang') || 'zh',
    locales: {},

    async init() {
        await this.loadLocale(this.currentLang);
        this.updateUI();
        this.bindEvents();
    },

    async loadLocale(lang) {
        try {
            // In Vite dev, reference public/locales. In prod, base path handles it manually or via root
            // We rely on relative path from root
            const res = await fetch(`./locales/${lang}.json`);
            this.locales[lang] = await res.json();
        } catch (e) {
            console.error('Failed to load locale:', e);
        }
    },

    updateUI() {
        const data = this.locales[this.currentLang];
        if (!data) return;

        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            // key like "hero.title", retrieve nested
            const text = key.split('.').reduce((obj, k) => obj && obj[k], data);
            if (text) el.innerHTML = text;
        });

        document.getElementById('current-lang').textContent = this.currentLang.toUpperCase();
    },

    bindEvents() {
        const btn = document.getElementById('lang-toggle');
        if (btn) {
            btn.onclick = async () => {
                const newLang = this.currentLang === 'zh' ? 'en' : 'zh';
                await this.loadLocale(newLang);
                this.currentLang = newLang;
                localStorage.setItem('monica_lang', newLang);
                this.updateUI();
            };
        }
    }
};

// Chart Logic
function renderChart(commits) {
    const canvas = document.getElementById('commit-chart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();

    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    // 1. Process Data (Buckets of 5 days for last 30 days)
    const now = new Date();
    const buckets = new Array(6).fill(0);
    const labels = [];

    // Create labels (reverse chron)
    for (let i = 5; i >= 0; i--) {
        const d = new Date(now);
        d.setDate(d.getDate() - (i * 5));
        labels.push(`${d.getMonth() + 1}/${d.getDate()}`);
    }

    // Fill buckets
    commits.forEach(c => {
        const date = new Date(c.date);
        const diffTime = Math.abs(now - date);
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        if (diffDays <= 30) {
            const bucketIndex = 5 - Math.floor((diffDays - 1) / 5);
            if (bucketIndex >= 0 && bucketIndex < 6) {
                buckets[bucketIndex]++;
            }
        }
    });

    // 2. Draw Chart
    const padding = 40;
    const width = rect.width;
    const height = rect.height;
    const chartWidth = width - padding * 2;
    const chartHeight = height - padding * 2;
    const maxVal = Math.max(...buckets, 5); // Min max is 5 for scale

    ctx.clearRect(0, 0, width, height);

    // Draw Grid & Labels
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.font = '10px Inter';
    ctx.textAlign = 'center';

    // X Axis
    labels.forEach((label, i) => {
        const x = padding + (i / 5) * chartWidth;
        const y = height - padding + 20;
        ctx.fillText(label, x, y);
    });

    // Draw Line
    ctx.beginPath();
    buckets.forEach((val, i) => {
        const x = padding + (i / 5) * chartWidth;
        const normalizedVal = val / maxVal;
        const y = height - padding - (normalizedVal * chartHeight);

        if (i === 0) ctx.moveTo(x, y);
        else {
            // Spline curve
            const prevX = padding + ((i - 1) / 5) * chartWidth;
            const prevVal = buckets[i - 1] / maxVal;
            const prevY = height - padding - (prevVal * chartHeight);

            const cp1x = prevX + (x - prevX) / 2;
            const cp1y = prevY;
            const cp2x = prevX + (x - prevX) / 2;
            const cp2y = y;

            ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y);
        }
    });

    // Stroke
    const gradient = ctx.createLinearGradient(0, 0, width, 0);
    gradient.addColorStop(0, '#006495');
    gradient.addColorStop(1, '#00a8cc');
    ctx.strokeStyle = gradient;
    ctx.lineWidth = 3;
    ctx.stroke();

    // Fill area
    ctx.lineTo(padding + chartWidth, height - padding);
    ctx.lineTo(padding, height - padding);
    ctx.closePath();
    ctx.fillStyle = 'rgba(0, 168, 204, 0.1)';
    ctx.fill();

    // Draw Points
    buckets.forEach((val, i) => {
        const x = padding + (i / 5) * chartWidth;
        const normalizedVal = val / maxVal;
        const y = height - padding - (normalizedVal * chartHeight);

        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#fff';
        ctx.fill();

        // Value label
        ctx.fillStyle = 'rgba(255,255,255,0.8)';
        ctx.fillText(val, x, y - 10);
    });
}

// 4. Github Data Render
async function renderGithubData() {
    const commitTimeline = document.getElementById('commit-timeline');
    const contribList = document.getElementById('contributors-list');

    try {
        const res = await fetch('./data/github-data.json');
        if (!res.ok) return; // Fallback or silent fail
        const data = await res.json();

        // Render Commits
        if (data.commits && data.commits.length > 0) {
            // Show only first 6 for timeline
            commitTimeline.innerHTML = data.commits.slice(0, 6).map((c, i) => `
            <div style="display: flex; gap: 12px; position: relative;">
                <div style="width: 2px; background: var(--sys-light-outline-variant); margin-left: 6px;"></div>
                <div style="flex: 1; padding-bottom: 20px;">
                    <div style="position: absolute; left: 0; width: 14px; height: 14px; background: var(--sys-light-primary); border-radius: 50%; border: 2px solid white;"></div>
                    <div style="margin-left: 10px;">
                        <div style="font-size: 0.85rem; color: var(--sys-light-secondary);">${new Date(c.date).toLocaleDateString()}</div>
                        <div style="font-weight: 500; margin-top: 4px;">${c.message}</div>
                        <a href="${c.url}" target="_blank" style="font-size: 0.8rem; color: var(--sys-light-primary); opacity: 0.8;">${c.sha} by ${c.author}</a>
                    </div>
                </div>
            </div>
        `).join('');

            // Render Chart
            renderChart(data.commits);
        }

        // Render Contributors
        if (data.contributors && data.contributors.length > 0) {
            contribList.innerHTML = data.contributors.map(c => `
            <a href="${c.html_url}" target="_blank" title="${c.login}: ${c.contributions} commits (${c.ratio}%)" style="position: relative; transition: transform 0.2s;">
                <img src="${c.avatar_url}" style="width: 48px; height: 48px; border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                <div style="position: absolute; bottom: 0; right: 0; background: var(--sys-light-primary); color: white; font-size: 10px; padding: 1px 4px; border-radius: 4px;">${c.ratio}%</div>
            </a>
        `).join('');
        }

    } catch (e) {
        console.log('No GitHub data found, skipping render.', e);
    }
}

// 5. Initialize
document.addEventListener('DOMContentLoaded', () => {
    initLightbox();
    initScrollEffect();
    LangManager.init();
    renderGithubData(); // Try to render if data exists (it might not in dev until script run)

    // Greeting Console
    console.log('%c Monica ', 'background: #006495; color: #fff; border-radius: 4px; padding: 4px;', 'Welcome to the safe zone.');
});
