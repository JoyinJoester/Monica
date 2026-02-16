import { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { Key, ArrowRight, User, Globe } from 'lucide-react';
import { getAllItems } from '../../utils/storage';
import type { SecureItem, PasswordEntry } from '../../types/models';
import { ItemType } from '../../types/models';

const Container = styled.div`
    padding: 16px;
    min-height: 400px;
    background: ${({ theme }) => theme.colors.background};
`;

const Header = styled.div`
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
`;

const Logo = styled.img`
    width: 40px;
    height: 40px;
    border-radius: 10px;
`;

const Title = styled.h1`
    font-size: 20px;
    font-weight: 600;
    margin: 0;
    color: ${({ theme }) => theme.colors.onSurface};
`;

const SiteInfo = styled.div`
    background: ${({ theme }) => theme.colors.surfaceVariant};
    border-radius: 12px;
    padding: 12px 16px;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 10px;
`;

const SiteIcon = styled.div`
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: ${({ theme }) => theme.colors.primary}20;
    display: flex;
    align-items: center;
    justify-content: center;
    color: ${({ theme }) => theme.colors.primary};
`;

const SiteName = styled.div`
    flex: 1;
    font-size: 14px;
    color: ${({ theme }) => theme.colors.onSurface};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

const ActionCard = styled.div`
    background: ${({ theme }) => theme.colors.surface};
    border-radius: 12px;
    padding: 16px;
    margin-bottom: 12px;
    cursor: pointer;
    transition: transform 0.1s, box-shadow 0.1s;
    border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
    
    &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }
`;

const CardHeader = styled.div`
    display: flex;
    align-items: center;
    gap: 12px;
`;

const CardIcon = styled.div<{ $bg?: string }>`
    width: 40px;
    height: 40px;
    border-radius: 10px;
    background: ${({ $bg }) => $bg || 'linear-gradient(135deg, #667eea, #764ba2)'};
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
`;

const CardContent = styled.div`
    flex: 1;
`;

const CardTitle = styled.div`
    font-weight: 600;
    font-size: 15px;
    color: ${({ theme }) => theme.colors.onSurface};
`;

const CardSubtitle = styled.div`
    font-size: 12px;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    margin-top: 2px;
`;

const PasswordListContainer = styled.div`
    margin-top: 16px;
`;

const PasswordItem = styled.div`
    background: ${({ theme }) => theme.colors.surface};
    border-radius: 10px;
    padding: 12px 14px;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
    transition: background 0.15s;
    
    &:hover {
        background: ${({ theme }) => theme.colors.surfaceVariant};
    }
`;

const PasswordIcon = styled.div`
    width: 36px;
    height: 36px;
    border-radius: 8px;
    background: linear-gradient(135deg, #4CAF50, #8BC34A);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
`;

const PasswordInfo = styled.div`
    flex: 1;
    overflow: hidden;
`;

const PasswordTitle = styled.div`
    font-size: 14px;
    font-weight: 500;
    color: ${({ theme }) => theme.colors.onSurface};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

const PasswordUsername = styled.div`
    font-size: 12px;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

const EmptyState = styled.div`
    text-align: center;
    padding: 24px;
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    font-size: 14px;
`;

interface QuickActionPageProps {
    currentUrl?: string;
    onClose?: () => void;
}

export function QuickActionPage({ currentUrl, onClose }: QuickActionPageProps) {
    const navigate = useNavigate();
    const [passwords, setPasswords] = useState<SecureItem[]>([]);
    const [matchedPasswords, setMatchedPasswords] = useState<SecureItem[]>([]);
    const [hostname, setHostname] = useState('');
    const isZh = navigator.language.startsWith('zh');

    const loadPasswords = useCallback(async () => {
        const items = await getAllItems();
        const pwItems = items.filter(item => item.itemType === ItemType.Password);
        setPasswords(pwItems);
    }, []);

    useEffect(() => {
        loadPasswords();
        if (currentUrl) {
            try {
                const url = new URL(currentUrl);
                setHostname(url.hostname);
            } catch {
                setHostname(currentUrl);
            }
        }
    }, [currentUrl, loadPasswords]);

    useEffect(() => {
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
                await chrome.tabs.sendMessage(tab.id, {
                    type: 'FILL_CREDENTIALS',
                    username: data?.username || '',
                    password: data?.password || '',
                });
                window.close();
            }
        } catch (err) {
            console.error('Failed to fill credentials:', err);
        }
    };

    const handleOpenManager = () => {
        if (onClose) {
            onClose();
        }
        navigate('/');
    };

    return (
        <Container>
            <Header>
                <Logo src="icons/icon.png" alt="Monica" />
                <Title>Monica</Title>
            </Header>

            {hostname && (
                <SiteInfo>
                    <SiteIcon>
                        <Globe size={18} />
                    </SiteIcon>
                    <SiteName>{hostname}</SiteName>
                </SiteInfo>
            )}

            <ActionCard onClick={handleOpenManager}>
                <CardHeader>
                    <CardIcon>
                        <ArrowRight size={20} />
                    </CardIcon>
                    <CardContent>
                        <CardTitle>{isZh ? '进入管理页面' : 'Open Manager'}</CardTitle>
                        <CardSubtitle>{isZh ? '管理密码、笔记和更多' : 'Manage passwords, notes and more'}</CardSubtitle>
                    </CardContent>
                </CardHeader>
            </ActionCard>

            {matchedPasswords.length > 0 ? (
                <PasswordListContainer>
                    <CardTitle style={{ marginBottom: 12 }}>
                        {isZh ? '匹配的密码' : 'Matching Passwords'}
                    </CardTitle>
                    {matchedPasswords.map((item) => (
                        <PasswordItem key={item.id} onClick={() => handleFillCredentials(item)}>
                            <PasswordIcon>
                                <Key size={18} />
                            </PasswordIcon>
                            <PasswordInfo>
                                <PasswordTitle>{item.title}</PasswordTitle>
                                <PasswordUsername>
                                    <User size={12} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                                    {(item.itemData as PasswordEntry)?.username || ''}
                                </PasswordUsername>
                            </PasswordInfo>
                        </PasswordItem>
                    ))}
                </PasswordListContainer>
            ) : hostname ? (
                <EmptyState>
                    {isZh ? '没有匹配此网站的密码' : 'No passwords match this site'}
                </EmptyState>
            ) : null}
        </Container>
    );
}
