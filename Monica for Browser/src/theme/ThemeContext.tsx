import React, { createContext, useContext, useState, useEffect } from 'react';
import { type DefaultTheme, ThemeProvider as StyledThemeProvider } from 'styled-components';
import { themes } from './theme';

type ThemeMode = 'light' | 'dark';

interface ThemeContextType {
    theme: DefaultTheme;
    themeMode: ThemeMode;
    toggleTheme: () => void;
    setThemeMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [themeMode, setThemeModeState] = useState<ThemeMode>('light');
    const [theme, setTheme] = useState<DefaultTheme>(themes.light);

    useEffect(() => {
        const savedMode = localStorage.getItem('themeMode') as ThemeMode | null;
        if (savedMode && themes[savedMode]) {
            setThemeModeState(savedMode);
            setTheme(themes[savedMode]);
        }
    }, []);

    const setThemeMode = (mode: ThemeMode) => {
        setThemeModeState(mode);
        setTheme(themes[mode]);
        localStorage.setItem('themeMode', mode);
    };

    const toggleTheme = () => {
        setThemeMode(themeMode === 'light' ? 'dark' : 'light');
    };

    return (
        <ThemeContext.Provider value={{ theme, themeMode, toggleTheme, setThemeMode }}>
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
