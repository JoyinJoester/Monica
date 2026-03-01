import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Cloud, CloudUpload, CloudDownload, Trash2, Loader2, Check, AlertCircle, Settings, RefreshCw, Lock, Pin, PinOff, Sparkles } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { webDavClient, backupManager } from '../../utils/webdav';
import type { BackupFile, BackupPreferences, BackupReport, RestoreReport, RestoreOptions } from '../../utils/webdav';
import { Input } from '../../components/common/Input';
import { exportMonicaCsv, exportKeePassCsv } from '../../utils/ExportManager';

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
  transition: all 0.15s ease;
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant};
  }
  &:active {
    transform: scale(0.9);
    opacity: 0.7;
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
  transition: all 0.15s ease;
  
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant}dd;
  }
  &:active {
    transform: scale(0.98);
    opacity: 0.85;
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

  &:active {
    transform: scale(0.92);
    opacity: 0.8;
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

const RestoreModeList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
`;

const RestoreModeOption = styled.label<{ $active: boolean }>`
  display: block;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid ${({ theme, $active }) => ($active ? theme.colors.primary : `${theme.colors.outline || '#999'}50`)};
  background: ${({ theme, $active }) => ($active ? `${theme.colors.primary}15` : theme.colors.surfaceVariant)};
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
  }
`;

const RestoreModeTitle = styled.div`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.onSurface};
`;

const RestoreModeDesc = styled.div`
  margin-top: 4px;
  font-size: 12px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  line-height: 1.5;
`;

const HiddenRadio = styled.input`
  position: absolute;
  opacity: 0;
  pointer-events: none;
`;

// ========== Component ==========
interface BackupPageProps {
    onBack: () => void;
    onOpenSettings: () => void;
    onNavigateToImport?: () => void;
}

type RestoreMode = 'overwrite' | 'dedupe';

export const BackupPage: React.FC<BackupPageProps> = ({ onBack, onOpenSettings, onNavigateToImport }) => {
    const { i18n } = useTranslation();
    const isZh = i18n.language.startsWith('zh');

    // States
    const [isConfigured, setIsConfigured] = useState(false);
    const [backups, setBackups] = useState<BackupFile[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [isCreatingBackup, setIsCreatingBackup] = useState(false);
    const [isSyncingLatest, setIsSyncingLatest] = useState(false);
    const [isCleaningBackups, setIsCleaningBackups] = useState(false);
    const [backupReport, setBackupReport] = useState<BackupReport | null>(null);
    const [restoreReport, setRestoreReport] = useState<RestoreReport | null>(null);
    const [exportResult, setExportResult] = useState<{ success: boolean; message: string } | null>(null);
    const [countdown, setCountdown] = useState(0);
    const [autoBackupEnabled, setAutoBackupEnabled] = useState(false);

    // Preferences
    const [preferences, setPreferences] = useState<BackupPreferences>({
        includePasswords: true,
        includeNotes: true,
        includeAuthenticators: true,
        includeDocuments: true,
        includeCards: true,
    });

    // Decrypt modal
    const [showRestoreConfirmModal, setShowRestoreConfirmModal] = useState(false);
    const [restoreMode, setRestoreMode] = useState<RestoreMode>('dedupe');
    const [pendingRestoreOptions, setPendingRestoreOptions] = useState<RestoreOptions>({
        overwriteLocalData: false,
        dedupeWithLocal: true,
    });
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
                setAutoBackupEnabled(webDavClient.isAutoBackupEnabled());
                loadBackups();
            }
        };
        init();
    }, []);

    // Countdown timer for page reload after restore
    useEffect(() => {
        if (countdown > 0) {
            const timer = setTimeout(() => {
                setCountdown(countdown - 1);
            }, 1000);
            return () => clearTimeout(timer);
        } else if (countdown === 0 && restoreReport?.success) {
            window.location.reload();
        }
    }, [countdown, restoreReport?.success]);

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

    const handleExportCsv = async () => {
        setExportResult(null);
        try {
            const { filename, content, count } = await exportMonicaCsv();
            const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            setExportResult({
                success: true,
                message: isZh ? `已导出 ${count} 条到 ${filename}` : `Exported ${count} items to ${filename}`,
            });
        } catch (e) {
            const err = e as Error;
            setExportResult({
                success: false,
                message: err.message || (isZh ? '导出失败' : 'Export failed'),
            });
        }
    };

    const handleExportKeePassCsv = async () => {
        setExportResult(null);
        try {
            const { filename, content, count } = await exportKeePassCsv();
            const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            setExportResult({
                success: true,
                message: isZh ? `已导出 ${count} 条密码到 ${filename}` : `Exported ${count} passwords to ${filename}`,
            });
        } catch (e) {
            const err = e as Error;
            setExportResult({
                success: false,
                message: err.message || (isZh ? '导出失败' : 'Export failed'),
            });
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
                cardCount: 0,
                totalSize: 0,
                filename: err.message,
            });
        } finally {
            setIsCreatingBackup(false);
        }
    };

    // Restore backup
    const getRestoreOptionsByMode = (mode: RestoreMode): RestoreOptions => (
        mode === 'overwrite'
            ? { overwriteLocalData: true, dedupeWithLocal: false }
            : { overwriteLocalData: false, dedupeWithLocal: true }
    );

    const shouldPromptDecrypt = (errors: string[]): boolean => {
        return errors.some((error) => {
            const lower = error.toLowerCase();
            return (
                error.includes('请提供密码') ||
                error.includes('输入解密密码') ||
                lower.includes('provide password') ||
                (lower.includes('encrypted') && lower.includes('password'))
            );
        });
    };

    const openRestoreConfirm = (backup: BackupFile) => {
        setPendingRestore(backup);
        setRestoreMode('dedupe');
        setPendingRestoreOptions({ overwriteLocalData: false, dedupeWithLocal: true });
        setShowRestoreConfirmModal(true);
    };

    const handleConfirmRestore = async () => {
        if (!pendingRestore) return;

        const selectedOptions = getRestoreOptionsByMode(restoreMode);
        setPendingRestoreOptions(selectedOptions);
        setShowRestoreConfirmModal(false);

        if (pendingRestore.isEncrypted) {
            setDecryptPassword('');
            setShowDecryptModal(true);
            return;
        }

        await doRestore(pendingRestore, undefined, selectedOptions);
    };

    const handleRestore = (backup: BackupFile) => {
        openRestoreConfirm(backup);
    };

    const doRestore = async (
        backup: BackupFile,
        password?: string,
        options: RestoreOptions = { overwriteLocalData: false, dedupeWithLocal: true }
    ) => {
        setIsRestoring(true);
        setRestoreReport(null);
        setShowDecryptModal(false);
        setCountdown(0);
        let keepPendingRestore = false;

        try {
            const data = await webDavClient.downloadBackup(backup.path || backup.filename);
            const report = await backupManager.restoreBackup(data, password, options);

            if (!report.success && !password && shouldPromptDecrypt(report.errors)) {
                keepPendingRestore = true;
                setRestoreReport(null);
                setDecryptPassword('');
                setShowDecryptModal(true);
                return;
            }

            setRestoreReport(report);

            // Start countdown for page reload (5 seconds)
            if (report.success) {
                setCountdown(5);
            }
        } catch (e) {
            const err = e as Error;
            if (!password && shouldPromptDecrypt([err.message])) {
                keepPendingRestore = true;
                setRestoreReport(null);
                setDecryptPassword('');
                setShowDecryptModal(true);
            } else {
                setRestoreReport({
                    success: false,
                    passwordsRestored: 0,
                    notesRestored: 0,
                    totpsRestored: 0,
                    documentsRestored: 0,
                    cardsRestored: 0,
                    errors: [err.message],
                });
            }
        } finally {
            setIsRestoring(false);
            if (!keepPendingRestore) {
                setPendingRestore(null);
                setPendingRestoreOptions({ overwriteLocalData: false, dedupeWithLocal: true });
            }
        }
    };

    const handleSyncLatest = async () => {
        setIsSyncingLatest(true);
        setRestoreReport(null);
        try {
            const latest = await webDavClient.getLatestBackup();
            if (!latest) {
                setRestoreReport({
                    success: false,
                    passwordsRestored: 0,
                    notesRestored: 0,
                    totpsRestored: 0,
                    documentsRestored: 0,
                    cardsRestored: 0,
                    errors: [isZh ? '服务器没有可同步的备份' : 'No backup found on server'],
                });
                return;
            }
            openRestoreConfirm(latest);
        } finally {
            setIsSyncingLatest(false);
        }
    };

    const handleCleanupBackups = async () => {
        setIsCleaningBackups(true);
        setExportResult(null);
        try {
            const deleted = await webDavClient.cleanupBackups(60);
            setExportResult({
                success: true,
                message: isZh ? `已清理 ${deleted} 个过期临时备份` : `Cleaned ${deleted} expired temporary backups`,
            });
            await loadBackups();
        } catch (e) {
            const err = e as Error;
            setExportResult({
                success: false,
                message: err.message || (isZh ? '清理失败' : 'Cleanup failed'),
            });
        } finally {
            setIsCleaningBackups(false);
        }
    };

    const handleTogglePermanent = async (backup: BackupFile) => {
        try {
            if (backup.isPermanent) {
                await webDavClient.unmarkBackupPermanent(backup);
            } else {
                await webDavClient.markBackupAsPermanent(backup);
            }
            await loadBackups();
        } catch (e) {
            const err = e as Error;
            setExportResult({
                success: false,
                message: err.message || (isZh ? '更新永久状态失败' : 'Failed to update permanent state'),
            });
        }
    };

    // Delete backup
    const handleDelete = async (backup: BackupFile) => {
        if (!confirm(isZh ? `确定删除 ${backup.filename}？` : `Delete ${backup.filename}?`)) return;

        try {
            await webDavClient.deleteBackup(backup.path || backup.filename);
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
                                ? `备份成功！密码: ${backupReport.passwordCount}, 笔记: ${backupReport.noteCount}, 验证器: ${backupReport.totpCount}, 证件: ${backupReport.documentCount}, 银行卡: ${backupReport.cardCount || 0}`
                                : `Backup success! Passwords: ${backupReport.passwordCount}, Notes: ${backupReport.noteCount}, TOTP: ${backupReport.totpCount}, Docs: ${backupReport.documentCount}, Cards: ${backupReport.cardCount || 0}`
                        ) : backupReport.filename}
                    </div>
                </ResultBanner>
            )}

            {/* Export Result */}
            {exportResult && (
                <ResultBanner $success={exportResult.success}>
                    {exportResult.success ? <Check size={20} /> : <AlertCircle size={20} />}
                    <div>{exportResult.message}</div>
                </ResultBanner>
            )}

            {/* Restore Report */}
            {restoreReport && (
                <ResultBanner $success={restoreReport.success}>
                    {restoreReport.success ? <Check size={20} /> : <AlertCircle size={20} />}
                    <div>
                        {restoreReport.success ? (
                            <>
                                {isZh
                                    ? `恢复成功！密码: ${restoreReport.passwordsRestored}, 笔记: ${restoreReport.notesRestored}, 验证器: ${restoreReport.totpsRestored}, 证件: ${restoreReport.documentsRestored}, 银行卡: ${restoreReport.cardsRestored || 0}`
                                    : `Restore success! Passwords: ${restoreReport.passwordsRestored}, Notes: ${restoreReport.notesRestored}, TOTP: ${restoreReport.totpsRestored}, Docs: ${restoreReport.documentsRestored}, Cards: ${restoreReport.cardsRestored || 0}`}
                                {countdown > 0 && (
                                    <div style={{ marginTop: '8px', fontSize: '14px', opacity: 0.8 }}>
                                        {isZh ? `页面将在 ${countdown} 秒后自动刷新` : `Page will reload in ${countdown} seconds`}
                                    </div>
                                )}
                            </>
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

                <PreferenceRow>
                    <Checkbox
                        type="checkbox"
                        checked={preferences.includeCards ?? true}
                        onChange={(e) => setPreferences({ ...preferences, includeCards: e.target.checked })}
                    />
                    <span>{isZh ? '银行卡' : 'Bank Cards'}</span>
                </PreferenceRow>

                <PreferenceRow>
                    <Checkbox
                        type="checkbox"
                        checked={autoBackupEnabled}
                        onChange={async (e) => {
                            const enabled = e.target.checked;
                            setAutoBackupEnabled(enabled);
                            await webDavClient.configureAutoBackup(enabled);
                        }}
                    />
                    <span>{isZh ? '自动备份（每天或12小时）' : 'Auto backup (daily or every 12h)'}</span>
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

                <Button
                    variant="secondary"
                    onClick={handleExportCsv}
                    style={{ width: '100%', marginTop: 8 }}
                >
                    <CloudDownload size={16} />
                    {isZh ? '导出 Monica CSV' : 'Export Monica CSV'}
                </Button>

                <Button
                    variant="secondary"
                    onClick={handleExportKeePassCsv}
                    style={{ width: '100%', marginTop: 8 }}
                >
                    <CloudDownload size={16} />
                    {isZh ? '导出 KeePass CSV' : 'Export KeePass CSV'}
                </Button>

                <Button
                    variant="secondary"
                    onClick={handleSyncLatest}
                    disabled={isSyncingLatest || isRestoring}
                    style={{ width: '100%', marginTop: 8 }}
                >
                    {isSyncingLatest ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
                    {isZh ? '同步最新备份' : 'Sync Latest Backup'}
                </Button>

                <Button
                    variant="secondary"
                    onClick={handleCleanupBackups}
                    disabled={isCleaningBackups}
                    style={{ width: '100%', marginTop: 8 }}
                >
                    {isCleaningBackups ? <Loader2 size={16} className="animate-spin" /> : <Trash2 size={16} />}
                    {isZh ? '清理过期备份' : 'Cleanup Expired Backups'}
                </Button>
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
                                    {backup.isPermanent && ` · ${isZh ? '永久' : 'Permanent'}`}
                                    {backup.isExpiring && ` · ${isZh ? '即将过期' : 'Expiring soon'}`}
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
                            <IconButton onClick={() => handleTogglePermanent(backup)}>
                                {backup.isPermanent ? <PinOff size={14} /> : <Pin size={14} />}
                                {backup.isPermanent ? (isZh ? '取消永久' : 'Unpin') : (isZh ? '设为永久' : 'Pin')}
                            </IconButton>
                            <IconButton $danger onClick={() => handleDelete(backup)}>
                                <Trash2 size={14} />
                                {isZh ? '删除' : 'Delete'}
                            </IconButton>
                        </BackupActions>
                    </BackupCard>
                ))}
            </Section>

            {/* Restore Strategy Modal */}
            {showRestoreConfirmModal && pendingRestore && (
                <Modal
                    onClick={() => {
                        setShowRestoreConfirmModal(false);
                        setPendingRestore(null);
                        setPendingRestoreOptions({ overwriteLocalData: false, dedupeWithLocal: true });
                    }}
                >
                    <ModalContent onClick={(e) => e.stopPropagation()}>
                        <h3 style={{ margin: '0 0 8px 0' }}>
                            {isZh ? '恢复选项确认' : 'Confirm Restore Options'}
                        </h3>
                        <div style={{ fontSize: 13, color: 'var(--text-secondary, #7a7a7a)', lineHeight: 1.5 }}>
                            {isZh
                                ? '请选择恢复策略。该操作会影响本地密码库数据。'
                                : 'Select a restore strategy. This operation will affect local vault data.'}
                        </div>
                        <RestoreModeList>
                            <RestoreModeOption $active={restoreMode === 'dedupe'}>
                                <HiddenRadio
                                    type="radio"
                                    name="restore-mode"
                                    checked={restoreMode === 'dedupe'}
                                    onChange={() => setRestoreMode('dedupe')}
                                />
                                <RestoreModeTitle>{isZh ? '仅与 Monica 本地去重' : 'Dedupe with local Monica data'}</RestoreModeTitle>
                                <RestoreModeDesc>
                                    {isZh
                                        ? '保留本地现有条目，只导入不重复的数据。'
                                        : 'Keep local items and only import non-duplicate records.'}
                                </RestoreModeDesc>
                            </RestoreModeOption>
                            <RestoreModeOption $active={restoreMode === 'overwrite'}>
                                <HiddenRadio
                                    type="radio"
                                    name="restore-mode"
                                    checked={restoreMode === 'overwrite'}
                                    onChange={() => setRestoreMode('overwrite')}
                                />
                                <RestoreModeTitle>{isZh ? '覆盖本地数据' : 'Overwrite local data'}</RestoreModeTitle>
                                <RestoreModeDesc>
                                    {isZh
                                        ? '先清空本地再恢复，恢复后本地仅保留备份中的数据。'
                                        : 'Clear local data first, then restore from backup only.'}
                                </RestoreModeDesc>
                            </RestoreModeOption>
                        </RestoreModeList>
                        <div style={{ display: 'flex', gap: 12, marginTop: 18 }}>
                            <Button
                                variant="text"
                                onClick={() => {
                                    setShowRestoreConfirmModal(false);
                                    setPendingRestore(null);
                                    setPendingRestoreOptions({ overwriteLocalData: false, dedupeWithLocal: true });
                                }}
                                style={{ flex: 1 }}
                            >
                                {isZh ? '取消' : 'Cancel'}
                            </Button>
                            <Button onClick={handleConfirmRestore} style={{ flex: 1 }}>
                                {isZh ? '继续' : 'Continue'}
                            </Button>
                        </div>
                    </ModalContent>
                </Modal>
            )}

            {/* Decrypt Modal */}
            {showDecryptModal && (
                <Modal
                    onClick={() => {
                        setShowDecryptModal(false);
                        setPendingRestore(null);
                        setPendingRestoreOptions({ overwriteLocalData: false, dedupeWithLocal: true });
                    }}
                >
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
                            <Button
                                variant="text"
                                onClick={() => {
                                    setShowDecryptModal(false);
                                    setPendingRestore(null);
                                    setPendingRestoreOptions({ overwriteLocalData: false, dedupeWithLocal: true });
                                }}
                                style={{ flex: 1 }}
                            >
                                {isZh ? '取消' : 'Cancel'}
                            </Button>
                            <Button
                                onClick={() => pendingRestore && doRestore(pendingRestore, decryptPassword, pendingRestoreOptions)}
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
