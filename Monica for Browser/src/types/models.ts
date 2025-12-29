// Replaces enum to satisfy "erasableSyntaxOnly"
export const ItemType = {
    Password: 0,
    Totp: 1,
    BankCard: 2, // Reserved
    Document: 3,
    Note: 4
} as const;
export type ItemType = typeof ItemType[keyof typeof ItemType];

// Maps to Android: SecureItemModels.kt -> DocumentType
export const DocumentType = {
    ID_CARD: "ID_CARD",
    PASSPORT: "PASSPORT",
    DRIVER_LICENSE: "DRIVER_LICENSE",
    SOCIAL_SECURITY: "SOCIAL_SECURITY",
    OTHER: "OTHER"
} as const;
export type DocumentType = typeof DocumentType[keyof typeof DocumentType];

// Maps to Android: OtpType
export const OtpType = {
    TOTP: "TOTP",
    HOTP: "HOTP",
    STEAM: "STEAM",
    YANDEX: "YANDEX",
    MOTP: "MOTP"
} as const;
export type OtpType = typeof OtpType[keyof typeof OtpType];

// Base Interface for all Secure Items
export interface SecureItem {
    id: number; // long in C#, Long in Kotlin
    itemType: ItemType;
    title: string;
    notes: string;
    isFavorite: boolean;
    sortOrder: number;
    createdAt: string; // ISO String
    updatedAt: string; // ISO String

    // Encrypted JSON data containing type-specific fields
    itemData: PasswordEntry | NoteData | DocumentData | TotpData | BankCardData;
}

// ==========================================
// Specific Data Models (Inside itemData)
// ==========================================

export interface PasswordEntry {
    username: string;
    password: string;
    website: string;
    categoryId?: number;
    category?: string;
}

export interface NoteData {
    content: string;
    tags?: string[];
    isMarkdown?: boolean;
}

export interface DocumentData {
    documentType: DocumentType;
    documentNumber: string;
    fullName: string;
    issuedDate?: string;
    expiryDate?: string;
    issuedBy?: string;
    nationality?: string;
    additionalInfo?: string;
}

export interface TotpData {
    secret: string;
    issuer?: string;
    accountName?: string;
    period?: number;
    digits?: number;
    algorithm?: string;
    otpType?: OtpType;
}

export interface BankCardData {
    cardNumber: string;
    cardholderName: string;
    expiryMonth: string;
    expiryYear: string;
    cvv?: string;
    bankName?: string;
}
