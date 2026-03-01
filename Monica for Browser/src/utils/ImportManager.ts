/**
 * Import Manager for Monica Browser Extension
 * Handles CSV/JSON/ZIP imports and keeps compatibility with Android strategies.
 */

import type {
    SecureItem,
    PasswordEntry,
    TotpData,
    NoteData,
    DocumentData,
    BankCardData,
    PasskeyData,
    SendData,
} from '../types/models';
import { ItemType, OtpType } from '../types/models';
import { saveItem } from './storage';
import { backupManager } from './webdav';

// ========== Types ==========

export const ImportFormat = {
    UNKNOWN: 'unknown',
    CHROME_PASSWORD: 'chrome_password',
    MONICA_CSV: 'monica_csv',
    KEEPASS_CSV: 'keepass_csv',
    AEGIS_JSON: 'aegis_json',
    MONICA_ZIP: 'monica_zip',
    KEEPASS_KDBX: 'keepass_kdbx',
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
    createdAt: number | string;
    updatedAt: number | string;
    categoryId?: number;
    keepassDatabaseId?: number;
    keepassGroupPath?: string;
    bitwardenVaultId?: number;
    bitwardenFolderId?: string;
    bitwardenCipherId?: string;
    isDeleted?: boolean;
    deletedAt?: string;
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
    otpType?: string;
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

// ========== CSV Utilities ==========

function parseCsvLine(line: string): string[] {
    const fields: string[] = [];
    let currentField = '';
    let inQuotes = false;
    let i = 0;

    while (i < line.length) {
        const char = line[i];
        if (char === '"' && inQuotes && i + 1 < line.length && line[i + 1] === '"') {
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

function isInQuotes(text: string): boolean {
    let inQuotes = false;
    let i = 0;

    while (i < text.length) {
        const c = text[i];
        if (c === '"' && inQuotes && i + 1 < text.length && text[i + 1] === '"') {
            i++;
        } else if (c === '"') {
            inQuotes = !inQuotes;
        }
        i++;
    }

    return inQuotes;
}

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

    if (currentRecord.trim()) {
        records.push(currentRecord);
    }

    return records;
}

// ========== Detection ==========

function detectCsvFormat(firstLine: string): ImportFormat {
    const lowerLine = firstLine.toLowerCase();

    if (lowerLine.includes('name') && lowerLine.includes('url') && lowerLine.includes('username') && lowerLine.includes('password')) {
        return ImportFormat.CHROME_PASSWORD;
    }

    if (lowerLine.includes('type') && lowerLine.includes('title') && lowerLine.includes('data')) {
        return ImportFormat.MONICA_CSV;
    }

    const keepassHints = ['title', 'user name', 'username', 'password', 'url', 'notes', 'account', 'login'];
    const keepassMatches = keepassHints.filter((k) => lowerLine.includes(k)).length;
    if (keepassMatches >= 2 && lowerLine.includes('password')) {
        return ImportFormat.KEEPASS_CSV;
    }

    const fields = parseCsvLine(firstLine);
    if (fields.length >= 9) return ImportFormat.MONICA_CSV;
    if (fields.length >= 4 && fields.length <= 6) return ImportFormat.CHROME_PASSWORD;

    return ImportFormat.UNKNOWN;
}

function isAegisJson(content: string): boolean {
    try {
        const json = JSON.parse(content);
        if (json.db?.entries || json.entries) {
            return true;
        }
        if (json.header?.slots && typeof json.db === 'string') {
            return true;
        }
        return false;
    } catch {
        return false;
    }
}

// ========== Shared Parsing ==========

function parseItemDataString(data: string): Record<string, string> {
    const result: Record<string, string> = {};
    if (!data) return result;

    try {
        return JSON.parse(data);
    } catch {
        // fall back
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

function readString(value: unknown): string {
    return typeof value === 'string' ? value : '';
}

function readPositiveInt(value: unknown, fallback: number): number {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
        return Math.floor(value);
    }
    if (typeof value === 'string') {
        const parsed = parseInt(value, 10);
        if (Number.isFinite(parsed) && parsed > 0) return parsed;
    }
    return fallback;
}

function normalizeAlgorithm(value: unknown): string {
    const upper = readString(value).trim().toUpperCase();
    if (upper === 'SHA1' || upper === 'SHA256' || upper === 'SHA512') {
        return upper;
    }
    return 'SHA1';
}

function normalizeOtpType(value: unknown): typeof OtpType[keyof typeof OtpType] | undefined {
    const upper = readString(value).trim().toUpperCase();
    if (upper === 'YAOTP') return OtpType.YANDEX;
    if (upper === OtpType.TOTP) return OtpType.TOTP;
    if (upper === OtpType.HOTP) return OtpType.HOTP;
    if (upper === OtpType.STEAM) return OtpType.STEAM;
    if (upper === OtpType.YANDEX) return OtpType.YANDEX;
    if (upper === OtpType.MOTP) return OtpType.MOTP;
    return undefined;
}

function normalizeTotpData(raw: Record<string, unknown>, fallbackTitle: string): TotpData {
    const secret = readString(raw.secret);
    const issuer = readString(raw.issuer);
    const accountName = readString(raw.accountName) || readString(raw.account);
    const inputOtpType = normalizeOtpType(raw.otpType);
    const hasSteamMetadata = [
        'steamFingerprint',
        'steamDeviceId',
        'steamSerialNumber',
        'steamSharedSecretBase64',
        'steamRevocationCode',
        'steamIdentitySecret',
        'steamTokenGid',
        'steamRawJson',
    ].some((key) => readString(raw[key]).length > 0);
    const looksLikeSteam = [issuer, accountName, fallbackTitle].some((text) => text.toLowerCase().includes('steam')) ||
        readString(raw.link).toLowerCase().includes('encoder=steam');
    const shouldUseSteam = inputOtpType === OtpType.STEAM ||
        hasSteamMetadata ||
        (looksLikeSteam && (inputOtpType === undefined || inputOtpType === OtpType.TOTP));

    const otpType = shouldUseSteam ? OtpType.STEAM : (inputOtpType || OtpType.TOTP);
    const digits = shouldUseSteam ? 5 : Math.min(10, Math.max(4, readPositiveInt(raw.digits, 6)));
    const period = shouldUseSteam ? 30 : Math.max(1, readPositiveInt(raw.period, 30));
    const algorithm = shouldUseSteam ? 'SHA1' : normalizeAlgorithm(raw.algorithm);

    return {
        secret,
        issuer,
        accountName,
        period,
        digits,
        algorithm,
        otpType,
    };
}

function parseBooleanLike(v: string | undefined): boolean {
    const normalized = (v || '').trim().toLowerCase();
    return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'y';
}

function parseTimeLikeToIso(v: string | number | undefined): string {
    if (v == null) return new Date().toISOString();

    if (typeof v === 'number') {
        const ms = Number.isFinite(v) ? v : Date.now();
        return new Date(ms).toISOString();
    }

    const raw = v.trim();
    if (!raw) return new Date().toISOString();

    if (/^\d+$/.test(raw)) {
        const n = parseInt(raw, 10);
        return new Date(Number.isFinite(n) ? n : Date.now()).toISOString();
    }

    const parsed = new Date(raw).getTime();
    return Number.isNaN(parsed) ? new Date().toISOString() : new Date(parsed).toISOString();
}

function normalizeHeader(h: string): string {
    return h.trim().toLowerCase().replace(/[ _-]+/g, ' ');
}

function buildHeaderIndexMap(headers: string[]): Map<string, number> {
    const map = new Map<string, number>();
    headers.forEach((h, idx) => {
        map.set(normalizeHeader(h), idx);
    });
    return map;
}

function getFieldByAliases(fields: string[], headerMap: Map<string, number> | null, aliases: string[]): string {
    if (!headerMap) return '';
    for (const alias of aliases) {
        const idx = headerMap.get(normalizeHeader(alias));
        if (idx != null) {
            return fields[idx] || '';
        }
    }
    return '';
}

// ========== Conversion ==========

function convertChromePassword(parsed: ParsedChromePassword): Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> {
    const passwordEntry: PasswordEntry = {
        username: parsed.username,
        password: parsed.password,
        website: parsed.url,
    };

    return {
        itemType: ItemType.Password,
        title: parsed.name || parsed.url || parsed.username,
        notes: parsed.note || '',
        isFavorite: false,
        sortOrder: 0,
        itemData: passwordEntry,
    };
}

function convertMonicaCsv(parsed: ParsedMonicaItem): Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> | null {
    const itemTypeMap: Record<string, typeof ItemType[keyof typeof ItemType]> = {
        PASSWORD: ItemType.Password,
        TOTP: ItemType.Totp,
        NOTE: ItemType.Note,
        DOCUMENT: ItemType.Document,
        BANKCARD: ItemType.BankCard,
        BANK_CARD: ItemType.BankCard,
        CARD: ItemType.BankCard,
        PASSKEY: ItemType.Passkey,
        SEND: ItemType.Send,
        '0': ItemType.Password,
        '1': ItemType.Totp,
        '2': ItemType.BankCard,
        '3': ItemType.Document,
        '4': ItemType.Note,
        '5': ItemType.Passkey,
        '6': ItemType.Send,
    };

    const itemType = itemTypeMap[(parsed.itemType || '').toUpperCase()] ?? itemTypeMap[parsed.itemType || ''];
    if (itemType === undefined) {
        console.warn(`[ImportManager] Unknown item type: ${parsed.itemType}`);
        return null;
    }

    const parsedData = parseItemDataString(parsed.itemData);
    let itemData: PasswordEntry | TotpData | NoteData | DocumentData | BankCardData | PasskeyData | SendData;

    switch (itemType) {
        case ItemType.Password:
            itemData = {
                username: parsedData.username || '',
                password: parsedData.password || '',
                website: parsedData.website || parsedData.url || '',
                categoryId: parsed.categoryId,
                keepassDatabaseId: parsed.keepassDatabaseId,
                keepassGroupPath: parsed.keepassGroupPath,
                bitwardenVaultId: parsed.bitwardenVaultId,
                bitwardenFolderId: parsed.bitwardenFolderId,
                bitwardenCipherId: parsed.bitwardenCipherId,
            } as PasswordEntry;
            break;
        case ItemType.Totp:
            itemData = normalizeTotpData(parsedData as unknown as Record<string, unknown>, parsed.title);
            break;
        case ItemType.Note:
            itemData = {
                content: parsedData.content || parsedData.notes || '',
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
                additionalInfo: parsedData.additionalInfo,
            } as DocumentData;
            break;
        case ItemType.BankCard:
            itemData = {
                cardNumber: parsedData.cardNumber || '',
                cardholderName: parsedData.cardholderName || '',
                expiryMonth: parsedData.expiryMonth || '',
                expiryYear: parsedData.expiryYear || '',
                cvv: parsedData.cvv,
                bankName: parsedData.bankName,
            } as BankCardData;
            break;
        case ItemType.Passkey:
            itemData = {
                username: parsedData.username || '',
                rpId: parsedData.rpId || parsedData.rpid || '',
                credentialId: parsedData.credentialId || parsedData.credentialID || '',
                userDisplayName: parsedData.userDisplayName || parsedData.displayName || '',
            } as PasskeyData;
            break;
        case ItemType.Send:
            itemData = {
                sendType: (parsedData.sendType as 'text' | 'link') || 'text',
                content: parsedData.content || parsedData.text || '',
                expirationAt: parsedData.expirationAt || parsedData.expiresAt || '',
                maxAccessCount: parsedData.maxAccessCount ? parseInt(parsedData.maxAccessCount, 10) : undefined,
                accessCount: parsedData.accessCount ? parseInt(parsedData.accessCount, 10) : undefined,
            } as SendData;
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
        itemData,
        isDeleted: parsed.isDeleted ?? false,
        deletedAt: parsed.deletedAt,
    } as Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'>;
}

function convertAegisEntry(entry: AegisEntry): Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> {
    const totpData: TotpData = normalizeTotpData({
        secret: entry.secret,
        issuer: entry.issuer,
        accountName: entry.name,
        period: entry.period || 30,
        digits: entry.digits || 6,
        algorithm: entry.algorithm || 'SHA1',
        otpType: entry.otpType || '',
    }, entry.name);

    return {
        itemType: ItemType.Totp,
        title: entry.issuer ? `${entry.issuer} - ${entry.name}` : entry.name,
        notes: entry.note || '',
        isFavorite: false,
        sortOrder: 0,
        itemData: totpData,
    };
}

// ========== Importers ==========

async function importChromePassword(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.CHROME_PASSWORD,
    };

    const records = readCsvRecords(content);
    if (records.length < 2) {
        result.errors.push('文件为空或格式不正确');
        return result;
    }

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
                note: fields[4] || '',
            };

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

async function importKeePassCsv(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.KEEPASS_CSV,
    };

    const records = readCsvRecords(content);
    if (records.length === 0) {
        result.errors.push('文件为空或格式不正确');
        return result;
    }

    const firstFields = parseCsvLine(records[0]).map((f) => normalizeHeader(f));
    const knownHeaderCount = firstFields.filter((f) => ['title', 'user name', 'username', 'password', 'url', 'notes', 'account', 'name'].includes(f)).length;
    const hasHeader = knownHeaderCount >= 2;

    const headerMap = hasHeader ? buildHeaderIndexMap(parseCsvLine(records[0])) : null;
    const dataRecords = hasHeader ? records.slice(1) : records;
    result.totalCount = dataRecords.length;

    for (let i = 0; i < dataRecords.length; i++) {
        try {
            const fields = parseCsvLine(dataRecords[i]);

            const title = getFieldByAliases(fields, headerMap, ['title', 'name', 'account', '标题', '账户']) || fields[0] || '';
            const username = getFieldByAliases(fields, headerMap, ['user name', 'username', 'user_name', 'login name', 'login', 'login_username', '用户名', '账号']) || fields[1] || '';
            const password = getFieldByAliases(fields, headerMap, ['password', 'pass', 'pwd', 'login_password', '密码']) || fields[2] || '';
            const url = getFieldByAliases(fields, headerMap, ['url', 'website', 'web site', 'web_site', 'location', 'address', 'login_uri', '网址']) || fields[3] || '';
            const note = getFieldByAliases(fields, headerMap, ['notes', 'note', 'comment', 'comments', 'description', '备注', '描述']) || fields[4] || '';

            if (!title.trim() && !username.trim() && !password.trim() && !url.trim()) {
                result.skippedCount++;
                continue;
            }

            await saveItem({
                itemType: ItemType.Password,
                title: title || url || username,
                notes: note || '',
                isFavorite: false,
                sortOrder: 0,
                itemData: {
                    username,
                    password,
                    website: url,
                } as PasswordEntry,
            });
            result.importedCount++;
        } catch (error) {
            result.errorCount++;
            const lineNo = i + (hasHeader ? 2 : 1);
            result.errors.push(`行 ${lineNo}: ${error instanceof Error ? error.message : '未知错误'}`);
        }
    }

    result.success = result.importedCount > 0;
    return result;
}

async function importMonicaCsv(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.MONICA_CSV,
    };

    const records = readCsvRecords(content);
    if (records.length < 1) {
        result.errors.push('文件为空或格式不正确');
        return result;
    }

    const firstLine = records[0].toLowerCase();
    const hasHeader = firstLine.includes('type') && firstLine.includes('title');
    const headerFields = hasHeader ? parseCsvLine(records[0]) : [];
    const dataRecords = hasHeader ? records.slice(1) : records;
    result.totalCount = dataRecords.length;

    for (let i = 0; i < dataRecords.length; i++) {
        try {
            const fields = parseCsvLine(dataRecords[i]);
            const lineNo = i + (hasHeader ? 2 : 1);

            let parsed: ParsedMonicaItem;
            if (hasHeader) {
                const row: Record<string, string> = {};
                headerFields.forEach((header, index) => {
                    row[normalizeHeader(header)] = fields[index] || '';
                });

                parsed = {
                    id: parseInt(row.id || '0', 10) || 0,
                    itemType: row.type || row.itemtype || '',
                    title: row.title || '',
                    itemData: row.data || row.itemdata || '{}',
                    notes: row.notes || '',
                    isFavorite: parseBooleanLike(row.isfavorite),
                    imagePaths: row.imagepaths || '',
                    createdAt: row.createdat || Date.now(),
                    updatedAt: row.updatedat || Date.now(),
                    categoryId: row.categoryid ? parseInt(row.categoryid, 10) : undefined,
                    keepassDatabaseId: row.keepassdatabaseid ? parseInt(row.keepassdatabaseid, 10) : undefined,
                    keepassGroupPath: row.keepassgrouppath || undefined,
                    bitwardenVaultId: row.bitwardenvaultid ? parseInt(row.bitwardenvaultid, 10) : undefined,
                    bitwardenFolderId: row.bitwardenfolderid || undefined,
                    bitwardenCipherId: row.bitwardencipherid || undefined,
                    isDeleted: parseBooleanLike(row.isdeleted),
                    deletedAt: row.deletedat || undefined,
                };
            } else {
                if (fields.length < 4) {
                    result.errorCount++;
                    result.errors.push(`行 ${lineNo}: 字段数量不足`);
                    continue;
                }

                const hasCipherColumn = fields.length >= 17;
                parsed = {
                    id: parseInt(fields[0], 10) || 0,
                    itemType: fields[1] || '',
                    title: fields[2] || '',
                    itemData: fields[3] || '{}',
                    notes: fields[4] || '',
                    isFavorite: parseBooleanLike(fields[5]),
                    imagePaths: fields[6] || '',
                    createdAt: fields[7] || Date.now(),
                    updatedAt: fields[8] || Date.now(),
                    categoryId: fields[9] ? parseInt(fields[9], 10) : undefined,
                    keepassDatabaseId: fields[10] ? parseInt(fields[10], 10) : undefined,
                    keepassGroupPath: fields[11] || undefined,
                    bitwardenVaultId: fields[12] ? parseInt(fields[12], 10) : undefined,
                    bitwardenFolderId: fields[13] || undefined,
                    bitwardenCipherId: hasCipherColumn ? (fields[14] || undefined) : undefined,
                    isDeleted: parseBooleanLike(fields[hasCipherColumn ? 15 : 14]),
                    deletedAt: fields[hasCipherColumn ? 16 : 15] || undefined,
                };
            }

            const item = convertMonicaCsv(parsed);
            if (!item) {
                result.skippedCount++;
                continue;
            }

            await saveItem({
                ...item,
                createdAt: parseTimeLikeToIso(parsed.createdAt),
                updatedAt: parseTimeLikeToIso(parsed.updatedAt),
                isDeleted: parsed.isDeleted,
                deletedAt: parsed.deletedAt,
            });
            result.importedCount++;
        } catch (error) {
            result.errorCount++;
            result.errors.push(`行 ${i + (hasHeader ? 2 : 1)}: ${error instanceof Error ? error.message : '未知错误'}`);
        }
    }

    result.success = result.importedCount > 0;
    return result;
}

async function importAegisJson(content: string): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.AEGIS_JSON,
    };

    try {
        const json = JSON.parse(content);

        if (json.header?.slots && typeof json.db === 'string') {
            result.errors.push('无法导入加密的 Aegis 备份文件。请导出未加密的 JSON 文件。');
            return result;
        }

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
                if (type && !['totp', 'steam', 'hotp', 'yaotp', 'yandex', 'motp'].includes(type)) {
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
                    digits: parseInt(info.digits || '6', 10) || 6,
                    period: parseInt(info.period || '30', 10) || 30,
                    otpType: type || 'totp',
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

async function importMonicaZip(file: File): Promise<ImportResult> {
    const result: ImportResult = {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 0,
        errors: [],
        format: ImportFormat.MONICA_ZIP,
    };

    try {
        const data = await file.arrayBuffer();
        const report = await backupManager.restoreBackup(data);
        const importedCount = (report.passwordsRestored || 0)
            + (report.notesRestored || 0)
            + (report.totpsRestored || 0)
            + (report.documentsRestored || 0)
            + (report.cardsRestored || 0);

        result.totalCount = importedCount;
        result.importedCount = importedCount;
        result.errorCount = report.errors.length;
        result.errors = report.errors;
        result.success = report.success;

        if (!report.success && report.errors.length === 0) {
            result.errors.push('ZIP 导入失败，请确认是否为 Monica 备份。');
            result.errorCount = 1;
        }
        if (report.errors.some((e) => e.includes('已加密') || e.toLowerCase().includes('password'))) {
            result.errors.unshift('检测到加密 ZIP，浏览器端暂不支持输入解密密码，请在 Android 端解密后再导入。');
        }
    } catch (error) {
        result.errorCount = 1;
        result.errors = [error instanceof Error ? error.message : 'ZIP 导入失败'];
    }

    return result;
}

function importKdbxUnsupported(): ImportResult {
    return {
        success: false,
        totalCount: 0,
        importedCount: 0,
        skippedCount: 0,
        errorCount: 1,
        errors: ['暂不支持直接导入 .kdbx。请先在 Android 端或 KeePass 客户端导出为 KeePass CSV（Title/User Name/Password/URL/Notes）后导入。'],
        format: ImportFormat.KEEPASS_KDBX,
    };
}

// ========== Public APIs ==========

export async function importFromFile(file: File): Promise<ImportResult> {
    const extension = file.name.split('.').pop()?.toLowerCase() || '';

    if (extension === 'zip') {
        return importMonicaZip(file);
    }

    if (extension === 'kdbx') {
        return importKdbxUnsupported();
    }

    const content = await file.text();
    const cleanContent = content.startsWith('\uFEFF') ? content.substring(1) : content;

    if (extension === 'json' || isAegisJson(cleanContent)) {
        return importAegisJson(cleanContent);
    }

    const firstLine = cleanContent.split(/\r?\n/)[0] || '';
    const format = detectCsvFormat(firstLine);

    switch (format) {
        case ImportFormat.CHROME_PASSWORD:
            return importChromePassword(cleanContent);
        case ImportFormat.KEEPASS_CSV:
            return importKeePassCsv(cleanContent);
        case ImportFormat.MONICA_CSV:
            return importMonicaCsv(cleanContent);
        default:
            return importChromePassword(cleanContent);
    }
}

export async function importWithFormat(file: File, format: ImportFormat): Promise<ImportResult> {
    if (format === ImportFormat.MONICA_ZIP) {
        return importMonicaZip(file);
    }

    if (format === ImportFormat.KEEPASS_KDBX) {
        return importKdbxUnsupported();
    }

    const content = await file.text();
    const cleanContent = content.startsWith('\uFEFF') ? content.substring(1) : content;

    switch (format) {
        case ImportFormat.CHROME_PASSWORD:
            return importChromePassword(cleanContent);
        case ImportFormat.KEEPASS_CSV:
            return importKeePassCsv(cleanContent);
        case ImportFormat.MONICA_CSV:
            return importMonicaCsv(cleanContent);
        case ImportFormat.AEGIS_JSON:
            return importAegisJson(cleanContent);
        default:
            return importFromFile(file);
    }
}

export function validateFile(file: File): { valid: boolean; format: ImportFormat; error?: string } {
    const extension = file.name.split('.').pop()?.toLowerCase();

    if (extension === 'json') {
        return { valid: true, format: ImportFormat.AEGIS_JSON };
    }

    if (extension === 'csv') {
        return { valid: true, format: ImportFormat.UNKNOWN };
    }

    if (extension === 'zip') {
        return { valid: true, format: ImportFormat.MONICA_ZIP };
    }

    if (extension === 'kdbx') {
        return { valid: true, format: ImportFormat.KEEPASS_KDBX };
    }

    return {
        valid: false,
        format: ImportFormat.UNKNOWN,
        error: '不支持的文件格式。请选择 CSV、JSON、ZIP 或 KDBX 文件。',
    };
}
