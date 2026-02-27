import { createGlobalStyle } from 'styled-components';

export const GlobalStyle = createGlobalStyle`
  html, body, #root {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
  }

  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
    background-color: ${({ theme }) => theme.colors.background};
    color: ${({ theme }) => theme.colors.onBackground};
    overflow: hidden;    
    transition: background-color 0.3s ease, color 0.3s ease;
  }

  body[data-mode='popup'] {
    width: 360px;
    min-width: 360px;
    height: 600px;
    min-height: 600px;
  }

  body[data-mode='manager'] {
    width: 100%;
    min-width: 320px;
    height: 100%;
    min-height: 100vh;
  }

  @media (max-width: 420px) {
    body[data-mode='popup'] {
      width: 100%;
      min-width: 320px;
      height: 100%;
      min-height: 100vh;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    * {
      animation: none !important;
      transition: none !important;
    }
  }

  code {
    font-family: source-code-pro, Menlo, Monaco, Consolas, 'Courier New', monospace;
  }

  * {
    box-sizing: border-box;
    /* Custom Scrollbar */
    &::-webkit-scrollbar {
      width: 6px;
    }
    &::-webkit-scrollbar-thumb {
      background-color: ${({ theme }) => theme.colors.outlineVariant};
      border-radius: 3px;
    }
    &::-webkit-scrollbar-track {
      background-color: transparent;
    }
  }
`;
