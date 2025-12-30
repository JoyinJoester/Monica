/**
 * Monica Autofill Content Script
 * Detects login forms, injects icons, and shows autofill popup
 */

// Prevent duplicate injection - if already loaded, do nothing
if ((window as any).__monica_content_loaded__) {
  console.log('[Monica] Content script already loaded, skipping');
} else {
  (window as any).__monica_content_loaded__ = true;

  // Types
  interface PasswordItem {
    id: number;
    title: string;
    username: string;
    password: string;
    website: string;
    itemData?: any;
  }

  interface AutofillMessage {
    type: 'GET_PASSWORDS_FOR_AUTOFILL' | 'CHECK_PASSWORD_FORM' | 'FILL_CREDENTIALS';
    url?: string;
    username?: string;
    password?: string;
  }

  interface AutofillResponse {
    success?: boolean;
    hasPasswordForm?: boolean;
    passwords?: PasswordItem[];
    matchedPasswords?: PasswordItem[];
  }

  // State
  let popupContainer: HTMLElement | null = null;
  let isPopupVisible = false;
  let matchedPasswords: PasswordItem[] = [];
  let activeInput: HTMLInputElement | null = null; // The input that triggered the popup
  let trackedInputs: Map<HTMLInputElement, HTMLElement> = new Map(); // Input -> Icon Element

  // Helper: Check if element is visible
  function isVisible(elem: HTMLElement) {
    if (!elem) return false;
    return !!(elem.offsetWidth || elem.offsetHeight || elem.getClientRects().length);
  }

  // 1. Icon Injection Logic
  function updateIconPosition(input: HTMLInputElement, icon: HTMLElement) {
    if (!isVisible(input)) {
      icon.style.display = 'none';
      return;
    }

    const rect = input.getBoundingClientRect();
    const iconSize = 20;
    const padding = 6;

    // Update position based on input rect + scroll
    const top = rect.top + window.scrollY + (rect.height - iconSize) / 2;
    const left = rect.right + window.scrollX - iconSize - padding;

    icon.style.display = 'block';
    icon.style.top = `${top}px`;
    icon.style.left = `${left}px`;
  }

  function injectIcon(input: HTMLInputElement) {
    if (trackedInputs.has(input)) return;

    // Use actual Monica icon image
    const icon = document.createElement('img');
    icon.src = chrome.runtime.getURL('icons/icon.png');
    icon.style.cssText = `
    position: absolute;
    z-index: 10000;
    cursor: pointer;
    width: 20px;
    height: 20px;
    border-radius: 4px;
    opacity: 0.6;
    transition: opacity 0.2s, transform 0.15s;
  `;

    // Hover effect
    icon.onmouseenter = () => {
      icon.style.opacity = '1';
      icon.style.transform = 'scale(1.1)';
    };
    icon.onmouseleave = () => {
      icon.style.opacity = '0.6';
      icon.style.transform = 'scale(1)';
    };

    // Click handler
    icon.onclick = (e) => {
      e.stopPropagation();
      e.preventDefault();
      activeInput = input;
      showPopup(icon);
    };

    document.body.appendChild(icon);
    trackedInputs.set(input, icon);

    // Initial position
    updateIconPosition(input, icon);

    // Update on events
    const update = () => updateIconPosition(input, icon);
    window.addEventListener('resize', update);
    window.addEventListener('scroll', update, true); // Capture phase for all scrollable parents

    // Observer for layout changes
    const observer = new ResizeObserver(update);
    observer.observe(input);
  }

  // 2. Popup UI Logic
  // State for popup
  let showAllMode = false;
  let allPasswords: PasswordItem[] = [];
  let searchQuery = '';

  function createPopup(): HTMLElement {
    const container = document.createElement('div');
    container.id = 'monica-autofill-popup';
    container.innerHTML = `
    <div class="monica-popup-content">
      <div class="monica-loading">加载中...</div>
    </div>
    <div class="monica-show-all-btn" style="display: none;">
      展示全部密码
    </div>
    <div class="monica-popup-footer">
      <div class="monica-footer-manage">
        <div class="monica-footer-icon">
          <img src="${chrome.runtime.getURL('icons/icon.png')}" />
        </div>
        <span>管理密码...</span>
      </div>
      <svg class="monica-footer-key" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M2 18v3c0 .6.4 1 1 1h4v-3h3v-3h2l1.4-1.4a6.5 6.5 0 1 0-4-4Z"/>
        <circle cx="16.5" cy="7.5" r=".5" fill="currentColor"/>
      </svg>
    </div>
    <div class="monica-search-box" style="display: none;">
      <input type="text" placeholder="搜索密码..." class="monica-search-input" />
    </div>
  `;

    const style = document.createElement('style');
    style.textContent = `
    #monica-autofill-popup {
      position: absolute;
      width: 280px;
      background: #202124;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
      z-index: 2147483647;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      overflow: hidden;
      border: 1px solid #3c4043;
    }
    
    .monica-popup-content {
      max-height: 240px;
      overflow-y: auto;
    }
    
    .monica-password-item {
      display: flex;
      align-items: center;
      padding: 10px 14px;
      cursor: pointer;
      color: #e8eaed;
      gap: 12px;
      transition: background 0.15s;
    }
    .monica-password-item:hover {
      background: #3c4043;
    }

    .monica-item-icon {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      background: transparent;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 500;
      color: #9aa0a6;
      flex-shrink: 0;
    }
    
    .monica-item-info {
      flex: 1;
      overflow: hidden;
    }
    
    .monica-item-title {
      font-size: 13px;
      font-weight: 400;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: #e8eaed;
    }
    
    .monica-item-desc {
      font-size: 11px;
      color: #9aa0a6;
      margin-top: 2px;
    }

    .monica-loading, .monica-empty {
      padding: 20px;
      text-align: center;
      color: #9aa0a6;
      font-size: 12px;
    }
    
    .monica-show-all-btn {
      padding: 10px 14px;
      color: #8ab4f8;
      font-size: 13px;
      cursor: pointer;
      border-top: 1px solid #3c4043;
      transition: background 0.15s;
    }
    .monica-show-all-btn:hover {
      background: #3c4043;
    }
    
    .monica-popup-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 14px;
      border-top: 1px solid #3c4043;
      background: #292a2d;
      cursor: pointer;
      transition: background 0.15s;
    }
    .monica-popup-footer:hover {
      background: #3c4043;
    }
    
    .monica-footer-manage {
      display: flex;
      align-items: center;
      gap: 10px;
      color: #e8eaed;
      font-size: 13px;
    }
    
    .monica-footer-icon {
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .monica-footer-icon img {
      width: 24px;
      height: 24px;
      border-radius: 4px;
    }
    
    .monica-footer-key {
      width: 20px;
      height: 20px;
      color: #8ab4f8;
    }
    
    .monica-search-box {
      padding: 8px 14px;
      border-top: 1px solid #3c4043;
      background: #292a2d;
    }
    
    .monica-search-input {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid #5f6368;
      border-radius: 6px;
      background: #202124;
      color: #e8eaed;
      font-size: 13px;
      outline: none;
    }
    .monica-search-input:focus {
      border-color: #8ab4f8;
    }
    .monica-search-input::placeholder {
      color: #9aa0a6;
    }
    
    /* Scrollbar */
    .monica-popup-content::-webkit-scrollbar {
      width: 6px;
    }
    .monica-popup-content::-webkit-scrollbar-thumb {
      background: #5f6368;
      border-radius: 3px;
    }
    .monica-popup-content::-webkit-scrollbar-track {
      background: transparent;
    }
  `;
    document.head.appendChild(style);

    // Event: Show all passwords
    container.querySelector('.monica-show-all-btn')?.addEventListener('click', handleShowAll);

    // Event: Manage passwords (open extension)
    container.querySelector('.monica-popup-footer')?.addEventListener('click', () => {
      chrome.runtime.sendMessage({ type: 'OPEN_POPUP' });
      hidePopup();
    });

    // Event: Search input
    const searchInput = container.querySelector('.monica-search-input') as HTMLInputElement;
    searchInput?.addEventListener('input', (e) => {
      searchQuery = (e.target as HTMLInputElement).value;
      renderPasswords();
    });

    return container;
  }

  function handleShowAll() {
    showAllMode = true;

    // Show search box, hide footer
    const footer = popupContainer?.querySelector('.monica-popup-footer') as HTMLElement;
    const searchBox = popupContainer?.querySelector('.monica-search-box') as HTMLElement;
    const showAllBtn = popupContainer?.querySelector('.monica-show-all-btn') as HTMLElement;

    if (footer) footer.style.display = 'none';
    if (searchBox) searchBox.style.display = 'block';
    if (showAllBtn) showAllBtn.style.display = 'none';

    // Fetch all passwords
    chrome.runtime.sendMessage({ type: 'GET_ALL_PASSWORDS' }, (response) => {
      if (response?.success) {
        allPasswords = response.passwords || [];
        renderPasswords();

        // Focus search input
        const input = popupContainer?.querySelector('.monica-search-input') as HTMLInputElement;
        input?.focus();
      }
    });
  }

  function renderPasswords() {
    const content = popupContainer?.querySelector('.monica-popup-content');
    const showAllBtn = popupContainer?.querySelector('.monica-show-all-btn') as HTMLElement;
    if (!content) return;

    let passwords = showAllMode ? allPasswords : matchedPasswords;

    // Apply search filter
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      passwords = passwords.filter(p =>
        p.title.toLowerCase().includes(q) ||
        p.username.toLowerCase().includes(q) ||
        p.website.toLowerCase().includes(q)
      );
    }

    // In show all mode, put matched passwords at top
    if (showAllMode && !searchQuery) {
      const matchedIds = new Set(matchedPasswords.map(p => p.id));
      const matched = passwords.filter(p => matchedIds.has(p.id));
      const others = passwords.filter(p => !matchedIds.has(p.id));
      passwords = [...matched, ...others];
    }

    if (passwords.length === 0) {
      content.innerHTML = `<div class="monica-empty">${searchQuery ? '没有搜索结果' : '没有匹配的密码'}</div>`;
      if (showAllBtn) showAllBtn.style.display = 'none';
      return;
    }

    let html = passwords.map(p => {
      const initial = (p.title || p.username || 'U')[0].toUpperCase();
      return `
      <div class="monica-password-item" data-id="${p.id}">
        <div class="monica-item-icon">${initial}</div>
        <div class="monica-item-info">
          <div class="monica-item-title">${escapeHtml(p.username || p.title)}</div>
          <div class="monica-item-desc">••••••••••••</div>
        </div>
      </div>
    `;
    }).join('');

    content.innerHTML = html;

    // Show "show all" button only in normal mode with matches
    if (!showAllMode && matchedPasswords.length > 0) {
      if (showAllBtn) showAllBtn.style.display = 'block';
    }

    content.querySelectorAll('.monica-password-item').forEach(item => {
      item.addEventListener('click', () => {
        const id = parseInt(item.getAttribute('data-id') || '0');
        const password = (showAllMode ? allPasswords : matchedPasswords).find(p => p.id === id);
        if (password) {
          fillCredentials(password);
        }
      });
    });
  }

  function escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function showPopup(anchor: HTMLElement) {
    if (isPopupVisible) {
      // If clicking same anchor, toggle off
      // Logic: check if we want to toggle or move
      // For now simple: just remove old and show new
      hidePopup();
      // If logic needed to toggle: return;
    }

    popupContainer = createPopup();
    document.body.appendChild(popupContainer);
    isPopupVisible = true;

    // Position popup logic
    const rect = anchor.getBoundingClientRect();
    const popupWidth = 300;

    // Default: show below icon, right aligned
    let top = rect.bottom + window.scrollY + 8;
    let left = rect.right + window.scrollX - popupWidth;

    // Adjust if off screen
    if (left < 10) left = 10;

    popupContainer.style.top = `${top}px`;
    popupContainer.style.left = `${left}px`;

    // Fetch passwords
    chrome.runtime.sendMessage<AutofillMessage, AutofillResponse>(
      { type: 'GET_PASSWORDS_FOR_AUTOFILL', url: window.location.href },
      (response) => {
        if (response?.success) {
          matchedPasswords = response.matchedPasswords || [];
          renderPasswords();
        } else {
          const content = popupContainer?.querySelector('.monica-popup-content');
          if (content) content.innerHTML = '<div class="monica-empty">无法获取数据</div>';
        }
      }
    );

    // Close when clicking outside
    document.addEventListener('click', handleOutsideClick);
  }

  function hidePopup() {
    if (popupContainer) {
      popupContainer.remove();
      popupContainer = null;
    }
    isPopupVisible = false;
    document.removeEventListener('click', handleOutsideClick);
  }

  function handleOutsideClick(e: MouseEvent) {
    if (popupContainer && !popupContainer.contains(e.target as Node)) {
      hidePopup();
    }
  }

  // 3. Check and Inject
  function detectAndInject() {
    const inputs = document.querySelectorAll('input');
    inputs.forEach(input => {
      // Check if password or username-like
      const isPassword = input.type === 'password';
      // Simple heuristic for username fields: text/email type and name/id contains user/login/email
      // Or if it is followed immediately by a password field
      const isUsername = (input.type === 'text' || input.type === 'email') &&
        /user|login|email|account/i.test(input.name + input.id + input.placeholder);

      if (isPassword || isUsername) {
        injectIcon(input);
      }
    });

    // Also check explicitly for password fields and their preceding inputs (often username)
    const passwords = document.querySelectorAll('input[type="password"]');
    passwords.forEach(pw => {
      injectIcon(pw as HTMLInputElement);
      // Try to find previous input
      // (Simplified logic for now, reliance on generic loop above is safer for performance)
    });
  }

  // 4. Fill Logic
  function fillCredentials(item: PasswordItem) {
    // We need to find the form fields again relative to activeInput or globally
    let uField: HTMLInputElement | null = null;
    let pField: HTMLInputElement | null = null;

    if (activeInput) {
      if (activeInput.type === 'password') {
        pField = activeInput;
        // try to find username in same form
        if (pField.form) {
          const inputs = Array.from(pField.form.querySelectorAll('input'));
          const idx = inputs.indexOf(pField);
          if (idx > 0) uField = inputs[idx - 1] as HTMLInputElement;
        }
      } else {
        uField = activeInput;
        // try find password
        if (uField.form) {
          const inputs = Array.from(uField.form.querySelectorAll('input'));
          const idx = inputs.indexOf(uField);
          if (idx < inputs.length - 1 && inputs[idx + 1].type === 'password') {
            pField = inputs[idx + 1] as HTMLInputElement;
          }
        }
      }
    }

    // Fallback to global detection if not found
    if (!pField) {
      pField = document.querySelector('input[type="password"]') as HTMLInputElement;
    }

    if (!uField && pField) {
      // Try to find username relative to password field
      if (pField.form) {
        const inputs = Array.from(pField.form.querySelectorAll('input'));
        const idx = inputs.indexOf(pField);
        if (idx > 0 && (inputs[idx - 1].type === 'text' || inputs[idx - 1].type === 'email')) {
          uField = inputs[idx - 1] as HTMLInputElement;
        }
      }
      // If still not found, try common selectors
      if (!uField) {
        uField = document.querySelector('input[type="email"], input[name*="user"], input[name*="email"], input[id*="user"], input[id*="email"], input[autocomplete="username"]') as HTMLInputElement;
      }
    }

    // Execute fill
    if (uField && item.username) {
      uField.value = item.username;
      uField.dispatchEvent(new Event('input', { bubbles: true }));
      uField.dispatchEvent(new Event('change', { bubbles: true }));
    }
    if (pField && item.password) {
      pField.value = item.password;
      pField.dispatchEvent(new Event('input', { bubbles: true }));
      pField.dispatchEvent(new Event('change', { bubbles: true }));
    }

    hidePopup();
  }

  // Message Listener for explicit requests
  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message.type === 'CHECK_PASSWORD_FORM') {
      const hasPw = document.querySelectorAll('input[type="password"]').length > 0;
      sendResponse({ hasPasswordForm: hasPw });
      return true; // async
    }
    if (message.type === 'FILL_CREDENTIALS') {
      // Logic for extension-icon triggered fill (generic)
      // Re-use logic or define new
      const pw = document.querySelector('input[type="password"]') as HTMLInputElement;
      if (pw) {
        activeInput = pw; // pretend interaction
        fillCredentials({ username: message.username, password: message.password } as PasswordItem);
      }
      sendResponse({ success: true });
      return true;
    }
    return false;
  });

  // Init
  function init() {
    detectAndInject();
    setupFormSubmitDetection();

    // Observe DOM for new inputs
    const observer = new MutationObserver((mutations) => {
      for (const m of mutations) {
        if (m.addedNodes.length > 0) detectAndInject();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  // ========== Password Save Feature ==========
  let savePromptShown = false;

  function setupFormSubmitDetection() {
    // Listen for form submissions
    document.addEventListener('submit', handleFormSubmit, true);

    // Also listen for click on submit buttons (some sites use JS)
    document.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (target.tagName === 'BUTTON' ||
        (target.tagName === 'INPUT' && (target as HTMLInputElement).type === 'submit')) {
        const form = target.closest('form');
        if (form) {
          setTimeout(() => handleFormSubmit({ target: form } as unknown as Event), 100);
        }
      }
    }, true);
  }

  function handleFormSubmit(e: Event) {
    if (savePromptShown) return;

    const form = e.target as HTMLFormElement;
    if (!form || form.tagName !== 'FORM') return;

    const passwordField = form.querySelector('input[type="password"]') as HTMLInputElement;
    if (!passwordField || !passwordField.value) return;

    // Find username field
    let usernameField: HTMLInputElement | null = null;
    const inputs = form.querySelectorAll('input');
    for (let i = 0; i < inputs.length; i++) {
      if (inputs[i] === passwordField && i > 0) {
        const prev = inputs[i - 1];
        if (prev.type === 'text' || prev.type === 'email') {
          usernameField = prev;
          break;
        }
      }
    }

    // Fallback: look for common username patterns
    if (!usernameField) {
      usernameField = form.querySelector('input[type="email"], input[name*="user"], input[name*="email"], input[id*="user"], input[id*="email"]') as HTMLInputElement;
    }

    if (!usernameField || !usernameField.value) return;

    const credentials = {
      website: window.location.origin,
      title: document.title || window.location.hostname,
      username: usernameField.value,
      password: passwordField.value
    };

    showSavePrompt(credentials);
  }

  function showSavePrompt(credentials: { website: string; title: string; username: string; password: string }) {
    savePromptShown = true;

    const isZh = navigator.language.startsWith('zh');

    const prompt = document.createElement('div');
    prompt.id = 'monica-save-prompt';
    prompt.innerHTML = `
    <div class="monica-save-header">
      <img src="${chrome.runtime.getURL('icons/icon.png')}" class="monica-save-logo" />
      <span class="monica-save-title">Monica</span>
      <span class="monica-save-close">×</span>
    </div>
    <div class="monica-save-content">
      <p class="monica-save-text">${isZh ? '是否保存此密码？' : 'Save this password?'}</p>
      <div class="monica-save-info">
        <div class="monica-save-row">
          <span class="monica-save-label">${isZh ? '网站' : 'Website'}</span>
          <span class="monica-save-value">${credentials.website}</span>
        </div>
        <div class="monica-save-row">
          <span class="monica-save-label">${isZh ? '用户名' : 'Username'}</span>
          <span class="monica-save-value">${credentials.username}</span>
        </div>
      </div>
      <div class="monica-save-buttons">
        <button class="monica-save-btn monica-save-btn-primary" id="monica-save-yes">${isZh ? '保存' : 'Save'}</button>
        <button class="monica-save-btn monica-save-btn-secondary" id="monica-save-no">${isZh ? '不保存' : 'Never'}</button>
      </div>
    </div>
  `;

    const style = document.createElement('style');
    style.textContent = `
    #monica-save-prompt {
      position: fixed;
      top: 16px;
      right: 16px;
      width: 320px;
      background: #1e1e2d;
      border-radius: 12px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.5);
      z-index: 2147483647;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      overflow: hidden;
      animation: monica-slide-in 0.3s ease;
    }
    @keyframes monica-slide-in {
      from { opacity: 0; transform: translateY(-20px); }
      to { opacity: 1; transform: translateY(0); }
    }
    .monica-save-header {
      background: #667eea;
      padding: 12px 16px;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .monica-save-logo {
      width: 24px;
      height: 24px;
      border-radius: 6px;
    }
    .monica-save-title {
      flex: 1;
      color: white;
      font-weight: 600;
      font-size: 14px;
    }
    .monica-save-close {
      color: white;
      cursor: pointer;
      font-size: 20px;
      opacity: 0.8;
    }
    .monica-save-close:hover { opacity: 1; }
    .monica-save-content {
      padding: 16px;
    }
    .monica-save-text {
      color: #e0e0e0;
      font-size: 15px;
      margin: 0 0 12px 0;
    }
    .monica-save-info {
      background: #252538;
      border-radius: 8px;
      padding: 12px;
      margin-bottom: 16px;
    }
    .monica-save-row {
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      margin-bottom: 8px;
    }
    .monica-save-row:last-child { margin-bottom: 0; }
    .monica-save-label { color: #888; }
    .monica-save-value { color: #e0e0e0; max-width: 180px; overflow: hidden; text-overflow: ellipsis; }
    .monica-save-buttons {
      display: flex;
      gap: 10px;
    }
    .monica-save-btn {
      flex: 1;
      padding: 10px;
      border: none;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: opacity 0.2s;
    }
    .monica-save-btn:hover { opacity: 0.9; }
    .monica-save-btn-primary {
      background: #667eea;
      color: white;
    }
    .monica-save-btn-secondary {
      background: #333;
      color: #888;
    }
  `;
    document.head.appendChild(style);
    document.body.appendChild(prompt);

    // Event handlers
    prompt.querySelector('.monica-save-close')?.addEventListener('click', () => {
      prompt.remove();
      savePromptShown = false;
    });

    document.getElementById('monica-save-yes')?.addEventListener('click', () => {
      chrome.runtime.sendMessage({ type: 'SAVE_PASSWORD', credentials }, (response) => {
        if (response?.success) {
          prompt.remove();
          savePromptShown = false;
        }
      });
    });

    document.getElementById('monica-save-no')?.addEventListener('click', () => {
      prompt.remove();
      savePromptShown = false;
    });

    // Auto-hide after 30 seconds
    setTimeout(() => {
      if (prompt.parentNode) {
        prompt.remove();
        savePromptShown = false;
      }
    }, 30000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
} // End of else block for duplicate injection check
