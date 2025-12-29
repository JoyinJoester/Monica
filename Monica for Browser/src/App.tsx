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

function App() {
  return (
    <ThemeProvider>
      <GlobalStyle />
      <Router>
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
      </Router>
    </ThemeProvider>
  );
}

export default App;
