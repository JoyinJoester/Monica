/**
 * Monica Autofill Content Script
 * Detects login forms, injects icons, and shows autofill popup
 */

// Prevent duplicate injection - if already loaded, do nothing
if ((window as Record<string, unknown>).__monica_content_loaded__) {
  console.log('[Monica] Content script already loaded, skipping');
} else {
  (window as Record<string, unknown>).__monica_content_loaded__ = true;

  // Types
  interface PasswordItem {
    id: number;
    title: string;
    username: string;
    password: string;
    website: string;
    itemData?: Record<string, unknown>;
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

  // 2FA/TOTP Types
  interface TotpItem {
    id: number;
    title: string;
    issuer: string;
    accountName: string;
    secret: string;
    period: number;
    digits: number;
    algorithm: string;
  }


  // State
  let popupContainer: HTMLElement | null = null;
  let isPopupVisible = false;
  let matchedPasswords: PasswordItem[] = [];
  let activeInput: HTMLInputElement | null = null; // The input that triggered the popup
  const trackedInputs: Map<HTMLInputElement, HTMLElement> = new Map(); // Input -> Icon Element

  // 2FA State
  let totpPopupContainer: HTMLElement | null = null;
  let isTotpPopupVisible = false;
  let matchedTotps: TotpItem[] = [];
  let activeTotpInput: HTMLInputElement | null = null;
  const trackedTotpInputs: Map<HTMLInputElement, HTMLElement> = new Map();

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
    // Skip if already tracked as 2FA input
    if (trackedTotpInputs.has(input)) return;

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

    const html = passwords.map(p => {
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
    const top = rect.bottom + window.scrollY + 8;
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
    console.log('[Monica] detectAndInject: found', inputs.length, 'inputs');

    // ===== 2FA/TOTP Detection (BEFORE password detection) =====
    detect2FAInputs();

    inputs.forEach(input => {
      // Skip if already tracked as 2FA
      if (trackedTotpInputs.has(input)) return;

      // Check if password or username-like
      const isPassword = input.type === 'password';

      // Heuristic for username fields - include inputs with no type (defaults to text)
      const inputType = input.type || 'text';
      const nameIdPlaceholder = (input.name + input.id + input.placeholder).toLowerCase();
      const usernamePattern = /user|login|email|account|用户|账号|邮箱|手机|username|phone/;
      const isUsername = (inputType === 'text' || inputType === 'email' || inputType === 'tel') &&
        usernamePattern.test(nameIdPlaceholder);

      console.log('[Monica] Input:', input.type || '(no type)', '| placeholder:', input.placeholder || '(none)', '| isPassword:', isPassword, '| isUsername:', isUsername);

      if (isPassword || isUsername) {
        console.log('[Monica] -> Injecting icon for:', input.placeholder || input.name || input.id);
        injectIcon(input);
      }
    });

    // Also inject icon for input fields immediately BEFORE password fields (often username)
    const passwords = document.querySelectorAll('input[type="password"]');
    passwords.forEach(pw => {
      injectIcon(pw as HTMLInputElement);

      // Find the previous visible input field and inject icon
      const allInputs = Array.from(document.querySelectorAll('input')) as HTMLInputElement[];
      const pwIndex = allInputs.indexOf(pw as HTMLInputElement);
      if (pwIndex > 0) {
        for (let i = pwIndex - 1; i >= 0; i--) {
          const prevInput = allInputs[i];
          const inputType = prevInput.type || 'text';
          // Skip hidden and password inputs
          if (inputType === 'hidden' || inputType === 'password') continue;
          // Check if visible - use multiple methods
          const isVisible = prevInput.offsetParent !== null ||
            (prevInput.offsetWidth > 0 && prevInput.offsetHeight > 0);
          if (isVisible) {
            console.log('[Monica] Injecting icon for username field:', prevInput.placeholder || prevInput.name || prevInput.id);
            injectIcon(prevInput);
            break;
          }
        }
      }
    });
  }

  // ===== 2FA/TOTP Auto-fill Feature =====

  // Detect 2FA/OTP input fields
  function detect2FAInputs() {
    const inputs = document.querySelectorAll('input');
    inputs.forEach(input => {
      if (is2FAInput(input)) {
        inject2FAIcon(input);
      }
    });
  }

  // Check if input looks like a 2FA/OTP field
  function is2FAInput(input: HTMLInputElement): boolean {
    // Skip if already tracked as password field
    if (trackedInputs.has(input)) return false;
    if (trackedTotpInputs.has(input)) return false;
    if (input.type === 'password') return false;

    const name = (input.name || '').toLowerCase();
    const id = (input.id || '').toLowerCase();
    const placeholder = (input.placeholder || '').toLowerCase();
    const autocomplete = (input.autocomplete || '').toLowerCase();

    // Check for 2FA indicators
    const is2FAPattern = /otp|totp|2fa|verification|verify|code|认证|验证码|6位|动态码|authenticator/.test(
      name + id + placeholder + autocomplete
    );

    // Check for typical OTP input attributes
    const isNumeric = input.inputMode === 'numeric' || input.type === 'tel' || input.type === 'number';
    const hasMaxLength = input.maxLength === 6 || input.maxLength === 4 || input.maxLength === 8;

    // Strong indicators
    if (is2FAPattern) return true;

    // Moderate indicators (numeric input with short maxLength)
    if (isNumeric && hasMaxLength && !input.name.includes('phone') && !input.name.includes('tel')) {
      return true;
    }

    return false;
  }

  // Inject 2FA icon
  function inject2FAIcon(input: HTMLInputElement) {
    if (trackedTotpInputs.has(input)) return;

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
      filter: hue-rotate(30deg); /* Slightly different color for 2FA */
    `;

    icon.onmouseenter = () => {
      icon.style.opacity = '1';
      icon.style.transform = 'scale(1.1)';
    };
    icon.onmouseleave = () => {
      icon.style.opacity = '0.6';
      icon.style.transform = 'scale(1)';
    };

    icon.onclick = (e) => {
      e.stopPropagation();
      e.preventDefault();
      activeTotpInput = input;
      show2FAPopup(icon);
    };

    document.body.appendChild(icon);
    trackedTotpInputs.set(input, icon);

    // Position icon
    update2FAIconPosition(input, icon);

    const update = () => update2FAIconPosition(input, icon);
    window.addEventListener('resize', update);
    window.addEventListener('scroll', update, true);

    const observer = new ResizeObserver(update);
    observer.observe(input);
  }

  function update2FAIconPosition(input: HTMLInputElement, icon: HTMLElement) {
    if (!isVisible(input)) {
      icon.style.display = 'none';
      return;
    }

    const rect = input.getBoundingClientRect();
    const iconSize = 20;
    const padding = 6;

    const top = rect.top + window.scrollY + (rect.height - iconSize) / 2;
    const left = rect.right + window.scrollX - iconSize - padding;

    icon.style.display = 'block';
    icon.style.top = `${top}px`;
    icon.style.left = `${left}px`;
  }

  // Show 2FA popup with password verification
  function show2FAPopup(anchor: HTMLElement) {
    if (isTotpPopupVisible) {
      hide2FAPopup();
    }

    totpPopupContainer = create2FAPopup();
    document.body.appendChild(totpPopupContainer);
    isTotpPopupVisible = true;

    // Position
    const rect = anchor.getBoundingClientRect();
    const popupWidth = 280;
    const top = rect.bottom + window.scrollY + 8;
    let left = rect.right + window.scrollX - popupWidth;
    if (left < 10) left = 10;

    totpPopupContainer.style.top = `${top}px`;
    totpPopupContainer.style.left = `${left}px`;

    // Show password verification first
    show2FAPasswordPrompt();

    document.addEventListener('click', handle2FAOutsideClick);
  }

  // Show password verification prompt in 2FA popup
  function show2FAPasswordPrompt() {
    const content = totpPopupContainer?.querySelector('.monica-2fa-content');
    if (!content) return;

    const isZh = navigator.language.startsWith('zh');

    content.innerHTML = `
      <div class="monica-2fa-password-prompt">
        <p class="monica-2fa-prompt-text">${isZh ? '请输入主密码以访问验证码' : 'Enter master password to access codes'}</p>
        <input type="text" class="monica-2fa-password-input" placeholder="${isZh ? '主密码' : 'Master password'}" autocomplete="off" />
        <button class="monica-2fa-unlock-btn">${isZh ? '解锁' : 'Unlock'}</button>
        <p class="monica-2fa-error" style="display: none;">${isZh ? '密码错误' : 'Incorrect password'}</p>
      </div>
    `;

    // Add styles for password prompt
    const style = document.createElement('style');
    style.textContent = `
      .monica-2fa-password-prompt {
        padding: 16px;
        text-align: center;
      }
      .monica-2fa-prompt-text {
        color: #e8eaed;
        font-size: 13px;
        margin-bottom: 12px;
      }
      .monica-2fa-password-input {
        width: 100%;
        padding: 10px 12px;
        border: 1px solid #5f6368;
        border-radius: 6px;
        background: #202124;
        color: #e8eaed;
        font-size: 14px;
        outline: none;
        margin-bottom: 12px;
        box-sizing: border-box;
        -webkit-text-security: disc;
        text-security: disc;
      }
      .monica-2fa-password-input:focus {
        border-color: #8ab4f8;
      }
      .monica-2fa-unlock-btn {
        width: 100%;
        padding: 10px;
        background: #8ab4f8;
        color: #202124;
        border: none;
        border-radius: 6px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: background 0.15s;
      }
      .monica-2fa-unlock-btn:hover {
        background: #aecbfa;
      }
      .monica-2fa-error {
        color: #f28b82;
        font-size: 12px;
        margin-top: 8px;
      }
    `;
    document.head.appendChild(style);

    // Handle unlock button click
    const unlockBtn = content.querySelector('.monica-2fa-unlock-btn');
    const passwordInput = content.querySelector('.monica-2fa-password-input') as HTMLInputElement;
    const errorMsg = content.querySelector('.monica-2fa-error') as HTMLElement;

    const tryUnlock = () => {
      const password = passwordInput?.value;
      if (!password) return;

      unlockBtn?.setAttribute('disabled', 'true');
      (unlockBtn as HTMLElement).textContent = '验证中...';

      chrome.runtime.sendMessage(
        { type: 'VERIFY_MASTER_PASSWORD', password },
        (response) => {
          if (response?.success && response.verified) {
            // Password correct, load 2FA list
            load2FAListAfterVerification();
          } else {
            // Password incorrect
            errorMsg.style.display = 'block';
            unlockBtn?.removeAttribute('disabled');
            (unlockBtn as HTMLElement).textContent = navigator.language.startsWith('zh') ? '解锁' : 'Unlock';
            passwordInput.focus();
          }
        }
      );
    };

    unlockBtn?.addEventListener('click', tryUnlock);
    passwordInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') tryUnlock();
    });

    // Focus password input
    setTimeout(() => passwordInput?.focus(), 100);
  }

  // Load 2FA list after password verification
  function load2FAListAfterVerification() {
    const content = totpPopupContainer?.querySelector('.monica-2fa-content');
    if (content) content.innerHTML = '<div class="monica-loading">加载中...</div>';

    chrome.runtime.sendMessage(
      { type: 'GET_TOTPS_FOR_AUTOFILL', url: window.location.href },
      (response) => {
        if (chrome.runtime.lastError) {
          console.error('[Monica 2FA] Error:', chrome.runtime.lastError);
          const content = totpPopupContainer?.querySelector('.monica-2fa-content');
          if (content) content.innerHTML = '<div class="monica-empty">扩展连接失败</div>';
          return;
        }

        if (response?.success && response.totps) {
          matchedTotps = response.matchedTotps || [];
          render2FAList(response.totps, matchedTotps);
        } else if (response?.success && response.totps?.length === 0) {
          const content = totpPopupContainer?.querySelector('.monica-2fa-content');
          if (content) content.innerHTML = '<div class="monica-empty">没有保存的2FA验证器</div>';
        } else {
          const content = totpPopupContainer?.querySelector('.monica-2fa-content');
          if (content) content.innerHTML = '<div class="monica-empty">没有保存的2FA验证器</div>';
        }
      }
    );
  }

  function hide2FAPopup() {
    if (totpPopupContainer) {
      totpPopupContainer.remove();
      totpPopupContainer = null;
    }
    isTotpPopupVisible = false;
    document.removeEventListener('click', handle2FAOutsideClick);
  }

  function handle2FAOutsideClick(e: MouseEvent) {
    if (totpPopupContainer && !totpPopupContainer.contains(e.target as Node)) {
      hide2FAPopup();
    }
  }

  function create2FAPopup(): HTMLElement {
    const container = document.createElement('div');
    container.id = 'monica-2fa-popup';
    container.innerHTML = `
      <div class="monica-2fa-content">
        <div class="monica-loading">加载中...</div>
      </div>
      <div class="monica-2fa-footer">
        <div class="monica-footer-manage">
          <div class="monica-footer-icon">
            <img src="${chrome.runtime.getURL('icons/icon.png')}" />
          </div>
          <span>2FA 验证码</span>
        </div>
        <svg class="monica-footer-key" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
          <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
        </svg>
      </div>
    `;

    const style = document.createElement('style');
    style.textContent = `
      #monica-2fa-popup {
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
      .monica-2fa-content {
        max-height: 240px;
        overflow-y: auto;
      }
      .monica-2fa-item {
        display: flex;
        align-items: center;
        padding: 10px 14px;
        cursor: pointer;
        color: #e8eaed;
        gap: 12px;
        transition: background 0.15s;
      }
      .monica-2fa-item:hover {
        background: #3c4043;
      }
      .monica-2fa-icon {
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
      .monica-2fa-info {
        flex: 1;
        overflow: hidden;
      }
      .monica-2fa-title {
        font-size: 13px;
        font-weight: 400;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        color: #e8eaed;
      }
      .monica-2fa-issuer {
        font-size: 11px;
        color: #9aa0a6;
        margin-top: 2px;
      }
      .monica-2fa-code {
        font-size: 16px;
        font-weight: 600;
        font-family: monospace;
        letter-spacing: 1px;
        color: #8ab4f8;
      }
      .monica-2fa-timer {
        font-size: 10px;
        color: #9aa0a6;
      }
      .monica-2fa-footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 10px 14px;
        border-top: 1px solid #3c4043;
        background: #292a2d;
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
      .monica-loading, .monica-empty {
        padding: 20px;
        text-align: center;
        color: #9aa0a6;
        font-size: 12px;
      }
    `;
    document.head.appendChild(style);

    return container;
  }

  function render2FAList(allTotps: TotpItem[], matched: TotpItem[]) {
    const content = totpPopupContainer?.querySelector('.monica-2fa-content');
    if (!content) return;

    const totps = matched.length > 0 ? matched : allTotps;

    if (totps.length === 0) {
      content.innerHTML = '<div class="monica-empty">没有保存的2FA验证器</div>';
      return;
    }

    const html = totps.map(t => {
      const initial = (t.issuer || t.title || 'T')[0].toUpperCase();
      return `
      <div class="monica-2fa-item" data-id="${t.id}" data-secret="${t.secret}" data-period="${t.period || 30}" data-digits="${t.digits || 6}" data-algorithm="${t.algorithm || 'SHA1'}">
        <div class="monica-2fa-icon">${initial}</div>
        <div class="monica-2fa-info">
          <div class="monica-2fa-title">${escapeHtml(t.title)}</div>
          <div class="monica-2fa-issuer">${escapeHtml(t.issuer || t.accountName || '')}</div>
        </div>
        <div style="text-align: right;">
          <div class="monica-2fa-code">------</div>
          <div class="monica-2fa-timer">30s</div>
        </div>
      </div>
    `;
    }).join('');

    content.innerHTML = html;

    // Generate codes for each item
    content.querySelectorAll('.monica-2fa-item').forEach(item => {
      const secret = item.getAttribute('data-secret') || '';
      const period = parseInt(item.getAttribute('data-period') || '30');
      const digits = parseInt(item.getAttribute('data-digits') || '6');
      const algorithm = item.getAttribute('data-algorithm') || 'SHA1';

      // Start generating TOTP code
      generate2FACode(item as HTMLElement, secret, period, digits, algorithm);

      // Click to fill
      item.addEventListener('click', () => {
        const codeEl = item.querySelector('.monica-2fa-code');
        const code = codeEl?.textContent || '';
        if (code && code !== '------' && activeTotpInput) {
          fill2FACode(code);
        }
      });
    });
  }

  function generate2FACode(item: HTMLElement, secret: string, period: number, digits: number) {
    const codeEl = item.querySelector('.monica-2fa-code');
    const timerEl = item.querySelector('.monica-2fa-timer');
    if (!codeEl) return;

    // Base32 decode
    function base32Decode(encoded: string): Uint8Array {
      const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
      const cleaned = encoded.replace(/[\s=]/g, '').toUpperCase();
      let bits = '';
      for (const char of cleaned) {
        const val = alphabet.indexOf(char);
        if (val === -1) continue;
        bits += val.toString(2).padStart(5, '0');
      }
      const bytes = new Uint8Array(Math.floor(bits.length / 8));
      for (let i = 0; i < bytes.length; i++) {
        bytes[i] = parseInt(bits.slice(i * 8, i * 8 + 8), 2);
      }
      return bytes;
    }

    // Generate TOTP using Web Crypto
    async function generateTOTP(): Promise<string> {
      try {
        const keyBytes = base32Decode(secret);
        const counter = Math.floor(Date.now() / 1000 / period);

        // Counter to bytes (big-endian)
        const counterBytes = new Uint8Array(8);
        let tmp = counter;
        for (let i = 7; i >= 0; i--) {
          counterBytes[i] = tmp & 0xff;
          tmp = Math.floor(tmp / 256);
        }

        // Import key
        const key = await crypto.subtle.importKey(
          'raw',
          keyBytes.buffer as ArrayBuffer,
          { name: 'HMAC', hash: 'SHA-1' },
          false,
          ['sign']
        );

        // Sign
        const signature = await crypto.subtle.sign('HMAC', key, counterBytes);
        const hash = new Uint8Array(signature);

        // Dynamic truncation
        const offset = hash[hash.length - 1] & 0x0f;
        const binary = ((hash[offset] & 0x7f) << 24) |
          ((hash[offset + 1] & 0xff) << 16) |
          ((hash[offset + 2] & 0xff) << 8) |
          (hash[offset + 3] & 0xff);

        const otp = binary % Math.pow(10, digits);
        return otp.toString().padStart(digits, '0');
      } catch (e) {
        console.error('[Monica 2FA] TOTP error:', e);
        return '------';
      }
    }

    // Update code periodically
    const updateCode = async () => {
      const code = await generateTOTP();
      const epoch = Math.floor(Date.now() / 1000);
      const remaining = period - (epoch % period);

      if (code !== '------') {
        codeEl.textContent = code.slice(0, 3) + ' ' + code.slice(3);
      } else {
        codeEl.textContent = '错误';
      }
      if (timerEl) timerEl.textContent = `${remaining}s`;
    };

    updateCode();
    setInterval(updateCode, 1000);
  }

  function fill2FACode(code: string) {
    if (!activeTotpInput) return;

    // Remove spaces
    const cleanCode = code.replace(/\s/g, '');

    activeTotpInput.value = cleanCode;
    activeTotpInput.dispatchEvent(new Event('input', { bubbles: true }));
    activeTotpInput.dispatchEvent(new Event('change', { bubbles: true }));

    hide2FAPopup();
  }

  // 4. Fill Logic
  function fillCredentials(item: PasswordItem) {
    // We need to find the form fields again relative to activeInput or globally
    let uField: HTMLInputElement | null = null;
    let pField: HTMLInputElement | null = null;

    // Step 1: Determine password field
    if (activeInput) {
      if (activeInput.type === 'password') {
        pField = activeInput;
      } else {
        uField = activeInput;
      }
    }

    // Fallback: find password field globally
    if (!pField) {
      pField = document.querySelector('input[type="password"]') as HTMLInputElement;
    }

    // Step 2: Find username field using multiple methods
    if (!uField && pField) {
      // Method 1: Check in same form
      if (pField.form) {
        const inputs = Array.from(pField.form.querySelectorAll('input'));
        const idx = inputs.indexOf(pField);
        for (let i = idx - 1; i >= 0; i--) {
          const input = inputs[i] as HTMLInputElement;
          if (input.type === 'text' || input.type === 'email' || input.type === 'tel' || !input.type) {
            uField = input;
            break;
          }
        }
      }

      // Method 2: Search all inputs on page (no form case)
      if (!uField) {
        const allInputs = Array.from(document.querySelectorAll('input')) as HTMLInputElement[];
        const pwIndex = allInputs.indexOf(pField);
        if (pwIndex > 0) {
          for (let i = pwIndex - 1; i >= 0; i--) {
            const input = allInputs[i];
            if (input.type === 'hidden' || input.type === 'password') continue;
            if ((input.type === 'text' || input.type === 'email' || input.type === 'tel' || !input.type) &&
              input.offsetParent !== null) {
              uField = input;
              console.log('[Monica] Found username field:', input.name || input.id || input.placeholder);
              break;
            }
          }
        }
      }

      // Method 3: Try common selectors
      if (!uField) {
        uField = document.querySelector('input[type="email"], input[type="text"], input[autocomplete="username"], input[placeholder*="用户"], input[placeholder*="账号"], input[placeholder*="邮箱"]') as HTMLInputElement;
        if (uField) console.log('[Monica] Found username via selector:', uField.name || uField.id || uField.placeholder);
      }
    }

    console.log('[Monica] Fill - username field:', uField ? 'found' : 'not found');
    console.log('[Monica] Fill - password field:', pField ? 'found' : 'not found');

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

    // Listen for Enter key on password fields
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const target = e.target as HTMLElement;
        if (target.tagName === 'INPUT') {
          const input = target as HTMLInputElement;
          if (input.type === 'password' || input.type === 'text' || input.type === 'email') {
            setTimeout(() => handlePageCredentials(), 100);
          }
        }
      }
    }, true);

    // Also listen for click on submit buttons (some sites use JS without form)
    document.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;

      // Check if it's a button or submit input
      const isSubmitButton = target.tagName === 'BUTTON' ||
        (target.tagName === 'INPUT' && (target as HTMLInputElement).type === 'submit') ||
        target.closest('button');

      // Check if button text contains login keywords
      const buttonText = target.textContent?.toLowerCase() || '';
      const isLoginButton = /登录|登入|login|sign.?in|submit|确定/.test(buttonText);

      if (isSubmitButton || isLoginButton) {
        const form = target.closest('form');
        if (form) {
          // Has form - use form-based detection
          setTimeout(() => handleFormSubmit({ target: form } as unknown as Event), 100);
        } else {
          // No form - search entire page for credentials
          setTimeout(() => handlePageCredentials(), 100);
        }
      }
    }, true);
  }

  // Handle login pages without <form> element
  function handlePageCredentials() {
    if (savePromptShown) return;

    // Find password field on page
    const passwordField = document.querySelector('input[type="password"]') as HTMLInputElement;
    if (!passwordField || !passwordField.value) return;

    // Find username field - look for text/email input before password
    let usernameField: HTMLInputElement | null = null;
    const allInputs = Array.from(document.querySelectorAll('input')) as HTMLInputElement[];
    const pwIndex = allInputs.indexOf(passwordField);

    // Method 1: Previous input in DOM order
    if (pwIndex > 0) {
      for (let i = pwIndex - 1; i >= 0; i--) {
        const input = allInputs[i];
        if ((input.type === 'text' || input.type === 'email' || input.type === 'tel') &&
          input.offsetParent !== null) {
          usernameField = input;
          break;
        }
      }
    }

    // Method 2: Look for common patterns
    if (!usernameField) {
      usernameField = document.querySelector('input[type="email"], input[type="text"][placeholder*="用户"], input[type="text"][placeholder*="账号"], input[type="text"][placeholder*="手机"], input[autocomplete="username"]') as HTMLInputElement;
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
    // First check if this password already exists
    chrome.runtime.sendMessage(
      { type: 'GET_PASSWORDS_FOR_AUTOFILL', url: credentials.website },
      (response) => {
        if (chrome.runtime.lastError) {
          // Extension error, skip check and show prompt
          displaySavePromptUI(credentials);
          return;
        }

        if (response?.success && response.matchedPasswords) {
          // Check if username already exists for this site
          const exists = response.matchedPasswords.some(
            (p: { username: string; password: string }) =>
              p.username === credentials.username && p.password === credentials.password
          );

          if (exists) {
            console.log('[Monica] Password already saved, skipping prompt');
            return; // Don't show prompt
          }
        }

        // Password doesn't exist, show save prompt
        displaySavePromptUI(credentials);
      }
    );
  }

  function displaySavePromptUI(credentials: { website: string; title: string; username: string; password: string }) {
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
    document.addEventListener('DOMContentLoaded', () => {
      // Delay init for SPA frameworks (Vue, React) that render after DOMContentLoaded
      setTimeout(init, 100);
      setTimeout(init, 500);
      setTimeout(init, 1500);
    });
  } else {
    // Document already loaded
    setTimeout(init, 100);
    setTimeout(init, 500);
    setTimeout(init, 1500);
  }
} // End of else block for duplicate injection check
