export interface PasswordEntry {
    id: number;
    title: string;
    username: string;
    website: string;
    category: string;
}

export interface NoteEntry {
    id: number;
    title: string;
    content: string;
    date: string;
}

export const mockPasswords: PasswordEntry[] = [
    { id: 1, title: 'Google', username: 'joyin@gmail.com', website: 'google.com', category: 'Social' },
    { id: 2, title: 'GitHub', username: 'joyinjoester', website: 'github.com', category: 'Dev' },
    { id: 3, title: 'Twitter', username: '@joyin', website: 'twitter.com', category: 'Social' },
    { id: 4, title: 'Amazon', username: 'joyin_shop', website: 'amazon.com', category: 'Shopping' },
];

export const mockNotes: NoteEntry[] = [
    { id: 1, title: 'WiFi Password', content: 'MySecretWiFi123!', date: '2025-10-12' },
    { id: 2, title: 'Shopping List', content: 'Milk, Eggs, Bread', date: '2025-10-15' },
];
