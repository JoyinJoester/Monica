import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import en from './locales/en.json';
import zh from './locales/zh.json';

i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources: {
            en: { translation: en },
            zh: { translation: zh },
        },
        fallbackLng: 'en',
        interpolation: {
            escapeValue: false,
        },
        detection: {
            order: ['localStorage', 'navigator'],
            caches: ['localStorage'],
        }
    });

// Sync language to chrome.storage for content script access
const syncLangToStorage = () => {
    const lang = i18n.language?.startsWith('zh') ? 'zh' : 'en';
    chrome.storage.local.set({ i18nextLng: lang });
};

// Sync on init
syncLangToStorage();

// Sync on language change
i18n.on('languageChanged', syncLangToStorage);

export default i18n;

