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
                    } catch (e) {
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

                        if (existingNoteTitles.has(entry.title)) continue;

                        const { saveItem } = await import('../storage');
                        const noteData: NoteData = entry.itemData ? JSON.parse(entry.itemData) : { content: '' };

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
                        errors.push(`笔记恢复失败: ${file.name}`);
                    }
                }
            }

            // 3. Restore TOTPs from CSV
            // Android CSV format: ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt
            // Data column contains JSON like {"secret":"xxx","issuer":"xxx",...}
            const totpFiles = Object.keys(zip.files).filter(name => name.includes('_totp.csv'));
            for (const filename of totpFiles) {
                try {
                    const content = await zip.file(filename)?.async('text');
                    if (content) {
                        const items = this.parseCSV(content);
                        for (const item of items) {
                            // Skip non-TOTP items
                            if (item.Type && item.Type !== 'TOTP') continue;

                            const title = item.Title || item.title || 'Unnamed';

                            // Parse itemData from Data column (Android format)
                            let totpData: TotpData = {
                                secret: '',
                                issuer: '',
                                accountName: '',
                                period: 30,
                                digits: 6,
                                algorithm: 'SHA1',
                                otpType: OtpType.TOTP,
                            };

                            const dataField = item.Data || item.data || item.itemData || '';
                            if (dataField) {
                                try {
                                    // Try parsing as JSON first
                                    const parsed = JSON.parse(dataField);
                                    totpData = {
                                        secret: parsed.secret || '',
                                        issuer: parsed.issuer || '',
                                        accountName: parsed.accountName || parsed.account_name || '',
                                        period: parsed.period || 30,
                                        digits: parsed.digits || 6,
                                        algorithm: parsed.algorithm || 'SHA1',
                                        otpType: OtpType.TOTP,
                                    };
                                } catch {
                                    // Try parsing as key:value;key:value format
                                    const parts = dataField.split(';');
                                    for (const part of parts) {
                                        const [key, value] = part.split(':').map((s: string) => s.trim());
                                        if (key && value) {
                                            if (key.toLowerCase() === 'secret') totpData.secret = value;
                                            else if (key.toLowerCase() === 'issuer') totpData.issuer = value;
                                            else if (key.toLowerCase() === 'accountname' || key.toLowerCase() === 'account') totpData.accountName = value;
                                            else if (key.toLowerCase() === 'period') totpData.period = parseInt(value) || 30;
                                            else if (key.toLowerCase() === 'digits') totpData.digits = parseInt(value) || 6;
                                            else if (key.toLowerCase() === 'algorithm') totpData.algorithm = value;
                                        }
                                    }
                                }
                            }

                            // Skip if no secret found
                            if (!totpData.secret) {
                                console.warn('[BackupManager] TOTP has no secret:', title);
                                continue;
                            }

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
                                notes: item.Notes || item.notes || '',
                                isFavorite: item.IsFavorite === 'true' || item.isFavorite === 'true',
                                sortOrder: 0,
                                itemData: totpData,
                            });
                            totpsRestored++;
                        }
                    }
                } catch (e) {
                    console.error('[BackupManager] TOTP restore error:', e);
                    errors.push(`TOTP 恢复失败: ${filename}`);
                }
            }

            // 4. Restore Documents from CSV
            // Android CSV format: ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt
            const docFiles = Object.keys(zip.files).filter(name => name.includes('_cards_docs.csv'));
            for (const filename of docFiles) {
                try {
                    const content = await zip.file(filename)?.async('text');
                    if (content) {
                        const items = this.parseCSV(content);
                        for (const item of items) {
                            // Check Type column (Android format uses uppercase DOCUMENT)
                            const itemType = item.Type || item.type || item.itemType || '';
                            if (itemType.toUpperCase() !== 'DOCUMENT') continue;

                            const title = item.Title || item.title || 'Unnamed';
                            if (existingDocTitles.has(title.toLowerCase())) {
                                console.log('[BackupManager] Skipping duplicate document:', title);
                                continue;
                            }

                            // Parse document data from Data column (Android format)
                            let docData: DocumentData = {
                                documentType: 'OTHER',
                                documentNumber: '',
                                fullName: '',
                                issuedDate: '',
                                expiryDate: '',
                                issuedBy: '',
                            };

                            const dataField = item.Data || item.data || item.itemData || '';
                            if (dataField) {
                                try {
                                    // Try parsing as JSON first
                                    const parsed = JSON.parse(dataField);
                                    docData = {
                                        documentType: parsed.documentType || parsed.document_type || 'OTHER',
                                        documentNumber: parsed.documentNumber || parsed.document_number || '',
                                        fullName: parsed.fullName || parsed.full_name || '',
                                        issuedDate: parsed.issuedDate || parsed.issued_date || '',
                                        expiryDate: parsed.expiryDate || parsed.expiry_date || '',
                                        issuedBy: parsed.issuedBy || parsed.issued_by || '',
                                    };
                                } catch {
                                    // Try parsing as key:value;key:value format
                                    const parts = dataField.split(';');
                                    for (const part of parts) {
                                        const [key, value] = part.split(':').map((s: string) => s.trim());
                                        if (key && value) {
                                            const lowerKey = key.toLowerCase();
                                            if (lowerKey === 'documenttype' || lowerKey === 'type') docData.documentType = value as DocumentType;
                                            else if (lowerKey === 'documentnumber' || lowerKey === 'number') docData.documentNumber = value;
                                            else if (lowerKey === 'fullname' || lowerKey === 'name') docData.fullName = value;
                                            else if (lowerKey === 'issueddate' || lowerKey === 'issued') docData.issuedDate = value;
                                            else if (lowerKey === 'expirydate' || lowerKey === 'expiry') docData.expiryDate = value;
                                            else if (lowerKey === 'issuedby' || lowerKey === 'issuer') docData.issuedBy = value;
                                        }
                                    }
                                }
                            }

                            const { saveItem } = await import('../storage');
                            await saveItem({
                                itemType: ItemType.Document,
                                title,
                                notes: item.Notes || item.notes || '',
                                isFavorite: item.IsFavorite === 'true' || item.isFavorite === 'true',
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
