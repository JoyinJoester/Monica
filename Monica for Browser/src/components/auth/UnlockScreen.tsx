import React, { useState } from 'react';
import styled from 'styled-components';
import { useMasterPassword } from '../../contexts/MasterPasswordContext';
import { validateEncryptionPassword } from '../../utils/webdav/EncryptionHelper';
import { useTranslation } from 'react-i18next';
import { Key, User, Globe } from 'lucide-react';
import { getAllItems, clearAllData } from '../../utils/storage';
import type { SecureItem, PasswordEntry } from '../../types/models';
import { ItemType } from '../../types/models';

const Container = styled.div`
    display: flex;
    flex-direction: column;
    align-items: center;
    min-height: 100vh;
    padding: 32px 24px;
    background: ${({ theme }) => theme.colors.background};
`;

const Card = styled.div`
    width: 100%;
    max-width: 360px;
`;

const Logo = styled.div`
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 24px;
    
    img {
        width: 48px;
        height: 48px;
        border-radius: 12px;
    }
    
    h1 {
        font-size: 24px;
        font-weight: 600;
        color: ${({ theme }) => theme.colors.onSurface};
        margin: 0;
    }
`;

const Title = styled.h2`
    font-size: 18px;
    font-weight: 500;
    color: ${({ theme }) => theme.colors.onSurface};
    text-align: center;
    margin: 0 0 8px 0;
`;

const Subtitle = styled.p`
    font-size: 13px;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    text-align: center;
    margin: 0 0 24px 0;
`;

const Input = styled.input`
    width: 100%;
    padding: 14px 16px;
    border: 1px solid ${({ theme }) => theme.colors.outline};
    border-radius: 10px;
    background: ${({ theme }) => theme.colors.surfaceVariant};
    color: ${({ theme }) => theme.colors.onSurface};
    font-size: 15px;
    margin-bottom: 12px;
    box-sizing: border-box;
    
    &:focus {
        outline: none;
        border-color: ${({ theme }) => theme.colors.primary};
    }
    
    &::placeholder {
        color: ${({ theme }) => theme.colors.onSurfaceVariant};
    }
`;

const Button = styled.button`
    width: 100%;
    padding: 14px;
    border: none;
    border-radius: 10px;
    background: ${({ theme }) => theme.colors.primary};
    color: ${({ theme }) => theme.colors.onPrimary};
    font-size: 15px;
    font-weight: 500;
    cursor: pointer;
    transition: opacity 0.2s, transform 0.15s ease;
    
    &:hover {
        opacity: 0.9;
    }
    
    &:active {
        transform: scale(0.97);
        opacity: 0.8;
    }
    
    &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
`;

const DangerButton = styled(Button)`
    background: linear-gradient(135deg, #F44336, #E91E63);
`;

const SecondaryButton = styled(Button)`
    background: transparent;
    color: ${({ theme }) => theme.colors.primary};
    border: 1px solid ${({ theme }) => theme.colors.outline};
    margin-top: 8px;
`;

const ErrorMessage = styled.div`
    color: ${({ theme }) => theme.colors.error};
    font-size: 13px;
    text-align: center;
    margin-bottom: 12px;
`;

const HintText = styled.div`
    font-size: 12px;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    text-align: center;
    margin-top: 16px;
`;

const ForgotLink = styled.button`
    background: none;
    border: none;
    color: ${({ theme }) => theme.colors.primary};
    font-size: 13px;
    cursor: pointer;
    margin-top: 16px;
    text-decoration: underline;
    transition: opacity 0.15s ease;
    
    &:hover {
        opacity: 0.8;
    }
    &:active {
        opacity: 0.5;
    }
`;

const QuestionText = styled.div`
    font-size: 14px;
    color: ${({ theme }) => theme.colors.onSurface};
    background: ${({ theme }) => theme.colors.surfaceVariant};
    padding: 12px;
    border-radius: 8px;
    margin-bottom: 12px;
    text-align: center;
`;

const WarningBox = styled.div`
    background: #F4433615;
    border: 1px solid #F44336;
    border-radius: 8px;
    padding: 12px;
    margin-bottom: 16px;
    font-size: 13px;
    color: #F44336;
    text-align: center;
`;

const PasswordList = styled.div`
    margin-top: 24px;
    border-top: 1px solid ${({ theme }) => theme.colors.outlineVariant};
    padding-top: 16px;
    width: 100%;
`;

const PasswordItem = styled.div`
    background: ${({ theme }) => theme.colors.surfaceVariant};
    border-radius: 10px;
    padding: 12px 14px;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    border: 1px solid transparent;
    transition: all 0.15s;
    
    &:hover {
        background: ${({ theme }) => theme.colors.background};
        border-color: ${({ theme }) => theme.colors.primary};
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }
    &:active {
        transform: scale(0.98);
        opacity: 0.85;
    }
`;

const PasswordIcon = styled.div`
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #4CAF50, #8BC34A);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    flex-shrink: 0;
`;

const PasswordInfo = styled.div`
    flex: 1;
    overflow: hidden;
`;

const PasswordTitle = styled.div`
    font-size: 13px;
    font-weight: 500;
    color: ${({ theme }) => theme.colors.onSurface};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

const PasswordUsername = styled.div`
    font-size: 11px;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

const SectionTitle = styled.div`
    font-size: 12px;
    font-weight: 600;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    margin-bottom: 12px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
`;

const SiteInfo = styled.div`
    background: ${({ theme }) => theme.colors.primary}15;
    border-radius: 8px;
    padding: 8px 12px;
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: ${({ theme }) => theme.colors.primary};
`;

type ScreenMode = 'unlock' | 'setup' | 'forgot' | 'resetWithQuestion' | 'clearData';

interface UnlockScreenProps {
    returnToQuickAction?: boolean;
    currentUrl?: string;
}

export function UnlockScreen({ currentUrl }: UnlockScreenProps = {}) {
    const { t } = useTranslation();
    const {
        isFirstTime,
        unlock,
        setupMasterPassword,
        hasSecurityQuestion,
        securityQuestion,
        resetPasswordWithSecurityQuestion
    } = useMasterPassword();

    const [mode, setMode] = useState<ScreenMode>(isFirstTime ? 'setup' : 'unlock');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [answer, setAnswer] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmNewPassword, setConfirmNewPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    // Autofill state
    const [passwords, setPasswords] = useState<SecureItem[]>([]);
    const [matchedPasswords, setMatchedPasswords] = useState<SecureItem[]>([]);
    const [hostname, setHostname] = useState('');

    const isZh = navigator.language.startsWith('zh');

    // Load passwords logic
    React.useEffect(() => {
        const load = async () => {
            const items = await getAllItems();
            const pwItems = items.filter(item => item.itemType === ItemType.Password);
            setPasswords(pwItems);
        };
        load();
    }, []);

    // Hostname and matching logic
    React.useEffect(() => {
        if (currentUrl) {
            try {
                const url = new URL(currentUrl);
                setHostname(url.hostname);
            } catch {
                setHostname(currentUrl);
            }
        }
    }, [currentUrl]);

    React.useEffect(() => {
        if (hostname && passwords.length > 0) {
            const matched = passwords.filter(p => {
                const data = p.itemData as PasswordEntry;
                const website = data?.website || '';
                try {
                    const pwUrl = new URL(website.startsWith('http') ? website : `https://${website}`);
                    return pwUrl.hostname.includes(hostname) || hostname.includes(pwUrl.hostname);
                } catch {
                    return website.toLowerCase().includes(hostname.toLowerCase());
                }
            });
            setMatchedPasswords(matched);
        }
    }, [hostname, passwords]);

    const handleFillCredentials = async (item: SecureItem) => {
        try {
            const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
            if (tab?.id) {
                const data = item.itemData as PasswordEntry;
                // Send message and wait for response
                chrome.tabs.sendMessage(tab.id, {
                    type: 'FILL_CREDENTIALS',
                    username: data?.username || '',
                    password: data?.password || '',
                }, () => {
                    // Close after message is sent
                    setTimeout(() => window.close(), 100);
                });
            }
        } catch (err) {
            console.error('Failed to fill credentials:', err);
        }
    };

    const handleUnlock = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const success = await unlock(password);
            if (!success) {
                setError(t('auth.wrongPassword', '密码错误'));
            }
        } catch (err) {
            setError(t('auth.error', '发生错误，请重试'));
            console.error(err);
        }

        setLoading(false);
    };

    const handleSetup = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        const validation = validateEncryptionPassword(password);
        if (!validation.valid) {
            setError(validation.message);
            return;
        }

        if (password !== confirmPassword) {
            setError(t('auth.passwordMismatch', '两次输入的密码不一致'));
            return;
        }

        setLoading(true);
        try {
            await setupMasterPassword(password);
        } catch (err) {
            setError(t('auth.error', '发生错误，请重试'));
            console.error(err);
        }
        setLoading(false);
    };

    const handleResetWithQuestion = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        const validation = validateEncryptionPassword(newPassword);
        if (!validation.valid) {
            setError(validation.message);
            return;
        }

        if (newPassword !== confirmNewPassword) {
            setError(isZh ? '两次输入的密码不一致' : 'Passwords do not match');
            return;
        }

        setLoading(true);
        try {
            const success = await resetPasswordWithSecurityQuestion(answer, newPassword);
            if (success) {
                // Successfully reset, will automatically unlock
            } else {
                setError(isZh ? '答案错误' : 'Incorrect answer');
            }
        } catch (err) {
            setError(isZh ? '重置失败' : 'Reset failed');
            console.error(err);
        }
        setLoading(false);
    };

    const handleClearData = async () => {
        setLoading(true);
        try {
            await clearAllData();
            await chrome.storage.local.clear();
            window.location.reload();
        } catch (err) {
            setError(isZh ? '清空失败' : 'Failed to clear data');
            console.error(err);
        }
        setLoading(false);
    };

    const handleForgot = () => {
        if (hasSecurityQuestion) {
            setMode('resetWithQuestion');
        } else {
            setMode('clearData');
        }
        setError('');
    };

    // Setup screen
    if (mode === 'setup' || isFirstTime) {
        return (
            <Container>
                <Card>
                    <Logo>
                        <img src="icons/icon.png" alt="Monica" />
                        <h1>Monica</h1>
                    </Logo>

                    <Title>{t('auth.setupTitle', '设置主密码')}</Title>
                    <Subtitle>{t('auth.setupSubtitle', '主密码用于加密保护您的所有数据')}</Subtitle>

                    <form onSubmit={handleSetup}>
                        <Input
                            type="password"
                            placeholder={t('auth.passwordPlaceholder', '输入主密码')}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            autoFocus
                        />
                        <Input
                            type="password"
                            placeholder={t('auth.confirmPlaceholder', '确认主密码')}
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                        />

                        {error && <ErrorMessage>{error}</ErrorMessage>}

                        <Button type="submit" disabled={loading || !password}>
                            {loading ? '...' : t('auth.setupButton', '设置密码')}
                        </Button>
                    </form>

                    <HintText>{t('auth.passwordHint', '密码至少6位')}</HintText>
                </Card>
            </Container>
        );
    }

    // Reset with security question
    if (mode === 'resetWithQuestion') {
        return (
            <Container>
                <Card>
                    <Logo>
                        <img src="icons/icon.png" alt="Monica" />
                        <h1>Monica</h1>
                    </Logo>

                    <Title>{isZh ? '重置密码' : 'Reset Password'}</Title>
                    <Subtitle>{isZh ? '请回答密保问题' : 'Answer your security question'}</Subtitle>

                    <QuestionText>{securityQuestion}</QuestionText>

                    <form onSubmit={handleResetWithQuestion}>
                        <Input
                            type="text"
                            placeholder={isZh ? '输入答案' : 'Enter your answer'}
                            value={answer}
                            onChange={(e) => setAnswer(e.target.value)}
                            autoFocus
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

                        {error && <ErrorMessage>{error}</ErrorMessage>}

                        <Button type="submit" disabled={loading || !answer || !newPassword}>
                            {loading ? '...' : (isZh ? '重置密码' : 'Reset Password')}
                        </Button>

                        <SecondaryButton type="button" onClick={() => setMode('unlock')}>
                            {isZh ? '返回' : 'Back'}
                        </SecondaryButton>
                    </form>
                </Card>
            </Container>
        );
    }

    // Clear data confirmation (no security question set)
    if (mode === 'clearData') {
        return (
            <Container>
                <Card>
                    <Logo>
                        <img src="icons/icon.png" alt="Monica" />
                        <h1>Monica</h1>
                    </Logo>

                    <Title>{isZh ? '无法恢复密码' : 'Cannot Recover Password'}</Title>
                    <Subtitle>{isZh ? '您未设置密保问题' : 'No security question set'}</Subtitle>

                    <WarningBox>
                        {isZh
                            ? '唯一的选择是清空所有数据并重新开始。这将删除所有保存的密码、笔记和其他数据！'
                            : 'The only option is to clear all data and start fresh. This will delete all saved passwords, notes and other data!'}
                    </WarningBox>

                    {error && <ErrorMessage>{error}</ErrorMessage>}

                    <DangerButton onClick={handleClearData} disabled={loading}>
                        {loading ? '...' : (isZh ? '清空数据并重新开始' : 'Clear Data & Start Over')}
                    </DangerButton>

                    <SecondaryButton onClick={() => setMode('unlock')}>
                        {isZh ? '返回' : 'Back'}
                    </SecondaryButton>
                </Card>
            </Container>
        );
    }

    // Default unlock screen
    return (
        <Container>
            <Card>
                <Logo>
                    <img src="icons/icon.png" alt="Monica" />
                    <h1>Monica</h1>
                </Logo>

                <Title>{t('auth.unlockTitle', '解锁 Monica')}</Title>
                <Subtitle>{t('auth.unlockSubtitle', '请输入主密码以访问您的数据')}</Subtitle>

                <form onSubmit={handleUnlock}>
                    <Input
                        type="password"
                        placeholder={t('auth.passwordPlaceholder', '输入主密码')}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        autoFocus
                    />

                    {error && <ErrorMessage>{error}</ErrorMessage>}

                    <Button type="submit" disabled={loading || !password}>
                        {loading ? '...' : t('auth.unlockButton', '解锁')}
                    </Button>
                </form>

                <ForgotLink onClick={handleForgot}>
                    {isZh ? '忘记密码？' : 'Forgot password?'}
                </ForgotLink>

                {/* Autofill Section */}
                {matchedPasswords.length > 0 && (
                    <PasswordList>
                        <SectionTitle>{isZh ? '点击填充' : 'Tap to autofill'}</SectionTitle>

                        {hostname && (
                            <SiteInfo>
                                <Globe size={14} />
                                {hostname}
                            </SiteInfo>
                        )}

                        {matchedPasswords.map((item) => (
                            <PasswordItem key={item.id} onClick={() => handleFillCredentials(item)}>
                                <PasswordIcon>
                                    <Key size={16} />
                                </PasswordIcon>
                                <PasswordInfo>
                                    <PasswordTitle>{item.title}</PasswordTitle>
                                    <PasswordUsername>
                                        <User size={10} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                                        {(item.itemData as PasswordEntry)?.username || ''}
                                    </PasswordUsername>
                                </PasswordInfo>
                            </PasswordItem>
                        ))}
                    </PasswordList>
                )}
            </Card>
        </Container>
    );
}

