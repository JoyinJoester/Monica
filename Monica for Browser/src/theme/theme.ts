import type { DefaultTheme } from 'styled-components';

// 🌞 Light Theme (Day Mode)
export const LightTheme: DefaultTheme = {
    colors: {
        primary: '#1565C0',
        onPrimary: '#FFFFFF',
        primaryContainer: '#E3F2FD',
        onPrimaryContainer: '#0D47A1',
        secondary: '#4FC3F7',
        onSecondary: '#000000',
        secondaryContainer: '#E1F5FE',
        onSecondaryContainer: '#0288D1',
        tertiary: '#80DEEA',
        onTertiary: '#000000',
        tertiaryContainer: '#E0F7FA',
        onTertiaryContainer: '#00ACC1',
        background: '#FAFAFA',
        onBackground: '#1A1A1A',
        surface: '#FFFFFF',
        onSurface: '#1A1A1A',
        surfaceVariant: '#F5F5F5',
        onSurfaceVariant: '#616161',
        outline: '#BDBDBD',
        outlineVariant: '#E0E0E0',
        error: '#D32F2F',
        onError: '#FFFFFF',
        errorContainer: '#FFCDD2',
        onErrorContainer: '#B71C1C',
    },
};

// 🌙 Dark Theme (Night Mode) - Matches autofill popup colors
export const DarkTheme: DefaultTheme = {
    colors: {
        primary: '#667eea',           // Purple-blue gradient start
        onPrimary: '#FFFFFF',
        primaryContainer: '#764ba2',   // Purple gradient end
        onPrimaryContainer: '#E0E0E0',
        secondary: '#764ba2',          // Purple accent
        onSecondary: '#FFFFFF',
        secondaryContainer: '#3a3a5e', // Muted purple
        onSecondaryContainer: '#E0E0E0',
        tertiary: '#80DEEA',
        onTertiary: '#006064',
        tertiaryContainer: '#252538',
        onTertiaryContainer: '#E0F7FA',
        background: '#1a1a2e',         // Deep blue-purple background
        onBackground: '#E0E0E0',
        surface: '#252538',            // Slightly lighter surface
        onSurface: '#E0E0E0',
        surfaceVariant: '#2a2a3e',     // Card/section background
        onSurfaceVariant: '#A0A0A0',
        outline: '#5c5c7e',            // Purple-tinted outline
        outlineVariant: '#3a3a5e',
        error: '#EF5350',
        onError: '#FFFFFF',
        errorContainer: '#B71C1C',
        onErrorContainer: '#FFCDD2',
    },
};

export const themes = {
    light: LightTheme,
    dark: DarkTheme,
};
