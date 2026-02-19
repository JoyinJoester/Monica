/**
 * WebDAV Client for Monica Browser Extension
 * Uses background script for network requests to bypass CORS
 */

import { obfuscateString, deobfuscateString } from './EncryptionHelper';
// ========== Types ==========
export interface WebDavConfig {
    serverUrl: string;
    username: string;
    password: string;
}

export interface BackupFile {
    filename: string;
    path: string;
    size: number;
    lastModified: Date;
    isEncrypted: boolean;
}

interface BackgroundResponse {
    success: boolean;
    status?: number;
    statusText?: string;
    headers?: Record<string, string>;
    body?: string;
    arrayBuffer?: number[];  // Deprecated
    arrayBufferBase64?: string;  // More efficient Base64 encoding
    error?: string;
}

// ========== Storage Keys ==========
const STORAGE_KEYS = {
    SERVER_URL: 'webdav_server_url',
    USERNAME: 'webdav_username',
    PASSWORD: 'webdav_password',
    ENABLE_ENCRYPTION: 'webdav_enable_encryption',
    ENCRYPTION_PASSWORD: 'webdav_encryption_password',
    AUTO_BACKUP_ENABLED: 'webdav_auto_backup',
    LAST_BACKUP_TIME: 'webdav_last_backup_time',
};

// ========== WebDAV Client Class ==========
class WebDavClientService {
    private config: WebDavConfig | null = null;
    private encryptionEnabled = false;
    private encryptionPassword = '';

    /**
     * Get Basic Auth header
     */
    private getAuthHeader(): string {
        if (!this.config) throw new Error('WebDAV not configured');
        const credentials = btoa(`${this.config.username}:${this.config.password}`);
        return `Basic ${credentials}`;
    }

    /**
     * Send request via background script
     */
    private async sendToBackground(
        method: string,
        url: string,
        headers: Record<string, string>,
        body?: string
    ): Promise<BackgroundResponse> {
        return new Promise((resolve) => {
            const api = typeof chrome !== 'undefined' ? chrome.runtime : (typeof browser !== 'undefined' ? browser.runtime : chrome.runtime);

            // Set timeout for message response (30 seconds)
            const timeout = setTimeout(() => {
                console.warn('[WebDav] Request timeout:', method, url);
                resolve({
                    success: false,
                    error: `请求超时 (30秒) - ${method} ${url}`,
                });
            }, 30000);

            api.sendMessage(
                {
                    type: 'WEBDAV_REQUEST',
                    method,
                    url,
                    headers,
                    body,
                },
                (response: BackgroundResponse) => {
                    clearTimeout(timeout);

                    // Explicitly check and consume runtime.lastError to suppress Chrome warnings
                    const lastError = api.lastError;

                    if (lastError) {
                        const errorMessage = lastError.message || 'Background script error';
                        console.warn('[WebDav] Runtime lastError:', errorMessage);

                        // Handle port closed error gracefully
                        if (errorMessage.includes('message port closed') || errorMessage.includes('receiving end')) {
                            resolve({
                                success: false,
                                error: `消息连接已关闭，可能是页面刷新或超时 - ${method} ${url}`,
                            });
                        } else {
                            resolve({
                                success: false,
                                error: errorMessage,
                            });
                        }
                    } else {
                        resolve(response || { success: false, error: 'No response from background' });
                    }
                }
            );
        });
    }

    /**
     * Make WebDAV request using background script
     */
    private async request(
        method: string,
        path: string,
        options: {
            body?: string;
            headers?: Record<string, string>;
        } = {}
    ): Promise<BackgroundResponse> {
        if (!this.config) throw new Error('WebDAV not configured');

        const url = `${this.config.serverUrl}${path}`;
        console.log(`[WebDav] ${method} ${url}`);

        const headers = {
            'Authorization': this.getAuthHeader(),
            ...options.headers,
        };

        return this.sendToBackground(method, url, headers, options.body);
    }

    /**
     * Check if response is successful
     */
    private isSuccess(response: BackgroundResponse, ...additionalOkStatuses: number[]): boolean {
        if (!response.success || !response.status) return false;
        const okStatuses = [200, 201, 204, 207, ...additionalOkStatuses];
        return okStatuses.includes(response.status);
    }

    /**
     * Configure WebDAV connection
     */
    async configure(url: string, username: string, password: string): Promise<void> {
        // Normalize URL - remove trailing slash
        const serverUrl = url.replace(/\/+$/, '');

        this.config = { serverUrl, username, password };

        // Save config
        await this.saveConfig();
        console.log('[WebDav] Configured:', serverUrl);
    }

    /**
     * Save configuration to storage
     */
    private async saveConfig(): Promise<void> {
        if (!this.config) return;

        // Obfuscate sensitive credentials before storage
        const data = {
            [STORAGE_KEYS.SERVER_URL]: this.config.serverUrl,
            [STORAGE_KEYS.USERNAME]: obfuscateString(this.config.username),
            [STORAGE_KEYS.PASSWORD]: obfuscateString(this.config.password),
            [STORAGE_KEYS.ENABLE_ENCRYPTION]: this.encryptionEnabled,
            [STORAGE_KEYS.ENCRYPTION_PASSWORD]: obfuscateString(this.encryptionPassword),
        };

        if (typeof chrome !== 'undefined' && chrome.storage?.local) {
            await chrome.storage.local.set(data);
        } else {
            Object.entries(data).forEach(([k, v]) =>
                localStorage.setItem(k, typeof v === 'string' ? v : JSON.stringify(v))
            );
        }
    }

    /**
     * Load configuration from storage
     */
    async loadConfig(): Promise<boolean> {
        let url = '', username = '', password = '';

        if (typeof chrome !== 'undefined' && chrome.storage?.local) {
            const data = await chrome.storage.local.get([
                STORAGE_KEYS.SERVER_URL,
                STORAGE_KEYS.USERNAME,
                STORAGE_KEYS.PASSWORD,
                STORAGE_KEYS.ENABLE_ENCRYPTION,
                STORAGE_KEYS.ENCRYPTION_PASSWORD,
            ]) as Record<string, string | boolean | number>;
            url = (data[STORAGE_KEYS.SERVER_URL] as string) || '';
            // Deobfuscate sensitive credentials
            username = deobfuscateString((data[STORAGE_KEYS.USERNAME] as string) || '');
            password = deobfuscateString((data[STORAGE_KEYS.PASSWORD] as string) || '');
            this.encryptionEnabled = (data[STORAGE_KEYS.ENABLE_ENCRYPTION] as boolean) || false;
            this.encryptionPassword = deobfuscateString((data[STORAGE_KEYS.ENCRYPTION_PASSWORD] as string) || '');
        } else {
            url = localStorage.getItem(STORAGE_KEYS.SERVER_URL) || '';
            username = deobfuscateString(localStorage.getItem(STORAGE_KEYS.USERNAME) || '');
            password = deobfuscateString(localStorage.getItem(STORAGE_KEYS.PASSWORD) || '');
            this.encryptionEnabled = localStorage.getItem(STORAGE_KEYS.ENABLE_ENCRYPTION) === 'true';
            this.encryptionPassword = deobfuscateString(localStorage.getItem(STORAGE_KEYS.ENCRYPTION_PASSWORD) || '');
        }

        if (url && username && password) {
            this.config = { serverUrl: url, username, password };
            console.log('[WebDav] Config loaded:', url);
            return true;
        }
        return false;
    }

    isConfigured(): boolean {
        return !!this.config;
    }

    getCurrentConfig(): { serverUrl: string; username: string } | null {
        if (!this.config) return null;
        return {
            serverUrl: this.config.serverUrl,
            username: this.config.username,
        };
    }

    async clearConfig(): Promise<void> {
        this.config = null;
        this.encryptionEnabled = false;
        this.encryptionPassword = '';

        const keys = Object.values(STORAGE_KEYS);
        if (typeof chrome !== 'undefined' && chrome.storage?.local) {
            await chrome.storage.local.remove(keys);
        } else {
            keys.forEach(k => localStorage.removeItem(k));
        }
        console.log('[WebDav] Config cleared');
    }

    async setEncryption(enabled: boolean, password: string = ''): Promise<void> {
        this.encryptionEnabled = enabled;
        this.encryptionPassword = enabled ? password : '';
        await this.saveConfig();
    }

    isEncryptionEnabled(): boolean {
        return this.encryptionEnabled;
    }

    getEncryptionPassword(): string {
        return this.encryptionPassword;
    }

    /**
     * Test connection to WebDAV server
     */
    async testConnection(): Promise<{ success: boolean; message: string }> {
        if (!this.config) {
            return { success: false, message: 'WebDAV 未配置' };
        }

        try {
            const response = await this.request('PROPFIND', '/', {
                headers: {
                    'Depth': '0',
                    'Content-Type': 'application/xml',
                },
            });

            if (!response.success) {
                return { success: false, message: response.error || '网络错误' };
            }

            if (response.status === 207 || response.status === 200) {
                return { success: true, message: '连接成功' };
            }

            if (response.status === 401) {
                return { success: false, message: '认证失败 (401)，请检查用户名和密码' };
            }
            if (response.status === 404) {
                return { success: false, message: '路径不存在 (404)' };
            }
            if (response.status === 403) {
                return { success: false, message: '访问被拒绝 (403)' };
            }

            return { success: false, message: `连接失败: HTTP ${response.status}` };
        } catch (error) {
            const err = error as Error;
            console.error('[WebDav] Connection test failed:', err);
            return { success: false, message: `连接失败: ${err.message}` };
        }
    }

    /**
     * Ensure Monica backup directory exists
     */
    async ensureBackupDir(): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');

        try {
            const response = await this.request('PROPFIND', '/Monica_Backups', {
                headers: { 'Depth': '0' },
            });

            if (response.status === 404) {
                const mkcolResponse = await this.request('MKCOL', '/Monica_Backups');
                if (this.isSuccess(mkcolResponse)) {
                    console.log('[WebDav] Created /Monica_Backups directory');
                }
            }
        } catch (e) {
            console.warn('[WebDav] Could not ensure /Monica_Backups dir:', e);
        }
    }

    /**
     * Upload backup file (sends as base64)
     */
    async uploadBackup(filename: string, data: ArrayBuffer | Uint8Array): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');

        await this.ensureBackupDir();
        const path = `/Monica_Backups/${filename}`;

        // Convert to base64 for sending via message
        const buffer = data instanceof Uint8Array ? data : new Uint8Array(data);
        const base64 = btoa(String.fromCharCode(...buffer));

        // For upload, we need to send binary data through background
        // The background script will handle base64 decoding
        const response = await this.sendToBackground(
            'PUT',
            `${this.config.serverUrl}${path}`,
            {
                'Authorization': this.getAuthHeader(),
                'Content-Type': 'application/octet-stream',
                'X-Content-Base64': 'true',
            },
            base64
        );

        if (!this.isSuccess(response)) {
            throw new Error(`Upload failed: ${response.error || `HTTP ${response.status}`}`);
        }

        console.log('[WebDav] Uploaded:', path);
    }

    /**
     * Download backup file
     */
    async downloadBackup(filename: string): Promise<ArrayBuffer> {
        if (!this.config) throw new Error('WebDAV not configured');

        const path = `/Monica_Backups/${filename}`;
        const response = await this.request('GET', path);

        // Try Base64 first (newer format), fallback to number array (old format)
        if (!response.success) {
            throw new Error(`Download failed: ${response.error || `HTTP ${response.status}`}`);
        }

        if (response.arrayBufferBase64) {
            // Convert Base64 string back to ArrayBuffer
            const binaryString = atob(response.arrayBufferBase64);
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            console.log('[WebDav] Downloaded (Base64):', path);
            return bytes.buffer;
        } else if (response.arrayBuffer) {
            // Legacy format: convert number array back to ArrayBuffer
            const uint8Array = new Uint8Array(response.arrayBuffer);
            console.log('[WebDav] Downloaded (legacy):', path);
            return uint8Array.buffer as ArrayBuffer;
        } else {
            throw new Error(`Download failed: No data received`);
        }
    }

    /**
     * List all backup files
     */
    async listBackups(): Promise<BackupFile[]> {
        if (!this.config) throw new Error('WebDAV not configured');

        await this.ensureBackupDir();

        try {
            const response = await this.request('PROPFIND', '/Monica_Backups', {
                headers: {
                    'Depth': '1',
                    'Content-Type': 'application/xml',
                },
                body: `<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:resourcetype/>
  </D:prop>
</D:propfind>`,
            });

            if (!response.success || !response.body) {
                console.warn('[WebDav] List failed:', response.error || response.status);
                return [];
            }

            const text = response.body;
            if (!text || text.length === 0) {
                console.warn('[WebDav] Empty response body');
                return [];
            }

            console.log('[WebDav] PROPFIND response:', text.substring(0, Math.min(500, text.length)));

            // Parse XML response
            const parser = new DOMParser();
            const doc = parser.parseFromString(text, 'application/xml');

            const backups: BackupFile[] = [];
            const responses = doc.querySelectorAll('response, D\\:response');

            if (!responses || responses.length === 0) {
                console.log('[WebDav] No backup files found');
                return [];
            }

            for (let i = 0; i < responses.length; i++) {
                try {
                    const resp = responses[i];
                    if (!resp) continue;

                    const href = resp.querySelector('href, D\\:href')?.textContent || '';
                    const displayName = resp.querySelector('displayname, D\\:displayname')?.textContent || '';
                    const contentLength = resp.querySelector('getcontentlength, D\\:getcontentlength')?.textContent || '0';
                    const lastModified = resp.querySelector('getlastmodified, D\\:getlastmodified')?.textContent || '';
                    const resourceType = resp.querySelector('resourcetype, D\\:resourcetype');
                    const isCollection = resourceType?.querySelector('collection, D\\:collection');

                    if (isCollection) continue;

                    const filename = displayName || decodeURIComponent(href.split('/').pop() || '');
                    if (!filename.endsWith('.zip')) continue;

                    backups.push({
                        filename,
                        path: href,
                        size: parseInt(contentLength, 10) || 0,
                        lastModified: lastModified ? new Date(lastModified) : new Date(),
                        isEncrypted: filename.includes('.enc.'),
                    });
                } catch (itemError) {
                    console.error('[WebDav] Error parsing backup item:', itemError);
                    // Continue to next item
                }
            }

            backups.sort((a, b) => b.lastModified.getTime() - a.lastModified.getTime());
            console.log('[WebDav] Found', backups.length, 'backups');
            return backups;
        } catch (e) {
            console.error('[WebDav] Error listing backups:', e, (e as Error).stack);
            return [];
        }
    }

    /**
     * Delete a backup file
     */
    async deleteBackup(filename: string): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');

        const path = `/Monica_Backups/${filename}`;
        const response = await this.request('DELETE', path);

        if (!this.isSuccess(response)) {
            throw new Error(`Delete failed: ${response.error || `HTTP ${response.status}`}`);
        }

        console.log('[WebDav] Deleted:', path);
    }

    async getLastBackupTime(): Promise<number> {
        if (typeof chrome !== 'undefined' && chrome.storage?.local) {
            const data = await chrome.storage.local.get(STORAGE_KEYS.LAST_BACKUP_TIME) as Record<string, number>;
            return (data[STORAGE_KEYS.LAST_BACKUP_TIME] as number) || 0;
        }
        return parseInt(localStorage.getItem(STORAGE_KEYS.LAST_BACKUP_TIME) || '0', 10);
    }

    async updateLastBackupTime(): Promise<void> {
        const time = Date.now();
        if (typeof chrome !== 'undefined' && chrome.storage?.local) {
            await chrome.storage.local.set({ [STORAGE_KEYS.LAST_BACKUP_TIME]: time });
        } else {
            localStorage.setItem(STORAGE_KEYS.LAST_BACKUP_TIME, time.toString());
        }
    }

    formatFileSize(bytes: number): string {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / 1024 / 1024).toFixed(2) + ' MB';
    }
}

export const webDavClient = new WebDavClientService();
