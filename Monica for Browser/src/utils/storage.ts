import type { SecureItem } from '../types/models';
import { ItemType } from '../types/models';

const STORAGE_KEY = 'monica_vault';

// Use chrome.storage.local for both popup and content/background access
const storage = {
    get: async (key: string): Promise<SecureItem[]> => {
        try {
            // Use chrome.storage.local API
            const result = await chrome.storage.local.get(key);
            return (result as Record<string, SecureItem[]>)[key] || [];
        } catch {
            return [];
        }
    },
    set: async (key: string, value: SecureItem[]): Promise<void> => {
        try {
            await chrome.storage.local.set({ [key]: value });
        } catch {
            console.error('Storage set failed');
        }
    }
};

// ======== PUBLIC API ========

export const getAllItems = async (): Promise<SecureItem[]> => {
    return storage.get(STORAGE_KEY);
};

export const getItemsByType = async (type: ItemType): Promise<SecureItem[]> => {
    const all = await getAllItems();
    return all.filter(item => item.itemType === type);
};

export const getPasswords = () => getItemsByType(ItemType.Password);
export const getNotes = () => getItemsByType(ItemType.Note);
export const getDocuments = () => getItemsByType(ItemType.Document);
export const getTotps = () => getItemsByType(ItemType.Totp);

export const saveItem = async (item: Omit<SecureItem, 'id' | 'createdAt' | 'updatedAt'>): Promise<SecureItem> => {
    const all = await getAllItems();
    const newItem: SecureItem = {
        ...item,
        id: Date.now(),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    };
    all.push(newItem);
    await storage.set(STORAGE_KEY, all);
    return newItem;
};

export const updateItem = async (id: number, updates: Partial<SecureItem>): Promise<void> => {
    const all = await getAllItems();
    const index = all.findIndex(item => item.id === id);
    if (index !== -1) {
        all[index] = { ...all[index], ...updates, updatedAt: new Date().toISOString() };
        await storage.set(STORAGE_KEY, all);
    }
};

export const deleteItem = async (id: number): Promise<void> => {
    const all = await getAllItems();
    const filtered = all.filter(item => item.id !== id);
    await storage.set(STORAGE_KEY, filtered);
};

export const clearAllData = async (): Promise<void> => {
    await storage.set(STORAGE_KEY, []);
    localStorage.removeItem(CATEGORIES_KEY);
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

    // Also clear categoryId from passwords that had this category
    const all = await getAllItems();
    let updated = false;
    for (const item of all) {
        if (item.itemType === ItemType.Password) {
            const data = item.itemData as { categoryId?: number };
            if (data.categoryId === id) {
                data.categoryId = undefined;
                updated = true;
            }
        }
    }
    if (updated) {
        await storage.set(STORAGE_KEY, all);
    }
};
