/**
 * Import Manager for Monica Browser Extension
 * Handles CSV and JSON file imports for passwords, notes, documents, and TOTP
 * 
 * Supported formats:
 * - Chrome Password CSV (name, url, username, password, note)
 * - Monica CSV Export (9-field format)
 * - Aegis JSON (TOTP authenticators)
 */

import type { SecureItem, PasswordEntry, TotpData, NoteData, DocumentData, BankCardData } from '../types/models';
import { ItemType } from '../types/models';
import { saveItem } from './storage';

// ========== Types ==========

export const ImportFormat = {
    UNKNOWN: 'unknown',
    CHROME_PASSWORD: 'chrome_password',
    MONICA_CSV: 'monica_csv',
    AEGIS_JSON: 'aegis_json'
} as const;
export type ImportFormat = typeof ImportFormat[keyof typeof ImportFormat];

export interface ImportResult {
    success: boolean;
    totalCount: number;
    importedCount: number;
    skippedCount: number;
    errorCount: number;
    errors: string[];
    format: ImportFormat;
}

interface ParsedChromePassword {
    name: string;
    url: string;
    username: string;
    password: string;
    note: string;
}

interface ParsedMonicaItem {
    id: number;
    itemType: string;
    title: string;
    itemData: string;
    notes: string;
    isFavorite: boolean;
    imagePaths: string;
    createdAt: number;
    updatedAt: number;
}

interface AegisEntry {
    uuid: string;
    name: string;
    issuer: string;
    note: string;
    secret: string;
    algorithm: string;
    digits: number;
    period: number;
}

interface AegisJsonEntry {
    uuid?: string;
    name?: string;
    issuer?: string;
    note?: string;
    type?: string;
    info: {
        secret: string;
        algo?: string;
        digits?: string;
        period?: string;
    };
}

// ========== CSV Parsing ==========

/**
 * Parse a CSV line handling quoted fields and escaped quotes
 */
function parseCsvLine(line: string): string[] {
    const fields: string[] = [];
    let currentField = '';
    let inQuotes = false;
    let i = 0;

    while (i < line.length) {
        const char = line[i];

        if (char === '"' && inQuotes && i + 1 < line.length && line[i + 1] === '"') {
            // Escaped quote
            currentField += '"';
            i++;
        } else if (char === '"') {
            inQuotes = !inQuotes;
        } else if (char === ',' && !inQuotes) {
            fields.push(currentField.trim());
            currentField = '';
        } else {
            currentField += char;
        }
        i++;
    }
    fields.push(currentField.trim());

    return fields;
}

/**
 * Check if we're in an unclosed quote state
 */
function isInQuotes(text: string): boolean {
    let inQuotes = false;
    let i = 0;

    while (i < text.length) {
        const c = text[i];
        if (c === '"' && inQuotes && i + 1 < text.length && text[i + 1] === '"') {
            // Escaped quote, skip
            i++;
        } else if (c === '"') {
            inQuotes = !inQuotes;
        }
        i++;
    }

    return inQuotes;
}

/**
 * Read complete CSV records (handling multi-line quoted fields)
 */
function readCsvRecords(content: string): string[] {
    const lines = content.split(/\r?\n/);
    const records: string[] = [];
    let currentRecord = '';

    for (const line of lines) {
        if (currentRecord) {
            currentRecord += '\n' + line;
        } else {
            currentRecord = line;
        }

        if (!isInQuotes(currentRecord)) {
            if (currentRecord.trim()) {
                records.push(currentRecord);
            }
            currentRecord = '';
        }
    }

    // Handle last record if not closed
    if (currentRecord.trim()) {
        records.push(currentRecord);
    }

    return records;
}

// ========== Format Detection ==========

/**
 * Detect CSV format from the header line
 */
function detectCsvFormat(firstLine: string): ImportFormat {
    const lowerLine = firstLine.toLowerCase();

    // Chrome Password format: name,url,username,password[,note]
    if (lowerLine.includes('name') &&
        lowerLine.includes('url') &&
        lowerLine.includes('username') &&
        lowerLine.includes('password')) {
        return ImportFormat.CHROME_PASSWORD;
    }

    // Monica CSV format: ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt
    if (lowerLine.includes('type') &&
        lowerLine.includes('title') &&
        lowerLine.includes('data')) {
        return ImportFormat.MONICA_CSV;
    }

    // Try to guess by field count
    const fields = parseCsvLine(firstLine);
    if (fields.length >= 9) {
        return ImportFormat.MONICA_CSV;
    } else if (fields.length >= 4 && fields.length <= 5) {
        return ImportFormat.CHROME_PASSWORD;
    }

    return ImportFormat.UNKNOWN;
}

/**
 * Check if content is Aegis JSON
 */
function isAegisJson(content: string): boolean {
    try {
        const json = JSON.parse(content);
        // Aegis has either db.entries or entries at root level
        if (json.db?.entries || json.entries) {
            return true;
        }
        // Check for encrypted Aegis (has header with slots)
        if (json.header?.slots && typeof json.db === 'string') {
            return true;
        }
        return false;
    } catch {
        return false;
    }
}

// ========== Format Conversion ==========

/**
 * Convert Chrome password entry to SecureItem
 */
function convertChromePassword(parsed: ParsedChromePassword): Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> {
    const passwordEntry: PasswordEntry = {
        username: parsed.username,
        password: parsed.password,
        website: parsed.url
    };

    return {
        itemType: ItemType.Password,
        title: parsed.name || parsed.url || parsed.username,
        notes: parsed.note || '',
        isFavorite: false,
        sortOrder: 0,
        itemData: passwordEntry
    };
}

/**
 * Parse itemData string (format: "key:value;key:value")
 */
function parseItemDataString(data: string): Record<string, string> {
    const result: Record<string, string> = {};

    if (!data) return result;

    // Try parsing as JSON first
    try {
        return JSON.parse(data);
    } catch {
        // Fall back to key:value;key:value format
    }

    const pairs = data.split(';');
    for (const pair of pairs) {
        const colonIndex = pair.indexOf(':');
        if (colonIndex > 0) {
            const key = pair.substring(0, colonIndex).trim();
            const value = pair.substring(colonIndex + 1).trim();
            result[key] = value;
        }
    }

    return result;
}

/**
 * Convert Monica CSV item to SecureItem
 */
function convertMonicaCsv(parsed: ParsedMonicaItem): Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> | null {
    const itemTypeMap: Record<string, typeof ItemType[keyof typeof ItemType]> = {
        'PASSWORD': ItemType.Password,
        'TOTP': ItemType.Totp,
        'NOTE': ItemType.Note,
        'DOCUMENT': ItemType.Document,
        'BANKCARD': ItemType.BankCard
    };

    const itemType = itemTypeMap[parsed.itemType.toUpperCase()];
    if (itemType === undefined) {
        console.warn(`[ImportManager] Unknown item type: ${parsed.itemType}`);
        return null;
    }

    // Parse itemData based on type
    let itemData: PasswordEntry | TotpData | NoteData | DocumentData | BankCardData;
    const parsedData = parseItemDataString(parsed.itemData);

    switch (itemType) {
        case ItemType.Password:
            itemData = {
                username: parsedData.username || '',
                password: parsedData.password || '',
                website: parsedData.website || parsedData.url || ''
            } as PasswordEntry;
            break;
        case ItemType.Totp:
            itemData = {
                secret: parsedData.secret || '',
                issuer: parsedData.issuer || '',
                accountName: parsedData.accountName || parsedData.account || '',
                period: parseInt(parsedData.period) || 30,
                digits: parseInt(parsedData.digits) || 6,
                algorithm: parsedData.algorithm || 'SHA1'
            } as TotpData;
            break;
        case ItemType.Note:
            itemData = {
                content: parsedData.content || ''
            } as NoteData;
            break;
        case ItemType.Document:
            itemData = {
                documentType: parsedData.documentType || 'OTHER',
                documentNumber: parsedData.documentNumber || '',
                fullName: parsedData.fullName || '',
                issuedDate: parsedData.issuedDate,
                expiryDate: parsedData.expiryDate,
                issuedBy: parsedData.issuedBy,
                additionalInfo: parsedData.additionalInfo
            } as DocumentData;
            break;
        case ItemType.BankCard:
            itemData = {
                cardNumber: parsedData.cardNumber || '',
                cardholderName: parsedData.cardholderName || '',
                expiryMonth: parsedData.expiryMonth || '',
                expiryYear: parsedData.expiryYear || '',
                cvv: parsedData.cvv,
                bankName: parsedData.bankName
            } as BankCardData;
            break;
        default:
            itemData = {} as PasswordEntry;
    }

    return {
        itemType,
        title: parsed.title,
        notes: parsed.notes,
        isFavorite: parsed.isFavorite,
        sortOrder: 0,
        itemData
    };
}

/**
 * Convert Aegis entry to SecureItem
 */
function convertAegisEntry(entry: AegisEntry): Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> {
    const totpData: TotpData = {
        secret: entry.secret,
        issuer: entry.issuer,
        accountName: entry.name,
        period: entry.period || 30,
        digits: entry.digits || 6,
        algorithm: entry.algorithm || 'SHA1'
    };

    return {
        itemType: ItemType.Totp,
        title: entry.issuer ? `${entry.issuer} - ${entry.name}` : entry.name,
        notes: entry.note || '',
        isFavorite: false,
        sortOrder: 0,
        itemData: totpData
    };
}

// ========== Import Functions ==========

/**
 * Import Chrome Password CSV
 */
async function importChromePassword(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.CHROME_PASSWORD
    };

    const records = readCsvRecords(content);
    if (records.length < 2) {
        result.errors.push('文件为空或格式不正确');
        return result;
    }

    // Skip header
    const dataRecords = records.slice(1);
    result.totalCount = dataRecords.length;

    for (let i = 0; i < dataRecords.length; i++) {
        try {
            const fields = parseCsvLine(dataRecords[i]);
            if (fields.length < 4) {
                result.errorCount++;
                result.errors.push(`行 ${i + 2}: 字段数量不足`);
                continue;
            }

            const parsed: ParsedChromePassword = {
                name: fields[0] || '',
                url: fields[1] || '',
                username: fields[2] || '',
                password: fields[3] || '',
                note: fields[4] || ''
            };

            // Skip empty entries
            if (!parsed.username && !parsed.password && !parsed.name) {
                result.skippedCount++;
                continue;
            }

            const item = convertChromePassword(parsed);
            await saveItem(item);
            result.importedCount++;
        } catch (error) {
            result.errorCount++;
            result.errors.push(`行 ${i + 2}: ${error instanceof Error ? error.message : '未知错误'}`);
        }
    }

    result.success = result.importedCount > 0;
    return result;
}

/**
 * Import Monica CSV
 */
async function importMonicaCsv(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.MONICA_CSV
    };

    const records = readCsvRecords(content);
    if (records.length < 2) {
        result.errors.push('文件为空或格式不正确');
        return result;
    }

    // Check if first line is header
    const firstLine = records[0].toLowerCase();
    const hasHeader = firstLine.includes('type') && firstLine.includes('title');
    const dataRecords = hasHeader ? records.slice(1) : records;
    result.totalCount = dataRecords.length;

    for (let i = 0; i < dataRecords.length; i++) {
        try {
            const fields = parseCsvLine(dataRecords[i]);
            if (fields.length < 9) {
                result.errorCount++;
                result.errors.push(`行 ${i + (hasHeader ? 2 : 1)}: 字段数量不足 (需要9个)`);
                continue;
            }

            const parsed: ParsedMonicaItem = {
                id: parseInt(fields[0]) || 0,
                itemType: fields[1] || '',
                title: fields[2] || '',
                itemData: fields[3] || '',
                notes: fields[4] || '',
                isFavorite: fields[5]?.toLowerCase() === 'true',
                imagePaths: fields[6] || '',
                createdAt: parseInt(fields[7]) || Date.now(),
                updatedAt: parseInt(fields[8]) || Date.now()
            };

            const item = convertMonicaCsv(parsed);
            if (!item) {
                result.skippedCount++;
                continue;
            }

            await saveItem(item);
            result.importedCount++;
        } catch (error) {
            result.errorCount++;
            result.errors.push(`行 ${i + (hasHeader ? 2 : 1)}: ${error instanceof Error ? error.message : '未知错误'}`);
        }
    }

    result.success = result.importedCount > 0;
    return result;
}

/**
 * Import Aegis JSON
 */
async function importAegisJson(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.AEGIS_JSON
    };

    try {
        const json = JSON.parse(content);

        // Check for encrypted vault
        if (json.header?.slots && typeof json.db === 'string') {
            result.errors.push('无法导入加密的 Aegis 备份文件。请导出未加密的 JSON 文件。');
            return result;
        }

        // Get entries array
        let entries: AegisJsonEntry[] = [];
        if (json.db?.entries) {
            entries = json.db.entries;
        } else if (json.entries) {
            entries = json.entries;
        }

        if (!entries || entries.length === 0) {
            result.errors.push('未找到有效的 TOTP 条目');
            return result;
        }

        result.totalCount = entries.length;

        for (let i = 0; i < entries.length; i++) {
            try {
                const entry = entries[i];
                const type = entry.type?.toLowerCase();

                // Only process TOTP entries
                if (type !== 'totp') {
                    result.skippedCount++;
                    continue;
                }

                const info = entry.info;
                if (!info?.secret) {
                    result.skippedCount++;
                    continue;
                }

                const aegisEntry: AegisEntry = {
                    uuid: entry.uuid || crypto.randomUUID(),
                    name: entry.name || '',
                    issuer: entry.issuer || '',
                    note: entry.note || '',
                    secret: info.secret,
                    algorithm: info.algo || 'SHA1',
                    digits: parseInt(info.digits || '6') || 6,
                    period: parseInt(info.period || '30') || 30
                };

                const item = convertAegisEntry(aegisEntry);
                await saveItem(item);
                result.importedCount++;
            } catch (error) {
                result.errorCount++;
                result.errors.push(`条目 ${i + 1}: ${error instanceof Error ? error.message : '未知错误'}`);
            }
        }

        result.success = result.importedCount > 0;
    } catch (error) {
        result.errors.push(`JSON 解析失败: ${error instanceof Error ? error.message : '未知错误'}`);
    }

    return result;
}

// ========== Main API ==========

/**
 * Import from file - auto-detects format
 */
export async function importFromFile(file: File): Promise<ImportResult> {
    const content = await file.text();

    // Remove BOM if present
    const cleanContent = content.startsWith('\uFEFF') ? content.substring(1) : content;

    // Detect format
    if (file.name.endsWith('.json') || isAegisJson(cleanContent)) {
        return importAegisJson(cleanContent);
    }

    // For CSV files, detect format from content
    const firstLine = cleanContent.split(/\r?\n/)[0] || '';
    const format = detectCsvFormat(firstLine);

    switch (format) {
        case ImportFormat.CHROME_PASSWORD:
            return importChromePassword(cleanContent);
        case ImportFormat.MONICA_CSV:
            return importMonicaCsv(cleanContent);
        default:
            // Try Chrome format as fallback for unknown CSV
            return importChromePassword(cleanContent);
    }
}

/**
 * Import with specific format
 */
export async function importWithFormat(file: File, format: ImportFormat): Promise<ImportResult> {
    const content = await file.text();
    const cleanContent = content.startsWith('\uFEFF') ? content.substring(1) : content;

    switch (format) {
        case ImportFormat.CHROME_PASSWORD:
            return importChromePassword(cleanContent);
        case ImportFormat.MONICA_CSV:
            return importMonicaCsv(cleanContent);
        case ImportFormat.AEGIS_JSON:
            return importAegisJson(cleanContent);
        default:
            return importFromFile(file);
    }
}

/**
 * Validate file before import (quick check)
 */
export function validateFile(file: File): { valid: boolean; format: ImportFormat; error?: string } {
    const extension = file.name.split('.').pop()?.toLowerCase();

    if (extension === 'json') {
        return { valid: true, format: ImportFormat.AEGIS_JSON };
    }

    if (extension === 'csv') {
        return { valid: true, format: ImportFormat.UNKNOWN }; // Will be detected later
    }

    return {
        valid: false,
        format: ImportFormat.UNKNOWN,
        error: '不支持的文件格式。请选择 CSV 或 JSON 文件。'
    };
}
