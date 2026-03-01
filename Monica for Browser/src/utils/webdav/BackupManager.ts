/**
 * Backup Manager for Monica Browser Extension
 * Handles creating and parsing backup ZIP files compatible with Android version
 */

import JSZip from 'jszip';
import {
    getPasswords,
    getNotes,
    getTotps,
    getPasskeys,
    getDocuments,
    getBankCards,
    getAllItems,
    getCategories,
    getRecycleBinItems,
    getTimelineEntries,
    replaceCategories,
    mergeTimelineEntries,
    saveCategory,
    clearAllData,
} from '../storage';
import type { SecureItem, PasswordEntry, NoteData, TotpData, DocumentData, BankCardData, PasskeyData } from '../../types/models';
import { ItemType, OtpType, DocumentType } from '../../types/models';
import { encrypt, decrypt, isEncrypted } from './EncryptionHelper';

// ========== Types ==========
export interface BackupPreferences {
    includePasswords: boolean;
    includeNotes: boolean;
    includeAuthenticators: boolean;
    includeDocuments: boolean;
    includeCards?: boolean;
}

export interface BackupReport {
    success: boolean;
    passwordCount: number;
    noteCount: number;
    totpCount: number;
    documentCount: number;
    cardCount?: number;
    totalSize: number;
    filename: string;
}

export interface RestoreReport {
    success: boolean;
    passwordsRestored: number;
    notesRestored: number;
    totpsRestored: number;
    documentsRestored: number;
    cardsRestored?: number;
    passkeysRestored?: number;
    errors: string[];
}

export interface RestoreOptions {
    overwriteLocalData?: boolean;
    dedupeWithLocal?: boolean;
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

interface TotpBackupEntry {
    id: number;
    title: string;
    itemData: string;
    notes?: string;
    isFavorite?: boolean;
    createdAt?: number | string;
    updatedAt?: number | string;
    categoryName?: string | null;
}

interface PasskeyBackupEntry {
    credentialId: string;
    rpId: string;
    userName?: string;
    userDisplayName?: string;
    createdAt?: number | string;
    lastUsedAt?: number | string;
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
            includeCards: true,
        },
        encryptionPassword?: string
    ): Promise<{ data: ArrayBuffer; report: BackupReport }> {
        const zip = new JSZip();
        const timestamp = this.formatTimestamp(new Date());
        let passwordCount = 0;
        let noteCount = 0;
        let totpCount = 0;
        let documentCount = 0;
        let cardCount = 0;
        const categories = await getCategories();
        const categoryNameById = new Map(categories.map((c) => [c.id, c.name]));
        const foldersRoot = zip.folder('folders');

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
                const folderKey = this.toFolderKey(data.categoryId ? categoryNameById.get(data.categoryId) : undefined);
                foldersRoot
                    ?.folder(folderKey)
                    ?.folder('passwords')
                    ?.file(filename, JSON.stringify(entry));
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
                const noteDataForFolder = {
                    ...entry,
                    categoryName: null,
                };
                foldersRoot
                    ?.folder('_root')
                    ?.folder('notes')
                    ?.file(filename, JSON.stringify(noteDataForFolder));
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
                const jsonFolder = foldersRoot?.folder('_root')?.folder('authenticators');
                totps.forEach((item) => {
                    const normalizedTotp = this.normalizeTotpDataForBackupRestore(item.itemData, item.title);
                    jsonFolder?.file(
                        `totp_${item.id}_${new Date(item.createdAt).getTime()}.json`,
                        JSON.stringify({
                            id: item.id,
                            title: item.title,
                            itemData: JSON.stringify(normalizedTotp),
                            notes: item.notes || '',
                            isFavorite: item.isFavorite,
                            createdAt: new Date(item.createdAt).getTime(),
                            updatedAt: new Date(item.updatedAt).getTime(),
                        })
                    );
                });
            }
        }

        // 3.5 Export Passkeys in Android folders structure
        const passkeys = await getPasskeys();
        if (passkeys.length > 0) {
            const passkeyFolder = foldersRoot?.folder('_root')?.folder('passkeys');
            passkeys.forEach((item) => {
                const data = item.itemData as PasskeyData;
                const safeId = (data.credentialId || String(item.id)).replace(/[\\/]/g, '_');
                passkeyFolder?.file(
                    `passkey_${safeId}.json`,
                    JSON.stringify({
                        credentialId: data.credentialId || '',
                        rpId: data.rpId || '',
                        userName: data.username || '',
                        userDisplayName: data.userDisplayName || '',
                        createdAt: new Date(item.createdAt).getTime(),
                        lastUsedAt: new Date(item.updatedAt).getTime(),
                    })
                );
            });
        }

        // 4. Export Documents to CSV
        if (preferences.includeDocuments) {
            const documents = await getDocuments();
            if (documents.length > 0) {
                const cards = preferences.includeCards === false ? [] : await getBankCards();
                const csvContent = this.documentsAndCardsToCSV(documents, cards);
                zip.file(`Monica_${timestamp}_cards_docs.csv`, csvContent);
                documentCount = documents.length;
                cardCount = cards.length;
            }
        }

        // 5. Export categories and operational history (Android-compatible artifacts)
        zip.file('categories.json', JSON.stringify(categories, null, 2));

        const timeline = await getTimelineEntries(1000);
        zip.file('timeline_history.json', JSON.stringify(timeline, null, 2));
        zip.file('generated_history.json', '[]');

        // 6. Export recycle bin snapshot
        const recycleItems = await getRecycleBinItems();
        const trashFolder = zip.folder('trash');
        if (trashFolder) {
            const trashPasswords = recycleItems.filter((item) => item.itemType === ItemType.Password);
            const trashSecureItems = recycleItems
                .filter((item) => item.itemType !== ItemType.Password)
                .map((item) => item.itemType === ItemType.Totp
                    ? {
                        ...item,
                        itemData: this.normalizeTotpDataForBackupRestore(item.itemData, item.title),
                    }
                    : item
                );
            trashFolder.file('trash_passwords.json', JSON.stringify(trashPasswords, null, 2));
            trashFolder.file('trash_secure_items.json', JSON.stringify(trashSecureItems, null, 2));
        }

        // 7. Export unified CSV for forward compatibility
        const allItems = await getAllItems({ includeDeleted: true });
        zip.file('vault/items.csv', this.itemsToCSV(allItems));

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
            cardCount,
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
        decryptionPassword?: string,
        options: RestoreOptions = {}
    ): Promise<RestoreReport> {
        const overwriteLocalData = options.overwriteLocalData === true;
        const dedupeWithLocal = overwriteLocalData ? false : options.dedupeWithLocal !== false;
        const errors: string[] = [];
        let passwordsRestored = 0;
        let notesRestored = 0;
        let totpsRestored = 0;
        let documentsRestored = 0;
        let cardsRestored = 0;
        let passkeysRestored = 0;

        try {
            // Decrypt if needed
            let zipData = data;
            if (isEncrypted(data)) {
                if (!decryptionPassword) {
                    throw new Error('该备份已加密，请提供密码');
                }
                zipData = await decrypt(data, decryptionPassword);
            }

            const zipBytes = new Uint8Array(zipData);
            const isZipHeader = zipBytes.length >= 4 && zipBytes[0] === 0x50 && zipBytes[1] === 0x4b;
            if (!isZipHeader) {
                const preview = new TextDecoder('utf-8', { fatal: false }).decode(zipBytes.slice(0, Math.min(160, zipBytes.length))).trim();
                if (preview.startsWith('<')) {
                    throw new Error('恢复失败：下载到的内容不是备份ZIP，可能是 WebDAV 错误页（请检查服务器路径、认证和权限）');
                }
                throw new Error('恢复失败：备份文件不是有效 ZIP 数据，可能文件损坏或格式不兼容');
            }

            // Load ZIP
            const zip = await JSZip.loadAsync(zipData);

            if (overwriteLocalData) {
                await clearAllData();
            }

            // Get existing items to avoid duplicates
            const existingPasswords = dedupeWithLocal ? await getPasswords() : [];
            const existingNotes = dedupeWithLocal ? await getNotes() : [];
            const existingTotps = dedupeWithLocal ? await getTotps() : [];
            const existingPasskeys = dedupeWithLocal ? await getPasskeys() : [];
            const existingDocs = dedupeWithLocal ? await getDocuments() : [];
            const existingCards = dedupeWithLocal ? await getBankCards() : [];

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
            const existingCardKeys = new Set(existingCards.map(c => {
                const data = c.itemData as BankCardData;
                return `${c.title}|${(data.cardNumber || '').replace(/\s+/g, '')}`.toLowerCase();
            }));
            const existingPasskeyKeys = new Set(existingPasskeys.map((item) => {
                const data = item.itemData as PasskeyData;
                return `${data.credentialId || ''}|${data.rpId || ''}`;
            }));
            const categoryIdByName = new Map<string, number>(
                (await getCategories()).map((c) => [c.name, c.id])
            );

            // -1. Restore categories/timeline metadata first
            const categoriesFile = zip.file('categories.json');
            if (categoriesFile) {
                try {
                    const categories = JSON.parse(await categoriesFile.async('text')) as Array<{ id: number; name: string; sortOrder: number }>;
                    if (Array.isArray(categories) && categories.length > 0) {
                        await replaceCategories(categories);
                        categoryIdByName.clear();
                        (await getCategories()).forEach((c) => categoryIdByName.set(c.name, c.id));
                    }
                } catch (e) {
                    console.warn('[BackupManager] categories restore failed:', e);
                    errors.push('分类恢复失败: categories.json');
                }
            }

            const timelineFile = zip.file('timeline_history.json');
            if (timelineFile) {
                try {
                    const timeline = JSON.parse(await timelineFile.async('text')) as Array<{
                        id: number;
                        action: 'created' | 'updated' | 'deleted' | 'restored' | 'purged';
                        itemId: number;
                        itemType: number;
                        title: string;
                        timestamp: string;
                    }>;
                    if (Array.isArray(timeline) && timeline.length > 0) {
                        await mergeTimelineEntries(timeline);
                    }
                } catch (e) {
                    console.warn('[BackupManager] timeline restore failed:', e);
                    errors.push('时间线恢复失败: timeline_history.json');
                }
            }

            // 0. Restore unified items.csv (new format)
            const unifiedItemsFile = zip.file('vault/items.csv');
            if (unifiedItemsFile) {
                try {
                    const content = await unifiedItemsFile.async('text');
                    const rows = this.parseCSV(content);
                    for (const row of rows) {
                        const itemTypeRaw = row.itemType || row.type || '';
                        const title = row.title || row.Title || 'Imported';
                        let itemType: number | null = null;
                        const upperType = itemTypeRaw.toUpperCase();
                        if (upperType === 'PASSWORD' || itemTypeRaw === '0') itemType = ItemType.Password;
                        else if (upperType === 'TOTP' || itemTypeRaw === '1') itemType = ItemType.Totp;
                        else if (upperType === 'BANKCARD' || upperType === 'BANK_CARD' || itemTypeRaw === '2') itemType = ItemType.BankCard;
                        else if (upperType === 'DOCUMENT' || itemTypeRaw === '3') itemType = ItemType.Document;
                        else if (upperType === 'NOTE' || itemTypeRaw === '4') itemType = ItemType.Note;
                        else if (upperType === 'PASSKEY' || itemTypeRaw === '5') itemType = ItemType.Passkey;
                        else if (upperType === 'SEND' || itemTypeRaw === '6') itemType = ItemType.Send;

                        if (itemType === null) continue;

                        let parsedData: unknown = {};
                        try {
                            parsedData = JSON.parse(row.itemData || row.data || '{}');
                        } catch {
                            parsedData = {};
                        }

                        if (itemType === ItemType.Password) {
                            const data = parsedData as PasswordEntry;
                            const key = `${title}|${data.username || ''}|${data.website || ''}`.toLowerCase();
                            if (existingPasswordKeys.has(key)) continue;
                        }
                        if (itemType === ItemType.Note) {
                            if (existingNoteTitles.has(title.toLowerCase())) continue;
                        }
                        if (itemType === ItemType.Totp) {
                            const data = this.normalizeTotpDataForBackupRestore(parsedData, title);
                            parsedData = data;
                            const key = `${title}|${data.issuer || ''}|${data.accountName || ''}`.toLowerCase();
                            if (existingTotpKeys.has(key)) continue;
                        }
                        if (itemType === ItemType.Document) {
                            if (existingDocTitles.has(title.toLowerCase())) continue;
                        }
                        if (itemType === ItemType.BankCard) {
                            const data = parsedData as BankCardData;
                            const key = `${title}|${(data.cardNumber || '').replace(/\s+/g, '')}`.toLowerCase();
                            if (existingCardKeys.has(key)) continue;
                        }
                        if (itemType === ItemType.Passkey) {
                            const data = parsedData as PasskeyData;
                            const key = `${data.credentialId || ''}|${data.rpId || ''}`;
                            if (!data.credentialId || !data.rpId || existingPasskeyKeys.has(key)) continue;
                        }

                        const { saveItem } = await import('../storage');
                        await saveItem({
                            itemType: itemType as SecureItem['itemType'],
                            title,
                            notes: row.notes || '',
                            isFavorite: (row.isFavorite || '').toLowerCase() === 'true',
                            sortOrder: parseInt(row.sortOrder || '0') || 0,
                            itemData: parsedData as SecureItem['itemData'],
                            createdAt: row.createdAt || undefined,
                            updatedAt: row.updatedAt || undefined,
                            isDeleted: (row.isDeleted || '').toLowerCase() === 'true',
                            deletedAt: row.deletedAt || undefined,
                        });
                        if (itemType === ItemType.Password) passwordsRestored++;
                        else if (itemType === ItemType.Note) notesRestored++;
                        else if (itemType === ItemType.Totp) totpsRestored++;
                        else if (itemType === ItemType.Document) documentsRestored++;
                        else if (itemType === ItemType.BankCard) cardsRestored++;
                        else if (itemType === ItemType.Passkey) passkeysRestored++;
                        if (itemType === ItemType.Password) {
                            const data = parsedData as PasswordEntry;
                            existingPasswordKeys.add(`${title}|${data.username || ''}|${data.website || ''}`.toLowerCase());
                        } else if (itemType === ItemType.Note) {
                            existingNoteTitles.add(title.toLowerCase());
                        } else if (itemType === ItemType.Totp) {
                            const data = parsedData as TotpData;
                            existingTotpKeys.add(`${title}|${data.issuer || ''}|${data.accountName || ''}`.toLowerCase());
                        } else if (itemType === ItemType.Document) {
                            existingDocTitles.add(title.toLowerCase());
                        } else if (itemType === ItemType.BankCard) {
                            const data = parsedData as BankCardData;
                            existingCardKeys.add(`${title}|${(data.cardNumber || '').replace(/\s+/g, '')}`.toLowerCase());
                        } else if (itemType === ItemType.Passkey) {
                            const data = parsedData as PasskeyData;
                            existingPasskeyKeys.add(`${data.credentialId || ''}|${data.rpId || ''}`);
                        }
                    }
                } catch (e) {
                    console.error('[BackupManager] Unified restore failed:', e);
                    errors.push('统一格式恢复失败: vault/items.csv');
                }
            }

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
                        existingPasswordKeys.add(key);
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
                        existingNoteTitles.add(entry.title.toLowerCase());
                        notesRestored++;
                    } catch (e) {
                        console.error('[BackupManager] Note restore error:', e, file.name);
                        errors.push(`笔记恢复失败: ${file.name}`);
                    }
                }
            }

            // 2.25 Restore Android new folders structure: folders/<cat>/passwords|notes|authenticators|passkeys/*.json
            const folderPasswordFiles = this.findZipFilesByPattern(zip, /^folders\/.+\/passwords\/.+\.json$/i);
            for (const fileName of folderPasswordFiles) {
                try {
                    const content = await zip.file(fileName)?.async('text');
                    if (!content) continue;
                    const entry = JSON.parse(content) as PasswordBackupEntry & {
                        keepassDatabaseId?: number;
                        keepassGroupPath?: string;
                        bitwardenVaultId?: number;
                        bitwardenFolderId?: string;
                        bitwardenCipherId?: string;
                        categoryName?: string | null;
                        createdAt?: number | string;
                        updatedAt?: number | string;
                    };
                    const key = `${entry.title}|${entry.username || ''}|${entry.website || ''}`.toLowerCase();
                    if (existingPasswordKeys.has(key)) continue;

                    const categoryId = await this.ensureCategoryId(entry.categoryName || undefined, categoryIdByName);
                    const { saveItem } = await import('../storage');
                    await saveItem({
                        itemType: ItemType.Password,
                        title: entry.title,
                        notes: entry.notes || '',
                        isFavorite: entry.isFavorite ?? false,
                        sortOrder: 0,
                        itemData: {
                            username: entry.username || '',
                            password: entry.password || '',
                            website: entry.website || '',
                            category: entry.categoryName || undefined,
                            categoryId: categoryId || undefined,
                            keepassDatabaseId: entry.keepassDatabaseId,
                            keepassGroupPath: entry.keepassGroupPath,
                            bitwardenVaultId: entry.bitwardenVaultId,
                            bitwardenFolderId: entry.bitwardenFolderId,
                            bitwardenCipherId: entry.bitwardenCipherId,
                        } as PasswordEntry,
                        createdAt: this.parseTimeLike(entry.createdAt),
                        updatedAt: this.parseTimeLike(entry.updatedAt),
                    });
                    existingPasswordKeys.add(key);
                    passwordsRestored++;
                } catch (e) {
                    console.warn('[BackupManager] folder password restore error', fileName, e);
                    errors.push(`文件夹密码恢复失败: ${fileName}`);
                }
            }

            const folderNoteFiles = this.findZipFilesByPattern(zip, /^folders\/.+\/notes\/.+\.json$/i);
            for (const fileName of folderNoteFiles) {
                try {
                    const content = await zip.file(fileName)?.async('text');
                    if (!content) continue;
                    const entry = JSON.parse(content) as NoteBackupEntry & { createdAt?: number | string; updatedAt?: number | string };
                    if (existingNoteTitles.has((entry.title || '').toLowerCase())) continue;
                    let noteData: NoteData = { content: '' };
                    if (entry.itemData) {
                        try {
                            noteData = typeof entry.itemData === 'string'
                                ? JSON.parse(entry.itemData)
                                : (entry.itemData as unknown as NoteData);
                        } catch {
                            noteData = { content: String(entry.itemData) };
                        }
                    }
                    const { saveItem } = await import('../storage');
                    await saveItem({
                        itemType: ItemType.Note,
                        title: entry.title || 'Imported Note',
                        notes: entry.notes || '',
                        isFavorite: entry.isFavorite ?? false,
                        sortOrder: 0,
                        itemData: noteData,
                        createdAt: this.parseTimeLike(entry.createdAt),
                        updatedAt: this.parseTimeLike(entry.updatedAt),
                    });
                    existingNoteTitles.add((entry.title || '').toLowerCase());
                    notesRestored++;
                } catch (e) {
                    console.warn('[BackupManager] folder note restore error', fileName, e);
                    errors.push(`文件夹笔记恢复失败: ${fileName}`);
                }
            }

            const folderTotpFiles = this.findZipFilesByPattern(zip, /^folders\/.+\/authenticators\/.+\.json$/i);
            for (const fileName of folderTotpFiles) {
                try {
                    const content = await zip.file(fileName)?.async('text');
                    if (!content) continue;
                    const entry = JSON.parse(content) as TotpBackupEntry;
                    const dataJson = entry.itemData || '{}';
                    const parsedRaw = JSON.parse(dataJson) as unknown;
                    const parsed = this.normalizeTotpDataForBackupRestore(parsedRaw, entry.title || '');
                    const title = entry.title || parsed.accountName || parsed.issuer || 'Imported TOTP';
                    const key = `${title}|${parsed.issuer || ''}|${parsed.accountName || ''}`.toLowerCase();
                    if (existingTotpKeys.has(key)) continue;

                    const { saveItem } = await import('../storage');
                    await saveItem({
                        itemType: ItemType.Totp,
                        title,
                        notes: entry.notes || '',
                        isFavorite: entry.isFavorite ?? false,
                        sortOrder: 0,
                        itemData: parsed,
                        createdAt: this.parseTimeLike(entry.createdAt),
                        updatedAt: this.parseTimeLike(entry.updatedAt),
                    });
                    existingTotpKeys.add(key);
                    totpsRestored++;
                } catch (e) {
                    console.warn('[BackupManager] folder totp restore error', fileName, e);
                    errors.push(`文件夹验证器恢复失败: ${fileName}`);
                }
            }

            const folderPasskeyFiles = this.findZipFilesByPattern(zip, /^folders\/.+\/passkeys\/.+\.json$/i);
            for (const fileName of folderPasskeyFiles) {
                try {
                    const content = await zip.file(fileName)?.async('text');
                    if (!content) continue;
                    const entry = JSON.parse(content) as PasskeyBackupEntry;
                    if (!entry.credentialId || !entry.rpId) continue;
                    const passkeyKey = `${entry.credentialId}|${entry.rpId}`;
                    if (existingPasskeyKeys.has(passkeyKey)) continue;
                    const title = entry.userDisplayName || entry.userName || entry.rpId;
                    const { saveItem } = await import('../storage');
                    await saveItem({
                        itemType: ItemType.Passkey,
                        title,
                        notes: '',
                        isFavorite: false,
                        sortOrder: 0,
                        itemData: {
                            username: entry.userName || '',
                            rpId: entry.rpId,
                            credentialId: entry.credentialId,
                            userDisplayName: entry.userDisplayName || '',
                        } as PasskeyData,
                        createdAt: this.parseTimeLike(entry.createdAt),
                        updatedAt: this.parseTimeLike(entry.lastUsedAt || entry.createdAt),
                    });
                    existingPasskeyKeys.add(passkeyKey);
                    passkeysRestored++;
                } catch (e) {
                    console.warn('[BackupManager] folder passkey restore error', fileName, e);
                    errors.push(`文件夹通行密钥恢复失败: ${fileName}`);
                }
            }

            // 2.5 Restore recycle bin snapshots for legacy ZIPs without unified vault/items.csv
            if (!unifiedItemsFile) {
            const trashPasswordFile = zip.file('trash/trash_passwords.json');
            if (trashPasswordFile) {
                try {
                    const trashPasswords = JSON.parse(await trashPasswordFile.async('text')) as Array<{
                        title: string;
                        username: string;
                        password: string;
                        website: string;
                        notes?: string;
                        isFavorite?: boolean;
                        createdAt?: number | string;
                        updatedAt?: number | string;
                        deletedAt?: number | string;
                    }>;
                    for (const entry of trashPasswords) {
                        const key = `${entry.title}|${entry.username || ''}|${entry.website || ''}`.toLowerCase();
                        if (existingPasswordKeys.has(key)) continue;
                        const { saveItem } = await import('../storage');
                        await saveItem({
                            itemType: ItemType.Password,
                            title: entry.title,
                            notes: entry.notes || '',
                            isFavorite: entry.isFavorite ?? false,
                            sortOrder: 0,
                            itemData: {
                                username: entry.username || '',
                                password: entry.password || '',
                                website: entry.website || '',
                            },
                            createdAt: this.parseTimeLike(entry.createdAt),
                            updatedAt: this.parseTimeLike(entry.updatedAt),
                            isDeleted: true,
                            deletedAt: this.parseTimeLike(entry.deletedAt),
                        });
                        existingPasswordKeys.add(key);
                        passwordsRestored++;
                    }
                } catch (e) {
                    console.error('[BackupManager] Trash password restore error:', e);
                    errors.push('回收站密码恢复失败: trash_passwords.json');
                }
            }

            const trashSecureItemsFile = zip.file('trash/trash_secure_items.json');
            if (trashSecureItemsFile) {
                try {
                    const trashItems = JSON.parse(await trashSecureItemsFile.async('text')) as Array<Partial<SecureItem>>;
                    for (const entry of trashItems) {
                        const itemType = typeof entry.itemType === 'number' ? entry.itemType : null;
                        if (itemType == null || !entry.title) continue;
                        const restoredItemData = itemType === ItemType.Totp
                            ? this.normalizeTotpDataForBackupRestore(entry.itemData, entry.title)
                            : ((entry.itemData || {}) as SecureItem['itemData']);
                        const { saveItem } = await import('../storage');
                        await saveItem({
                            itemType: itemType as SecureItem['itemType'],
                            title: entry.title,
                            notes: entry.notes || '',
                            isFavorite: entry.isFavorite ?? false,
                            sortOrder: entry.sortOrder ?? 0,
                            itemData: restoredItemData as SecureItem['itemData'],
                            createdAt: this.parseTimeLike(entry.createdAt),
                            updatedAt: this.parseTimeLike(entry.updatedAt),
                            isDeleted: true,
                            deletedAt: this.parseTimeLike(entry.deletedAt),
                        });
                        if (itemType === ItemType.Note) notesRestored++;
                        else if (itemType === ItemType.Totp) totpsRestored++;
                        else if (itemType === ItemType.Document) documentsRestored++;
                        else if (itemType === ItemType.BankCard) cardsRestored++;
                    }
                } catch (e) {
                    console.error('[BackupManager] Trash secure items restore error:', e);
                    errors.push('回收站条目恢复失败: trash_secure_items.json');
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
                            const title = item.title || item.Title || 'Unnamed';
                            let secret = item.secret || item.Secret || '';
                            let totpData: TotpData;

                            console.log('[BackupManager] Processing TOTP:', title, 'Keys:', Object.keys(item));

                            // Check if Data column exists (export format with JSON)
                            const dataJson = item.Data || item.data;

                            if (dataJson) {
                                // Export format: parse JSON from Data column
                                try {
                                    const parsedData = JSON.parse(dataJson) as unknown;
                                    totpData = this.normalizeTotpDataForBackupRestore(parsedData, title);
                                    secret = totpData.secret;
                                } catch {
                                    console.warn('[BackupManager] Failed to parse Data JSON:', dataJson);
                                    continue;
                                }
                            } else {
                                // Browser format: direct columns
                                if (!secret) {
                                    console.warn('[BackupManager] TOTP CSV has no secret:', title, 'All columns:', JSON.stringify(item));
                                    continue;
                                }
                                totpData = this.normalizeTotpDataForBackupRestore({
                                    secret,
                                    issuer: item.issuer || item.Issuer || '',
                                    accountName: item.accountName || item.AccountName || '',
                                    period: parseInt(item.period || item.Period || '30') || 30,
                                    digits: parseInt(item.digits || item.Digits || '6') || 6,
                                    algorithm: item.algorithm || item.Algorithm || 'SHA1',
                                    otpType: item.otpType || item.OtpType || item.Type || '',
                                }, title);
                            }

                            // Get notes from Notes column (if available)
                            const notes = item.Notes || item.notes || '';

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
                                notes,
                                isFavorite: false,
                                sortOrder: 0,
                                itemData: totpData,
                            });
                            existingTotpKeys.add(totpKey);
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
                            const normalizedType = itemType.toUpperCase();
                            if (normalizedType !== 'DOCUMENT' && normalizedType !== 'BANKCARD' && normalizedType !== 'BANK_CARD') continue;

                            const title = item.title || item.Title || 'Unnamed';
                            const { saveItem } = await import('../storage');
                            if (normalizedType === 'DOCUMENT') {
                                if (existingDocTitles.has(title.toLowerCase())) {
                                    console.log('[BackupManager] Skipping duplicate document:', title);
                                    continue;
                                }
                                const docData: DocumentData = {
                                    documentType: (item.documentType || item.DocumentType || 'OTHER') as DocumentType,
                                    documentNumber: item.documentNumber || item.DocumentNumber || '',
                                    fullName: item.fullName || item.FullName || '',
                                    issuedDate: item.issuedDate || item.IssuedDate || '',
                                    expiryDate: item.expiryDate || item.ExpiryDate || '',
                                    issuedBy: item.issuedBy || item.IssuedBy || '',
                                };
                                await saveItem({
                                    itemType: ItemType.Document,
                                    title,
                                    notes: '',
                                    isFavorite: false,
                                    sortOrder: 0,
                                    itemData: docData,
                                });
                                existingDocTitles.add(title.toLowerCase());
                                documentsRestored++;
                            } else {
                                const cardNumber = item.cardNumber || item.CardNumber || '';
                                const cardKey = `${title}|${cardNumber.replace(/\s+/g, '')}`.toLowerCase();
                                if (existingCardKeys.has(cardKey)) continue;
                                const cardData: BankCardData = {
                                    cardNumber,
                                    cardholderName: item.cardholderName || item.CardholderName || '',
                                    expiryMonth: item.expiryMonth || item.ExpiryMonth || '',
                                    expiryYear: item.expiryYear || item.ExpiryYear || '',
                                    cvv: item.cvv || item.CVV || '',
                                    bankName: item.bankName || item.BankName || '',
                                };
                                await saveItem({
                                    itemType: ItemType.BankCard,
                                    title,
                                    notes: '',
                                    isFavorite: false,
                                    sortOrder: 0,
                                    itemData: cardData,
                                });
                                existingCardKeys.add(cardKey);
                                cardsRestored++;
                            }
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
                cardsRestored,
                passkeysRestored,
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
                cardsRestored: 0,
                passkeysRestored: 0,
                errors: [err.message || '恢复失败'],
            };
        }
    }

    // ========== Helper Functions ==========

    private findZipFilesByPattern(zip: JSZip, pattern: RegExp): string[] {
        return Object.values(zip.files)
            .filter((file) => {
                if (file.dir) return false;
                pattern.lastIndex = 0;
                return pattern.test(file.name);
            })
            .map((file) => file.name);
    }

    private async ensureCategoryId(
        categoryName: string | undefined,
        categoryIdByName: Map<string, number>
    ): Promise<number | undefined> {
        const normalized = categoryName?.trim();
        if (!normalized) return undefined;

        const direct = categoryIdByName.get(normalized);
        if (direct !== undefined) return direct;

        const lower = normalized.toLowerCase();
        for (const [name, id] of categoryIdByName.entries()) {
            if (name.toLowerCase() === lower) return id;
        }

        const created = await saveCategory(normalized);
        categoryIdByName.set(created.name, created.id);
        return created.id;
    }

    private toFolderKey(categoryName: string | undefined): string {
        const normalized = (categoryName || '').trim();
        if (!normalized) return '_root';

        const key = normalized
            .split('')
            .map((ch) => {
                if (/[A-Za-z0-9]/.test(ch)) return ch;
                if (ch === '-' || ch === '_') return ch;
                if (/\s/.test(ch)) return '_';
                return '_';
            })
            .join('')
            .replace(/^_+|_+$/g, '');

        return key || '_root';
    }

    private formatTimestamp(date: Date): string {
        const pad = (n: number) => n.toString().padStart(2, '0');
        return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
    }

    private totpsToCSV(items: SecureItem[]): string {
        const headers = ['title', 'issuer', 'accountName', 'secret', 'period', 'digits', 'algorithm', 'otpType'];
        const rows = items.map(item => {
            const data = this.normalizeTotpDataForBackupRestore(item.itemData, item.title);
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

    private documentsAndCardsToCSV(documents: SecureItem[], cards: SecureItem[]): string {
        const headers = ['itemType', 'title', 'documentType', 'documentNumber', 'fullName', 'issuedDate', 'expiryDate', 'issuedBy', 'cardNumber', 'cardholderName', 'expiryMonth', 'expiryYear', 'cvv', 'bankName'];
        const docRows = documents.map(item => {
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
                '',
                '',
                '',
                '',
                '',
                '',
            ].join(',');
        });
        const cardRows = cards.map(item => {
            const data = item.itemData as BankCardData;
            return [
                'BANKCARD',
                this.escapeCsv(item.title),
                '',
                '',
                '',
                '',
                '',
                '',
                this.escapeCsv(data.cardNumber || ''),
                this.escapeCsv(data.cardholderName || ''),
                this.escapeCsv(data.expiryMonth || ''),
                this.escapeCsv(data.expiryYear || ''),
                this.escapeCsv(data.cvv || ''),
                this.escapeCsv(data.bankName || ''),
            ].join(',');
        });
        return [headers.join(','), ...docRows, ...cardRows].join('\n');
    }

    private itemsToCSV(items: SecureItem[]): string {
        const headers = ['id', 'itemType', 'title', 'notes', 'isFavorite', 'sortOrder', 'isDeleted', 'deletedAt', 'createdAt', 'updatedAt', 'itemData'];
        const rows = items.map(item => {
            const normalizedItemData = item.itemType === ItemType.Totp
                ? this.normalizeTotpDataForBackupRestore(item.itemData, item.title)
                : (item.itemData || {});
            return [
                item.id.toString(),
                item.itemType.toString(),
                this.escapeCsv(item.title),
                this.escapeCsv(item.notes || ''),
                item.isFavorite ? 'true' : 'false',
                item.sortOrder?.toString() || '0',
                item.isDeleted ? 'true' : 'false',
                item.deletedAt || '',
                item.createdAt || '',
                item.updatedAt || '',
                this.escapeCsv(JSON.stringify(normalizedItemData)),
            ].join(',');
        });
        return [headers.join(','), ...rows].join('\n');
    }

    private normalizeTotpDataForBackupRestore(raw: unknown, fallbackTitle: string): TotpData {
        const src = (raw && typeof raw === 'object') ? (raw as Record<string, unknown>) : {};
        const secret = this.readString(src.secret);
        const issuer = this.readString(src.issuer);
        const accountName = this.readString(src.accountName);
        const otpType = this.normalizeOtpType(src.otpType);
        const period = this.readPositiveInt(src.period);
        const digits = this.readPositiveInt(src.digits);
        const algorithm = this.normalizeAlgorithm(this.readString(src.algorithm));

        const steamMetadataKeys = [
            'steamFingerprint',
            'steamDeviceId',
            'steamSerialNumber',
            'steamSharedSecretBase64',
            'steamRevocationCode',
            'steamIdentitySecret',
            'steamTokenGid',
            'steamRawJson',
        ];
        const hasSteamMetadata = steamMetadataKeys.some((key) => this.readString(src[key]).length > 0);
        const looksLikeSteam = [issuer, accountName, fallbackTitle].some((text) => text.toLowerCase().includes('steam')) ||
            this.readString(src.link).toLowerCase().includes('encoder=steam');
        const shouldUseSteam = otpType === OtpType.STEAM ||
            hasSteamMetadata ||
            (looksLikeSteam && (otpType === undefined || otpType === OtpType.TOTP));

        const normalizedOtpType = shouldUseSteam ? OtpType.STEAM : (otpType || OtpType.TOTP);
        const normalizedDigits = shouldUseSteam ? 5 : this.clampInt(digits ?? 6, 4, 10);
        const normalizedPeriod = shouldUseSteam ? 30 : Math.max(1, period ?? 30);
        const normalizedAlgorithm = shouldUseSteam ? 'SHA1' : algorithm;

        return {
            ...(src as Record<string, unknown>),
            secret,
            issuer,
            accountName,
            period: normalizedPeriod,
            digits: normalizedDigits,
            algorithm: normalizedAlgorithm,
            otpType: normalizedOtpType,
        } as TotpData;
    }

    private normalizeOtpType(value: unknown): OtpType | undefined {
        if (typeof value !== 'string') return undefined;
        const upper = value.trim().toUpperCase();
        if (upper === OtpType.TOTP) return OtpType.TOTP;
        if (upper === OtpType.HOTP) return OtpType.HOTP;
        if (upper === OtpType.STEAM) return OtpType.STEAM;
        if (upper === OtpType.YANDEX) return OtpType.YANDEX;
        if (upper === OtpType.MOTP) return OtpType.MOTP;
        return undefined;
    }

    private normalizeAlgorithm(value: string): string {
        const upper = value.trim().toUpperCase();
        if (upper === 'SHA1' || upper === 'SHA256' || upper === 'SHA512') {
            return upper;
        }
        return 'SHA1';
    }

    private readString(value: unknown): string {
        return typeof value === 'string' ? value : '';
    }

    private readPositiveInt(value: unknown): number | undefined {
        if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
            return Math.floor(value);
        }
        if (typeof value === 'string') {
            const parsed = parseInt(value, 10);
            if (Number.isFinite(parsed) && parsed > 0) return parsed;
        }
        return undefined;
    }

    private clampInt(value: number, min: number, max: number): number {
        return Math.min(max, Math.max(min, value));
    }

    private escapeCsv(str: string): string {
        if (!str) return '';
        if (str.includes(',') || str.includes('"') || str.includes('\n')) {
            return `"${str.replace(/"/g, '""')}"`;
        }
        return str;
    }

    private parseCSV(content: string): Record<string, string>[] {
        const records = this.readCsvRecords(content);
        if (records.length < 2) return [];

        const headers = this.parseCSVLine(records[0]);
        const items: Record<string, string>[] = [];

        for (let i = 1; i < records.length; i++) {
            const values = this.parseCSVLine(records[i]);
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

    private isInQuotes(text: string): boolean {
        let inQuotes = false;
        for (let i = 0; i < text.length; i++) {
            const c = text[i];
            if (c === '"' && inQuotes && i + 1 < text.length && text[i + 1] === '"') {
                i++;
            } else if (c === '"') {
                inQuotes = !inQuotes;
            }
        }
        return inQuotes;
    }

    private readCsvRecords(content: string): string[] {
        const lines = content.split(/\r?\n/);
        const records: string[] = [];
        let current = '';

        for (const line of lines) {
            current = current ? `${current}\n${line}` : line;
            if (!this.isInQuotes(current)) {
                if (current.trim()) {
                    records.push(current);
                }
                current = '';
            }
        }

        if (current.trim()) {
            records.push(current);
        }
        return records;
    }

    private parseTimeLike(value: unknown): string | undefined {
        if (value == null || value === '') return undefined;
        if (typeof value === 'number') {
            return new Date(value).toISOString();
        }
        const text = String(value).trim();
        if (!text) return undefined;
        if (/^\d+$/.test(text)) {
            return new Date(parseInt(text, 10)).toISOString();
        }
        const ts = new Date(text).getTime();
        if (Number.isNaN(ts)) return undefined;
        return new Date(ts).toISOString();
    }
}

// Export singleton
export const backupManager = new BackupManagerService();
