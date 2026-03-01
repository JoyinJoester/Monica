import { useState, useEffect, Component, type ErrorInfo, type ReactNode } from 'react';
import { GlobalStyle } from './theme/GlobalStyle';
import { ThemeProvider } from './theme/ThemeContext';
import { HashRouter as Router, Routes, Route, useNavigate } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { PasswordList } from './features/passwords/PasswordList';
import { NoteList } from './features/notes/NoteList';
import { DocumentList } from './features/documents/DocumentList';
import { BankCardList } from './features/cards';
import { AuthenticatorList } from './features/authenticator/AuthenticatorList';
import { Settings } from './features/settings/Settings';
import { WebDavSettings, BackupPage } from './features/backup';
import { ImportPage } from './features/import';
import { PasskeyPage, SendPage, GeneratorPage, TimelinePage, RecycleBinPage, BitwardenPage } from './features/extra';
import { MasterPasswordProvider, useMasterPassword } from './contexts/MasterPasswordContext';
import { UnlockScreen } from './components/auth/UnlockScreen';
import { webDavClient, backupManager } from './utils/webdav';
import { bitwardenSyncBridge } from './utils/bitwarden/BitwardenSyncBridge';

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
  message: string;
}

class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = {
    hasError: false,
    message: '',
  };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return {
      hasError: true,
      message: error?.message || 'Unknown error',
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('[AppErrorBoundary] UI render crash:', error, errorInfo);
  }

  private handleGoHome = () => {
    window.location.hash = '#/';
    this.setState({ hasError: false, message: '' });
  };

  private handleReload = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#0d1730',
          color: '#eaf1ff',
          padding: 20,
        }}
      >
        <div
          style={{
            width: 'min(560px, 100%)',
            border: '1px solid rgba(118, 148, 206, 0.35)',
            borderRadius: 14,
            background: 'rgba(11, 20, 38, 0.92)',
            padding: 18,
          }}
        >
          <h3 style={{ margin: '0 0 10px 0', fontSize: 18 }}>页面出现错误</h3>
          <div style={{ fontSize: 13, color: '#9fb3de', lineHeight: 1.6 }}>
            检测到运行时异常，已阻止白屏。你可以先返回首页继续使用，再回到当前页排查数据问题。
          </div>
          <div
            style={{
              marginTop: 12,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              fontSize: 12,
              color: '#ffb5c0',
              background: 'rgba(95, 35, 50, 0.35)',
              borderRadius: 10,
              padding: '10px 12px',
              wordBreak: 'break-word',
            }}
          >
            {this.state.message}
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 14 }}>
            <button
              type="button"
              onClick={this.handleGoHome}
              style={{
                border: '1px solid rgba(118, 148, 206, 0.35)',
                background: '#182847',
                color: '#eaf1ff',
                borderRadius: 10,
                padding: '10px 14px',
                cursor: 'pointer',
              }}
            >
              返回首页
            </button>
            <button
              type="button"
              onClick={this.handleReload}
              style={{
                border: '1px solid rgba(94, 137, 228, 0.6)',
                background: 'linear-gradient(135deg, #2e69df, #315fd9)',
                color: '#f3f7ff',
                borderRadius: 10,
                padding: '10px 14px',
                cursor: 'pointer',
              }}
            >
              刷新页面
            </button>
          </div>
        </div>
      </div>
    );
  }
}

function BackupPageWrapper() {
  const navigate = useNavigate();
  return (
    <BackupPage
      onBack={() => navigate('/settings')}
      onOpenSettings={() => navigate('/backup/settings')}
      onNavigateToImport={() => navigate('/import')}
    />
  );
}

function WebDavSettingsWrapper() {
  const navigate = useNavigate();
  return (
    <WebDavSettings
      onBack={() => navigate('/backup')}
      onConfigured={() => navigate('/backup')}
    />
  );
}

function ImportPageWrapper() {
  const navigate = useNavigate();
  return (
    <ImportPage
      onBack={() => navigate('/backup')}
    />
  );
}


// Main app content (only shown when unlocked)
function AppContent() {
  const { isLocked, isFirstTime } = useMasterPassword();
  const [currentUrl, setCurrentUrl] = useState('');
  const isManagerMode = new URLSearchParams(window.location.search).get('manager') === '1';
  const [autoBackupChecked, setAutoBackupChecked] = useState(false);

  useEffect(() => {
    document.body.dataset.mode = isManagerMode ? 'manager' : 'popup';
  }, [isManagerMode]);

  useEffect(() => {
    // Get current URL for unlock screen autofill
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.url) {
        setCurrentUrl(tabs[0].url);
      }
    });
  }, []);

  useEffect(() => {
    const tryAutoBackup = async () => {
      if (isLocked || isFirstTime || autoBackupChecked) return;
      setAutoBackupChecked(true);

      try {
        const configured = await webDavClient.loadConfig();
        if (!configured) return;

        const shouldBackup = await webDavClient.shouldAutoBackup();
        if (!shouldBackup) return;

        const encPassword = webDavClient.isEncryptionEnabled()
          ? webDavClient.getEncryptionPassword()
          : undefined;

        const { data, report } = await backupManager.createBackup(undefined, encPassword);
        await webDavClient.uploadBackup(report.filename, data);
        await webDavClient.updateLastBackupTime();

        // Keep same retention behavior as Android temporary backup cleanup.
        await webDavClient.cleanupBackups(60);
      } catch (e) {
        console.warn('[AutoBackup] failed:', e);
      }
    };

    void tryAutoBackup();
  }, [autoBackupChecked, isFirstTime, isLocked]);

  useEffect(() => {
    if (isLocked || isFirstTime) return;
    bitwardenSyncBridge.requestAppResumeSync();
  }, [isFirstTime, isLocked]);

  // Show unlock screen if locked (except during first-time setup which shows inline)
  if (isLocked && !isFirstTime) {
    return <UnlockScreen currentUrl={currentUrl} />;
  }

  // Show setup screen for first-time users
  if (isFirstTime) {
    return <UnlockScreen currentUrl="" />;
  }

  // When unlocked, always show the main Layout with routes
  return (
    <Layout isManagerMode={isManagerMode}>
      <Routes>
        <Route path="/" element={<PasswordList />} />
        <Route path="/notes" element={<NoteList />} />
        <Route path="/documents" element={<DocumentList />} />
        <Route path="/cards" element={<BankCardList />} />
        <Route path="/authenticator" element={<AuthenticatorList />} />
        <Route path="/passkeys" element={<PasskeyPage />} />
        <Route path="/send" element={<SendPage />} />
        <Route path="/bitwarden" element={<BitwardenPage />} />
        <Route path="/generator" element={<GeneratorPage />} />
        <Route path="/timeline" element={<TimelinePage />} />
        <Route path="/recycle-bin" element={<RecycleBinPage />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/backup" element={<BackupPageWrapper />} />
        <Route path="/backup/settings" element={<WebDavSettingsWrapper />} />
        <Route path="/import" element={<ImportPageWrapper />} />
      </Routes>
    </Layout>
  );
}

function App() {
  return (
    <ThemeProvider>
      <GlobalStyle />
      <MasterPasswordProvider>
        <AppErrorBoundary>
          <Router>
            <AppContent />
          </Router>
        </AppErrorBoundary>
      </MasterPasswordProvider>
    </ThemeProvider>
  );
}

export default App;


