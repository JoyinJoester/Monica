/**
 * Background Service Worker for Monica Browser Extension
 * Handles WebDAV requests to bypass CORS restrictions
 */

// Message types
interface WebDavRequest {
    type: 'WEBDAV_REQUEST';
    method: string;
    url: string;
    headers?: Record<string, string>;
    body?: string;
}

interface WebDavResponse {
    success: boolean;
    status?: number;
    statusText?: string;
    headers?: Record<string, string>;
    body?: string;
    arrayBuffer?: number[];  // ArrayBuffer as number array for serialization
    error?: string;
}


async function handleWebDavRequest(request: WebDavRequest): Promise<WebDavResponse> {
    console.log(`[Background] ${request.method} ${request.url}`);

    try {
        const headers = { ...request.headers };
        let requestBody: BodyInit | undefined;

        // Check if body is base64 encoded (for binary uploads)
        if (request.body && headers['X-Content-Base64'] === 'true') {
            // Decode base64 to binary
            delete headers['X-Content-Base64'];
            const binaryString = atob(request.body);
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            requestBody = bytes;
        } else if (request.body && ['PUT', 'POST', 'PROPFIND'].includes(request.method)) {
            requestBody = request.body;
        }

        const fetchOptions: RequestInit = {
            method: request.method,
            headers,
            body: requestBody,
        };

        const response = await fetch(request.url, fetchOptions);

        // Get response headers
        const responseHeaders: Record<string, string> = {};
        response.headers.forEach((value, key) => {
            responseHeaders[key] = value;
        });

        // Check if response is binary (for downloads)
        const contentType = response.headers.get('content-type') || '';
        const isBinary = contentType.includes('octet-stream') ||
            contentType.includes('zip') ||
            request.method === 'GET';

        let responseBody: string | undefined;
        let arrayBuffer: number[] | undefined;

        if (isBinary && response.ok) {
            // Return as array of numbers for serialization
            const buffer = await response.arrayBuffer();
            arrayBuffer = Array.from(new Uint8Array(buffer));
        } else {
            responseBody = await response.text();
        }

        return {
            success: true,
            status: response.status,
            statusText: response.statusText,
            headers: responseHeaders,
            body: responseBody,
            arrayBuffer,
        };
    } catch (error) {
        const err = error as Error;
        console.error('[Background] WebDAV request failed:', err);
        return {
            success: false,
            error: err.message || 'Network error',
        };
    }
}

// ========== Autofill Support ==========

interface AutofillRequest {
    type: 'GET_PASSWORDS_FOR_AUTOFILL';
    url: string;
}

interface PasswordItem {
    id: number;
    title: string;
    username: string;
    password: string;
    website: string;
}

interface AutofillResponse {
    success: boolean;
    passwords: PasswordItem[];
    matchedPasswords: PasswordItem[];
    lang: string;
}

interface SavePasswordRequest {
    type: 'SAVE_PASSWORD';
    credentials: {
        website: string;
        title: string;
        username: string;
        password: string;
    };
}

interface SavePasswordResponse {
    success: boolean;
    error?: string;
}

// Storage key (same as in storage.ts)
const STORAGE_KEY = 'monica_vault';

// Get passwords from storage
async function getPasswordsFromStorage(): Promise<PasswordItem[]> {
    try {
        // Try chrome.storage.local first
        const result = await chrome.storage.local.get(STORAGE_KEY);
        const rawItems = result[STORAGE_KEY];

        // Return empty if no items
        if (!rawItems || !Array.isArray(rawItems) || rawItems.length === 0) {
            return [];
        }

        // Filter only password items and extract data
        return rawItems
            .filter((item) => item.itemType === 0) // ItemType.Password = 0
            .map((item) => ({
                id: item.id as number,
                title: item.title as string,
                username: (item.itemData?.username as string) || '',
                password: (item.itemData?.password as string) || '',
                website: (item.itemData?.website as string) || '',
            }));
    } catch (error) {
        console.error('[Background] Failed to get passwords:', error);
        return [];
    }
}

// Match passwords by URL domain
function matchPasswordsByUrl(passwords: PasswordItem[], url: string): PasswordItem[] {
    try {
        const urlObj = new URL(url);
        const domain = urlObj.hostname.replace(/^www\./, '').toLowerCase();

        return passwords.filter(p => {
            if (!p.website) return false;
            const pwdDomain = p.website
                .replace(/^https?:\/\//, '')
                .replace(/^www\./, '')
                .split('/')[0]
                .toLowerCase();
            return domain.includes(pwdDomain) || pwdDomain.includes(domain);
        });
    } catch {
        return [];
    }
}

// Handle autofill requests
chrome.runtime.onMessage.addListener(
    (request: AutofillRequest | WebDavRequest | SavePasswordRequest, _sender, sendResponse: (response: AutofillResponse | WebDavResponse | SavePasswordResponse) => void) => {
        if (request.type === 'GET_PASSWORDS_FOR_AUTOFILL') {
            Promise.all([
                getPasswordsFromStorage(),
                chrome.storage.local.get('i18nextLng')
            ]).then(([passwords, langResult]) => {
                const matched = matchPasswordsByUrl(passwords, request.url);
                const lang = (langResult.i18nextLng as string) || 'en';
                sendResponse({
                    success: true,
                    passwords,
                    matchedPasswords: matched,
                    lang,
                });
            }).catch(() => {
                sendResponse({
                    success: false,
                    passwords: [],
                    matchedPasswords: [],
                    lang: 'en',
                });
            });
            return true;
        }

        if (request.type === 'SAVE_PASSWORD') {
            saveNewPassword(request.credentials)
                .then(() => sendResponse({ success: true }))
                .catch((error) => sendResponse({ success: false, error: error.message }));
            return true;
        }

        if (request.type === 'WEBDAV_REQUEST') {
            handleWebDavRequest(request)
                .then(sendResponse)
                .catch((error) => {
                    sendResponse({
                        success: false,
                        error: error.message || 'Unknown error',
                    });
                });
            return true;
        }
        return false;
    }
);

// Save new password to storage
async function saveNewPassword(credentials: { website: string; title: string; username: string; password: string }) {
    const result = await chrome.storage.local.get(STORAGE_KEY);
    const rawItems = result[STORAGE_KEY];
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const items: any[] = Array.isArray(rawItems) ? rawItems : [];

    // Generate new ID
    const maxId = items.reduce((max, item) => Math.max(max, item.id || 0), 0);

    // Create item matching SecureItem interface
    const newPassword = {
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
            category: 'default'
        }
    };

    items.push(newPassword);
    await chrome.storage.local.set({ [STORAGE_KEY]: items });
    console.log('[Monica] Password saved:', newPassword.title);
}

// ========== Automatic Content Script Injection ==========

// Helper function to inject content script
async function injectContentScript(tabId: number, url: string) {
    // Skip invalid URLs
    if (!url || url.startsWith('chrome://') ||
        url.startsWith('chrome-extension://') ||
        url.startsWith('about:') ||
        url.startsWith('edge://')) {
        return;
    }

    console.log('[Monica] Injecting content script into:', url);

    try {
        await chrome.scripting.executeScript({
            target: { tabId: tabId },
            files: ['content.js']
        });
        console.log('[Monica] Content script injected successfully into tab', tabId);
    } catch (error) {
        console.error('[Monica] Failed to inject content script:', error);
    }
}

// Handle extension installation - inject into all existing tabs
chrome.runtime.onInstalled.addListener(async () => {
    console.log('[Monica] Extension installed');

    // Inject into all existing tabs
    const tabs = await chrome.tabs.query({});
    for (const tab of tabs) {
        if (tab.id && tab.url) {
            injectContentScript(tab.id, tab.url);
        }
    }
});

// Inject content script when page loads
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.status === 'complete' && tab.url) {
        injectContentScript(tabId, tab.url);
    }
});

