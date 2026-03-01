import type { SecureItem } from '../types/models';
import { ItemType } from '../types/models';

const STORAGE_KEY = 'monica_vault';
const TIMELINE_KEY = 'monica_timeline';

export interface TimelineEntry {
    id: number;
    action: 'created' | 'updated' | 'deleted' | 'restored' | 'purged';
    itemId: number;
    itemType: number;
    title: string;
    timestamp: string;
}

type BitwardenMutationOperation = 'upsert' | 'delete' | 'restore' | 'purge';

interface BitwardenLocalMutationPayload {
    operation: BitwardenMutationOperation;
    item: SecureItem;
}

export type SaveItemInput = Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'> & {
    id?: number;
    createdAt?: string;
    updatedAt?: string;
    isDeleted?: boolean;
    deletedAt?: string;
};

const storage = {
    getItems: async (key: string): Promise<SecureItem[]> => {
        try {
            const result = await chrome.storage.local.get(key);
            return (result as Record<string, SecureItem[]>)[key] || [];
        } catch {
            return [];
        }
    },
    setItems: async (key: string, value: SecureItem[]): Promise<void> => {
        try {
            await chrome.storage.local.set({ [key]: value });
        } catch {
            console.error('Storage set failed');
        }
    },
    getTimeline: async (): Promise<TimelineEntry[]> => {
        try {
            const result = await chrome.storage.local.get(TIMELINE_KEY);
            return (result as Record<string, TimelineEntry[]>)[TIMELINE_KEY] || [];
        } catch {
            return [];
        }
    },
    setTimeline: async (entries: TimelineEntry[]): Promise<void> => {
        try {
            await chrome.storage.local.set({ [TIMELINE_KEY]: entries });
        } catch {
            console.error('Timeline set failed');
        }
    }
};

const logTimeline = async (
    action: TimelineEntry['action'],
    item: Pick<SecureItem, 'id' | 'itemType' | 'title'>
): Promise<void> => {
    const entries = await storage.getTimeline();
    entries.unshift({
        id: Date.now() + Math.floor(Math.random() * 1000),
        action,
        itemId: item.id,
        itemType: item.itemType,
        title: item.title,
        timestamp: new Date().toISOString(),
    });
    await storage.setTimeline(entries.slice(0, 1000));
};

const notifyBitwardenLocalMutation = async (mutation?: BitwardenLocalMutationPayload): Promise<void> => {
    try {
        const { bitwardenSyncBridge } = await import('./bitwarden/BitwardenSyncBridge');
        if (mutation) {
            await bitwardenSyncBridge.recordLocalMutation(mutation);
        } else {
            bitwardenSyncBridge.requestLocalMutationSync();
        }
    } catch {
        // Keep local mutation robust even if Bitwarden module is unavailable.
    }
};

// ======== PUBLIC API ========
export const getAllItems = async (options?: { includeDeleted?: boolean }): Promise<SecureItem[]> => {
    const all = await storage.getItems(STORAGE_KEY);
    const includeDeleted = options?.includeDeleted === true;
    return includeDeleted ? all : all.filter((item) => !item.isDeleted);
};

export const getItemsByType = async (
    type: number,
    options?: { includeDeleted?: boolean }
): Promise<SecureItem[]> => {
    const all = await getAllItems(options);
    return all.filter(item => item.itemType === type);
};

export const getPasswords = () => getItemsByType(ItemType.Password);
export const getNotes = () => getItemsByType(ItemType.Note);
export const getDocuments = () => getItemsByType(ItemType.Document);
export const getTotps = () => getItemsByType(ItemType.Totp);
export const getBankCards = () => getItemsByType(ItemType.BankCard);
export const getPasskeys = () => getItemsByType(ItemType.Passkey);
export const getSendItems = () => getItemsByType(ItemType.Send);

export const getRecycleBinItems = async (): Promise<SecureItem[]> => {
    const all = await getAllItems({ includeDeleted: true });
    return all
        .filter(item => item.isDeleted)
        .sort((a, b) => new Date(b.deletedAt || 0).getTime() - new Date(a.deletedAt || 0).getTime());
};

export const getTimelineEntries = async (limit = 200): Promise<TimelineEntry[]> => {
    const entries = await storage.getTimeline();
    return entries.slice(0, limit);
};

export const replaceTimelineEntries = async (entries: TimelineEntry[]): Promise<void> => {
    const normalized = [...entries]
        .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
        .slice(0, 1000);
    await storage.setTimeline(normalized);
};

export const mergeTimelineEntries = async (entries: TimelineEntry[]): Promise<void> => {
    if (entries.length === 0) return;
    const existing = await storage.getTimeline();
    const keyOf = (entry: TimelineEntry) => `${entry.action}|${entry.itemId}|${entry.timestamp}|${entry.title}`;
    const merged = new Map<string, TimelineEntry>();
    [...existing, ...entries].forEach((entry) => {
        merged.set(keyOf(entry), entry);
    });
    const sorted = Array.from(merged.values())
        .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
        .slice(0, 1000);
    await storage.setTimeline(sorted);
};

export const saveItem = async (item: SaveItemInput): Promise<SecureItem> => {
    const all = await getAllItems({ includeDeleted: true });
    const now = new Date().toISOString();
    const newItem: SecureItem = {
        ...item,
        id: typeof item.id === 'number' ? item.id : Date.now() + Math.floor(Math.random() * 1000),
        createdAt: item.createdAt || now,
        updatedAt: item.updatedAt || now,
        isDeleted: item.isDeleted ?? false,
        deletedAt: item.deletedAt,
    };
    all.push(newItem);
    await storage.setItems(STORAGE_KEY, all);
    await logTimeline('created', newItem);
    await notifyBitwardenLocalMutation({ operation: 'upsert', item: newItem });
    return newItem;
};

export const updateItem = async (id: number, updates: Partial<SecureItem>): Promise<void> => {
    const all = await getAllItems({ includeDeleted: true });
    const index = all.findIndex(item => item.id === id);
    if (index !== -1) {
        all[index] = {
            ...all[index],
            ...updates,
            updatedAt: new Date().toISOString()
        };
        await storage.setItems(STORAGE_KEY, all);
        await logTimeline('updated', all[index]);
        await notifyBitwardenLocalMutation({ operation: 'upsert', item: all[index] });
    }
};

export const deleteItem = async (id: number): Promise<void> => {
    const all = await getAllItems({ includeDeleted: true });
    const index = all.findIndex(item => item.id === id);
    if (index !== -1) {
        all[index] = {
            ...all[index],
            isDeleted: true,
            deletedAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };
        await storage.setItems(STORAGE_KEY, all);
        await logTimeline('deleted', all[index]);
        await notifyBitwardenLocalMutation({ operation: 'delete', item: all[index] });
    }
};

export const restoreItem = async (id: number): Promise<void> => {
    const all = await getAllItems({ includeDeleted: true });
    const index = all.findIndex(item => item.id === id);
    if (index !== -1) {
        all[index] = {
            ...all[index],
            isDeleted: false,
            deletedAt: undefined,
            updatedAt: new Date().toISOString(),
        };
        await storage.setItems(STORAGE_KEY, all);
        await logTimeline('restored', all[index]);
        await notifyBitwardenLocalMutation({ operation: 'restore', item: all[index] });
    }
};

export const permanentlyDeleteItem = async (id: number): Promise<void> => {
    const all = await getAllItems({ includeDeleted: true });
    const target = all.find(item => item.id === id);
    const filtered = all.filter(item => item.id !== id);
    await storage.setItems(STORAGE_KEY, filtered);
    if (target) {
        await logTimeline('purged', target);
        await notifyBitwardenLocalMutation({ operation: 'purge', item: target });
        return;
    }
    await notifyBitwardenLocalMutation();
};

export const clearAllData = async (): Promise<void> => {
    await storage.setItems(STORAGE_KEY, []);
    await storage.setTimeline([]);
    localStorage.removeItem(CATEGORIES_KEY);
    await notifyBitwardenLocalMutation();
};

// ======== CATEGORIES ========
const CATEGORIES_KEY = 'monica_categories';

export interface Category {
    id: number;
    name: string;
    sortOrder: number;
}

export const getCategories = async (): Promise<Category[]> => {
    try {
        const data = localStorage.getItem(CATEGORIES_KEY);
        return data ? JSON.parse(data) : [];
    } catch {
        return [];
    }
};

export const saveCategory = async (name: string): Promise<Category> => {
    const categories = await getCategories();
    const newCategory: Category = {
        id: Date.now(),
        name,
        sortOrder: categories.length,
    };
    categories.push(newCategory);
    localStorage.setItem(CATEGORIES_KEY, JSON.stringify(categories));
    return newCategory;
};

export const updateCategory = async (id: number, name: string): Promise<void> => {
    const categories = await getCategories();
    const index = categories.findIndex(c => c.id === id);
    if (index !== -1) {
        categories[index].name = name;
        localStorage.setItem(CATEGORIES_KEY, JSON.stringify(categories));
    }
};

export const deleteCategory = async (id: number): Promise<void> => {
    const categories = await getCategories();
    const filtered = categories.filter(c => c.id !== id);
    localStorage.setItem(CATEGORIES_KEY, JSON.stringify(filtered));

    const all = await getAllItems({ includeDeleted: true });
    let updated = false;
    for (const item of all) {
        if (item.itemType === ItemType.Password && !item.isDeleted) {
            const data = item.itemData as { categoryId?: number };
            if (data.categoryId === id) {
                data.categoryId = undefined;
                updated = true;
            }
        }
    }
    if (updated) {
        await storage.setItems(STORAGE_KEY, all);
    }
};

export const replaceCategories = async (categories: Category[]): Promise<void> => {
    const normalized = [...categories]
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
        .map((cat, index) => ({
            id: cat.id ?? Date.now() + index,
            name: cat.name,
            sortOrder: typeof cat.sortOrder === 'number' ? cat.sortOrder : index,
        }));
    localStorage.setItem(CATEGORIES_KEY, JSON.stringify(normalized));
};
