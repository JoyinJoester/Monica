import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { encrypt, decrypt, isEncrypted } from '../utils/webdav/EncryptionHelper';

// ========== Types ==========
export type AutoLockDuration = 1 | 5 | 10 | 30 | 1440; // minutes (1440 = 1 day)

interface MasterPasswordContextType {
    isLocked: boolean;
    isFirstTime: boolean;
    unlock: (password: string) => Promise<boolean>;
    lock: () => void;
    setupMasterPassword: (password: string) => Promise<void>;
    changeMasterPassword: (oldPassword: string, newPassword: string) => Promise<boolean>;
    setSecurityQuestion: (question: string, answer: string) => Promise<void>;
    verifySecurityAnswer: (answer: string) => Promise<boolean>;
    resetPasswordWithSecurityQuestion: (answer: string, newPassword: string) => Promise<boolean>;
    encryptData: (data: string) => Promise<string>;
    decryptData: (data: string) => Promise<string>;
    masterPasswordHash: string | null;
    hasSecurityQuestion: boolean;
    securityQuestion: string | null;
    autoLockDuration: AutoLockDuration;
    setAutoLockDuration: (duration: AutoLockDuration) => Promise<void>;
}

// ========== Storage Keys ==========
const MASTER_PASSWORD_HASH_KEY = 'monica_master_hash';
const MASTER_PASSWORD_SALT_KEY = 'monica_master_salt';
const SECURITY_QUESTION_KEY = 'monica_security_question';
const SECURITY_ANSWER_HASH_KEY = 'monica_security_answer_hash';
const AUTO_LOCK_DURATION_KEY = 'monica_auto_lock_duration';
const LAST_UNLOCK_TIME_KEY = 'monica_last_unlock_time';

// ========== Context ==========
const MasterPasswordContext = createContext<MasterPasswordContextType | null>(null);

// ========== Utility Functions ==========
async function hashPassword(password: string, salt: Uint8Array): Promise<string> {
    const encoder = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
        'raw',
        encoder.encode(password),
        'PBKDF2',
        false,
        ['deriveBits']
    );

    const bits = await crypto.subtle.deriveBits(
        {
            name: 'PBKDF2',
            salt: salt.buffer as ArrayBuffer,
            iterations: 100000,
            hash: 'SHA-256',
        },
        keyMaterial,
        256
    );

    return btoa(String.fromCharCode(...new Uint8Array(bits)));
}

function generateSalt(): Uint8Array {
    return crypto.getRandomValues(new Uint8Array(16));
}

// ========== Provider ==========
export function MasterPasswordProvider({ children }: { children: React.ReactNode }) {
    const [isLocked, setIsLocked] = useState(true);
    const [isFirstTime, setIsFirstTime] = useState(false);
    const [derivedKey, setDerivedKey] = useState<string | null>(null);
    const [masterPasswordHash, setMasterPasswordHash] = useState<string | null>(null);
    const [salt, setSalt] = useState<Uint8Array | null>(null);
    const [securityQuestion, setSecurityQuestionState] = useState<string | null>(null);
    const [autoLockDuration, setAutoLockDurationState] = useState<AutoLockDuration>(5);
    const [securityAnswerHash, setSecurityAnswerHash] = useState<string | null>(null);

    // Check if master password is set on mount
    useEffect(() => {
        const checkMasterPassword = async () => {
            try {
                const result = await chrome.storage.local.get([
                    MASTER_PASSWORD_HASH_KEY,
                    MASTER_PASSWORD_SALT_KEY,
                    SECURITY_QUESTION_KEY,
                    SECURITY_ANSWER_HASH_KEY,
                    AUTO_LOCK_DURATION_KEY,
                    LAST_UNLOCK_TIME_KEY,
                ]);

                const hash = result[MASTER_PASSWORD_HASH_KEY] as string | undefined;
                const saltBase64 = result[MASTER_PASSWORD_SALT_KEY] as string | undefined;
                const question = result[SECURITY_QUESTION_KEY] as string | undefined;
                const answerHash = result[SECURITY_ANSWER_HASH_KEY] as string | undefined;
                const savedDuration = result[AUTO_LOCK_DURATION_KEY] as AutoLockDuration | undefined;
                const lastUnlockTime = result[LAST_UNLOCK_TIME_KEY] as number | undefined;

                // Load auto lock duration
                if (savedDuration) {
                    setAutoLockDurationState(savedDuration);
                }

                if (hash && saltBase64 && typeof hash === 'string' && typeof saltBase64 === 'string') {
                    setMasterPasswordHash(hash);
                    const saltArray = new Uint8Array(
                        atob(saltBase64).split('').map(c => c.charCodeAt(0))
                    );
                    setSalt(saltArray);
                    setIsFirstTime(false);

                    if (question && answerHash) {
                        setSecurityQuestionState(question);
                        setSecurityAnswerHash(answerHash);
                    }

                    // Check if still within auto-lock window
                    if (lastUnlockTime) {
                        const elapsed = Date.now() - lastUnlockTime;
                        const duration = savedDuration || 5;
                        if (elapsed < duration * 60 * 1000) {
                            setIsLocked(false);
                        }
                    }
                } else {
                    setIsFirstTime(true);
                    setIsLocked(false); // Allow access for first-time setup
                }
            } catch (error) {
                console.error('[MasterPassword] Error checking master password:', error);
                setIsFirstTime(true);
                setIsLocked(false);
            }
        };

        checkMasterPassword();
    }, []);

    // Setup master password for first time
    const setupMasterPassword = useCallback(async (password: string) => {
        const newSalt = generateSalt();
        const hash = await hashPassword(password, newSalt);

        await chrome.storage.local.set({
            [MASTER_PASSWORD_HASH_KEY]: hash,
            [MASTER_PASSWORD_SALT_KEY]: btoa(String.fromCharCode(...newSalt)),
        });

        setMasterPasswordHash(hash);
        setSalt(newSalt);
        setDerivedKey(password);
        setIsFirstTime(false);
        setIsLocked(false);
    }, []);

    // Change master password
    const changeMasterPassword = useCallback(async (oldPassword: string, newPassword: string): Promise<boolean> => {
        if (!salt || !masterPasswordHash) return false;

        // Verify old password
        const oldHash = await hashPassword(oldPassword, salt);
        if (oldHash !== masterPasswordHash) {
            return false;
        }

        // Generate new salt and hash for new password
        const newSalt = generateSalt();
        const newHash = await hashPassword(newPassword, newSalt);

        await chrome.storage.local.set({
            [MASTER_PASSWORD_HASH_KEY]: newHash,
            [MASTER_PASSWORD_SALT_KEY]: btoa(String.fromCharCode(...newSalt)),
        });

        setMasterPasswordHash(newHash);
        setSalt(newSalt);
        setDerivedKey(newPassword);
        return true;
    }, [salt, masterPasswordHash]);

    // Set security question
    const setSecurityQuestion = useCallback(async (question: string, answer: string): Promise<void> => {
        if (!salt) return;

        const answerHash = await hashPassword(answer.toLowerCase().trim(), salt);

        await chrome.storage.local.set({
            [SECURITY_QUESTION_KEY]: question,
            [SECURITY_ANSWER_HASH_KEY]: answerHash,
        });

        setSecurityQuestionState(question);
        setSecurityAnswerHash(answerHash);
    }, [salt]);

    // Verify security answer
    const verifySecurityAnswer = useCallback(async (answer: string): Promise<boolean> => {
        if (!salt || !securityAnswerHash) return false;

        const answerHash = await hashPassword(answer.toLowerCase().trim(), salt);
        return answerHash === securityAnswerHash;
    }, [salt, securityAnswerHash]);

    // Reset password with security question
    const resetPasswordWithSecurityQuestion = useCallback(async (answer: string, newPassword: string): Promise<boolean> => {
        if (!salt || !securityAnswerHash) return false;

        // Verify answer
        const answerHash = await hashPassword(answer.toLowerCase().trim(), salt);
        if (answerHash !== securityAnswerHash) {
            return false;
        }

        // Set new password
        const newSalt = generateSalt();
        const newHash = await hashPassword(newPassword, newSalt);

        await chrome.storage.local.set({
            [MASTER_PASSWORD_HASH_KEY]: newHash,
            [MASTER_PASSWORD_SALT_KEY]: btoa(String.fromCharCode(...newSalt)),
        });

        setMasterPasswordHash(newHash);
        setSalt(newSalt);
        setDerivedKey(newPassword);
        setIsLocked(false);
        return true;
    }, [salt, securityAnswerHash]);

    // Unlock with password
    const unlock = useCallback(async (password: string): Promise<boolean> => {
        if (!salt || !masterPasswordHash) return false;

        const hash = await hashPassword(password, salt);

        if (hash === masterPasswordHash) {
            setDerivedKey(password);
            setIsLocked(false);
            // Save unlock time for auto-lock feature
            await chrome.storage.local.set({ [LAST_UNLOCK_TIME_KEY]: Date.now() });
            return true;
        }
        return false;
    }, [salt, masterPasswordHash]);

    // Lock
    const lock = useCallback(() => {
        setDerivedKey(null);
        setIsLocked(true);
    }, []);

    // Encrypt data with master password
    const encryptData = useCallback(async (data: string): Promise<string> => {
        if (!derivedKey) throw new Error('Not unlocked');

        const encoder = new TextEncoder();
        const encrypted = await encrypt(encoder.encode(data), derivedKey);
        return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
    }, [derivedKey]);

    // Decrypt data with master password
    const decryptData = useCallback(async (data: string): Promise<string> => {
        if (!derivedKey) throw new Error('Not unlocked');

        try {
            const encryptedBytes = new Uint8Array(
                atob(data).split('').map(c => c.charCodeAt(0))
            );

            // Check if data is actually encrypted
            if (!isEncrypted(encryptedBytes)) {
                // Return as-is if not encrypted (for backwards compatibility)
                return data;
            }

            const decrypted = await decrypt(encryptedBytes, derivedKey);
            const decoder = new TextDecoder();
            return decoder.decode(decrypted);
        } catch {
            // Return as-is if decryption fails (backwards compatibility)
            return data;
        }
    }, [derivedKey]);

    return (
        <MasterPasswordContext.Provider
            value={{
                isLocked,
                isFirstTime,
                unlock,
                lock,
                setupMasterPassword,
                changeMasterPassword,
                setSecurityQuestion,
                verifySecurityAnswer,
                resetPasswordWithSecurityQuestion,
                encryptData,
                decryptData,
                masterPasswordHash,
                hasSecurityQuestion: !!securityQuestion,
                securityQuestion,
                autoLockDuration,
                setAutoLockDuration: async (duration: AutoLockDuration) => {
                    setAutoLockDurationState(duration);
                    await chrome.storage.local.set({ [AUTO_LOCK_DURATION_KEY]: duration });
                },
            }}
        >
            {children}
        </MasterPasswordContext.Provider>
    );
}

// ========== Hook ==========
export function useMasterPassword() {
    const context = useContext(MasterPasswordContext);
    if (!context) {
        throw new Error('useMasterPassword must be used within MasterPasswordProvider');
    }
    return context;
}
