import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Server, Eye, EyeOff, Check, X, Loader2, Shield, Cloud } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { webDavClient } from '../../utils/webdav';

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

const InputWrapper = styled.div`
  position: relative;
  margin-bottom: 16px;
`;

const PasswordToggle = styled.button`
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const StatusBadge = styled.div<{ $status: 'success' | 'error' | 'pending' }>`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  background: ${({ $status }) =>
        $status === 'success' ? '#4CAF5020' :
            $status === 'error' ? '#F4433620' : '#FF980020'};
  color: ${({ $status }) =>
        $status === 'success' ? '#4CAF50' :
            $status === 'error' ? '#F44336' : '#FF9800'};
`;

const ButtonRow = styled.div`
  display: flex;
  gap: 12px;
  margin-top: 16px;
`;

const ConfigCard = styled.div`
  background: ${({ theme }) => theme.colors.surfaceVariant};
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 16px;
`;

const ConfigRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  
  &:not(:last-child) {
    border-bottom: 1px solid ${({ theme }) => theme.colors.outline}20;
  }
`;

const ConfigLabel = styled.span`
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  font-size: 13px;
`;

const ConfigValue = styled.span`
  font-weight: 500;
  font-size: 14px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const ToggleSwitch = styled.label`
  position: relative;
  display: inline-block;
  width: 48px;
  height: 28px;
`;

const ToggleInput = styled.input`
  opacity: 0;
  width: 0;
  height: 0;
  
  &:checked + span {
    background-color: ${({ theme }) => theme.colors.primary};
  }
  
  &:checked + span:before {
    transform: translateX(20px);
  }
`;

const ToggleSlider = styled.span`
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: ${({ theme }) => theme.colors.surfaceVariant};
  border-radius: 28px;
  transition: 0.3s;
  
  &:before {
    position: absolute;
    content: "";
    height: 20px;
    width: 20px;
    left: 4px;
    bottom: 4px;
    background-color: white;
    border-radius: 50%;
    transition: 0.3s;
  }
`;

const EncryptionRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
`;

// ========== Component ==========
interface WebDavSettingsProps {
    onBack: () => void;
    onConfigured?: () => void;
}

export const WebDavSettings: React.FC<WebDavSettingsProps> = ({ onBack, onConfigured }) => {
    const { i18n } = useTranslation();
    const isZh = i18n.language.startsWith('zh');

    // Config state
    const [serverUrl, setServerUrl] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);

    // Encryption state
    const [encryptionEnabled, setEncryptionEnabled] = useState(false);
    const [encryptionPassword, setEncryptionPassword] = useState('');
    const [showEncryptionPassword, setShowEncryptionPassword] = useState(false);

    // UI state
    const [isConfigured, setIsConfigured] = useState(false);
    const [isTesting, setIsTesting] = useState(false);
    const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
    const [isSaving, setIsSaving] = useState(false);

    // Load existing config
    useEffect(() => {
        const loadConfig = async () => {
            const loaded = await webDavClient.loadConfig();
            if (loaded) {
                const config = webDavClient.getCurrentConfig();
                if (config) {
                    setServerUrl(config.serverUrl);
                    setUsername(config.username);
                    setIsConfigured(true);
                    setEncryptionEnabled(webDavClient.isEncryptionEnabled());
                    setEncryptionPassword(webDavClient.getEncryptionPassword());
                }
            }
        };
        loadConfig();
    }, []);

    // Test connection
    const handleTestConnection = async () => {
        if (!serverUrl || !username || !password) {
            setTestResult({ success: false, message: isZh ? '请填写完整配置' : 'Please fill all fields' });
            return;
        }

        setIsTesting(true);
        setTestResult(null);

        try {
            await webDavClient.configure(serverUrl, username, password);
            const result = await webDavClient.testConnection();
            setTestResult(result);
        } catch (e) {
            const err = e as Error;
            setTestResult({ success: false, message: err.message });
        } finally {
            setIsTesting(false);
        }
    };

    // Save configuration
    const handleSave = async () => {
        if (!serverUrl || !username || !password) return;

        setIsSaving(true);
        try {
            await webDavClient.configure(serverUrl, username, password);
            await webDavClient.setEncryption(encryptionEnabled, encryptionPassword);
            setIsConfigured(true);
            onConfigured?.();
        } catch (e) {
            console.error('Failed to save config:', e);
        } finally {
            setIsSaving(false);
        }
    };

    // Clear configuration
    const handleClear = async () => {
        if (confirm(isZh ? '确定要清除 WebDAV 配置吗？' : 'Are you sure you want to clear WebDAV configuration?')) {
            await webDavClient.clearConfig();
            setServerUrl('');
            setUsername('');
            setPassword('');
            setEncryptionEnabled(false);
            setEncryptionPassword('');
            setIsConfigured(false);
            setTestResult(null);
        }
    };

    return (
        <Container>
            <Header>
                <BackButton onClick={onBack}>
                    <ArrowLeft size={20} />
                </BackButton>
                <Title>{isZh ? 'WebDAV 配置' : 'WebDAV Configuration'}</Title>
            </Header>

            {/* Current Config Display (if configured) */}
            {isConfigured && (
                <ConfigCard>
                    <ConfigRow>
                        <ConfigLabel>{isZh ? '服务器' : 'Server'}</ConfigLabel>
                        <ConfigValue>{serverUrl}</ConfigValue>
                    </ConfigRow>
                    <ConfigRow>
                        <ConfigLabel>{isZh ? '用户名' : 'Username'}</ConfigLabel>
                        <ConfigValue>{username}</ConfigValue>
                    </ConfigRow>
                    <ConfigRow>
                        <ConfigLabel>{isZh ? '加密' : 'Encryption'}</ConfigLabel>
                        <ConfigValue>{encryptionEnabled ? (isZh ? '已启用' : 'Enabled') : (isZh ? '未启用' : 'Disabled')}</ConfigValue>
                    </ConfigRow>
                    <ButtonRow>
                        <Button variant="secondary" onClick={handleClear} style={{ flex: 1 }}>
                            {isZh ? '清除配置' : 'Clear Config'}
                        </Button>
                    </ButtonRow>
                </ConfigCard>
            )}

            {/* Server Configuration */}
            <Section>
                <SectionTitle>
                    <Server size={16} />
                    {isZh ? '服务器配置' : 'Server Configuration'}
                </SectionTitle>

                <InputWrapper>
                    <Input
                        label={isZh ? '服务器地址' : 'Server URL'}
                        placeholder="https://dav.jianguoyun.com/dav/monica"
                        value={serverUrl}
                        onChange={(e) => setServerUrl(e.target.value)}
                    />
                </InputWrapper>

                <InputWrapper>
                    <Input
                        label={isZh ? '用户名' : 'Username'}
                        placeholder={isZh ? '用户名或邮箱' : 'Username or Email'}
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                    />
                </InputWrapper>

                <InputWrapper style={{ position: 'relative' }}>
                    <Input
                        label={isZh ? '密码' : 'Password'}
                        type={showPassword ? 'text' : 'password'}
                        placeholder={isZh ? '应用密码' : 'App Password'}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />
                    <PasswordToggle
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        style={{ top: '65%' }}
                    >
                        {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </PasswordToggle>
                </InputWrapper>

                {/* Test Result */}
                {testResult && (
                    <StatusBadge $status={testResult.success ? 'success' : 'error'}>
                        {testResult.success ? <Check size={16} /> : <X size={16} />}
                        {testResult.message}
                    </StatusBadge>
                )}

                <ButtonRow>
                    <Button
                        variant="secondary"
                        onClick={handleTestConnection}
                        disabled={isTesting || !serverUrl || !username || !password}
                        style={{ flex: 1 }}
                    >
                        {isTesting ? <Loader2 size={16} className="animate-spin" /> : null}
                        {isZh ? '测试连接' : 'Test Connection'}
                    </Button>
                    <Button
                        onClick={handleSave}
                        disabled={isSaving || !serverUrl || !username || !password}
                        style={{ flex: 1 }}
                    >
                        {isSaving ? <Loader2 size={16} className="animate-spin" /> : null}
                        {isZh ? '保存配置' : 'Save Config'}
                    </Button>
                </ButtonRow>
            </Section>

            {/* Encryption Settings */}
            <Section>
                <SectionTitle>
                    <Shield size={16} />
                    {isZh ? '加密设置' : 'Encryption Settings'}
                </SectionTitle>

                <EncryptionRow>
                    <div>
                        <div style={{ fontWeight: 500 }}>{isZh ? '启用加密备份' : 'Enable Encrypted Backup'}</div>
                        <div style={{ fontSize: 12, color: 'gray' }}>
                            {isZh ? '使用密码加密备份文件' : 'Encrypt backup files with password'}
                        </div>
                    </div>
                    <ToggleSwitch>
                        <ToggleInput
                            type="checkbox"
                            checked={encryptionEnabled}
                            onChange={(e) => setEncryptionEnabled(e.target.checked)}
                        />
                        <ToggleSlider />
                    </ToggleSwitch>
                </EncryptionRow>

                {encryptionEnabled && (
                    <InputWrapper style={{ position: 'relative', marginTop: 12 }}>
                        <Input
                            label={isZh ? '加密密码' : 'Encryption Password'}
                            type={showEncryptionPassword ? 'text' : 'password'}
                            placeholder={isZh ? '至少6个字符' : 'At least 6 characters'}
                            value={encryptionPassword}
                            onChange={(e) => setEncryptionPassword(e.target.value)}
                        />
                        <PasswordToggle
                            type="button"
                            onClick={() => setShowEncryptionPassword(!showEncryptionPassword)}
                            style={{ top: '65%' }}
                        >
                            {showEncryptionPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                        </PasswordToggle>
                    </InputWrapper>
                )}
            </Section>

            {/* Help Text */}
            <Section>
                <SectionTitle>
                    <Cloud size={16} />
                    {isZh ? '支持的服务' : 'Supported Services'}
                </SectionTitle>
                <div style={{ fontSize: 13, color: 'gray', lineHeight: 1.6 }}>
                    {isZh ? (
                        <>
                            • 坚果云 (推荐)<br />
                            • Nextcloud / ownCloud<br />
                            • 群晖 WebDAV<br />
                            • 其他标准 WebDAV 服务
                        </>
                    ) : (
                        <>
                            • Jianguo Cloud (Recommended for China)<br />
                            • Nextcloud / ownCloud<br />
                            • Synology WebDAV<br />
                            • Other standard WebDAV services
                        </>
                    )}
                </div>
            </Section>
        </Container>
    );
};
