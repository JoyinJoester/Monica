/**
 * Monica Autofill Content Script
 * Detects login forms and shows autofill popup
 */

(function () {
    'use strict';

    console.log('[Monica Autofill] Script executing...');

    // i18n translations
    const i18n = {
        en: {
            close: 'Close',
            loading: 'Loading...',
            noPasswords: 'No saved passwords',
            matched: 'Matched',
            others: 'Others',
            allPasswords: 'All Passwords',
            showAll: 'Show All Passwords',
            cannotLoad: 'Cannot load passwords',
            searchPlaceholder: 'Search passwords...',
            noResults: 'No matching passwords',
            savePromptTitle: 'Save Password?',
            savePromptDesc: 'Would you like to save this password to Monica?',
            website: 'Website',
            username: 'Username',
            password: 'Password',
            save: 'Save',
            dontSave: "Don't Save",
            saved: 'Saved!'
        },
        zh: {
            close: '关闭',
            loading: '检测中...',
            noPasswords: '没有保存的密码',
            matched: '匹配的密码',
            others: '其他密码',
            allPasswords: '所有密码',
            showAll: '显示所有密码',
            cannotLoad: '无法获取密码',
            searchPlaceholder: '搜索密码...',
            noResults: '没有匹配的密码',
            savePromptTitle: '保存密码？',
            savePromptDesc: '是否将此密码保存到 Monica？',
            website: '网站',
            username: '账号',
            password: '密码',
            save: '保存',
            dontSave: '不保存',
            saved: '已保存！'
        }
    };

    // State
    let popupContainer = null;
    let miniButton = null;
    let isPopupVisible = false;
    let isCollapsed = false;
    let currentPasswords = [];
    let matchedPasswords = [];
    let usernameField = null;
    let passwordField = null;
    let currentLang = 'en';
    let searchTerm = '';
    let savePromptContainer = null;
    let pendingCredentials = null;

    // Get translation
    function t(key) {
        const lang = currentLang.startsWith('zh') ? 'zh' : 'en';
        return i18n[lang][key] || i18n.en[key] || key;
    }

    // Create mini floating button
    function createMiniButton() {
        const btn = document.createElement('div');
        btn.id = 'monica-mini-btn';
        btn.innerHTML = `<img src="${chrome.runtime.getURL('icons/icon.png')}" alt="Monica" />`;

        const style = document.createElement('style');
        style.textContent = `
            #monica-mini-btn {
                position: fixed;
                top: 16px;
                right: 16px;
                width: 48px;
                height: 48px;
                background: #667eea;
                border-radius: 50%;
                box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
                z-index: 2147483647;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: transform 0.2s, box-shadow 0.2s;
            }
            #monica-mini-btn:hover {
                transform: scale(1.1);
                box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
            }
            #monica-mini-btn img {
                width: 28px;
                height: 28px;
                border-radius: 4px;
            }
        `;
        document.head.appendChild(style);

        btn.addEventListener('click', () => {
            isCollapsed = false;
            hideMiniButton();
            showPopup();
        });

        return btn;
    }

    function showMiniButton() {
        if (!miniButton) {
            miniButton = createMiniButton();
        }
        if (!document.body.contains(miniButton)) {
            document.body.appendChild(miniButton);
        }
    }

    function hideMiniButton() {
        if (miniButton && document.body.contains(miniButton)) {
            miniButton.remove();
        }
    }

    // Detect login forms on page
    function detectLoginForm() {
        const passwordFields = document.querySelectorAll('input[type="password"]:not([aria-hidden="true"])');

        if (passwordFields.length === 0) {
            return { username: null, password: null };
        }

        const password = passwordFields[0];
        let username = null;

        // Strategy 1: Look for common username field patterns (expanded)
        const usernameSelectors = [
            'input[type="email"]',
            'input[autocomplete="username"]',
            'input[autocomplete="email"]',
            'input[name*="user"]',
            'input[name*="email"]',
            'input[name*="login"]',
            'input[name*="account"]',
            'input[name*="phone"]',
            'input[name*="mobile"]',
            'input[id*="user"]',
            'input[id*="email"]',
            'input[id*="login"]',
            'input[id*="account"]',
            'input[id*="phone"]',
            'input[name*="name"]',
            'input[placeholder*="用户"]',
            'input[placeholder*="邮箱"]',
            'input[placeholder*="手机"]',
            'input[placeholder*="账号"]',
            'input[placeholder*="email"]',
            'input[placeholder*="user"]',
        ];

        for (const selector of usernameSelectors) {
            try {
                const field = document.querySelector(selector);
                if (field && field.type !== 'password' && field.type !== 'hidden' && isVisible(field)) {
                    username = field;
                    break;
                }
            } catch (e) {
                // Ignore invalid selector errors
            }
        }

        // Strategy 2: Look for text input near password field (within same form)
        if (!username && password.form) {
            const formInputs = Array.from(password.form.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"])'));
            const passwordIndex = formInputs.indexOf(password);

            // Try inputs before password field
            for (let i = passwordIndex - 1; i >= 0; i--) {
                const input = formInputs[i];
                if ((input.type === 'text' || input.type === 'email' || input.type === 'tel') && isVisible(input)) {
                    username = input;
                    break;
                }
            }
        }

        // Strategy 3: Look for any visible text/email input on page before password
        if (!username) {
            const allInputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"]');
            for (const input of allInputs) {
                if (isVisible(input) && input.type !== 'password') {
                    // Check if this input appears before password field in DOM
                    const passwordRect = password.getBoundingClientRect();
                    const inputRect = input.getBoundingClientRect();
                    if (inputRect.top < passwordRect.top ||
                        (Math.abs(inputRect.top - passwordRect.top) < 100 && inputRect.left < passwordRect.left)) {
                        username = input;
                        break;
                    }
                }
            }
        }

        console.log('[Monica Autofill] Detected fields:', {
            username: username?.name || username?.id || 'none',
            password: password?.name || password?.id || 'found'
        });

        return { username, password };
    }

    // Check if element is visible
    function isVisible(element) {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        return style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            element.offsetWidth > 0 &&
            element.offsetHeight > 0;
    }

    // Create popup UI
    function createPopup() {
        const container = document.createElement('div');
        container.id = 'monica-autofill-popup';
        container.innerHTML = `
            <div class="monica-popup-header">
                <img src="${chrome.runtime.getURL('icons/icon.png')}" alt="Monica" class="monica-logo" />
                <span class="monica-title">Monica</span>
                <button class="monica-close-btn" title="Close">×</button>
            </div>
            <div class="monica-search-bar">
                <input type="text" class="monica-search-input" placeholder="" />
            </div>
            <div class="monica-popup-content">
                <div class="monica-loading">Loading...</div>
            </div>
        `;

        // Add styles
        const style = document.createElement('style');
        style.textContent = `
            #monica-autofill-popup {
                position: fixed;
                top: 16px;
                right: 16px;
                width: 320px;
                max-height: 450px;
                background: #1a1a2e;
                border-radius: 12px;
                box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
                z-index: 2147483647;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                color: #e0e0e0;
                overflow: hidden;
                animation: monica-slide-in 0.3s ease;
            }

            @keyframes monica-slide-in {
                from { opacity: 0; transform: translateY(-20px); }
                to { opacity: 1; transform: translateY(0); }
            }

            .monica-popup-header {
                display: flex;
                align-items: center;
                padding: 12px 16px;
                background: #667eea;
                gap: 8px;
            }

            .monica-logo { width: 24px; height: 24px; border-radius: 6px; }
            .monica-title { flex: 1; font-size: 14px; font-weight: 600; color: white; }
            .monica-close-btn { background: none; border: none; color: white; font-size: 20px; cursor: pointer; padding: 0 4px; opacity: 0.8; }
            .monica-close-btn:hover { opacity: 1; }
            
            .monica-search-bar { padding: 8px 12px; background: #252538; display: none; }
            .monica-search-bar.visible { display: block; }
            .monica-search-input {
                width: 100%;
                padding: 10px 12px;
                border: 1px solid #3a3a5e;
                border-radius: 8px;
                background: #1a1a2e;
                color: #e0e0e0;
                font-size: 13px;
                outline: none;
                box-sizing: border-box;
            }
            .monica-search-input:focus {
                border-color: #667eea;
            }
            .monica-search-input::placeholder {
                color: #666;
            }
            
            .monica-popup-content { max-height: 300px; overflow-y: auto; }
            .monica-loading { padding: 24px; text-align: center; color: #888; }
            .monica-section-title { padding: 8px 16px; font-size: 11px; font-weight: 600; color: #888; text-transform: uppercase; background: #252538; }
            .monica-password-item { display: flex; align-items: center; padding: 12px 16px; cursor: pointer; transition: background 0.2s; border-bottom: 1px solid #2a2a3e; }
            .monica-password-item:hover { background: #2a2a3e; }
            .monica-password-icon { width: 36px; height: 36px; border-radius: 8px; background: #667eea; display: flex; align-items: center; justify-content: center; color: white; font-weight: 600; font-size: 14px; margin-right: 12px; }
            .monica-password-info { flex: 1; overflow: hidden; }
            .monica-password-title { font-size: 14px; font-weight: 500; color: #e0e0e0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .monica-password-username { font-size: 12px; color: #888; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .monica-empty { padding: 24px; text-align: center; color: #666; }
            .monica-show-all { display: block; width: 100%; padding: 12px; background: none; border: none; border-top: 1px solid #2a2a3e; color: #667eea; font-size: 13px; cursor: pointer; }
            .monica-show-all:hover { background: #2a2a3e; }
        `;
        document.head.appendChild(style);

        container.querySelector('.monica-close-btn').addEventListener('click', hidePopup);

        // Search input handler
        const searchInput = container.querySelector('.monica-search-input');
        searchInput.addEventListener('input', (e) => {
            searchTerm = e.target.value.toLowerCase();
            renderPasswords(true); // Show all when searching
        });

        return container;
    }

    // Update search placeholder when language is set
    function updateSearchPlaceholder() {
        const searchInput = popupContainer?.querySelector('.monica-search-input');
        if (searchInput) {
            searchInput.placeholder = t('searchPlaceholder');
        }
    }

    // Render passwords in popup
    function renderPasswords(showAll) {
        const content = popupContainer ? popupContainer.querySelector('.monica-popup-content') : null;
        const searchBar = popupContainer?.querySelector('.monica-search-bar');
        if (!content) return;

        // Filter by search term
        let passwordsToShow = currentPasswords;
        let matchedToShow = matchedPasswords;

        if (searchTerm) {
            passwordsToShow = currentPasswords.filter(p =>
                p.title.toLowerCase().includes(searchTerm) ||
                p.username.toLowerCase().includes(searchTerm) ||
                (p.website && p.website.toLowerCase().includes(searchTerm))
            );
            matchedToShow = matchedPasswords.filter(p =>
                p.title.toLowerCase().includes(searchTerm) ||
                p.username.toLowerCase().includes(searchTerm) ||
                (p.website && p.website.toLowerCase().includes(searchTerm))
            );
        }

        const hasMatched = matchedToShow.length > 0;

        // Show search bar only when no match or showing all passwords
        if (searchBar) {
            if (!hasMatched || showAll || searchTerm) {
                searchBar.classList.add('visible');
            } else {
                searchBar.classList.remove('visible');
            }
        }

        if (currentPasswords.length === 0) {
            content.innerHTML = '<div class="monica-empty">' + t('noPasswords') + '</div>';
            return;
        }

        if (searchTerm && passwordsToShow.length === 0) {
            content.innerHTML = '<div class="monica-empty">' + t('noResults') + '</div>';
            return;
        }

        let html = '';

        if (searchTerm) {
            // When searching, show all filtered results
            html += passwordsToShow.map(createPasswordItemHtml).join('');
        } else if (hasMatched && !showAll) {
            html += '<div class="monica-section-title">' + t('matched') + '</div>';
            html += matchedToShow.map(createPasswordItemHtml).join('');
            if (currentPasswords.length > matchedPasswords.length) {
                html += '<button class="monica-show-all">' + t('showAll') + '</button>';
            }
        } else if (showAll || !hasMatched) {
            if (hasMatched) {
                html += '<div class="monica-section-title">' + t('matched') + '</div>';
                html += matchedToShow.map(createPasswordItemHtml).join('');
                html += '<div class="monica-section-title">' + t('others') + '</div>';
                const otherPasswords = passwordsToShow.filter(p => !matchedToShow.find(m => m.id === p.id));
                html += otherPasswords.map(createPasswordItemHtml).join('');
            } else {
                html += '<div class="monica-section-title">' + t('allPasswords') + '</div>';
                html += passwordsToShow.map(createPasswordItemHtml).join('');
            }
        }

        content.innerHTML = html;

        // Add click handlers
        content.querySelectorAll('.monica-password-item').forEach(item => {
            item.addEventListener('click', () => {
                const id = parseInt(item.getAttribute('data-id') || '0');
                const password = currentPasswords.find(p => p.id === id);
                if (password) fillCredentials(password);
            });
        });

        const showAllBtn = content.querySelector('.monica-show-all');
        if (showAllBtn) {
            showAllBtn.addEventListener('click', () => renderPasswords(true));
        }
    }

    function createPasswordItemHtml(p) {
        const initial = (p.title || 'U')[0].toUpperCase();
        return `
            <div class="monica-password-item" data-id="${p.id}">
                <div class="monica-password-icon">${initial}</div>
                <div class="monica-password-info">
                    <div class="monica-password-title">${escapeHtml(p.title)}</div>
                    <div class="monica-password-username">${escapeHtml(p.username)}</div>
                </div>
            </div>
        `;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Fill credentials into form
    function fillCredentials(password) {
        if (usernameField && password.username) {
            usernameField.value = password.username;
            usernameField.dispatchEvent(new Event('input', { bubbles: true }));
            usernameField.dispatchEvent(new Event('change', { bubbles: true }));
        }

        if (passwordField && password.password) {
            passwordField.value = password.password;
            passwordField.dispatchEvent(new Event('input', { bubbles: true }));
            passwordField.dispatchEvent(new Event('change', { bubbles: true }));
        }

        hidePopup();
    }

    // Show popup
    function showPopup() {
        if (isPopupVisible) return;

        if (!popupContainer) {
            popupContainer = createPopup();
        }

        document.body.appendChild(popupContainer);
        isPopupVisible = true;

        // Request passwords from background
        chrome.runtime.sendMessage(
            { type: 'GET_PASSWORDS_FOR_AUTOFILL', url: window.location.href },
            (response) => {
                if (response && response.success) {
                    currentLang = response.lang || 'en';
                    searchTerm = ''; // Reset search
                    updateSearchPlaceholder();
                    currentPasswords = response.passwords;
                    matchedPasswords = response.matchedPasswords;
                    renderPasswords(false);
                } else {
                    const content = popupContainer ? popupContainer.querySelector('.monica-popup-content') : null;
                    if (content) {
                        content.innerHTML = '<div class="monica-empty">' + t('cannotLoad') + '</div>';
                    }
                }
            }
        );
    }

    // Hide popup and show mini button
    function hidePopup() {
        if (popupContainer && isPopupVisible) {
            popupContainer.remove();
            isPopupVisible = false;
            isCollapsed = true;
            showMiniButton();
        }
    }

    // Check for login form
    function checkForLoginForm() {
        const { username, password } = detectLoginForm();
        console.log('[Monica Autofill] Form detection:', { foundPassword: !!password, foundUsername: !!username });

        if (password) {
            usernameField = username;
            passwordField = password;
            console.log('[Monica Autofill] Login form detected!');

            setTimeout(() => {
                if (!isPopupVisible && !isCollapsed) {
                    console.log('[Monica Autofill] Showing popup');
                    showPopup();
                }
            }, 500);
        }
    }

    // ========== Save Password Prompt ==========

    // Create save password prompt UI
    function createSavePrompt(credentials) {
        const container = document.createElement('div');
        container.id = 'monica-save-prompt';
        container.innerHTML = `
            <div class="monica-save-header">
                <img src="${chrome.runtime.getURL('icons/icon.png')}" alt="Monica" class="monica-logo" />
                <span class="monica-title">${t('savePromptTitle')}</span>
                <button class="monica-close-btn" title="${t('close')}">×</button>
            </div>
            <div class="monica-save-content">
                <p class="monica-save-desc">${t('savePromptDesc')}</p>
                <div class="monica-save-field">
                    <label>${t('website')}</label>
                    <span>${escapeHtml(credentials.website)}</span>
                </div>
                <div class="monica-save-field">
                    <label>${t('username')}</label>
                    <span>${escapeHtml(credentials.username)}</span>
                </div>
                <div class="monica-save-field">
                    <label>${t('password')}</label>
                    <span>••••••••</span>
                </div>
                <div class="monica-save-actions">
                    <button class="monica-btn-secondary">${t('dontSave')}</button>
                    <button class="monica-btn-primary">${t('save')}</button>
                </div>
            </div>
        `;

        // Add styles
        const style = document.createElement('style');
        style.id = 'monica-save-prompt-style';
        if (!document.getElementById('monica-save-prompt-style')) {
            style.textContent = `
                #monica-save-prompt {
                    position: fixed;
                    top: 16px;
                    right: 16px;
                    width: 320px;
                    background: #1a1a2e;
                    border-radius: 12px;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
                    z-index: 2147483647;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    color: #e0e0e0;
                    overflow: hidden;
                    animation: monica-slide-in 0.3s ease;
                }
                .monica-save-header {
                    display: flex;
                    align-items: center;
                    padding: 12px 16px;
                    background: #667eea;
                    gap: 8px;
                }
                .monica-save-content {
                    padding: 16px;
                }
                .monica-save-desc {
                    margin: 0 0 16px 0;
                    font-size: 13px;
                    color: #a0a0a0;
                }
                .monica-save-field {
                    display: flex;
                    justify-content: space-between;
                    padding: 8px 0;
                    border-bottom: 1px solid #2a2a3e;
                    font-size: 13px;
                }
                .monica-save-field label {
                    color: #888;
                }
                .monica-save-field span {
                    color: #e0e0e0;
                    max-width: 180px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
                .monica-save-actions {
                    display: flex;
                    gap: 12px;
                    margin-top: 16px;
                }
                .monica-btn-primary, .monica-btn-secondary {
                    flex: 1;
                    padding: 10px 16px;
                    border-radius: 8px;
                    font-size: 14px;
                    font-weight: 500;
                    cursor: pointer;
                    border: none;
                    transition: opacity 0.2s;
                }
                .monica-btn-primary {
                    background: #667eea;
                    color: white;
                }
                .monica-btn-secondary {
                    background: #2a2a3e;
                    color: #a0a0a0;
                }
                .monica-btn-primary:hover, .monica-btn-secondary:hover {
                    opacity: 0.9;
                }
            `;
            document.head.appendChild(style);
        }

        // Event handlers
        container.querySelector('.monica-close-btn').addEventListener('click', hideSavePrompt);
        container.querySelector('.monica-btn-secondary').addEventListener('click', hideSavePrompt);
        container.querySelector('.monica-btn-primary').addEventListener('click', () => {
            savePassword(credentials);
        });

        return container;
    }

    function showSavePrompt(credentials) {
        hideSavePrompt(); // Remove any existing
        pendingCredentials = credentials;
        savePromptContainer = createSavePrompt(credentials);
        document.body.appendChild(savePromptContainer);
    }

    function hideSavePrompt() {
        if (savePromptContainer) {
            savePromptContainer.remove();
            savePromptContainer = null;
        }
        pendingCredentials = null;
    }

    function savePassword(credentials) {
        // Show success message immediately
        const content = savePromptContainer?.querySelector('.monica-save-content');
        if (content) {
            content.innerHTML = `<div style="text-align: center; padding: 24px; color: #4caf50; font-size: 16px;">✓ ${t('saved')}</div>`;
        }

        console.log('[Monica] Saving password directly to storage:', credentials);

        savePasswordToStorage(credentials)
            .then((newItem) => {
                console.log('[Monica] Save successful:', newItem);
                setTimeout(hideSavePrompt, 800);
            })
            .catch((err) => {
                console.error('[Monica] Save failed:', err);
                alert('Monica Save Error: ' + (err.message || 'Unknown error'));
                hideSavePrompt();
            });
    }

    // Direct storage access to bypass background script update issues
    function savePasswordToStorage(credentials) {
        return new Promise((resolve, reject) => {
            const STORAGE_KEY = 'monica_vault';
            chrome.storage.local.get(STORAGE_KEY, (result) => {
                if (chrome.runtime.lastError) {
                    return reject(chrome.runtime.lastError);
                }

                try {
                    const rawItems = result[STORAGE_KEY];
                    const items = Array.isArray(rawItems) ? rawItems : [];

                    // Generate new ID
                    const maxId = items.reduce((max, item) => Math.max(max, item.id || 0), 0);

                    // Create item matching SecureItem interface (Source of Truth)
                    const newItem = {
                        id: maxId + 1,
                        itemType: 0, // ItemType.Password
                        title: credentials.title || credentials.website,
                        notes: '',
                        isFavorite: false,
                        sortOrder: items.length,
                        createdAt: new Date().toISOString(),
                        updatedAt: new Date().toISOString(),
                        itemData: {
                            username: credentials.username,
                            password: credentials.password,
                            website: credentials.website,
                            categoryId: undefined
                        }
                    };

                    items.push(newItem);

                    chrome.storage.local.set({ [STORAGE_KEY]: items }, () => {
                        if (chrome.runtime.lastError) {
                            reject(chrome.runtime.lastError);
                        } else {
                            // Update local cache to prevent re-prompting on this page
                            currentPasswords.push({
                                id: newItem.id,
                                title: newItem.title,
                                username: newItem.itemData.username,
                                password: newItem.itemData.password,
                                website: newItem.itemData.website
                            });
                            resolve(newItem);
                        }
                    });
                } catch (e) {
                    reject(e);
                }
            });
        });
    }

    // Escape HTML for display
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text || '';
        return div.innerHTML;
    }

    // Detect form submission to prompt save
    function setupFormSubmitDetection() {
        document.addEventListener('submit', handleFormSubmit, true);

        // Also listen for button clicks that might submit forms
        document.addEventListener('click', (e) => {
            const target = e.target;
            if (target.tagName === 'BUTTON' &&
                (target.type === 'submit' || target.closest('form'))) {
                setTimeout(() => checkAndPromptSave(), 100);
            }
        }, true);
    }

    function handleFormSubmit(e) {
        setTimeout(() => checkAndPromptSave(), 100);
    }

    function checkAndPromptSave() {
        if (!usernameField && !passwordField) return;

        const username = usernameField?.value || '';
        const password = passwordField?.value || '';

        if (!password || password.length < 4) return; // Must have password

        const website = window.location.hostname;

        // Check if this credential already exists
        const exists = currentPasswords.some(p =>
            p.username.toLowerCase() === username.toLowerCase() &&
            p.website?.includes(website)
        );

        if (!exists) {
            console.log('[Monica Autofill] New credentials detected, prompting save');
            showSavePrompt({
                website: window.location.origin,
                title: website,
                username,
                password
            });
        }
    }

    // Initialize
    function init() {
        console.log('[Monica Autofill] Content script loaded!');

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => {
                checkForLoginForm();
                setupFormSubmitDetection();
            });
        } else {
            checkForLoginForm();
            setupFormSubmitDetection();
        }

        // Also check after dynamic content loads
        const observer = new MutationObserver(() => {
            if (!isPopupVisible) {
                checkForLoginForm();
            }
        });

        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
        }
    }

    // Start
    init();
})();
