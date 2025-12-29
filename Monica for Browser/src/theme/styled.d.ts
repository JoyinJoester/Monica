import 'styled-components';

declare module 'styled-components' {
    export interface DefaultTheme {
        colors: {
            primary: string;
            onPrimary: string;
            primaryContainer: string;
            onPrimaryContainer: string;
            secondary: string;
            onSecondary: string;
            secondaryContainer: string;
            onSecondaryContainer: string;
            tertiary: string;
            onTertiary: string;
            tertiaryContainer: string;
            onTertiaryContainer: string;
            background: string;
            onBackground: string;
            surface: string;
            onSurface: string;
            surfaceVariant: string;
            onSurfaceVariant: string;
            outline: string;
            outlineVariant: string;
            error: string;
            onError: string;
            errorContainer: string;
            onErrorContainer: string;
        };
    }
}
