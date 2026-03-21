import type { DefaultTheme } from 'styled-components';

// Anthropic Style CN — Light Theme
// 暖米色底 + 低饱和橙色强调
export const LightTheme: DefaultTheme = {
    colors: {
        primary: '#d66a2e',
        onPrimary: '#ffffff',
        primaryContainer: '#f3d7be',
        onPrimaryContainer: '#7a3810',
        secondary: '#b17a18',
        onSecondary: '#ffffff',
        secondaryContainer: '#f5e4b8',
        onSecondaryContainer: '#5a3e08',
        tertiary: '#4b7a5d',
        onTertiary: '#ffffff',
        tertiaryContainer: '#d4eddf',
        onTertiaryContainer: '#1d4a2e',
        background: '#f4efe4',
        onBackground: '#2f2922',
        surface: '#fffaf0',
        onSurface: '#2f2922',
        surfaceVariant: '#efe7d7',
        onSurfaceVariant: '#6f6253',
        outline: '#d8ccb6',
        outlineVariant: '#e8dfcf',
        error: '#a24337',
        onError: '#ffffff',
        errorContainer: '#f5d5cf',
        onErrorContainer: '#5c1e15',
    },
};

// Anthropic Style CN — Dark Theme
// 暖深棕底，非纯黑，保持品牌温度
export const DarkTheme: DefaultTheme = {
    colors: {
        primary: '#e68445',
        onPrimary: '#1d1a16',
        primaryContainer: '#4e3421',
        onPrimaryContainer: '#f3d7be',
        secondary: '#c9963a',
        onSecondary: '#1d1a16',
        secondaryContainer: '#3d2e0f',
        onSecondaryContainer: '#f5e4b8',
        tertiary: '#7ab892',
        onTertiary: '#1d1a16',
        tertiaryContainer: '#1e3d2a',
        onTertiaryContainer: '#d4eddf',
        background: '#171411',
        onBackground: '#f3eadf',
        surface: '#231f19',
        onSurface: '#f3eadf',
        surfaceVariant: '#2a241d',
        onSurfaceVariant: '#c7b8a5',
        outline: '#4b3f30',
        outlineVariant: '#3a3025',
        error: '#d9725f',
        onError: '#1d1a16',
        errorContainer: '#4a1e17',
        onErrorContainer: '#f5d5cf',
    },
};

export const themes = {
    light: LightTheme,
    dark: DarkTheme,
};
