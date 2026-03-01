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
    isPermanent: boolean;
    isExpiring: boolean;
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

const BACKUP_DIR_CANDIDATES = [
    '/Monica_Backups',
    '/Monica/Backups',
    '/Monica/backups',
    '/backups',
] as const;

// ========== WebDAV Client Class ==========
class WebDavClientService {
    private config: WebDavConfig | null = null;
    private encryptionEnabled = false;
    private encryptionPassword = '';
    private autoBackupEnabled = false;
    private activeBackupDir: string | null = null;

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

    private normalizeDirPath(path: string): string {
        if (!path.startsWith('/')) return `/${path}`.replace(/\/+/g, '/');
        return path.replace(/\/+/g, '/');
    }

    private async getExistingBackupDir(): Promise<string | null> {
        for (const rawDir of BACKUP_DIR_CANDIDATES) {
            const dir = this.normalizeDirPath(rawDir);
            const response = await this.request('PROPFIND', dir, { headers: { Depth: '0' } });
            if (this.isSuccess(response)) {
                return dir;
            }
        }
        return null;
    }

    private async resolveBackupDirForUpload(): Promise<string> {
        if (this.activeBackupDir) return this.activeBackupDir;
        const existing = await this.getExistingBackupDir();
        if (existing) {
            this.activeBackupDir = existing;
            return existing;
        }
        const preferred = this.normalizeDirPath(BACKUP_DIR_CANDIDATES[0]);
        this.activeBackupDir = preferred;
        return preferred;
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
            [STORAGE_KEYS.AUTO_BACKUP_ENABLED]: this.autoBackupEnabled,
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
                STORAGE_KEYS.AUTO_BACKUP_ENABLED,
            ]) as Record<string, string | boolean | number>;
            url = (data[STORAGE_KEYS.SERVER_URL] as string) || '';
            // Deobfuscate sensitive credentials
            username = deobfuscateString((data[STORAGE_KEYS.USERNAME] as string) || '');
            password = deobfuscateString((data[STORAGE_KEYS.PASSWORD] as string) || '');
            this.encryptionEnabled = (data[STORAGE_KEYS.ENABLE_ENCRYPTION] as boolean) || false;
            this.encryptionPassword = deobfuscateString((data[STORAGE_KEYS.ENCRYPTION_PASSWORD] as string) || '');
            this.autoBackupEnabled = (data[STORAGE_KEYS.AUTO_BACKUP_ENABLED] as boolean) || false;
        } else {
            url = localStorage.getItem(STORAGE_KEYS.SERVER_URL) || '';
            username = deobfuscateString(localStorage.getItem(STORAGE_KEYS.USERNAME) || '');
            password = deobfuscateString(localStorage.getItem(STORAGE_KEYS.PASSWORD) || '');
            this.encryptionEnabled = localStorage.getItem(STORAGE_KEYS.ENABLE_ENCRYPTION) === 'true';
            this.encryptionPassword = deobfuscateString(localStorage.getItem(STORAGE_KEYS.ENCRYPTION_PASSWORD) || '');
            this.autoBackupEnabled = localStorage.getItem(STORAGE_KEYS.AUTO_BACKUP_ENABLED) === 'true';
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
        this.autoBackupEnabled = false;

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

    async configureAutoBackup(enabled: boolean): Promise<void> {
        this.autoBackupEnabled = enabled;
        if (typeof chrome !== 'undefined' && chrome.storage?.local) {
            await chrome.storage.local.set({ [STORAGE_KEYS.AUTO_BACKUP_ENABLED]: enabled });
        } else {
            localStorage.setItem(STORAGE_KEYS.AUTO_BACKUP_ENABLED, String(enabled));
        }
    }

    isAutoBackupEnabled(): boolean {
        return this.autoBackupEnabled;
    }

    async shouldAutoBackup(): Promise<boolean> {
        if (!this.autoBackupEnabled) return false;
        const lastBackupTime = await this.getLastBackupTime();
        if (lastBackupTime === 0) return true;

        const currentTime = Date.now();
        const last = new Date(lastBackupTime);
        const now = new Date(currentTime);

        const isNewDay = now.getFullYear() > last.getFullYear() ||
            (now.getFullYear() === last.getFullYear() && now.getMonth() > last.getMonth()) ||
            (now.getFullYear() === last.getFullYear() && now.getMonth() === last.getMonth() && now.getDate() > last.getDate());

        if (isNewDay) return true;

        const hoursSince = (currentTime - lastBackupTime) / (1000 * 60 * 60);
        return hoursSince >= 12;
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

        const targetDir = await this.resolveBackupDirForUpload();
        const segments = targetDir.split('/').filter(Boolean);
        let current = '';

        for (const segment of segments) {
            current += `/${segment}`;
            const response = await this.request('PROPFIND', current, {
                headers: { 'Depth': '0' },
            });
            if (!this.isSuccess(response) && response.status === 404) {
                const mkcolResponse = await this.request('MKCOL', current);
                if (this.isSuccess(mkcolResponse, 405)) {
                    console.log('[WebDav] Created directory:', current);
                }
            }
        }
    }

    /**
     * Upload backup file (sends as base64)
     */
    async uploadBackup(filename: string, data: ArrayBuffer | Uint8Array): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');

        await this.ensureBackupDir();
        const backupDir = await this.resolveBackupDirForUpload();
        const path = `${backupDir}/${filename}`;

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
    async downloadBackup(filenameOrPath: string): Promise<ArrayBuffer> {
        if (!this.config) throw new Error('WebDAV not configured');

        const path = this.resolvePathFromBackupOrFilename(filenameOrPath);
        const fallbackDir = this.normalizeDirPath(BACKUP_DIR_CANDIDATES[0]);
        const fileName = filenameOrPath.split('/').pop() || filenameOrPath;
        const response = await this.request('GET', path);

        let finalResponse = response;
        if (!this.isSuccess(response)) {
            const fallbackPath = `${fallbackDir}/${fileName}`;
            if (fallbackPath !== path) {
                finalResponse = await this.request('GET', fallbackPath);
            }
        }

        // HTTP 4xx/5xx still come back with success=true from background fetch,
        // so we must validate status explicitly.
        if (!this.isSuccess(finalResponse)) {
            throw new Error(`Download failed: ${finalResponse.error || `HTTP ${finalResponse.status}`}`);
        }

        // Try Base64 first (newer format), fallback to number array (old format)
        if (finalResponse.arrayBufferBase64) {
            // Convert Base64 string back to ArrayBuffer
            const binaryString = atob(finalResponse.arrayBufferBase64);
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            console.log('[WebDav] Downloaded (Base64):', filenameOrPath);
            return bytes.buffer;
        } else if (finalResponse.arrayBuffer) {
            // Legacy format: convert number array back to ArrayBuffer
            const uint8Array = new Uint8Array(finalResponse.arrayBuffer);
            console.log('[WebDav] Downloaded (legacy):', filenameOrPath);
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
            const allBackups: BackupFile[] = [];

            for (const rawDir of BACKUP_DIR_CANDIDATES) {
                const dir = this.normalizeDirPath(rawDir);
                const response = await this.request('PROPFIND', dir, {
                    headers: {
                        Depth: '1',
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
                    continue;
                }

                const text = response.body;
                if (!text || text.length === 0) continue;

                const parser = new DOMParser();
                const doc = parser.parseFromString(text, 'application/xml');
                const responses = doc.querySelectorAll('response, D\\:response');

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

                        allBackups.push({
                            filename,
                            path: href || `${dir}/${filename}`,
                            size: parseInt(contentLength, 10) || 0,
                            lastModified: lastModified ? new Date(lastModified) : new Date(),
                            isEncrypted: filename.includes('.enc.'),
                            isPermanent: filename.includes('_permanent'),
                            isExpiring: false,
                        });
                    } catch (itemError) {
                        console.error('[WebDav] Error parsing backup item:', itemError);
                    }
                }
            }

            const deduped = Array.from(
                new Map(allBackups.map((item) => [`${item.filename}|${item.lastModified.getTime()}`, item])).values()
            );
            const fiftyDaysMs = 50 * 24 * 60 * 60 * 1000;
            const now = Date.now();
            deduped.forEach((item) => {
                item.isExpiring = !item.isPermanent && (now - item.lastModified.getTime()) > fiftyDaysMs;
            });
            deduped.sort((a, b) => b.lastModified.getTime() - a.lastModified.getTime());
            console.log('[WebDav] Found', deduped.length, 'backups');
            return deduped;
        } catch (e) {
            console.error('[WebDav] Error listing backups:', e, (e as Error).stack);
            return [];
        }
    }

    private resolvePathFromBackupOrFilename(input: string): string {
        if (!this.config) return input;
        let normalizedInput = input;

        if (input.startsWith('http://') || input.startsWith('https://')) {
            try {
                const base = new URL(this.config.serverUrl);
                const full = new URL(input);
                if (base.origin === full.origin) {
                    normalizedInput = full.pathname;
                }
            } catch {
                // fall through
            }
        }

        if (normalizedInput.startsWith('/')) {
            // Some WebDAV providers return href as absolute path including the same
            // server base path (e.g. /remote.php/dav/files/user/...). Request()
            // already prefixes serverUrl, so strip duplicated base path.
            try {
                const basePath = new URL(this.config.serverUrl).pathname.replace(/\/+$/, '');
                if (basePath && normalizedInput.startsWith(`${basePath}/`)) {
                    const relative = normalizedInput.slice(basePath.length);
                    return relative.startsWith('/') ? relative : `/${relative}`;
                }
            } catch {
                // ignore URL parse issues and keep original path
            }
            return normalizedInput;
        }

        const active = this.activeBackupDir || this.normalizeDirPath(BACKUP_DIR_CANDIDATES[0]);
        return `${active}/${normalizedInput}`;
    }

    private toAbsoluteWebDavUrl(path: string): string {
        if (!this.config) return path;
        if (path.startsWith('http://') || path.startsWith('https://')) return path;
        return `${this.config.serverUrl}${path}`;
    }

    /**
     * Delete a backup file
     */
    async deleteBackup(filenameOrPath: string): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');

        const path = this.resolvePathFromBackupOrFilename(filenameOrPath);
        const response = await this.request('DELETE', path);

        if (!this.isSuccess(response)) {
            throw new Error(`Delete failed: ${response.error || `HTTP ${response.status}`}`);
        }

        console.log('[WebDav] Deleted:', filenameOrPath);
    }

    async markBackupAsPermanent(backup: BackupFile): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');
        if (backup.isPermanent) return;
        const currentPath = this.resolvePathFromBackupOrFilename(backup.path || backup.filename);
        const newFilename = backup.filename.replace('.zip', '_permanent.zip');
        const parentDir = currentPath.substring(0, currentPath.lastIndexOf('/'));
        const newPath = `${parentDir}/${newFilename}`;

        const response = await this.request('MOVE', currentPath, {
            headers: {
                Destination: this.toAbsoluteWebDavUrl(newPath),
                Overwrite: 'F',
            },
        });
        if (!this.isSuccess(response, 201)) {
            throw new Error(`Mark permanent failed: ${response.error || `HTTP ${response.status}`}`);
        }
    }

    async unmarkBackupPermanent(backup: BackupFile): Promise<void> {
        if (!this.config) throw new Error('WebDAV not configured');
        if (!backup.isPermanent) return;
        const currentPath = this.resolvePathFromBackupOrFilename(backup.path || backup.filename);
        const newFilename = backup.filename.replace('_permanent', '');
        const parentDir = currentPath.substring(0, currentPath.lastIndexOf('/'));
        const newPath = `${parentDir}/${newFilename}`;

        const response = await this.request('MOVE', currentPath, {
            headers: {
                Destination: this.toAbsoluteWebDavUrl(newPath),
                Overwrite: 'T',
            },
        });
        if (!this.isSuccess(response, 201)) {
            throw new Error(`Unmark permanent failed: ${response.error || `HTTP ${response.status}`}`);
        }
    }

    async cleanupBackups(retentionDays = 60): Promise<number> {
        const backups = await this.listBackups();
        const cutoff = Date.now() - retentionDays * 24 * 60 * 60 * 1000;
        const targets = backups.filter((item) => !item.isPermanent && item.lastModified.getTime() < cutoff);
        let deletedCount = 0;
        for (const backup of targets) {
            try {
                await this.deleteBackup(backup.path || backup.filename);
                deletedCount++;
            } catch (e) {
                console.warn('[WebDav] Cleanup delete failed:', backup.filename, e);
            }
        }
        return deletedCount;
    }

    async getLatestBackup(): Promise<BackupFile | null> {
        const backups = await this.listBackups();
        if (!backups.length) return null;
        return backups[0];
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
