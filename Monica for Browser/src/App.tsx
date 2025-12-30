import { useState, useEffect } from 'react';
import { GlobalStyle } from './theme/GlobalStyle';
import { ThemeProvider } from './theme/ThemeContext';
import { HashRouter as Router, Routes, Route, useNavigate } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { PasswordList } from './features/passwords/PasswordList';
import { NoteList } from './features/notes/NoteList';
import { DocumentList } from './features/documents/DocumentList';
import { AuthenticatorList } from './features/authenticator/AuthenticatorList';
import { Settings } from './features/settings/Settings';
import { WebDavSettings, BackupPage } from './features/backup';
import { MasterPasswordProvider, useMasterPassword } from './contexts/MasterPasswordContext';
import { UnlockScreen } from './components/auth/UnlockScreen';

// Wrapper component for BackupPage to provide navigation
function BackupPageWrapper() {
  const navigate = useNavigate();
  return (
    <BackupPage
      onBack={() => navigate('/settings')}
      onOpenSettings={() => navigate('/backup/settings')}
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

// Main app content (only shown when unlocked)
function AppContent() {
  const { isLocked, isFirstTime } = useMasterPassword();
  const [currentUrl, setCurrentUrl] = useState('');

  useEffect(() => {
    // Get current URL for unlock screen autofill
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.url) {
        setCurrentUrl(tabs[0].url);
      }
    });
  }, []);

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
    <Layout>
      <Routes>
        <Route path="/" element={<PasswordList />} />
        <Route path="/notes" element={<NoteList />} />
        <Route path="/documents" element={<DocumentList />} />
        <Route path="/authenticator" element={<AuthenticatorList />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/backup" element={<BackupPageWrapper />} />
        <Route path="/backup/settings" element={<WebDavSettingsWrapper />} />
      </Routes>
    </Layout>
  );
}

function App() {
  return (
    <ThemeProvider>
      <GlobalStyle />
      <MasterPasswordProvider>
        <Router>
          <AppContent />
        </Router>
      </MasterPasswordProvider>
    </ThemeProvider>
  );
}

export default App;


