/**
 * Background Service Worker for Monica Browser Extension
 * Handles WebDAV requests to bypass CORS restrictions
 */

// Browser compatibility layer
const browserAPI = {
  runtime: typeof chrome !== 'undefined' ? chrome.runtime : (typeof browser !== 'undefined' ? browser.runtime : chrome.runtime),
  storage: typeof chrome !== 'undefined' ? chrome.storage : (typeof browser !== 'undefined' ? browser.storage : chrome.storage),
  scripting: typeof chrome !== 'undefined' ? chrome.scripting : (typeof browser !== 'undefined' ? browser.scripting : chrome.scripting),
  tabs: typeof chrome !== 'undefined' ? chrome.tabs : (typeof browser !== 'undefined' ? browser.tabs : chrome.tabs),
  action: typeof chrome !== 'undefined' ? chrome.action : (typeof browser !== 'undefined' ? browser.browserAction : chrome.action),
}

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
    arrayBuffer?: number[];  // ArrayBuffer as number array for serialization (deprecated)
    arrayBufferBase64?: string;  // ArrayBuffer as Base64 string (more efficient)
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

        // Get response headers (safe check)
        const responseHeaders: Record<string, string> = {};
        if (response && response.headers) {
            try {
                response.headers.forEach((value, key) => {
                    responseHeaders[key] = value;
                });
            } catch (e) {
                console.error('[Background] Failed to iterate headers:', e);
            }
        }

        // Check if response is binary (for downloads)
        const contentType = response?.headers?.get('content-type') || '';
        const isBinary = contentType.includes('octet-stream') ||
            contentType.includes('zip') ||
            request.method === 'GET';

        let responseBody: string | undefined;
        let arrayBufferBase64: string | undefined;

        if (isBinary && response.ok) {
            // Return as Base64 string for more efficient serialization
            const buffer = await response.arrayBuffer();
            // Limit file size to prevent memory overflow (max 100MB)
            const maxSize = 100 * 1024 * 1024; // 100MB
            if (buffer.byteLength > maxSize) {
                throw new Error(`文件过大 (${(buffer.byteLength / 1024 / 1024).toFixed(2)}MB)，超过 100MB 限制`);
            }
            // Convert ArrayBuffer to Base64 string
            const bytes = new Uint8Array(buffer);
            let binary = '';
            for (let i = 0; i < bytes.length; i++) {
                binary += String.fromCharCode(bytes[i]);
            }
            arrayBufferBase64 = btoa(binary);
        } else {
            responseBody = await response.text();
        }

        return {
            success: true,
            status: response.status,
            statusText: response.statusText,
            headers: responseHeaders,
            body: responseBody,
            arrayBufferBase64,
        };
    } catch (error) {
        const err = error as Error & { cause?: unknown };
        console.error('[Background] WebDAV request failed:', {
            method: request.method,
            url: request.url,
            error: err.message,
            errorDetails: err.stack,
            errorName: err.name,
            cause: err.cause,
        });

        // Check for specific error types
        if (err.message.includes('Failed to fetch')) {
            return {
                success: false,
                error: `网络请求失败 (${request.method} ${request.url})。可能原因：CORS、HTTPS证书、或网络连接`,
            };
        }

        return {
            success: false,
            error: `${err.message} (${request.method} ${request.url})`,
        };
    }
}

// ========== Autofill Support ==========

interface AutofillRequest {
    type: 'GET_PASSWORDS_FOR_AUTOFILL';
    url: string;
}

interface GetAllPasswordsRequest {
    type: 'GET_ALL_PASSWORDS';
}

interface OpenPopupRequest {
    type: 'OPEN_POPUP';
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

interface GetAllPasswordsResponse {
    success: boolean;
    passwords: PasswordItem[];
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

// ========== 2FA/TOTP Autofill Support ==========

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

interface GetTotpsRequest {
    type: 'GET_TOTPS_FOR_AUTOFILL';
    url: string;
}

interface GetTotpsResponse {
    success: boolean;
    totps: TotpItem[];
    matchedTotps: TotpItem[];
}

interface GenerateTotpRequest {
    type: 'GENERATE_TOTP_CODE';
    totpId: number;
}

interface GenerateTotpResponse {
    success: boolean;
    code?: string;
    timeRemaining?: number;
    error?: string;
}

interface VerifyMasterPasswordRequest {
    type: 'VERIFY_MASTER_PASSWORD';
    password: string;
}

interface VerifyMasterPasswordResponse {
    success: boolean;
    verified: boolean;
    error?: string;
}

// Storage key (same as in storage.ts)
const STORAGE_KEY = 'monica_vault';
// Keys must match MasterPasswordContext exactly
const MASTER_PASSWORD_HASH_KEY = 'monica_master_hash';
const MASTER_PASSWORD_SALT_KEY = 'monica_master_salt';

// Password hashing function - MUST match MasterPasswordContext (PBKDF2)
async function hashPassword(password: string, saltArray: number[]): Promise<string> {
    const encoder = new TextEncoder();
    const salt = new Uint8Array(saltArray);

    const keyMaterial = await crypto.subtle.importKey(
        'raw',
        encoder.encode(password),
        'PBKDF2',
        false,
        ['deriveBits']
    );

    const bits = await crypto.subtle.deriveBits(
        {
            name: 'PBKDF2',
            salt: salt.buffer as ArrayBuffer,
            iterations: 100000,
            hash: 'SHA-256',
        },
        keyMaterial,
        256
    );

    return btoa(String.fromCharCode(...new Uint8Array(bits)));
}

// Verify master password
async function verifyMasterPassword(password: string): Promise<boolean> {
    try {
        const result = await browserAPI.storage.local.get([MASTER_PASSWORD_HASH_KEY, MASTER_PASSWORD_SALT_KEY]);
        const storedHash = result[MASTER_PASSWORD_HASH_KEY] as string | undefined;
        const saltBase64 = result[MASTER_PASSWORD_SALT_KEY] as string | undefined;

        if (!storedHash || !saltBase64) {
            console.log('[Background] No master password hash found');
            return false;
        }

        // Salt is stored as Base64 string - decode it
        const saltArray = atob(saltBase64).split('').map(c => c.charCodeAt(0));
        const hash = await hashPassword(password, saltArray);

        console.log('[Background] Verifying password, hash match:', hash === storedHash);
        return hash === storedHash;
    } catch (error) {
        console.error('[Background] Password verification error:', error);
        return false;
    }
}

// Get passwords from storage
async function getPasswordsFromStorage(): Promise<PasswordItem[]> {
    try {
        // Try browser storage first
        const result = await browserAPI.storage.local.get(STORAGE_KEY);
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

// Get all passwords (alias for content script)
async function getAllPasswords(): Promise<PasswordItem[]> {
    return getPasswordsFromStorage();
}

// ========== TOTP Functions ==========

// Get TOTPs from storage
async function getTotpsFromStorage(): Promise<TotpItem[]> {
    try {
        const result = await browserAPI.storage.local.get(STORAGE_KEY);
        const rawItems = result[STORAGE_KEY];

        if (!rawItems || !Array.isArray(rawItems) || rawItems.length === 0) {
            return [];
        }

        // Filter TOTP items (itemType === 1)
        return rawItems
            .filter((item) => item.itemType === 1)
            .map((item) => ({
                id: item.id as number,
                title: item.title as string,
                issuer: (item.itemData?.issuer as string) || '',
                accountName: (item.itemData?.accountName as string) || '',
                secret: (item.itemData?.secret as string) || '',
                period: (item.itemData?.period as number) || 30,
                digits: (item.itemData?.digits as number) || 6,
                algorithm: (item.itemData?.algorithm as string) || 'SHA1',
            }));
    } catch (error) {
        console.error('[Background] Failed to get TOTPs:', error);
        return [];
    }
}

// Match TOTPs by URL domain or issuer
function matchTotpsByUrl(totps: TotpItem[], url: string): TotpItem[] {
    try {
        const urlObj = new URL(url);
        const domain = urlObj.hostname.replace(/^www\./, '').toLowerCase();
        const domainParts = domain.split('.');
        const siteName = domainParts.length > 1 ? domainParts[domainParts.length - 2] : domain;

        return totps.filter(t => {
            const issuer = (t.issuer || '').toLowerCase();
            const title = (t.title || '').toLowerCase();
            const account = (t.accountName || '').toLowerCase();

            // Match by issuer, title, account, or domain name
            return issuer.includes(siteName) ||
                title.includes(siteName) ||
                account.includes(siteName) ||
                siteName.includes(issuer) ||
                domain.includes(issuer) ||
                issuer.includes(domain.split('.')[0]);
        });
    } catch {
        return [];
    }
}

// Generate TOTP code
function generateTotpCode(totp: TotpItem): { code: string; timeRemaining: number } | null {
    try {
        // TOTP generation is done client-side with OTPAuth library
        // Background script just returns the raw data
        const period = totp.period || 30;
        const epoch = Math.floor(Date.now() / 1000);
        const timeRemaining = period - (epoch % period);

        // Return placeholder - actual code generation happens in content script
        return {
            code: '------',
            timeRemaining
        };
    } catch (error) {
        console.error('[Background] TOTP generation failed:', error);
        return null;
    }
}


// Handle autofill requests
(browserAPI.runtime.onMessage as typeof chrome.runtime.onMessage).addListener(
    (request: AutofillRequest | WebDavRequest | SavePasswordRequest | GetAllPasswordsRequest | OpenPopupRequest, _sender: chrome.runtime.MessageSender, sendResponse: (response: AutofillResponse | WebDavResponse | SavePasswordResponse | GetAllPasswordsResponse | { success: boolean }) => void) => {
        if (request.type === 'GET_PASSWORDS_FOR_AUTOFILL') {
            Promise.all([
                getPasswordsFromStorage(),
                browserAPI.storage.local.get('i18nextLng')
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

        if (request.type === 'GET_ALL_PASSWORDS') {
            getAllPasswords().then((passwords) => {
                sendResponse({
                    success: true,
                    passwords: passwords,
                });
            }).catch(() => {
                sendResponse({
                    success: false,
                    passwords: [],
                });
            });
            return true;
        }

        if (request.type === 'OPEN_POPUP') {
            // Open extension popup by focusing/creating a new tab
            // Firefox doesn't support openPopup(), so we always use fallback
            if (browserAPI.action && browserAPI.action.openPopup) {
                browserAPI.action.openPopup().catch(() => {
                    // Fallback: create tab with extension page
                    browserAPI.tabs.create({ url: browserAPI.runtime.getURL('index.html') });
                });
            } else {
                // Firefox fallback
                browserAPI.tabs.create({ url: browserAPI.runtime.getURL('index.html') });
            }
            sendResponse({ success: true });
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

        // Master password verification for 2FA
        if ((request as VerifyMasterPasswordRequest).type === 'VERIFY_MASTER_PASSWORD') {
            const verifyRequest = request as VerifyMasterPasswordRequest;
            verifyMasterPassword(verifyRequest.password)
                .then((verified) => {
                    sendResponse({ success: true, verified } as VerifyMasterPasswordResponse);
                })
                .catch((error) => {
                    sendResponse({ success: false, verified: false, error: error.message } as VerifyMasterPasswordResponse);
                });
            return true;
        }

        // 2FA/TOTP handlers
        if ((request as GetTotpsRequest).type === 'GET_TOTPS_FOR_AUTOFILL') {
            const totpRequest = request as GetTotpsRequest;
            getTotpsFromStorage().then((totps) => {
                const matched = matchTotpsByUrl(totps, totpRequest.url);
                sendResponse({
                    success: true,
                    totps,
                    matchedTotps: matched,
                } as GetTotpsResponse);
            }).catch(() => {
                sendResponse({
                    success: false,
                    totps: [],
                    matchedTotps: [],
                } as GetTotpsResponse);
            });
            return true;
        }

        if ((request as GenerateTotpRequest).type === 'GENERATE_TOTP_CODE') {
            const totpRequest = request as GenerateTotpRequest;
            getTotpsFromStorage().then((totps) => {
                const totp = totps.find(t => t.id === totpRequest.totpId);
                if (totp) {
                    const result = generateTotpCode(totp);
                    sendResponse({
                        success: true,
                        code: result?.code,
                        timeRemaining: result?.timeRemaining,
                    } as GenerateTotpResponse);
                } else {
                    sendResponse({
                        success: false,
                        error: 'TOTP not found',
                    } as GenerateTotpResponse);
                }
            }).catch((error) => {
                sendResponse({
                    success: false,
                    error: error.message,
                } as GenerateTotpResponse);
            });
            return true;
        }

        return false;
    }
);

// Save new password to storage
async function saveNewPassword(credentials: { website: string; title: string; username: string; password: string }) {
    const result = await browserAPI.storage.local.get(STORAGE_KEY);
    const rawItems = result[STORAGE_KEY];
    const items: Record<string, unknown>[] = Array.isArray(rawItems) ? rawItems : [];

    // Generate new ID
    const maxId = items.reduce((max, item) => Math.max(max, (item as Record<string, unknown>).id as number || 0), 0);

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
    await browserAPI.storage.local.set({ [STORAGE_KEY]: items });
    console.log('[Monica] Password saved:', newPassword.title);
}

// ========== Automatic Content Script Injection ==========
// Content script is now injected via manifest.json
// But we still try to inject into existing tabs upon installation to avoid reload
browserAPI.runtime.onInstalled.addListener(async () => {
    console.log('[Monica] Extension installed');

    // Inject into all existing tabs
    const tabs = await browserAPI.tabs.query({});
    for (const tab of tabs) {
        // Skip restricted URLs
        if (!tab.url || tab.url.startsWith('chrome://') ||
            tab.url.startsWith('chrome-extension://') ||
            tab.url.startsWith('about:') ||
            tab.url.startsWith('edge://') ||
            tab.url.startsWith('file://') ||
            tab.url.startsWith('moz-extension://')) { // Also skip file:// if not allowed
            continue;
        }

        if (tab.id) {
            try {
                if (browserAPI.scripting && browserAPI.scripting.executeScript) {
                    await browserAPI.scripting.executeScript({
                        target: { tabId: tab.id },
                        files: ['content.js']
                    });
                }
            } catch (err) {
                // Ignore "Cannot access contents of the page" error which is expected for some pages
                if (err instanceof Error && !err.message.includes('Cannot access contents of the page')) {
                    console.log('[Monica] Failed to inject into existing tab:', tab.url, err);
                }
            }
        }
    }
});

