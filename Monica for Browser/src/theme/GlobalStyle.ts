import { createGlobalStyle } from 'styled-components';

export const GlobalStyle = createGlobalStyle`
  html, body, #root {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
  }

  body {
    font-family: 'Source Sans 3', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
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

  code, pre, kbd {
    font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, 'Courier New', monospace;
  }

  * {
    box-sizing: border-box;

    &::-webkit-scrollbar {
      width: 5px;
    }
    &::-webkit-scrollbar-thumb {
      background-color: ${({ theme }) => theme.colors.outline};
      border-radius: 999px;
    }
    &::-webkit-scrollbar-track {
      background-color: transparent;
    }
  }

  button, [role="button"], a, select, summary {
    -webkit-tap-highlight-color: rgba(0, 0, 0, 0.06);
  }

  :focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.primary};
    outline-offset: 2px;
    border-radius: 4px;
  }

  ::selection {
    background: ${({ theme }) => theme.colors.primaryContainer};
    color: ${({ theme }) => theme.colors.onPrimaryContainer};
  }
`;
