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

// 4. Github Data Render
async function renderGithubData() {
    const commitTimeline = document.getElementById('commit-timeline');
    const contribList = document.getElementById('contributors-list');

    if (!commitTimeline || !contribList) return;

    try {
        const res = await fetch('./data/github-data.json');
        if (!res.ok) return; // Fallback or silent fail
        const data = await res.json();

        // Render Commits
        if (data.commits && data.commits.length > 0) {
            commitTimeline.innerHTML = data.commits.map((c, i) => `
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
        console.log('No GitHub data found, skipping render.');
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
