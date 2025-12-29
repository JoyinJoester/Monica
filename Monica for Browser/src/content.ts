/**
 * Monica Autofill Content Script
 * Detects login forms and shows autofill popup
 */

// Types
interface PasswordItem {
  id: number;
  title: string;
  username: string;
  password: string;
  website: string;
}

interface AutofillMessage {
  type: 'GET_PASSWORDS_FOR_AUTOFILL';
  url: string;
}

interface AutofillResponse {
  success: boolean;
  passwords: PasswordItem[];
  matchedPasswords: PasswordItem[];
}

// State
let popupContainer: HTMLElement | null = null;
let isPopupVisible = false;
let currentPasswords: PasswordItem[] = [];
let matchedPasswords: PasswordItem[] = [];
let usernameField: HTMLInputElement | null = null;
let passwordField: HTMLInputElement | null = null;

// Detect login forms on page
function detectLoginForm(): { username: HTMLInputElement | null; password: HTMLInputElement | null } {
  // Find password field
  const passwordFields = document.querySelectorAll<HTMLInputElement>(
    'input[type="password"]:not([aria-hidden="true"])'
  );

  if (passwordFields.length === 0) {
    return { username: null, password: null };
  }

  const password = passwordFields[0];

  // Find username field (typically before password)
  let username: HTMLInputElement | null = null;

  // Strategy 1: Look for common username field patterns
  const usernameSelectors = [
    'input[type="email"]',
    'input[autocomplete="username"]',
    'input[autocomplete="email"]',
    'input[name*="user"]',
    'input[name*="email"]',
    'input[name*="login"]',
    'input[id*="user"]',
    'input[id*="email"]',
    'input[id*="login"]',
  ];

  for (const selector of usernameSelectors) {
    const field = document.querySelector<HTMLInputElement>(selector);
    if (field && field.type !== 'password' && field.type !== 'hidden') {
      username = field;
      break;
    }
  }

  // Strategy 2: Look for text input near password field
  if (!username && password.form) {
    const formInputs = password.form.querySelectorAll<HTMLInputElement>('input');
    for (let i = 0; i < formInputs.length; i++) {
      if (formInputs[i] === password && i > 0) {
        const prev = formInputs[i - 1];
        if (prev.type === 'text' || prev.type === 'email') {
          username = prev;
          break;
        }
      }
    }
  }

  return { username, password };
}

// Create popup UI
function createPopup(): HTMLElement {
  const container = document.createElement('div');
  container.id = 'monica-autofill-popup';
  container.innerHTML = `
    <div class="monica-popup-header">
      <img src="${chrome.runtime.getURL('icons/icon.png')}" alt="Monica" class="monica-logo" />
      <span class="monica-title">Monica</span>
      <button class="monica-close-btn" title="关闭">×</button>
    </div>
    <div class="monica-popup-content">
      <div class="monica-loading">检测中...</div>
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
      max-height: 400px;
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
      from {
        opacity: 0;
        transform: translateY(-20px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .monica-popup-header {
      display: flex;
      align-items: center;
      padding: 12px 16px;
      background: linear-gradient(135deg, #667eea, #764ba2);
      gap: 8px;
    }

    .monica-logo {
      width: 24px;
      height: 24px;
      border-radius: 6px;
    }

    .monica-title {
      flex: 1;
      font-size: 14px;
      font-weight: 600;
      color: white;
    }

    .monica-close-btn {
      background: none;
      border: none;
      color: white;
      font-size: 20px;
      cursor: pointer;
      padding: 0 4px;
      opacity: 0.8;
      transition: opacity 0.2s;
    }

    .monica-close-btn:hover {
      opacity: 1;
    }

    .monica-popup-content {
      max-height: 340px;
      overflow-y: auto;
    }

    .monica-loading {
      padding: 24px;
      text-align: center;
      color: #888;
    }

    .monica-section-title {
      padding: 8px 16px;
      font-size: 11px;
      font-weight: 600;
      color: #888;
      text-transform: uppercase;
      background: #252538;
    }

    .monica-password-item {
      display: flex;
      align-items: center;
      padding: 12px 16px;
      cursor: pointer;
      transition: background 0.2s;
      border-bottom: 1px solid #2a2a3e;
    }

    .monica-password-item:hover {
      background: #2a2a3e;
    }

    .monica-password-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      background: linear-gradient(135deg, #667eea, #764ba2);
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: 600;
      font-size: 14px;
      margin-right: 12px;
    }

    .monica-password-info {
      flex: 1;
      overflow: hidden;
    }

    .monica-password-title {
      font-size: 14px;
      font-weight: 500;
      color: #e0e0e0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .monica-password-username {
      font-size: 12px;
      color: #888;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .monica-empty {
      padding: 24px;
      text-align: center;
      color: #666;
    }

    .monica-show-all {
      display: block;
      width: 100%;
      padding: 12px;
      background: none;
      border: none;
      border-top: 1px solid #2a2a3e;
      color: #667eea;
      font-size: 13px;
      cursor: pointer;
      transition: background 0.2s;
    }

    .monica-show-all:hover {
      background: #2a2a3e;
    }
  `;
  document.head.appendChild(style);

  // Add event listeners
  container.querySelector('.monica-close-btn')?.addEventListener('click', hidePopup);

  return container;
}

// Render passwords in popup
function renderPasswords(showAll: boolean = false) {
  const content = popupContainer?.querySelector('.monica-popup-content');
  if (!content) return;

  const passwordsToShow = showAll ? currentPasswords : matchedPasswords;
  const hasMatched = matchedPasswords.length > 0;

  if (currentPasswords.length === 0) {
    content.innerHTML = '<div class="monica-empty">没有保存的密码</div>';
    return;
  }

  let html = '';

  if (hasMatched && !showAll) {
    html += '<div class="monica-section-title">匹配的密码</div>';
    html += matchedPasswords.map(p => createPasswordItemHtml(p)).join('');
    if (currentPasswords.length > matchedPasswords.length) {
      html += '<button class="monica-show-all">显示所有密码</button>';
    }
  } else if (showAll) {
    if (hasMatched) {
      html += '<div class="monica-section-title">匹配的密码</div>';
      html += matchedPasswords.map(p => createPasswordItemHtml(p)).join('');
      html += '<div class="monica-section-title">其他密码</div>';
      html += currentPasswords
        .filter(p => !matchedPasswords.find(m => m.id === p.id))
        .map(p => createPasswordItemHtml(p))
        .join('');
    } else {
      html += '<div class="monica-section-title">所有密码</div>';
      html += passwordsToShow.map(p => createPasswordItemHtml(p)).join('');
    }
  } else {
    html += '<div class="monica-section-title">所有密码</div>';
    html += currentPasswords.map(p => createPasswordItemHtml(p)).join('');
  }

  content.innerHTML = html;

  // Add click handlers
  content.querySelectorAll('.monica-password-item').forEach(item => {
    item.addEventListener('click', () => {
      const id = parseInt(item.getAttribute('data-id') || '0');
      const password = currentPasswords.find(p => p.id === id);
      if (password) {
        fillCredentials(password);
      }
    });
  });

  // Show all button handler
  content.querySelector('.monica-show-all')?.addEventListener('click', () => {
    renderPasswords(true);
  });
}

function createPasswordItemHtml(p: PasswordItem): string {
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

function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// Fill credentials into form
function fillCredentials(password: PasswordItem) {
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
  chrome.runtime.sendMessage<AutofillMessage, AutofillResponse>(
    { type: 'GET_PASSWORDS_FOR_AUTOFILL', url: window.location.href },
    (response) => {
      if (response?.success) {
        currentPasswords = response.passwords;
        matchedPasswords = response.matchedPasswords;
        renderPasswords();
      } else {
        const content = popupContainer?.querySelector('.monica-popup-content');
        if (content) {
          content.innerHTML = '<div class="monica-empty">无法获取密码</div>';
        }
      }
    }
  );
}

// Hide popup
function hidePopup() {
  if (popupContainer && isPopupVisible) {
    popupContainer.remove();
    isPopupVisible = false;
  }
}

// Initialize
function init() {
  console.log('[Monica Autofill] Content script loaded!');

  // Wait for page to load
  if (document.readyState === 'loading') {
    console.log('[Monica Autofill] Page loading, waiting for DOMContentLoaded');
    document.addEventListener('DOMContentLoaded', checkForLoginForm);
  } else {
    console.log('[Monica Autofill] Page already loaded, checking for form');
    checkForLoginForm();
  }

  // Also check after dynamic content loads
  const observer = new MutationObserver(() => {
    if (!isPopupVisible) {
      checkForLoginForm();
    }
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true,
  });
}

function checkForLoginForm() {
  const { username, password } = detectLoginForm();
  console.log('[Monica Autofill] Form detection:', { foundPassword: !!password, foundUsername: !!username });

  if (password) {
    usernameField = username;
    passwordField = password;
    console.log('[Monica Autofill] Login form detected! Will show popup...');

    // Small delay to ensure page is ready
    setTimeout(() => {
      if (!isPopupVisible) {
        console.log('[Monica Autofill] Showing popup');
        showPopup();
      }
    }, 500);
  }
}

// Start
console.log('[Monica Autofill] Script executing...');
init();
