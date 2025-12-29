import { useState } from 'react';
import styled from 'styled-components';
import { useTheme } from '../../theme/ThemeContext';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Card, CardTitle } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Sun, Moon, Globe, Cloud, ChevronRight, Trash2, AlertTriangle } from 'lucide-react';
import { clearAllData } from '../../utils/storage';

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

const Toggle = styled.button<{ isOn: boolean }>`
  width: 52px;
  height: 28px;
  border-radius: 14px;
  border: none;
  cursor: pointer;
  position: relative;
  background-color: ${({ theme, isOn }) => isOn ? theme.colors.primary : theme.colors.outlineVariant};
  transition: background-color 0.2s ease;

  &::after {
    content: '';
    position: absolute;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background-color: white;
    top: 3px;
    left: ${({ isOn }) => isOn ? 'calc(100% - 25px)' : '3px'};
    transition: left 0.2s ease;
    box-shadow: 0 1px 3px rgba(0,0,0,0.2);
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

export const Settings = () => {
  const { themeMode, toggleTheme } = useTheme();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const isZh = i18n.language.startsWith('zh');
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [isClearing, setIsClearing] = useState(false);

  const currentLang = i18n.language?.startsWith('zh') ? 'zh' : 'en';

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

      <div style={{ marginTop: 24 }}>
        <SectionTitle>{t('settings.appearance')}</SectionTitle>
        <Card>
          <SettingRow>
            <SettingLabel>
              {themeMode === 'dark' ? <Moon /> : <Sun />}
              {t('settings.themes.' + themeMode)}
            </SettingLabel>
            <Toggle isOn={themeMode === 'dark'} onClick={toggleTheme} />
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
          <div style={{ fontSize: 13, marginTop: 4, opacity: 0.7 }}>{t('settings.version')}: 1.0.0</div>
        </Card>
      </div>

      {/* Confirmation Modal */}
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
    </Container>
  );
};
