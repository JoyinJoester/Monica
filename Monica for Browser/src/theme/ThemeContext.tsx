import React, { createContext, useContext, useState, useEffect } from 'react';
import { type DefaultTheme, ThemeProvider as StyledThemeProvider } from 'styled-components';
import { themes } from './theme';

type ThemeMode = 'light' | 'dark' | 'auto';

interface ThemeContextType {
    theme: DefaultTheme;
    themeMode: ThemeMode;
    effectiveThemeMode: 'light' | 'dark';
    toggleTheme: () => void;
    setThemeMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

const getSystemTheme = (): 'light' | 'dark' => {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return 'dark';
    }
    return 'light';
};

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [themeMode, setThemeModeState] = useState<ThemeMode>('auto');
    const [effectiveThemeMode, setEffectiveThemeMode] = useState<'light' | 'dark'>(getSystemTheme());
    const [theme, setTheme] = useState<DefaultTheme>(themes[effectiveThemeMode]);

    useEffect(() => {
        const savedMode = localStorage.getItem('themeMode') as ThemeMode | null;
        if (savedMode) {
            setThemeModeState(savedMode);
        }
    }, []);

    useEffect(() => {
        const applyTheme = (mode: ThemeMode) => {
            const effectiveMode = mode === 'auto' ? getSystemTheme() : mode;
            setEffectiveThemeMode(effectiveMode);
            setTheme(themes[effectiveMode]);
        };

        applyTheme(themeMode);

        if (themeMode === 'auto') {
            const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
            const handleChange = () => {
                const newEffectiveMode = getSystemTheme();
                setEffectiveThemeMode(newEffectiveMode);
                setTheme(themes[newEffectiveMode]);
            };

            mediaQuery.addEventListener('change', handleChange);
            return () => mediaQuery.removeEventListener('change', handleChange);
        }
    }, [themeMode]);

    const setThemeMode = (mode: ThemeMode) => {
        setThemeModeState(mode);
        localStorage.setItem('themeMode', mode);
    };

    const toggleTheme = () => {
        const newMode = effectiveThemeMode === 'light' ? 'dark' : 'light';
        setThemeMode(newMode);
    };

    return (
        <ThemeContext.Provider value={{ theme, themeMode, effectiveThemeMode, toggleTheme, setThemeMode }}>
            <StyledThemeProvider theme={theme}>
                {children}
            </StyledThemeProvider>
        </ThemeContext.Provider>
    );
};

export const useTheme = () => {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within a ThemeProvider');
    }
    return context;
};
