import { useState } from 'react';
import styled from 'styled-components';
import { useTheme } from '../../theme/ThemeContext';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Card, CardTitle } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Sun, Moon, Globe, Cloud, ChevronRight, Trash2, AlertTriangle, Lock, Key, Shield } from 'lucide-react';
import { clearAllData } from '../../utils/storage';
import { useMasterPassword } from '../../contexts/MasterPasswordContext';
import { validateEncryptionPassword } from '../../utils/webdav/EncryptionHelper';

const Container = styled.div`
  padding: 16px;
`;

const SectionTitle = styled.h2`
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: ${({ theme }) => theme.colors.primary};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const SettingRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid ${({ theme }) => theme.colors.outlineVariant};

  &:last-child {
    border-bottom: none;
  }
`;

const SettingLabel = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: ${({ theme }) => theme.colors.onSurface};

  svg {
    width: 22px;
    height: 22px;
    color: ${({ theme }) => theme.colors.primary};
  }
`;
 
const Select = styled.select`
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background-color: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-size: 14px;
  cursor: pointer;
`;

const ClickableCard = styled(Card)`
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }
  
  &:active {
    transform: translateY(0);
  }
`;

const BackupCardContent = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const BackupIcon = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2196F3, #00BCD4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
`;

const BackupInfo = styled.div`
  flex: 1;
`;

const BackupTitle = styled.div`
  font-weight: 600;
  font-size: 15px;
`;

const BackupSubtitle = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  margin-top: 2px;
`;

const DangerButton = styled(Button)`
  background: linear-gradient(135deg, #F44336, #E91E63);
  &:hover {
    background: linear-gradient(135deg, #D32F2F, #C2185B);
  }
`;

const Modal = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 16px;
  padding: 24px;
  max-width: 360px;
  width: 100%;
  text-align: center;
`;

const ModalIcon = styled.div`
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #F4433620;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  color: #F44336;
`;

const ModalTitle = styled.h3`
  margin: 0 0 8px;
  font-size: 18px;
`;

const ModalMessage = styled.p`
  margin: 0 0 24px;
  font-size: 14px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  line-height: 1.5;
`;

const ModalButtons = styled.div`
  display: flex;
  gap: 12px;
`;

const Input = styled.input`
  width: 100%;
  padding: 12px 14px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  border-radius: 10px;
  background: ${({ theme }) => theme.colors.surfaceVariant};
  color: ${({ theme }) => theme.colors.onSurface};
  font-size: 14px;
  margin-bottom: 12px;
  box-sizing: border-box;
  
  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
  }
`;

const ErrorText = styled.div`
  color: ${({ theme }) => theme.colors.error};
  font-size: 13px;
  margin-bottom: 12px;
`;

const SuccessIcon = styled(ModalIcon)`
  background: #4CAF5020;
  color: #4CAF50;
`;

export const Settings = () => {
  const { themeMode, effectiveThemeMode, setThemeMode } = useTheme();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const isZh = i18n.language.startsWith('zh');
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [isClearing, setIsClearing] = useState(false);

  // Password change state
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [showPasswordSuccess, setShowPasswordSuccess] = useState(false);

  // Security question state
  const [showSecurityQuestion, setShowSecurityQuestion] = useState(false);
  const [securityQ, setSecurityQ] = useState('');
  const [securityA, setSecurityA] = useState('');
  const [securityError, setSecurityError] = useState('');
  const [isSavingQuestion, setIsSavingQuestion] = useState(false);
  const [showQuestionSuccess, setShowQuestionSuccess] = useState(false);

  const { changeMasterPassword, setSecurityQuestion, hasSecurityQuestion, securityQuestion, autoLockDuration, setAutoLockDuration } = useMasterPassword();

  const currentLang = i18n.language?.startsWith('zh') ? 'zh' : 'en';

  const handleThemeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setThemeMode(e.target.value as 'light' | 'dark' | 'auto');
  };

  const handleLanguageChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const lang = e.target.value;
    i18n.changeLanguage(lang);
    // Sync to chrome.storage for content script access
    chrome.storage.local.set({ i18nextLng: lang });
  };

  const handleClearData = async () => {
    setIsClearing(true);
    try {
      await clearAllData();
      setShowClearConfirm(false);
      // Reload the page to reflect changes
      window.location.reload();
    } catch (e) {
      console.error('Failed to clear data:', e);
    } finally {
      setIsClearing(false);
    }
  };

  const handleChangePassword = async () => {
    setPasswordError('');

    const validation = validateEncryptionPassword(newPassword);
    if (!validation.valid) {
      setPasswordError(validation.message);
      return;
    }

    if (newPassword !== confirmNewPassword) {
      setPasswordError(isZh ? '两次输入的密码不一致' : 'Passwords do not match');
      return;
    }

    setIsChangingPassword(true);
    try {
      const success = await changeMasterPassword(oldPassword, newPassword);
      if (success) {
        setShowChangePassword(false);
        setShowPasswordSuccess(true);
        setOldPassword('');
        setNewPassword('');
        setConfirmNewPassword('');
        setTimeout(() => setShowPasswordSuccess(false), 2000);
      } else {
        setPasswordError(isZh ? '原密码错误' : 'Current password is incorrect');
      }
    } catch {
      setPasswordError(isZh ? '修改失败' : 'Failed to change password');
    }
    setIsChangingPassword(false);
  };

  const handleSetSecurityQuestion = async () => {
    setSecurityError('');

    if (!securityQ.trim()) {
      setSecurityError(isZh ? '请输入密保问题' : 'Please enter a security question');
      return;
    }

    if (!securityA.trim()) {
      setSecurityError(isZh ? '请输入答案' : 'Please enter an answer');
      return;
    }

    setIsSavingQuestion(true);
    try {
      await setSecurityQuestion(securityQ.trim(), securityA.trim());
      setShowSecurityQuestion(false);
      setShowQuestionSuccess(true);
      setSecurityQ('');
      setSecurityA('');
      setTimeout(() => setShowQuestionSuccess(false), 2000);
    } catch {
      setSecurityError(isZh ? '保存失败' : 'Failed to save');
    }
    setIsSavingQuestion(false);
  };

  return (
    <Container>
      {/* WebDAV Backup Section */}
      <SectionTitle>{isZh ? '数据同步' : 'Data Sync'}</SectionTitle>
      <ClickableCard onClick={() => navigate('/backup')}>
        <BackupCardContent>
          <BackupIcon>
            <Cloud size={22} />
          </BackupIcon>
          <BackupInfo>
            <BackupTitle>{isZh ? 'WebDAV 云备份' : 'WebDAV Cloud Backup'}</BackupTitle>
            <BackupSubtitle>
              {isZh ? '备份和恢复您的数据到云端' : 'Backup and restore your data to cloud'}
            </BackupSubtitle>
          </BackupInfo>
          <ChevronRight size={20} style={{ opacity: 0.5 }} />
        </BackupCardContent>
      </ClickableCard>

      {/* Security Settings Section */}
      <div style={{ marginTop: 24 }}>
        <SectionTitle>{isZh ? '安全设置' : 'Security'}</SectionTitle>
        <ClickableCard onClick={() => setShowChangePassword(true)}>
          <BackupCardContent>
            <BackupIcon style={{ background: 'linear-gradient(135deg, #9C27B0, #673AB7)' }}>
              <Key size={22} />
            </BackupIcon>
            <BackupInfo>
              <BackupTitle>{isZh ? '修改主密码' : 'Change Master Password'}</BackupTitle>
              <BackupSubtitle>
                {isZh ? '定期更换密码以提高安全性' : 'Change your password regularly'}
              </BackupSubtitle>
            </BackupInfo>
            <ChevronRight size={20} style={{ opacity: 0.5 }} />
          </BackupCardContent>
        </ClickableCard>

        <div style={{ marginTop: 12 }}>
          <ClickableCard onClick={() => {
            setSecurityQ(securityQuestion || '');
            setShowSecurityQuestion(true);
          }}>
            <BackupCardContent>
              <BackupIcon style={{ background: 'linear-gradient(135deg, #FF9800, #F57C00)' }}>
                <Shield size={22} />
              </BackupIcon>
              <BackupInfo>
                <BackupTitle>{isZh ? '密保问题' : 'Security Question'}</BackupTitle>
                <BackupSubtitle>
                  {hasSecurityQuestion
                    ? (isZh ? '已设置，可用于找回密码' : 'Set up, can be used to recover password')
                    : (isZh ? '设置后可找回忘记的密码' : 'Set up to recover forgotten password')}
                </BackupSubtitle>
              </BackupInfo>
              <ChevronRight size={20} style={{ opacity: 0.5 }} />
            </BackupCardContent>
          </ClickableCard>
        </div>

        {/* Auto Lock Duration */}
        <div style={{ marginTop: 12 }}>
          <Card>
            <SettingRow>
              <SettingLabel>
                <Lock />
                {isZh ? '自动锁定' : 'Auto Lock'}
              </SettingLabel>
              <Select
                value={autoLockDuration}
                onChange={(e) => setAutoLockDuration(Number(e.target.value) as typeof autoLockDuration)}
              >
                <option value={0}>{isZh ? '立即 (关闭即锁定)' : 'Immediate'}</option>
                <option value={1}>{isZh ? '1 分钟' : '1 min'}</option>
                <option value={5}>{isZh ? '5 分钟' : '5 min'}</option>
                <option value={10}>{isZh ? '10 分钟' : '10 min'}</option>
                <option value={30}>{isZh ? '30 分钟' : '30 min'}</option>
                <option value={1440}>{isZh ? '1 天' : '1 day'}</option>
                <option value={-1}>{isZh ? '不锁定' : 'Never'}</option>
              </Select>
            </SettingRow>
          </Card>
        </div>
      </div>

      <div style={{ marginTop: 24 }}>
        <SectionTitle>{t('settings.appearance')}</SectionTitle>
        <Card>
          <SettingRow>
            <SettingLabel>
              {effectiveThemeMode === 'dark' ? <Moon /> : <Sun />}
              {t('settings.theme')}
            </SettingLabel>
            <Select value={themeMode} onChange={handleThemeChange}>
              <option value="auto">{t('settings.themes.auto')}</option>
              <option value="light">{t('settings.themes.light')}</option>
              <option value="dark">{t('settings.themes.dark')}</option>
            </Select>
          </SettingRow>
        </Card>
      </div>

      <div style={{ marginTop: 24 }}>
        <SectionTitle>{t('settings.language')}</SectionTitle>
        <Card>
          <SettingRow>
            <SettingLabel>
              <Globe />
              {t('settings.language')}
            </SettingLabel>
            <Select value={currentLang} onChange={handleLanguageChange}>
              <option value="en">English</option>
              <option value="zh">简体中文</option>
            </Select>
          </SettingRow>
        </Card>
      </div>

      {/* Clear Data Section */}
      <div style={{ marginTop: 24 }}>
        <SectionTitle>{isZh ? '数据管理' : 'Data Management'}</SectionTitle>
        <ClickableCard onClick={() => setShowClearConfirm(true)}>
          <BackupCardContent>
            <BackupIcon style={{ background: 'linear-gradient(135deg, #F44336, #E91E63)' }}>
              <Trash2 size={22} />
            </BackupIcon>
            <BackupInfo>
              <BackupTitle>{isZh ? '清空所有数据' : 'Clear All Data'}</BackupTitle>
              <BackupSubtitle>
                {isZh ? '删除密码、笔记、验证器和证件' : 'Delete passwords, notes, authenticators and documents'}
              </BackupSubtitle>
            </BackupInfo>
            <ChevronRight size={20} style={{ opacity: 0.5 }} />
          </BackupCardContent>
        </ClickableCard>
      </div>

      <div style={{ marginTop: 24 }}>
        <SectionTitle>{t('settings.about')}</SectionTitle>
        <Card>
          <CardTitle>Monica Browser Extension</CardTitle>
          <div style={{ fontSize: 13, marginTop: 4, opacity: 0.7 }}>{t('settings.version')}: 1.0.26a</div>
        </Card>
      </div>

      {/* Clear Data Confirmation Modal */}
      {showClearConfirm && (
        <Modal onClick={() => setShowClearConfirm(false)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalIcon>
              <AlertTriangle size={28} />
            </ModalIcon>
            <ModalTitle>{isZh ? '确认清空数据？' : 'Clear All Data?'}</ModalTitle>
            <ModalMessage>
              {isZh
                ? '此操作将永久删除所有存储的密码、笔记、验证器和证件数据。此操作无法撤销！'
                : 'This will permanently delete all stored passwords, notes, authenticators and documents. This action cannot be undone!'}
            </ModalMessage>
            <ModalButtons>
              <Button
                variant="secondary"
                onClick={() => setShowClearConfirm(false)}
                style={{ flex: 1 }}
              >
                {isZh ? '取消' : 'Cancel'}
              </Button>
              <DangerButton
                onClick={handleClearData}
                disabled={isClearing}
                style={{ flex: 1 }}
              >
                {isClearing ? (isZh ? '清空中...' : 'Clearing...') : (isZh ? '确认清空' : 'Confirm')}
              </DangerButton>
            </ModalButtons>
          </ModalContent>
        </Modal>
      )}

      {/* Change Password Modal */}
      {showChangePassword && (
        <Modal onClick={() => setShowChangePassword(false)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalIcon style={{ background: '#9C27B020', color: '#9C27B0' }}>
              <Key size={28} />
            </ModalIcon>
            <ModalTitle>{isZh ? '修改主密码' : 'Change Master Password'}</ModalTitle>
            <div style={{ textAlign: 'left', marginTop: 16 }}>
              <Input
                type="password"
                placeholder={isZh ? '当前密码' : 'Current password'}
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
              />
              <Input
                type="password"
                placeholder={isZh ? '新密码 (至少6位)' : 'New password (min 6 chars)'}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
              <Input
                type="password"
                placeholder={isZh ? '确认新密码' : 'Confirm new password'}
                value={confirmNewPassword}
                onChange={(e) => setConfirmNewPassword(e.target.value)}
              />
              {passwordError && <ErrorText>{passwordError}</ErrorText>}
            </div>
            <ModalButtons>
              <Button
                variant="secondary"
                onClick={() => {
                  setShowChangePassword(false);
                  setOldPassword('');
                  setNewPassword('');
                  setConfirmNewPassword('');
                  setPasswordError('');
                }}
                style={{ flex: 1 }}
              >
                {isZh ? '取消' : 'Cancel'}
              </Button>
              <Button
                onClick={handleChangePassword}
                disabled={isChangingPassword || !oldPassword || !newPassword}
                style={{ flex: 1 }}
              >
                {isChangingPassword ? '...' : (isZh ? '确认' : 'Confirm')}
              </Button>
            </ModalButtons>
          </ModalContent>
        </Modal>
      )}

      {/* Security Question Modal */}
      {showSecurityQuestion && (
        <Modal onClick={() => setShowSecurityQuestion(false)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalIcon style={{ background: '#FF980020', color: '#FF9800' }}>
              <Shield size={28} />
            </ModalIcon>
            <ModalTitle>{isZh ? '设置密保问题' : 'Set Security Question'}</ModalTitle>
            <div style={{ textAlign: 'left', marginTop: 16 }}>
              <Input
                type="text"
                placeholder={isZh ? '密保问题 (如：我的出生城市)' : 'Security question (e.g., My birth city)'}
                value={securityQ}
                onChange={(e) => setSecurityQ(e.target.value)}
              />
              <Input
                type="text"
                placeholder={isZh ? '答案' : 'Answer'}
                value={securityA}
                onChange={(e) => setSecurityA(e.target.value)}
              />
              {securityError && <ErrorText>{securityError}</ErrorText>}
              <div style={{ fontSize: 12, color: '#888', marginTop: 8 }}>
                {isZh ? '提示：答案不区分大小写' : 'Tip: Answer is case-insensitive'}
              </div>
            </div>
            <ModalButtons style={{ marginTop: 16 }}>
              <Button
                variant="secondary"
                onClick={() => {
                  setShowSecurityQuestion(false);
                  setSecurityQ('');
                  setSecurityA('');
                  setSecurityError('');
                }}
                style={{ flex: 1 }}
              >
                {isZh ? '取消' : 'Cancel'}
              </Button>
              <Button
                onClick={handleSetSecurityQuestion}
                disabled={isSavingQuestion || !securityQ || !securityA}
                style={{ flex: 1 }}
              >
                {isSavingQuestion ? '...' : (isZh ? '保存' : 'Save')}
              </Button>
            </ModalButtons>
          </ModalContent>
        </Modal>
      )}

      {/* Password Change Success */}
      {showPasswordSuccess && (
        <Modal>
          <ModalContent>
            <SuccessIcon>
              <Lock size={28} />
            </SuccessIcon>
            <ModalTitle>{isZh ? '密码已修改' : 'Password Changed'}</ModalTitle>
          </ModalContent>
        </Modal>
      )}

      {/* Security Question Success */}
      {showQuestionSuccess && (
        <Modal>
          <ModalContent>
            <SuccessIcon>
              <Shield size={28} />
            </SuccessIcon>
            <ModalTitle>{isZh ? '密保问题已保存' : 'Security Question Saved'}</ModalTitle>
          </ModalContent>
        </Modal>
      )}
    </Container>
  );
};

