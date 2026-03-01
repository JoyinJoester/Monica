/**
 * Encryption Helper for Monica Browser Extension
 * Uses Web Crypto API with AES-GCM for secure encryption
 */

// ========== Constants ==========
// Android-compatible header: "MONICA_ENC_V1"
const ANDROID_MAGIC_HEADER = new TextEncoder().encode('MONICA_ENC_V1');
const ANDROID_SALT_LENGTH = 32;

// Legacy browser header (kept for backward compatibility)
const LEGACY_MAGIC_HEADER = new Uint8Array([0x4D, 0x4F, 0x4E, 0x49, 0x43, 0x41, 0x45, 0x4E]); // "MONICAEN"
const LEGACY_SALT_LENGTH = 16;

const IV_LENGTH = 12;
const KEY_LENGTH = 256;
const ITERATIONS = 100000;

// ========== Types ==========
export interface EncryptedData {
    salt: Uint8Array;
    iv: Uint8Array;
    data: Uint8Array;
}

type EncryptionFormat = 'android_v1' | 'legacy_browser';

function detectEncryptionFormat(data: ArrayBuffer | Uint8Array): EncryptionFormat | null {
    const arr = new Uint8Array(data);

    if (arr.length >= ANDROID_MAGIC_HEADER.length) {
        let androidMatch = true;
        for (let i = 0; i < ANDROID_MAGIC_HEADER.length; i++) {
            if (arr[i] !== ANDROID_MAGIC_HEADER[i]) {
                androidMatch = false;
                break;
            }
        }
        if (androidMatch) {
            return 'android_v1';
        }
    }

    if (arr.length >= LEGACY_MAGIC_HEADER.length) {
        let legacyMatch = true;
        for (let i = 0; i < LEGACY_MAGIC_HEADER.length; i++) {
            if (arr[i] !== LEGACY_MAGIC_HEADER[i]) {
                legacyMatch = false;
                break;
            }
        }
        if (legacyMatch) {
            return 'legacy_browser';
        }
    }

    return null;
}

// ========== PBKDF2 Key Derivation ==========
async function deriveKey(password: string, salt: Uint8Array): Promise<CryptoKey> {
    const encoder = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
        'raw',
        encoder.encode(password),
        'PBKDF2',
        false,
        ['deriveKey']
    );

    return crypto.subtle.deriveKey(
        {
            name: 'PBKDF2',
            salt: salt.buffer as ArrayBuffer,
            iterations: ITERATIONS,
            hash: 'SHA-256',
        },
        keyMaterial,
        { name: 'AES-GCM', length: KEY_LENGTH },
        false,
        ['encrypt', 'decrypt']
    );
}

// ========== Encryption Functions ==========

/**
 * Encrypt data with password
 * @param data - Data to encrypt (ArrayBuffer or Uint8Array)
 * @param password - Encryption password
 * @returns Encrypted data as ArrayBuffer (with magic header, salt, iv)
 */
export async function encrypt(
    data: ArrayBuffer | Uint8Array,
    password: string
): Promise<ArrayBuffer> {
    // Generate random salt and IV
    // Use Android-compatible format by default.
    const salt = crypto.getRandomValues(new Uint8Array(ANDROID_SALT_LENGTH));
    const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));

    // Derive key from password
    const key = await deriveKey(password, salt);

    // Convert Uint8Array to ArrayBuffer if needed
    const dataBuffer = data instanceof Uint8Array ? data.buffer as ArrayBuffer : data;

    // Encrypt
    const encrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: iv.buffer as ArrayBuffer },
        key,
        dataBuffer
    );

    // Combine: ANDROID_MAGIC_HEADER + salt + iv + encryptedData
    const encryptedArray = new Uint8Array(encrypted);
    const result = new Uint8Array(
        ANDROID_MAGIC_HEADER.length + ANDROID_SALT_LENGTH + IV_LENGTH + encryptedArray.length
    );

    let offset = 0;
    result.set(ANDROID_MAGIC_HEADER, offset);
    offset += ANDROID_MAGIC_HEADER.length;

    result.set(salt, offset);
    offset += ANDROID_SALT_LENGTH;

    result.set(iv, offset);
    offset += IV_LENGTH;

    result.set(encryptedArray, offset);

    return result.buffer;
}

/**
 * Decrypt data with password
 * @param encryptedData - Encrypted data (with header)
 * @param password - Decryption password
 * @returns Decrypted data as ArrayBuffer
 */
export async function decrypt(
    encryptedData: ArrayBuffer | Uint8Array,
    password: string
): Promise<ArrayBuffer> {
    const data = new Uint8Array(encryptedData);

    const format = detectEncryptionFormat(data);
    if (!format) {
        throw new Error('Invalid encrypted file format');
    }

    const headerLength = format === 'android_v1' ? ANDROID_MAGIC_HEADER.length : LEGACY_MAGIC_HEADER.length;
    const saltLength = format === 'android_v1' ? ANDROID_SALT_LENGTH : LEGACY_SALT_LENGTH;

    let offset = headerLength;

    // Extract salt
    const salt = data.slice(offset, offset + saltLength);
    offset += saltLength;

    // Extract IV
    const iv = data.slice(offset, offset + IV_LENGTH);
    offset += IV_LENGTH;

    // Extract encrypted data
    const encrypted = data.slice(offset);

    // Derive key
    const key = await deriveKey(password, salt);

    // Decrypt
    try {
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: iv.buffer as ArrayBuffer },
            key,
            encrypted.buffer as ArrayBuffer
        );
        return decrypted;
    } catch {
        throw new Error('解密失败：密码错误或文件已损坏');
    }
}

/**
 * Check if data is encrypted (has Monica magic header)
 */
export function isEncrypted(data: ArrayBuffer | Uint8Array): boolean {
    return detectEncryptionFormat(data) !== null;
}

/**
 * Encrypt file data and return with .enc suffix indicator
 */
export async function encryptFile(
    fileData: ArrayBuffer,
    password: string
): Promise<ArrayBuffer> {
    return encrypt(fileData, password);
}

/**
 * Decrypt file data
 */
export async function decryptFile(
    encryptedData: ArrayBuffer,
    password: string
): Promise<ArrayBuffer> {
    return decrypt(encryptedData, password);
}

// ========== Password Validation ==========
export function validateEncryptionPassword(password: string): { valid: boolean; message: string } {
    if (!password) {
        return { valid: false, message: '请输入加密密码' };
    }
    if (password.length < 6) {
        return { valid: false, message: '密码至少需要 6 个字符' };
    }
    return { valid: true, message: '' };
}

// ========== Simple Obfuscation for Settings ==========
// Note: This is NOT cryptographic security, just basic obfuscation
// to prevent casual viewing of credentials in storage
const OBFUSCATION_KEY = 'M0n1c4_Br0ws3r_K3y';

export function obfuscateString(value: string): string {
    if (!value) return '';
    const encoded = btoa(unescape(encodeURIComponent(value)));
    // XOR with key for basic obfuscation
    let result = '';
    for (let i = 0; i < encoded.length; i++) {
        result += String.fromCharCode(
            encoded.charCodeAt(i) ^ OBFUSCATION_KEY.charCodeAt(i % OBFUSCATION_KEY.length)
        );
    }
    return btoa(result);
}

export function deobfuscateString(obfuscated: string): string {
    if (!obfuscated) return '';
    try {
        const decoded = atob(obfuscated);
        let result = '';
        for (let i = 0; i < decoded.length; i++) {
            result += String.fromCharCode(
                decoded.charCodeAt(i) ^ OBFUSCATION_KEY.charCodeAt(i % OBFUSCATION_KEY.length)
            );
        }
        return decodeURIComponent(escape(atob(result)));
    } catch {
        return obfuscated; // Return as-is if decoding fails (backwards compatibility)
    }
}
