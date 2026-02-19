/**
 * Backup Manager for Monica Browser Extension
 * Handles creating and parsing backup ZIP files compatible with Android version
 */

import JSZip from 'jszip';
import { getPasswords, getNotes, getTotps, getDocuments } from '../storage';
import type { SecureItem, PasswordEntry, NoteData, TotpData, DocumentData } from '../../types/models';
import { ItemType, OtpType, DocumentType } from '../../types/models';
import { encrypt, decrypt, isEncrypted } from './EncryptionHelper';

// ========== Types ==========
export interface BackupPreferences {
    includePasswords: boolean;
    includeNotes: boolean;
    includeAuthenticators: boolean;
    includeDocuments: boolean;
}

export interface BackupReport {
    success: boolean;
    passwordCount: number;
    noteCount: number;
    totpCount: number;
    documentCount: number;
    totalSize: number;
    filename: string;
}

export interface RestoreReport {
    success: boolean;
    passwordsRestored: number;
    notesRestored: number;
    totpsRestored: number;
    documentsRestored: number;
    errors: string[];
}

// Backup entry types (compatible with Android)
interface PasswordBackupEntry {
    id: number;
    title: string;
    username: string;
    password: string;
    website: string;
    notes: string;
    isFavorite: boolean;
    categoryId: number | null;
    categoryName: string | null;
    createdAt: number;
    updatedAt: number;
}

interface NoteBackupEntry {
    id: number;
    title: string;
    notes: string;
    itemData: string;
    isFavorite: boolean;
    imagePaths: string;
    createdAt: number;
    updatedAt: number;
}

// ========== Backup Manager Class ==========
class BackupManagerService {

    /**
     * Create a backup ZIP file
     */
    async createBackup(
        preferences: BackupPreferences = {
            includePasswords: true,
            includeNotes: true,
            includeAuthenticators: true,
            includeDocuments: true,
        },
        encryptionPassword?: string
    ): Promise<{ data: ArrayBuffer; report: BackupReport }> {
        const zip = new JSZip();
        const timestamp = this.formatTimestamp(new Date());
        let passwordCount = 0;
        let noteCount = 0;
        let totpCount = 0;
        let documentCount = 0;

        // 1. Export Passwords to JSON files
        if (preferences.includePasswords) {
            const passwords = await getPasswords();
            const passwordsFolder = zip.folder('passwords');

            for (const item of passwords) {
                const data = item.itemData as PasswordEntry;
                const entry: PasswordBackupEntry = {
                    id: item.id,
                    title: item.title,
                    username: data.username || '',
                    password: data.password || '',
                    website: data.website || '',
                    notes: item.notes || '',
                    isFavorite: item.isFavorite,
                    categoryId: null,
                    categoryName: data.category || null,
                    createdAt: new Date(item.createdAt).getTime(),
                    updatedAt: new Date(item.updatedAt).getTime(),
                };
                const filename = `password_${item.id}_${entry.createdAt}.json`;
                passwordsFolder?.file(filename, JSON.stringify(entry));
                passwordCount++;
            }
        }

        // 2. Export Notes to JSON files
        if (preferences.includeNotes) {
            const notes = await getNotes();
            const notesFolder = zip.folder('notes');

            for (const item of notes) {
                const data = item.itemData as NoteData;
                const entry: NoteBackupEntry = {
                    id: item.id,
                    title: item.title,
                    notes: item.notes || '',
                    itemData: JSON.stringify(data),
                    isFavorite: item.isFavorite,
                    imagePaths: '',
                    createdAt: new Date(item.createdAt).getTime(),
                    updatedAt: new Date(item.updatedAt).getTime(),
                };
                const filename = `note_${item.id}_${entry.createdAt}.json`;
                notesFolder?.file(filename, JSON.stringify(entry));
                noteCount++;
            }
        }

        // 3. Export TOTPs to CSV (compatible with Android)
        if (preferences.includeAuthenticators) {
            const totps = await getTotps();
            if (totps.length > 0) {
                const csvContent = this.totpsToCSV(totps);
                zip.file(`Monica_${timestamp}_totp.csv`, csvContent);
                totpCount = totps.length;
            }
        }

        // 4. Export Documents to CSV
        if (preferences.includeDocuments) {
            const documents = await getDocuments();
            if (documents.length > 0) {
                const csvContent = this.documentsToCSV(documents);
                zip.file(`Monica_${timestamp}_cards_docs.csv`, csvContent);
                documentCount = documents.length;
            }
        }

        // Generate ZIP
        let zipData = await zip.generateAsync({ type: 'arraybuffer' });

        // Encrypt if password provided
        const filename = encryptionPassword
            ? `monica_backup_${timestamp}.enc.zip`
            : `monica_backup_${timestamp}.zip`;

        if (encryptionPassword) {
            zipData = await encrypt(zipData, encryptionPassword);
        }

        const report: BackupReport = {
            success: true,
            passwordCount,
            noteCount,
            totpCount,
            documentCount,
            totalSize: zipData.byteLength,
            filename,
        };

        return { data: zipData, report };
    }

    /**
     * Restore from backup ZIP file
     */
    async restoreBackup(
        data: ArrayBuffer,
        decryptionPassword?: string
    ): Promise<RestoreReport> {
        const errors: string[] = [];
        let passwordsRestored = 0;
        let notesRestored = 0;
        let totpsRestored = 0;
        let documentsRestored = 0;

        try {
            // Decrypt if needed
            let zipData = data;
            if (isEncrypted(data)) {
                if (!decryptionPassword) {
                    throw new Error('该备份已加密，请提供密码');
                }
                zipData = await decrypt(data, decryptionPassword);
            }

            // Load ZIP
            const zip = await JSZip.loadAsync(zipData);

            // Get existing items to avoid duplicates
            const existingPasswords = await getPasswords();
            const existingNotes = await getNotes();
            const existingTotps = await getTotps();
            const existingDocs = await getDocuments();

            // Create composite keys for better deduplication
            // Password: title + username + website
            const existingPasswordKeys = new Set(existingPasswords.map(p => {
                const data = p.itemData as PasswordEntry;
                return `${p.title}|${data.username || ''}|${data.website || ''}`.toLowerCase();
            }));
            // Notes: just by title
            const existingNoteTitles = new Set(existingNotes.map(n => n.title.toLowerCase()));
            // TOTPs: title + issuer + accountName
            const existingTotpKeys = new Set(existingTotps.map(t => {
                const data = t.itemData as TotpData;
                return `${t.title}|${data.issuer || ''}|${data.accountName || ''}`.toLowerCase();
            }));
            // Docs: just by title
            const existingDocTitles = new Set(existingDocs.map(d => d.title.toLowerCase()));

            // 1. Restore passwords from JSON
            const passwordFiles = zip.folder('passwords')?.file(/.json$/);
            if (passwordFiles) {
                for (const file of passwordFiles) {
                    try {
                        const content = await file.async('text');
                        const entry: PasswordBackupEntry = JSON.parse(content);

                        // Skip if already exists (composite key match)
                        const key = `${entry.title}|${entry.username || ''}|${entry.website || ''}`.toLowerCase();
                        if (existingPasswordKeys.has(key)) {
                            console.log('[BackupManager] Skipping duplicate password:', entry.title);
                            continue;
                        }

                        // Import to storage
                        const { saveItem } = await import('../storage');
                        await saveItem({
                            itemType: ItemType.Password,
                            title: entry.title,
                            notes: entry.notes,
                            isFavorite: entry.isFavorite,
                            sortOrder: 0,
                            itemData: {
                                username: entry.username,
                                password: entry.password,
                                website: entry.website,
                                category: entry.categoryName || undefined,
                            },
                        });
                        passwordsRestored++;
                    } catch {
                        errors.push(`密码恢复失败: ${file.name}`);
                    }
                }
            }

            // 2. Restore notes from JSON
            const noteFiles = zip.folder('notes')?.file(/.json$/);
            if (noteFiles) {
                for (const file of noteFiles) {
                    try {
                        const content = await file.async('text');
                        const entry: NoteBackupEntry = JSON.parse(content);

                        if (existingNoteTitles.has(entry.title.toLowerCase())) continue;

                        const { saveItem } = await import('../storage');

                        // Parse itemData - handle both string and object formats
                        let noteData: NoteData;
                        if (entry.itemData) {
                            if (typeof entry.itemData === 'string') {
                                try {
                                    noteData = JSON.parse(entry.itemData);
                                } catch {
                                    // If parsing fails, use the string as content
                                    noteData = { content: entry.itemData };
                                }
                            } else {
                                // Already an object
                                noteData = entry.itemData as unknown as NoteData;
                            }
                        } else {
                            noteData = { content: '' };
                        }

                        await saveItem({
                            itemType: ItemType.Note,
                            title: entry.title,
                            notes: entry.notes,
                            isFavorite: entry.isFavorite,
                            sortOrder: 0,
                            itemData: noteData,
                        });
                        notesRestored++;
                    } catch (e) {
                        console.error('[BackupManager] Note restore error:', e, file.name);
                        errors.push(`笔记恢复失败: ${file.name}`);
                    }
                }
            }



            // 3b. Restore TOTPs from CSV (Browser/WebDAV format)
            // Browser CSV format: title,issuer,accountName,secret,period,digits,algorithm,otpType
            const totpCsvFiles = Object.keys(zip.files).filter(name => name.includes('_totp.csv'));
            console.log('[BackupManager] TOTP CSV files found:', totpCsvFiles);

            for (const filename of totpCsvFiles) {
                try {
                    const content = await zip.file(filename)?.async('text');
                    if (content) {
                        console.log('[BackupManager] TOTP CSV content (first 500 chars):', content.substring(0, Math.min(500, content.length)));

                        const items = this.parseCSV(content);
                        console.log('[BackupManager] Parsed', items.length, 'TOTP items from', filename);

                        for (const item of items) {
                            // Get title from column directly
                            const title = item.title || item.Title || 'Unnamed';
                            console.log('[BackupManager] Processing TOTP:', title, 'Keys:', Object.keys(item));

                            // Get secret from column directly (browser format has direct columns)
                            const secret = item.secret || item.Secret || '';

                            // Skip if no secret
                            if (!secret) {
                                console.warn('[BackupManager] TOTP CSV has no secret:', title, 'All columns:', JSON.stringify(item));
                                continue;
                            }

                            // Build TOTP data from direct columns
                            const totpData: TotpData = {
                                secret,
                                issuer: item.issuer || item.Issuer || '',
                                accountName: item.accountName || item.AccountName || '',
                                period: parseInt(item.period || item.Period || '30') || 30,
                                digits: parseInt(item.digits || item.Digits || '6') || 6,
                                algorithm: item.algorithm || item.Algorithm || 'SHA1',
                                otpType: OtpType.TOTP,
                            };

                            // Check duplicate with composite key
                            const totpKey = `${title}|${totpData.issuer || ''}|${totpData.accountName || ''}`.toLowerCase();
                            if (existingTotpKeys.has(totpKey)) {
                                console.log('[BackupManager] Skipping duplicate TOTP:', title);
                                continue;
                            }

                            const { saveItem } = await import('../storage');
                            await saveItem({
                                itemType: ItemType.Totp,
                                title,
                                notes: '',
                                isFavorite: false,
                                sortOrder: 0,
                                itemData: totpData,
                            });
                            totpsRestored++;
                        }
                    }
                } catch (e) {
                    console.error('[BackupManager] TOTP CSV restore error:', e, (e as Error).stack);
                    errors.push(`TOTP 恢复失败: ${filename}`);
                }
            }

            // 4. Restore Documents from CSV (Browser/WebDAV format)
            // Browser CSV format: itemType,title,documentType,documentNumber,fullName,issuedDate,expiryDate,issuedBy
            const docFiles = Object.keys(zip.files).filter(name => name.includes('_cards_docs.csv'));
            for (const filename of docFiles) {
                try {
                    const content = await zip.file(filename)?.async('text');
                    if (content) {
                        const items = this.parseCSV(content);
                        for (const item of items) {
                            // Check itemType column
                            const itemType = item.itemType || item.ItemType || item.Type || item.type || '';
                            if (itemType.toUpperCase() !== 'DOCUMENT') continue;

                            const title = item.title || item.Title || 'Unnamed';
                            if (existingDocTitles.has(title.toLowerCase())) {
                                console.log('[BackupManager] Skipping duplicate document:', title);
                                continue;
                            }

                            // Build document data from direct columns
                            const docData: DocumentData = {
                                documentType: (item.documentType || item.DocumentType || 'OTHER') as DocumentType,
                                documentNumber: item.documentNumber || item.DocumentNumber || '',
                                fullName: item.fullName || item.FullName || '',
                                issuedDate: item.issuedDate || item.IssuedDate || '',
                                expiryDate: item.expiryDate || item.ExpiryDate || '',
                                issuedBy: item.issuedBy || item.IssuedBy || '',
                            };

                            const { saveItem } = await import('../storage');
                            await saveItem({
                                itemType: ItemType.Document,
                                title,
                                notes: '',
                                isFavorite: false,
                                sortOrder: 0,
                                itemData: docData,
                            });
                            documentsRestored++;
                        }
                    }
                } catch (e) {
                    console.error('[BackupManager] Document restore error:', e);
                    errors.push(`证件恢复失败: ${filename}`);
                }
            }

            return {
                success: errors.length === 0,
                passwordsRestored,
                notesRestored,
                totpsRestored,
                documentsRestored,
                errors,
            };
        } catch (e) {
            const err = e as Error;
            return {
                success: false,
                passwordsRestored: 0,
                notesRestored: 0,
                totpsRestored: 0,
                documentsRestored: 0,
                errors: [err.message || '恢复失败'],
            };
        }
    }

    // ========== Helper Functions ==========

    private formatTimestamp(date: Date): string {
        const pad = (n: number) => n.toString().padStart(2, '0');
        return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
    }

    private totpsToCSV(items: SecureItem[]): string {
        const headers = ['title', 'issuer', 'accountName', 'secret', 'period', 'digits', 'algorithm', 'otpType'];
        const rows = items.map(item => {
            const data = item.itemData as TotpData;
            return [
                this.escapeCsv(item.title),
                this.escapeCsv(data.issuer || ''),
                this.escapeCsv(data.accountName || ''),
                this.escapeCsv(data.secret),
                data.period?.toString() || '30',
                data.digits?.toString() || '6',
                data.algorithm || 'SHA1',
                data.otpType || 'TOTP',
            ].join(',');
        });
        return [headers.join(','), ...rows].join('\n');
    }

    private documentsToCSV(items: SecureItem[]): string {
        const headers = ['itemType', 'title', 'documentType', 'documentNumber', 'fullName', 'issuedDate', 'expiryDate', 'issuedBy'];
        const rows = items.map(item => {
            const data = item.itemData as DocumentData;
            return [
                'DOCUMENT',
                this.escapeCsv(item.title),
                data.documentType || 'OTHER',
                this.escapeCsv(data.documentNumber || ''),
                this.escapeCsv(data.fullName || ''),
                data.issuedDate || '',
                data.expiryDate || '',
                this.escapeCsv(data.issuedBy || ''),
            ].join(',');
        });
        return [headers.join(','), ...rows].join('\n');
    }

    private escapeCsv(str: string): string {
        if (!str) return '';
        if (str.includes(',') || str.includes('"') || str.includes('\n')) {
            return `"${str.replace(/"/g, '""')}"`;
        }
        return str;
    }

    private parseCSV(content: string): Record<string, string>[] {
        const lines = content.split('\n').filter(line => line.trim());
        if (lines.length < 2) return [];

        const headers = this.parseCSVLine(lines[0]);
        const items: Record<string, string>[] = [];

        for (let i = 1; i < lines.length; i++) {
            const values = this.parseCSVLine(lines[i]);
            const item: Record<string, string> = {};
            headers.forEach((header, index) => {
                item[header] = values[index] || '';
            });
            items.push(item);
        }

        return items;
    }

    private parseCSVLine(line: string): string[] {
        const result: string[] = [];
        let current = '';
        let inQuotes = false;

        for (let i = 0; i < line.length; i++) {
            const char = line[i];

            if (char === '"') {
                if (inQuotes && line[i + 1] === '"') {
                    current += '"';
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (char === ',' && !inQuotes) {
                result.push(current);
                current = '';
            } else {
                current += char;
            }
        }
        result.push(current);

        return result;
    }
}

// Export singleton
export const backupManager = new BackupManagerService();
