import { getAllItems } from './storage';
import type { SecureItem } from '../types/models';
import { ItemType } from '../types/models';

const typeToName = (type: number): string => {
    if (type === ItemType.Password) return 'PASSWORD';
    if (type === ItemType.Totp) return 'TOTP';
    if (type === ItemType.BankCard) return 'BANKCARD';
    if (type === ItemType.Document) return 'DOCUMENT';
    if (type === ItemType.Note) return 'NOTE';
    if (type === ItemType.Passkey) return 'PASSKEY';
    if (type === ItemType.Send) return 'SEND';
    return 'UNKNOWN';
};

const escapeCsv = (value: string): string => {
    if (!value) return '';
    if (value.includes(',') || value.includes('"') || value.includes('\n') || value.includes('\r')) {
        return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
};

const toCsvLine = (columns: string[]): string => columns.map(escapeCsv).join(',');

const toTimestamp = (iso?: string): string => {
    if (!iso) return Date.now().toString();
    const ts = new Date(iso).getTime();
    return Number.isNaN(ts) ? Date.now().toString() : ts.toString();
};

export const exportMonicaCsv = async (): Promise<{ filename: string; content: string; count: number }> => {
    const items: SecureItem[] = await getAllItems({ includeDeleted: false });
    const headers = [
        'ID',
        'Type',
        'Title',
        'Data',
        'Notes',
        'IsFavorite',
        'ImagePaths',
        'CreatedAt',
        'UpdatedAt',
        'CategoryId',
        'KeePassDatabaseId',
        'KeePassGroupPath',
        'BitwardenVaultId',
        'BitwardenFolderId',
        'BitwardenCipherId',
    ];
    const rows = items.map((item) => {
        const data = JSON.stringify(item.itemData || {});
        const passwordData = item.itemType === ItemType.Password
            ? (item.itemData as {
                categoryId?: number;
                keepassDatabaseId?: number;
                keepassGroupPath?: string;
                bitwardenVaultId?: number;
                bitwardenFolderId?: string;
                bitwardenCipherId?: string;
            })
            : {};
        return toCsvLine([
            String(item.id),
            typeToName(item.itemType),
            item.title || '',
            data,
            item.notes || '',
            item.isFavorite ? 'true' : 'false',
            '',
            toTimestamp(item.createdAt),
            toTimestamp(item.updatedAt),
            passwordData.categoryId != null ? String(passwordData.categoryId) : '',
            passwordData.keepassDatabaseId != null ? String(passwordData.keepassDatabaseId) : '',
            passwordData.keepassGroupPath || '',
            passwordData.bitwardenVaultId != null ? String(passwordData.bitwardenVaultId) : '',
            passwordData.bitwardenFolderId || '',
            passwordData.bitwardenCipherId || '',
        ]);
    });

    const content = [toCsvLine(headers), ...rows].join('\n');
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const filename = `Monica_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.csv`;
    return { filename, content, count: items.length };
};

export const exportKeePassCsv = async (): Promise<{ filename: string; content: string; count: number }> => {
    const items: SecureItem[] = await getAllItems({ includeDeleted: false });
    const passwordItems = items.filter((item) => item.itemType === ItemType.Password);
    const headers = ['Title', 'User Name', 'Password', 'URL', 'Notes'];
    const rows = passwordItems.map((item) => {
        const data = item.itemData as { username?: string; password?: string; website?: string };
        return toCsvLine([
            item.title || '',
            data.username || '',
            data.password || '',
            data.website || '',
            item.notes || '',
        ]);
    });
    const content = [toCsvLine(headers), ...rows].join('\n');
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const filename = `Monica_Keepass_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.csv`;
    return { filename, content, count: passwordItems.length };
};
