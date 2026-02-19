import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Cloud, CloudUpload, CloudDownload, Trash2, Loader2, Check, AlertCircle, Settings, RefreshCw, Lock } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { webDavClient, backupManager } from '../../utils/webdav';
import type { BackupFile, BackupPreferences, BackupReport, RestoreReport } from '../../utils/webdav';
import { Input } from '../../components/common/Input';

// ========== Styled Components ==========
const Container = styled.div`
  padding: 16px;
  max-width: 480px;
  margin: 0 auto;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${({ theme }) => theme.colors.onSurface};
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant};
  }
`;

const Title = styled.h2`
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  flex: 1;
`;

const Section = styled.div`
  margin-bottom: 24px;
`;

const SectionTitle = styled.h3`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.primary};
  margin: 0 0 12px 0;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const PreferenceRow = styled.label`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: ${({ theme }) => theme.colors.surfaceVariant};
  border-radius: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant}dd;
  }
`;

const Checkbox = styled.input`
  width: 20px;
  height: 20px;
  accent-color: ${({ theme }) => theme.colors.primary};
`;

const BackupCard = styled.div`
  background: ${({ theme }) => theme.colors.surfaceVariant};
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
`;

const BackupHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
`;

const BackupIcon = styled.div<{ $encrypted?: boolean }>`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: ${({ $encrypted }) => $encrypted ? '#9C27B020' : '#2196F320'};
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${({ $encrypted }) => $encrypted ? '#9C27B0' : '#2196F3'};
`;

const BackupInfo = styled.div`
  flex: 1;
`;

const BackupName = styled.div`
  font-weight: 500;
  font-size: 14px;
`;

const BackupMeta = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const BackupActions = styled.div`
  display: flex;
  gap: 8px;
  margin-top: 12px;
`;

const IconButton = styled.button<{ $danger?: boolean }>`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  font-size: 13px;
  background: ${({ $danger }) => $danger ? '#F4433620' : '#ffffff20'};
  color: ${({ theme, $danger }) => $danger ? '#F44336' : theme.colors.onSurface};
  
  &:hover {
    background: ${({ $danger }) => $danger ? '#F4433640' : '#ffffff40'};
  }
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const EmptyState = styled.div`
  text-align: center;
  padding: 32px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const ResultBanner = styled.div<{ $success: boolean }>`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  margin-bottom: 16px;
  background: ${({ $success }) => $success ? '#4CAF5020' : '#F4433620'};
  color: ${({ $success }) => $success ? '#4CAF50' : '#F44336'};
`;

const Modal = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 24px;
  padding: 24px;
  width: 90%;
  max-width: 360px;
`;

// ========== Component ==========
interface BackupPageProps {
    onBack: () => void;
    onOpenSettings: () => void;
    onNavigateToImport?: () => void;
}

export const BackupPage: React.FC<BackupPageProps> = ({ onBack, onOpenSettings, onNavigateToImport }) => {
    const { i18n } = useTranslation();
    const isZh = i18n.language.startsWith('zh');

    // States
    const [isConfigured, setIsConfigured] = useState(false);
    const [backups, setBackups] = useState<BackupFile[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [isCreatingBackup, setIsCreatingBackup] = useState(false);
    const [backupReport, setBackupReport] = useState<BackupReport | null>(null);
    const [restoreReport, setRestoreReport] = useState<RestoreReport | null>(null);

    // Preferences
    const [preferences, setPreferences] = useState<BackupPreferences>({
        includePasswords: true,
        includeNotes: true,
        includeAuthenticators: true,
        includeDocuments: true,
    });

    // Decrypt modal
    const [showDecryptModal, setShowDecryptModal] = useState(false);
    const [decryptPassword, setDecryptPassword] = useState('');
    const [pendingRestore, setPendingRestore] = useState<BackupFile | null>(null);
    const [isRestoring, setIsRestoring] = useState(false);

    // Load config and backups
    useEffect(() => {
        const init = async () => {
            const loaded = await webDavClient.loadConfig();
            setIsConfigured(loaded);
            if (loaded) {
                loadBackups();
            }
        };
        init();
    }, []);

    const loadBackups = async () => {
        setIsLoading(true);
        try {
            const list = await webDavClient.listBackups();
            setBackups(list);
        } catch (e) {
            console.error('Failed to load backups:', e);
        } finally {
            setIsLoading(false);
        }
    };

    // Create backup
    const handleCreateBackup = async () => {
        setIsCreatingBackup(true);
        setBackupReport(null);

        try {
            const encPassword = webDavClient.isEncryptionEnabled()
                ? webDavClient.getEncryptionPassword()
                : undefined;

            const { data, report } = await backupManager.createBackup(preferences, encPassword);
            await webDavClient.uploadBackup(report.filename, data);
            await webDavClient.updateLastBackupTime();

            setBackupReport(report);
            loadBackups();
        } catch (e) {
            const err = e as Error;
            setBackupReport({
                success: false,
                passwordCount: 0,
                noteCount: 0,
                totpCount: 0,
                documentCount: 0,
                totalSize: 0,
                filename: err.message,
            });
        } finally {
            setIsCreatingBackup(false);
        }
    };

    // Restore backup
    const handleRestore = async (backup: BackupFile) => {
        if (backup.isEncrypted) {
            setPendingRestore(backup);
            setDecryptPassword('');
            setShowDecryptModal(true);
            return;
        }

        await doRestore(backup);
    };

    const doRestore = async (backup: BackupFile, password?: string) => {
        setIsRestoring(true);
        setRestoreReport(null);
        setShowDecryptModal(false);

        try {
            const data = await webDavClient.downloadBackup(backup.filename);
            const report = await backupManager.restoreBackup(data, password);
            setRestoreReport(report);

            // Reload the page immediately after restore to show new data
            if (report.success) {
                window.location.reload();
            }
        } catch (e) {
            const err = e as Error;
            setRestoreReport({
                success: false,
                passwordsRestored: 0,
                notesRestored: 0,
                totpsRestored: 0,
                documentsRestored: 0,
                errors: [err.message],
            });
        } finally {
            setIsRestoring(false);
            setPendingRestore(null);
        }
    };

    // Delete backup
    const handleDelete = async (backup: BackupFile) => {
        if (!confirm(isZh ? `确定删除 ${backup.filename}？` : `Delete ${backup.filename}?`)) return;

        try {
            await webDavClient.deleteBackup(backup.filename);
            loadBackups();
        } catch (e) {
            console.error('Failed to delete:', e);
        }
    };

    // Format date
    const formatDate = (date: Date) => {
        const now = new Date();
        const diff = now.getTime() - date.getTime();
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(diff / 3600000);
        const days = Math.floor(diff / 86400000);

        if (minutes < 1) return isZh ? '刚刚' : 'Just now';
        if (minutes < 60) return isZh ? `${minutes}分钟前` : `${minutes}m ago`;
        if (hours < 24) return isZh ? `${hours}小时前` : `${hours}h ago`;
        if (days === 1) return isZh ? '昨天' : 'Yesterday';
        return date.toLocaleDateString();
    };

    // Not configured - show prompt
    if (!isConfigured) {
        return (
            <Container>
                <Header>
                    <BackButton onClick={onBack}>
                        <ArrowLeft size={20} />
                    </BackButton>
                    <Title>{isZh ? '云备份' : 'Cloud Backup'}</Title>
                </Header>

                <EmptyState>
                    <Cloud size={48} style={{ marginBottom: 16, opacity: 0.5 }} />
                    <div style={{ marginBottom: 16 }}>
                        {isZh ? '请先配置 WebDAV 服务器' : 'Please configure WebDAV server first'}
                    </div>
                    <Button onClick={onOpenSettings}>
                        <Settings size={16} />
                        {isZh ? '配置 WebDAV' : 'Configure WebDAV'}
                    </Button>
                </EmptyState>
            </Container>
        );
    }

    return (
        <Container>
            <Header>
                <BackButton onClick={onBack}>
                    <ArrowLeft size={20} />
                </BackButton>
                <Title>{isZh ? '云备份' : 'Cloud Backup'}</Title>
                <IconButton onClick={onOpenSettings}>
                    <Settings size={18} />
                </IconButton>
            </Header>

            {/* Backup Report */}
            {backupReport && (
                <ResultBanner $success={backupReport.success}>
                    {backupReport.success ? <Check size={20} /> : <AlertCircle size={20} />}
                    <div>
                        {backupReport.success ? (
                            isZh
                                ? `备份成功！密码: ${backupReport.passwordCount}, 笔记: ${backupReport.noteCount}, 验证器: ${backupReport.totpCount}, 证件: ${backupReport.documentCount}`
                                : `Backup success! Passwords: ${backupReport.passwordCount}, Notes: ${backupReport.noteCount}, TOTP: ${backupReport.totpCount}, Docs: ${backupReport.documentCount}`
                        ) : backupReport.filename}
                    </div>
                </ResultBanner>
            )}

            {/* Restore Report */}
            {restoreReport && (
                <ResultBanner $success={restoreReport.success}>
                    {restoreReport.success ? <Check size={20} /> : <AlertCircle size={20} />}
                    <div>
                        {restoreReport.success ? (
                            isZh
                                ? `恢复成功！密码: ${restoreReport.passwordsRestored}, 笔记: ${restoreReport.notesRestored}, 验证器: ${restoreReport.totpsRestored}, 证件: ${restoreReport.documentsRestored}`
                                : `Restore success! Passwords: ${restoreReport.passwordsRestored}, Notes: ${restoreReport.notesRestored}, TOTP: ${restoreReport.totpsRestored}, Docs: ${restoreReport.documentsRestored}`
                        ) : restoreReport.errors.join(', ')}
                    </div>
                </ResultBanner>
            )}

            {/* Backup Preferences */}
            <Section>
                <SectionTitle>
                    <CloudUpload size={16} />
                    {isZh ? '备份内容' : 'Backup Content'}
                </SectionTitle>

                <PreferenceRow>
                    <Checkbox
                        type="checkbox"
                        checked={preferences.includePasswords}
                        onChange={(e) => setPreferences({ ...preferences, includePasswords: e.target.checked })}
                    />
                    <span>{isZh ? '密码' : 'Passwords'}</span>
                </PreferenceRow>

                <PreferenceRow>
                    <Checkbox
                        type="checkbox"
                        checked={preferences.includeNotes}
                        onChange={(e) => setPreferences({ ...preferences, includeNotes: e.target.checked })}
                    />
                    <span>{isZh ? '安全笔记' : 'Secure Notes'}</span>
                </PreferenceRow>

                <PreferenceRow>
                    <Checkbox
                        type="checkbox"
                        checked={preferences.includeAuthenticators}
                        onChange={(e) => setPreferences({ ...preferences, includeAuthenticators: e.target.checked })}
                    />
                    <span>{isZh ? '验证器' : 'Authenticators'}</span>
                </PreferenceRow>

                <PreferenceRow>
                    <Checkbox
                        type="checkbox"
                        checked={preferences.includeDocuments}
                        onChange={(e) => setPreferences({ ...preferences, includeDocuments: e.target.checked })}
                    />
                    <span>{isZh ? '证件' : 'Documents'}</span>
                </PreferenceRow>

                <Button
                    onClick={handleCreateBackup}
                    disabled={isCreatingBackup}
                    style={{ width: '100%', marginTop: 12 }}
                >
                    {isCreatingBackup ? <Loader2 size={16} className="animate-spin" /> : <CloudUpload size={16} />}
                    {isZh ? '立即备份' : 'Create Backup'}
                </Button>

                {onNavigateToImport && (
                    <Button
                        variant="secondary"
                        onClick={onNavigateToImport}
                        style={{ width: '100%', marginTop: 8 }}
                    >
                        <CloudDownload size={16} />
                        {isZh ? '导入数据' : 'Import Data'}
                    </Button>
                )}
            </Section>

            {/* Backup List */}
            <Section>
                <SectionTitle>
                    <CloudDownload size={16} />
                    {isZh ? '服务器备份' : 'Server Backups'}
                    <IconButton onClick={loadBackups} disabled={isLoading} style={{ marginLeft: 'auto' }}>
                        <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
                    </IconButton>
                </SectionTitle>

                {isLoading && backups.length === 0 && (
                    <EmptyState>
                        <Loader2 size={24} className="animate-spin" />
                    </EmptyState>
                )}

                {!isLoading && backups.length === 0 && (
                    <EmptyState>
                        {isZh ? '暂无备份' : 'No backups yet'}
                    </EmptyState>
                )}

                {backups.map((backup) => (
                    <BackupCard key={backup.filename}>
                        <BackupHeader>
                            <BackupIcon $encrypted={backup.isEncrypted}>
                                {backup.isEncrypted ? <Lock size={20} /> : <Cloud size={20} />}
                            </BackupIcon>
                            <BackupInfo>
                                <BackupName>{backup.filename}</BackupName>
                                <BackupMeta>
                                    {webDavClient.formatFileSize(backup.size)} · {formatDate(backup.lastModified)}
                                    {backup.isEncrypted && ` · ${isZh ? '已加密' : 'Encrypted'}`}
                                </BackupMeta>
                            </BackupInfo>
                        </BackupHeader>
                        <BackupActions>
                            <IconButton onClick={() => handleRestore(backup)} disabled={isRestoring}>
                                {isRestoring && pendingRestore?.filename === backup.filename ? (
                                    <Loader2 size={14} className="animate-spin" />
                                ) : (
                                    <CloudDownload size={14} />
                                )}
                                {isZh ? '恢复' : 'Restore'}
                            </IconButton>
                            <IconButton $danger onClick={() => handleDelete(backup)}>
                                <Trash2 size={14} />
                                {isZh ? '删除' : 'Delete'}
                            </IconButton>
                        </BackupActions>
                    </BackupCard>
                ))}
            </Section>

            {/* Decrypt Modal */}
            {showDecryptModal && (
                <Modal onClick={() => setShowDecryptModal(false)}>
                    <ModalContent onClick={(e) => e.stopPropagation()}>
                        <h3 style={{ margin: '0 0 16px 0' }}>
                            {isZh ? '输入解密密码' : 'Enter Decryption Password'}
                        </h3>
                        <Input
                            type="password"
                            label={isZh ? '密码' : 'Password'}
                            value={decryptPassword}
                            onChange={(e) => setDecryptPassword(e.target.value)}
                            placeholder={isZh ? '输入备份加密密码' : 'Enter backup encryption password'}
                        />
                        <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
                            <Button variant="text" onClick={() => setShowDecryptModal(false)} style={{ flex: 1 }}>
                                {isZh ? '取消' : 'Cancel'}
                            </Button>
                            <Button
                                onClick={() => pendingRestore && doRestore(pendingRestore, decryptPassword)}
                                disabled={!decryptPassword}
                                style={{ flex: 1 }}
                            >
                                {isZh ? '解密并恢复' : 'Decrypt & Restore'}
                            </Button>
                        </div>
                    </ModalContent>
                </Modal>
            )}
        </Container>
    );
};
