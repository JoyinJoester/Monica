import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Plus, Trash2, Clock, Copy, Star, Settings, Search, ShieldCheck, UserRound, TimerReset, PencilLine } from 'lucide-react';
import { getTotps, saveItem, deleteItem, updateItem } from '../../utils/storage';
import type { SecureItem, TotpData } from '../../types/models';
import { ItemType, OtpType } from '../../types/models';
import * as OTPAuth from 'otpauth';
import {
    InteractionHint,
    ManagerContainer,
    ManagerDashboard,
    ManagerTopBar,
    ManagerSearchWrap,
    ManagerSearchInput,
    ManagerPrimaryButton,
    ManagerWorkspace,
    ManagerListPane,
    ManagerRows,
    ManagerRow,
    ManagerRowHead,
    ManagerRowTitle,
    ManagerMeta,
    ManagerMetaItem,
    ManagerDetailPane,
    ManagerDetailHeader,
    ManagerFavorite,
    ManagerDetailBody,
    ManagerDetailTitle,
    ManagerDetailLabel,
    ManagerDetailField,
    ManagerDetailFooter,
    ManagerGhostButton,
    ManagerDangerButton,
    ManagerEmptyState,
    ManagerChip,
} from '../shared/ManagerStyles';

const FAB = styled(Button)<{ $manager: boolean }>`
  position: fixed;
  bottom: 90px;
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  padding: 0;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);

  ${({ $manager }) => $manager && `
    right: 24px;
    bottom: 24px;
    border-radius: 18px;
    background: linear-gradient(135deg, #7e60ff 0%, #6e49fb 100%);
    border: 1px solid rgba(182, 167, 255, 0.55);
    box-shadow: 0 12px 28px rgba(90, 58, 206, 0.45);
  `}
`;

const Modal = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 100;
  padding: 16px;
  overflow-y: auto;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 16px;
  padding: 24px;
  width: 100%;
  max-width: 420px;
  margin: 20px 0;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
`;

const OTPCode = styled.div`
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  font-family: monospace;
  color: ${({ theme }) => theme.colors.primary};
  margin: 8px 0;
  text-align: center;
`;

const Timer = styled.div<{ time: number }>`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: ${({ theme, time }) => time < 10 ? theme.colors.error : theme.colors.onSurfaceVariant};
`;

const ProgressBar = styled.div<{ progress: number }>`
  height: 4px;
  border-radius: 2px;
  background: ${({ theme }) => theme.colors.outlineVariant};
  margin-top: 4px;
  overflow: hidden;

  &::after {
    content: '';
    display: block;
    width: ${({ progress }) => progress}%;
    height: 100%;
    background: ${({ theme }) => theme.colors.primary};
    transition: width 1s linear;
  }
`;

const IconButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  padding: 10px;
  border-radius: 8px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  transition: all 0.15s ease;

  &:hover {
    background-color: ${({ theme }) => theme.colors.surfaceVariant};
  }

  &:active {
    transform: scale(0.92);
    opacity: 0.8;
  }

  svg {
    width: 20px;
    height: 20px;
  }
`;

const ButtonRow = styled.div`
  display: flex;
  gap: 12px;
  margin-top: 16px;
`;

const EmptyState = styled.div`
  text-align: center;
  padding: 48px 0;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const FavoriteIcon = styled(Star)<{ active: boolean }>`
  color: ${({ active }) => active ? '#FFC107' : '#999'};
  fill: ${({ active }) => active ? '#FFC107' : 'none'};
  cursor: pointer;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.85);
  }
`;

const Select = styled.select`
  width: 100%;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-size: 14px;
`;

const ErrorBadge = styled.div`
  background: ${({ theme }) => theme.colors.errorContainer || '#ffebee'};
  color: ${({ theme }) => theme.colors.error || '#b71c1c'};
  padding: 8px 12px;
  border-radius: 8px;
  margin: 12px 0;
  font-size: 13px;
  text-align: center;
`;

const ManagerCodeCard = styled.div`
  border: 1px solid rgba(116, 147, 203, 0.25);
  border-radius: 12px;
  background: rgba(8, 17, 34, 0.72);
  padding: 14px 12px 12px;
`;

const ManagerCode = styled.div`
  color: #dff0ff;
  font-size: clamp(30px, 5vw, 38px);
  font-weight: 700;
  letter-spacing: 5px;
  line-height: 1;
  text-align: center;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
`;

const ManagerCodeMeta = styled.div`
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #93a8d4;
  font-size: 12px;
`;

const ManagerCodeProgress = styled.div<{ $progress: number; $danger: boolean }>`
  margin-top: 8px;
  height: 5px;
  border-radius: 999px;
  background: rgba(116, 147, 203, 0.3);
  overflow: hidden;

  &::after {
    content: '';
    display: block;
    height: 100%;
    width: ${({ $progress }) => `${$progress}%`};
    background: ${({ $danger }) => ($danger ? '#ff7a89' : 'linear-gradient(90deg, #49b3ff, #4c7dff)')};
    transition: width 1s linear;
  }
`;

const getOtpTypeLabel = (otpType: string, isZh: boolean): string => {
    switch (otpType) {
        case OtpType.HOTP:
            return isZh ? '计数器 OTP' : 'Counter OTP';
        case OtpType.STEAM:
            return 'Steam';
        case OtpType.YANDEX:
            return 'Yandex';
        case OtpType.MOTP:
            return 'mOTP';
        case OtpType.TOTP:
        default:
            return isZh ? '时间 OTP' : 'Time OTP';
    }
};

const isValidBase32 = (secret: string): boolean => {
    const cleaned = secret.replace(/[\s=]/g, '').toUpperCase();
    return /^[A-Z2-7]+$/.test(cleaned) && cleaned.length >= 8;
};

const STEAM_CHARS = '23456789BCDFGHJKMNPQRTVWXY';

const normalizeOtpType = (value: unknown): string => {
    const upper = typeof value === 'string' ? value.trim().toUpperCase() : '';
    if (upper === OtpType.HOTP || upper === OtpType.STEAM || upper === OtpType.YANDEX || upper === OtpType.MOTP || upper === OtpType.TOTP) {
        return upper;
    }
    if (upper === 'YAOTP') {
        return OtpType.YANDEX;
    }
    return OtpType.TOTP;
};

const normalizeTotpData = (data: TotpData, fallbackTitle = ''): TotpData => {
    const source = (data || {}) as TotpData & Record<string, unknown>;
    const issuer = source.issuer || '';
    const accountName = source.accountName || '';
    const rawOtpType = normalizeOtpType(source.otpType);
    const hasSteamMetadata = [
        'steamFingerprint',
        'steamDeviceId',
        'steamSerialNumber',
        'steamSharedSecretBase64',
        'steamRevocationCode',
        'steamIdentitySecret',
        'steamTokenGid',
        'steamRawJson',
    ].some((key) => typeof source[key] === 'string' && (source[key] as string).length > 0);
    const looksLikeSteam = [issuer, accountName, fallbackTitle].some((text) => text.toLowerCase().includes('steam')) ||
        (typeof source.link === 'string' && source.link.toLowerCase().includes('encoder=steam'));
    const shouldUseSteam = rawOtpType === OtpType.STEAM || hasSteamMetadata || (looksLikeSteam && rawOtpType === OtpType.TOTP);
    const otpType = shouldUseSteam ? OtpType.STEAM : rawOtpType;
    const digits = shouldUseSteam ? 5 : Math.min(10, Math.max(4, Number(source.digits) || 6));
    const period = shouldUseSteam ? 30 : Math.max(1, Number(source.period) || 30);
    const algorithmRaw = typeof source.algorithm === 'string' ? source.algorithm.toUpperCase() : 'SHA1';
    const algorithm = shouldUseSteam
        ? 'SHA1'
        : (algorithmRaw === 'SHA1' || algorithmRaw === 'SHA256' || algorithmRaw === 'SHA512' ? algorithmRaw : 'SHA1');
    return {
        ...(source as TotpData),
        secret: source.secret || '',
        issuer,
        accountName,
        period,
        digits,
        algorithm,
        otpType: otpType as TotpData['otpType'],
    };
};

const formatOtpCode = (code: string, otpType: string): string => {
    if (code === '------') return code;
    if (otpType === OtpType.STEAM) {
        return code.length === 5 ? `${code.slice(0, 2)} ${code.slice(2)}` : code;
    }
    if (code.length === 6) return `${code.slice(0, 3)} ${code.slice(3)}`;
    if (code.length === 8) return `${code.slice(0, 4)} ${code.slice(4)}`;
    return code;
};

const decodeBase32 = (encoded: string): Uint8Array => {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    const cleaned = encoded.replace(/[\s=]/g, '').toUpperCase();
    let bits = '';
    for (const char of cleaned) {
        const val = alphabet.indexOf(char);
        if (val === -1) continue;
        bits += val.toString(2).padStart(5, '0');
    }
    const bytes = new Uint8Array(Math.floor(bits.length / 8));
    for (let i = 0; i < bytes.length; i++) {
        bytes[i] = parseInt(bits.slice(i * 8, i * 8 + 8), 2);
    }
    return bytes;
};

const generateSteamCode = async (secret: string, unixSeconds = Math.floor(Date.now() / 1000)): Promise<string> => {
    const keyBytes = decodeBase32(secret);
    if (keyBytes.length === 0) return '------';

    const timeStep = Math.floor(unixSeconds / 30);
    const counterBytes = new Uint8Array(8);
    let tmp = timeStep;
    for (let i = 7; i >= 0; i--) {
        counterBytes[i] = tmp & 0xff;
        tmp = Math.floor(tmp / 256);
    }

    const key = await crypto.subtle.importKey(
        'raw',
        keyBytes.buffer as ArrayBuffer,
        { name: 'HMAC', hash: 'SHA-1' },
        false,
        ['sign']
    );
    const signature = await crypto.subtle.sign('HMAC', key, counterBytes);
    const hash = new Uint8Array(signature);
    const offset = hash[hash.length - 1] & 0x0f;
    let fullCode = ((hash[offset] & 0x7f) << 24) |
        ((hash[offset + 1] & 0xff) << 16) |
        ((hash[offset + 2] & 0xff) << 8) |
        (hash[offset + 3] & 0xff);

    let code = '';
    for (let i = 0; i < 5; i++) {
        code += STEAM_CHARS[fullCode % STEAM_CHARS.length];
        fullCode = Math.floor(fullCode / STEAM_CHARS.length);
    }
    return code;
};

interface TotpItemProps {
    item: SecureItem;
    onEdit: () => void;
    onDelete: () => void;
}

const TotpItem = ({ item, onEdit, onDelete }: TotpItemProps) => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');
    const data = normalizeTotpData(item.itemData as TotpData, item.title);
    const [code, setCode] = useState('------');
    const [timeRemaining, setTimeRemaining] = useState(data.otpType === OtpType.STEAM ? 30 : (data.period || 30));
    const [hasError, setHasError] = useState(false);
    const totpRef = useRef<OTPAuth.TOTP | null>(null);

    useEffect(() => {
        if (!data.secret || !isValidBase32(data.secret)) {
            setHasError(true);
            setCode('------');
            return;
        }

        if (data.otpType !== OtpType.STEAM) {
            try {
                const cleanedSecret = data.secret.replace(/[\s=]/g, '').toUpperCase();
                totpRef.current = new OTPAuth.TOTP({
                    secret: OTPAuth.Secret.fromBase32(cleanedSecret),
                    digits: data.digits || 6,
                    period: data.period || 30,
                    algorithm: data.algorithm || 'SHA1',
                });
            } catch {
                setHasError(true);
                setCode('------');
                return;
            }
        } else {
            totpRef.current = null;
        }

        let cancelled = false;
        const updateCode = async () => {
            try {
                const epoch = Math.floor(Date.now() / 1000);
                const period = data.otpType === OtpType.STEAM ? 30 : (data.period || 30);
                const nextCode = data.otpType === OtpType.STEAM
                    ? await generateSteamCode(data.secret, epoch)
                    : (totpRef.current?.generate() || '------');
                if (cancelled) return;
                setCode(nextCode);
                setTimeRemaining(period - (epoch % period));
                setHasError(nextCode === '------');
            } catch {
                if (cancelled) return;
                setHasError(true);
                setCode('------');
            }
        };

        void updateCode();
        const interval = window.setInterval(() => {
            void updateCode();
        }, 1000);
        return () => {
            cancelled = true;
            window.clearInterval(interval);
        };
    }, [data.secret, data.digits, data.period, data.algorithm, data.otpType]);

    const handleCopy = () => {
        if (code !== '------') {
            void navigator.clipboard.writeText(code);
        }
    };

    return (
        <Card onClick={onEdit} style={{ cursor: 'pointer', padding: '12px 16px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <CardTitle style={{ fontSize: 14 }}>{item.title}</CardTitle>
                    <CardSubtitle style={{ fontSize: 11 }}>{data.issuer}{data.accountName ? ` · ${data.accountName}` : ''}</CardSubtitle>
                </div>
                {item.isFavorite && <Star size={14} fill="#FFC107" color="#FFC107" style={{ marginLeft: 8 }} />}
            </div>

            {hasError ? (
                <ErrorBadge style={{ padding: '6px 10px', margin: '8px 0', fontSize: 12 }}>
                    {isZh ? '密钥无效' : 'Invalid secret'}
                </ErrorBadge>
            ) : (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '8px 0' }}>
                    <OTPCode style={{ margin: 0, fontSize: 24 }}>{formatOtpCode(code, data.otpType || OtpType.TOTP)}</OTPCode>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Timer time={timeRemaining} style={{ fontSize: 11 }}>
                            <Clock size={12} />
                            {timeRemaining}s
                        </Timer>
                        <IconButton onClick={(e) => { e.stopPropagation(); handleCopy(); }} title={t('common.copy')} disabled={hasError} style={{ padding: 6 }}>
                            <Copy size={16} />
                        </IconButton>
                        <IconButton onClick={(e) => { e.stopPropagation(); onDelete(); }} title={t('common.delete')} style={{ padding: 6 }}>
                            <Trash2 size={16} />
                        </IconButton>
                    </div>
                </div>
            )}
            <ProgressBar progress={(timeRemaining / (data.otpType === OtpType.STEAM ? 30 : (data.period || 30))) * 100} style={{ height: 3 }} />
        </Card>
    );
};

export const AuthenticatorList = () => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');
    const isManagerMode = useMemo(() => new URLSearchParams(window.location.search).get('manager') === '1', []);

    const [totps, setTotps] = useState<SecureItem[]>([]);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [showAdvanced, setShowAdvanced] = useState(false);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [interactionHint, setInteractionHint] = useState('');
    const interactionHintTimerRef = useRef<number | null>(null);
    const [managerCode, setManagerCode] = useState('------');
    const [managerTimeRemaining, setManagerTimeRemaining] = useState(30);
    const [managerCodeError, setManagerCodeError] = useState(false);

    const [form, setForm] = useState({
        title: '',
        secret: '',
        issuer: '',
        accountName: '',
        period: 30,
        digits: 6,
        algorithm: 'SHA1',
        otpType: OtpType.TOTP as string,
        isFavorite: false,
    });

    const resetForm = () => {
        setForm({
            title: '',
            secret: '',
            issuer: '',
            accountName: '',
            period: 30,
            digits: 6,
            algorithm: 'SHA1',
            otpType: OtpType.TOTP,
            isFavorite: false,
        });
        setEditingId(null);
        setShowAdvanced(false);
    };

    const showInteractionHint = useCallback((message: string) => {
        setInteractionHint(message);
        if (interactionHintTimerRef.current) {
            window.clearTimeout(interactionHintTimerRef.current);
        }
        interactionHintTimerRef.current = window.setTimeout(() => {
            setInteractionHint('');
        }, 1400);
    }, []);

    useEffect(() => {
        return () => {
            if (interactionHintTimerRef.current) {
                window.clearTimeout(interactionHintTimerRef.current);
            }
        };
    }, []);

    const loadTotps = useCallback(async () => {
        const data = await getTotps();
        setTotps(data);
    }, []);

    useEffect(() => {
        void loadTotps();
    }, [loadTotps]);

    const filteredTotps = useMemo(() => {
        const term = search.trim().toLowerCase();
        if (!term) return totps;
        return totps.filter((item) => {
            const data = item.itemData as TotpData;
            return (
                (item.title || '').toLowerCase().includes(term) ||
                (data.issuer || '').toLowerCase().includes(term) ||
                (data.accountName || '').toLowerCase().includes(term)
            );
        });
    }, [totps, search]);

    useEffect(() => {
        if (filteredTotps.length === 0) {
            setSelectedId(null);
            return;
        }
        if (!selectedId || !filteredTotps.some((item) => item.id === selectedId)) {
            setSelectedId(filteredTotps[0].id);
        }
    }, [filteredTotps, selectedId]);

    const selectedItem = useMemo(
        () => filteredTotps.find((item) => item.id === selectedId) || null,
        [filteredTotps, selectedId]
    );

    const selectedData = useMemo(
        () => selectedItem ? normalizeTotpData(selectedItem.itemData as TotpData, selectedItem.title) : null,
        [selectedItem]
    );

    useEffect(() => {
        if (!selectedData) {
            setManagerCode('------');
            setManagerTimeRemaining(30);
            setManagerCodeError(false);
            return;
        }

        if (!selectedData.secret || !isValidBase32(selectedData.secret)) {
            setManagerCode('------');
            setManagerCodeError(true);
            setManagerTimeRemaining(selectedData.otpType === OtpType.STEAM ? 30 : (selectedData.period || 30));
            return;
        }

        let totp: OTPAuth.TOTP | null = null;
        if (selectedData.otpType !== OtpType.STEAM) {
            try {
                const cleanedSecret = selectedData.secret.replace(/[\s=]/g, '').toUpperCase();
                totp = new OTPAuth.TOTP({
                    secret: OTPAuth.Secret.fromBase32(cleanedSecret),
                    digits: selectedData.digits || 6,
                    period: selectedData.period || 30,
                    algorithm: selectedData.algorithm || 'SHA1',
                });
            } catch {
                setManagerCode('------');
                setManagerCodeError(true);
                setManagerTimeRemaining(selectedData.period || 30);
                return;
            }
        }

        let cancelled = false;
        const refresh = async () => {
            try {
                const epoch = Math.floor(Date.now() / 1000);
                const period = selectedData.otpType === OtpType.STEAM ? 30 : (selectedData.period || 30);
                const nextCode = selectedData.otpType === OtpType.STEAM
                    ? await generateSteamCode(selectedData.secret, epoch)
                    : (totp?.generate() || '------');
                if (cancelled) return;
                setManagerCode(nextCode);
                setManagerTimeRemaining(period - (epoch % period));
                setManagerCodeError(nextCode === '------');
            } catch {
                if (cancelled) return;
                setManagerCode('------');
                setManagerCodeError(true);
            }
        };

        void refresh();
        const interval = window.setInterval(() => {
            void refresh();
        }, 1000);
        return () => {
            cancelled = true;
            window.clearInterval(interval);
        };
    }, [selectedData, selectedItem?.id]);

    const handleSave = async () => {
        if (!form.title.trim() || !form.secret.trim()) return;

        const itemData: TotpData = normalizeTotpData({
            secret: form.secret,
            issuer: form.issuer,
            accountName: form.accountName,
            period: form.period,
            digits: form.digits,
            algorithm: form.algorithm,
            otpType: form.otpType as typeof OtpType[keyof typeof OtpType],
        }, form.title);

        if (editingId) {
            await updateItem(editingId, { title: form.title, isFavorite: form.isFavorite, itemData });
        } else {
            await saveItem({
                itemType: ItemType.Totp,
                title: form.title,
                notes: '',
                isFavorite: form.isFavorite,
                sortOrder: 0,
                itemData,
            });
        }
        resetForm();
        setShowModal(false);
        await loadTotps();
        showInteractionHint(isZh ? '已保存身份验证器' : 'Authenticator saved');
    };

    const handleEdit = (item: SecureItem) => {
        const data = normalizeTotpData(item.itemData as TotpData, item.title);
        setForm({
            title: item.title,
            secret: data.secret || '',
            issuer: data.issuer || '',
            accountName: data.accountName || '',
            period: data.period || 30,
            digits: data.digits || 6,
            algorithm: data.algorithm || 'SHA1',
            otpType: data.otpType || OtpType.TOTP,
            isFavorite: item.isFavorite,
        });
        setEditingId(item.id);
        setShowModal(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm(t('common.confirmDelete'))) return;
        await deleteItem(id);
        if (editingId === id) {
            resetForm();
        }
        await loadTotps();
        showInteractionHint(isZh ? '已删除' : 'Deleted');
    };

    const handleToggleFavorite = async (item: SecureItem) => {
        await updateItem(item.id, { isFavorite: !item.isFavorite });
        await loadTotps();
        showInteractionHint(!item.isFavorite ? (isZh ? '已加入收藏' : 'Added to favorites') : (isZh ? '已取消收藏' : 'Removed from favorites'));
    };

    const handleCopyCode = async () => {
        if (!selectedItem || managerCodeError || managerCode === '------') {
            showInteractionHint(isZh ? '无法复制当前验证码' : 'Cannot copy current code');
            return;
        }
        try {
            await navigator.clipboard.writeText(managerCode);
            showInteractionHint(isZh ? '已复制验证码' : 'Code copied');
        } catch {
            showInteractionHint(isZh ? '复制失败' : 'Copy failed');
        }
    };

    return (
        <ManagerContainer $manager={isManagerMode}>
            {isManagerMode ? (
                <ManagerDashboard>
                    <ManagerTopBar>
                        <ManagerSearchWrap>
                            <ManagerSearchInput
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                placeholder={t('app.searchPlaceholder')}
                            />
                            <Search />
                        </ManagerSearchWrap>
                        <ManagerPrimaryButton onClick={() => { resetForm(); setShowModal(true); }}>
                            <Plus size={16} />
                            {t('common.add')} {t('authenticator.title')}
                        </ManagerPrimaryButton>
                    </ManagerTopBar>

                    <ManagerWorkspace>
                        <ManagerListPane>
                            <ManagerRows>
                                {totps.length === 0 ? (
                                    <ManagerEmptyState>
                                        <h3>{isZh ? '还没有验证器条目' : 'No authenticator items yet'}</h3>
                                        <p>{isZh ? '添加 TOTP 密钥后，即可在这里实时查看动态验证码。' : 'Add a TOTP secret to start generating verification codes.'}</p>
                                    </ManagerEmptyState>
                                ) : filteredTotps.length === 0 ? (
                                    <ManagerEmptyState>
                                        <h3>{isZh ? '未找到匹配结果' : 'No matching authenticator items'}</h3>
                                        <p>{isZh ? '可按标题、发行方或账户名进行筛选。' : 'Filter by title, issuer, or account name.'}</p>
                                    </ManagerEmptyState>
                                ) : filteredTotps.map((item) => {
                                    const data = normalizeTotpData(item.itemData as TotpData, item.title);
                                    return (
                                        <ManagerRow
                                            key={item.id}
                                            $active={selectedId === item.id}
                                            onClick={() => setSelectedId(item.id)}
                                        >
                                            <ManagerRowHead>
                                                <ManagerRowTitle>{item.title}</ManagerRowTitle>
                                                <ManagerFavorite
                                                    $active={item.isFavorite}
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        void handleToggleFavorite(item);
                                                    }}
                                                    title={item.isFavorite ? (isZh ? '取消收藏' : 'Unfavorite') : (isZh ? '收藏' : 'Favorite')}
                                                >
                                                    <Star size={18} fill={item.isFavorite ? '#ffd34a' : 'none'} />
                                                </ManagerFavorite>
                                            </ManagerRowHead>
                                            <div style={{ marginTop: 8 }}>
                                                <ManagerChip>{getOtpTypeLabel(data.otpType || OtpType.TOTP, !!isZh)}</ManagerChip>
                                            </div>
                                            <ManagerMeta>
                                                <ManagerMetaItem>
                                                    <ShieldCheck size={13} />
                                                    <span>{data.issuer || (isZh ? '未填写发行方' : 'No issuer')}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <UserRound size={13} />
                                                    <span>{data.accountName || (isZh ? '未填写账户名' : 'No account')}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <TimerReset size={13} />
                                                    <span>{`${data.otpType === OtpType.STEAM ? 30 : (data.period || 30)}s · ${data.digits || 6} ${isZh ? '位' : 'digits'}`}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <Clock size={13} />
                                                    <span>{(data.algorithm || 'SHA1').toUpperCase()}</span>
                                                </ManagerMetaItem>
                                            </ManagerMeta>
                                        </ManagerRow>
                                    );
                                })}
                            </ManagerRows>
                        </ManagerListPane>

                        <ManagerDetailPane>
                            <ManagerDetailHeader>
                                <span>{isZh ? '条目详情' : 'Item Details'}</span>
                                <ManagerFavorite
                                    $active={!!selectedItem?.isFavorite}
                                    onClick={() => {
                                        if (selectedItem) {
                                            void handleToggleFavorite(selectedItem);
                                        }
                                    }}
                                    title={selectedItem?.isFavorite ? (isZh ? '取消收藏' : 'Unfavorite') : (isZh ? '收藏' : 'Favorite')}
                                    disabled={!selectedItem}
                                >
                                    <Star size={18} fill={selectedItem?.isFavorite ? '#ffd34a' : 'none'} />
                                </ManagerFavorite>
                            </ManagerDetailHeader>
                            <ManagerDetailBody>
                                {selectedItem && selectedData ? (
                                    <>
                                        <ManagerDetailTitle>{selectedItem.title}</ManagerDetailTitle>
                                        <ManagerCodeCard>
                                            {managerCodeError ? (
                                                <ErrorBadge style={{ margin: 0 }}>
                                                    {isZh ? '密钥无效，无法生成验证码' : 'Invalid secret, code unavailable'}
                                                </ErrorBadge>
                                            ) : (
                                                <>
                                                    <ManagerCode>{formatOtpCode(managerCode, selectedData.otpType || OtpType.TOTP)}</ManagerCode>
                                                    <ManagerCodeMeta>
                                                        <Clock size={13} />
                                                        <span>{managerTimeRemaining}s</span>
                                                    </ManagerCodeMeta>
                                                    <ManagerCodeProgress
                                                        $progress={(managerTimeRemaining / (selectedData.otpType === OtpType.STEAM ? 30 : (selectedData.period || 30))) * 100}
                                                        $danger={managerTimeRemaining < 10}
                                                    />
                                                </>
                                            )}
                                        </ManagerCodeCard>
                                        <ManagerDetailLabel>{isZh ? 'OTP 类型' : 'OTP Type'}</ManagerDetailLabel>
                                        <ManagerDetailField>{getOtpTypeLabel(selectedData.otpType || OtpType.TOTP, !!isZh)}</ManagerDetailField>
                                        <ManagerDetailLabel>{t('authenticator.issuer')}</ManagerDetailLabel>
                                        <ManagerDetailField>{selectedData.issuer || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                        <ManagerDetailLabel>{t('authenticator.account')}</ManagerDetailLabel>
                                        <ManagerDetailField>{selectedData.accountName || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                        <ManagerDetailLabel>{isZh ? '周期 / 位数 / 算法' : 'Period / Digits / Algorithm'}</ManagerDetailLabel>
                                        <ManagerDetailField>{`${selectedData.otpType === OtpType.STEAM ? 30 : (selectedData.period || 30)}s / ${selectedData.digits || 6} / ${(selectedData.algorithm || 'SHA1').toUpperCase()}`}</ManagerDetailField>
                                        <ManagerDetailLabel>{t('authenticator.secret')}</ManagerDetailLabel>
                                        <ManagerDetailField>{selectedData.secret || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                    </>
                                ) : (
                                    <ManagerEmptyState>
                                        <ShieldCheck size={24} />
                                        <h3>{isZh ? '选择一个验证器条目' : 'Select an authenticator item'}</h3>
                                        <p>{isZh ? '左侧选中条目后，这里会展示动态验证码和详细配置。' : 'Pick an item on the left to view live code and full configuration.'}</p>
                                    </ManagerEmptyState>
                                )}
                            </ManagerDetailBody>
                            <ManagerDetailFooter>
                                <ManagerGhostButton
                                    onClick={() => {
                                        if (selectedItem) {
                                            handleEdit(selectedItem);
                                        }
                                    }}
                                    disabled={!selectedItem}
                                >
                                    <PencilLine size={14} />
                                    {t('common.edit')}
                                </ManagerGhostButton>
                                <ManagerGhostButton
                                    onClick={() => { void handleCopyCode(); }}
                                    disabled={!selectedItem}
                                >
                                    <Copy size={14} />
                                    {t('common.copy')}
                                </ManagerGhostButton>
                                <ManagerDangerButton
                                    onClick={() => {
                                        if (selectedItem) {
                                            void handleDelete(selectedItem.id);
                                        }
                                    }}
                                    disabled={!selectedItem}
                                >
                                    <Trash2 size={14} />
                                    {t('common.delete')}
                                </ManagerDangerButton>
                            </ManagerDetailFooter>
                        </ManagerDetailPane>
                    </ManagerWorkspace>
                </ManagerDashboard>
            ) : (
                <>
                    <h2 style={{ fontSize: 18, margin: '0 0 16px 0' }}>{t('authenticator.title')}</h2>

                    {totps.length === 0 && <EmptyState>{t('common.noItems')}</EmptyState>}

                    {totps.map((item) => (
                        <TotpItem
                            key={item.id}
                            item={item}
                            onEdit={() => handleEdit(item)}
                            onDelete={() => { void handleDelete(item.id); }}
                        />
                    ))}
                </>
            )}

            <FAB $manager={isManagerMode} onClick={() => { resetForm(); setShowModal(true); }}><Plus /></FAB>

            {showModal && (
                <Modal onClick={() => { setShowModal(false); resetForm(); }}>
                    <ModalContent onClick={(e) => e.stopPropagation()}>
                        <ModalHeader>
                            <h3 style={{ margin: 0 }}>{editingId ? t('common.edit') : t('common.add')} {t('authenticator.title')}</h3>
                            <FavoriteIcon active={form.isFavorite} onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })} size={24} />
                        </ModalHeader>

                        <Input label={`${isZh ? '标题' : 'Title'} *`} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />

                        <div style={{ marginTop: 12 }}>
                            <label style={{ fontSize: 12, color: 'gray' }}>{isZh ? 'OTP 类型' : 'OTP Type'}</label>
                            <Select
                                value={form.otpType}
                                onChange={(e) => {
                                    const newType = e.target.value;
                                    let newDigits = form.digits;
                                    if (newType === OtpType.STEAM) {
                                        newDigits = 5;
                                    } else if (newType === OtpType.YANDEX) {
                                        newDigits = 8;
                                    } else if (form.otpType === OtpType.STEAM || form.otpType === OtpType.YANDEX) {
                                        newDigits = 6;
                                    }
                                    setForm({ ...form, otpType: newType, digits: newDigits });
                                }}
                                style={{ marginTop: 4 }}
                            >
                                <option value={OtpType.TOTP}>TOTP ({isZh ? '基于时间' : 'Time-based'})</option>
                                <option value={OtpType.HOTP}>HOTP ({isZh ? '基于计数器' : 'Counter-based'})</option>
                                <option value={OtpType.STEAM}>Steam Guard ({isZh ? '5位' : '5 digits'})</option>
                                <option value={OtpType.YANDEX}>Yandex ({isZh ? '8位' : '8 digits'})</option>
                                <option value={OtpType.MOTP}>mOTP</option>
                            </Select>
                        </div>

                        <Input label={t('authenticator.issuer')} placeholder="Google, GitHub, Steam..." value={form.issuer} onChange={(e) => setForm({ ...form, issuer: e.target.value })} />
                        <Input label={t('authenticator.account')} placeholder="user@email.com" value={form.accountName} onChange={(e) => setForm({ ...form, accountName: e.target.value })} />
                        <Input label={`${t('authenticator.secret')} *`} placeholder="JBSWY3DPEHPK3PXP" value={form.secret} onChange={(e) => setForm({ ...form, secret: e.target.value })} />

                        <div
                            onClick={() => setShowAdvanced(!showAdvanced)}
                            style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 16, cursor: 'pointer', color: '#666' }}
                        >
                            <Settings size={16} />
                            <span style={{ fontSize: 13 }}>{isZh ? '高级选项' : 'Advanced Options'}</span>
                        </div>

                        {showAdvanced && (
                            <div style={{ marginTop: 12 }}>
                                {form.otpType !== OtpType.HOTP && (
                                    <div style={{ marginBottom: 12 }}>
                                        <label style={{ fontSize: 12, color: 'gray' }}>{t('authenticator.period')}</label>
                                        <Select value={form.period} onChange={(e) => setForm({ ...form, period: Number.parseInt(e.target.value, 10) })}>
                                            <option value={30}>30s</option>
                                            <option value={60}>60s</option>
                                        </Select>
                                    </div>
                                )}

                                <div style={{ marginBottom: 12 }}>
                                    <label style={{ fontSize: 12, color: 'gray' }}>
                                        {t('authenticator.digits')}
                                        {form.otpType === OtpType.STEAM && <span style={{ marginLeft: 8, color: '#888' }}>({isZh ? 'Steam 固定 5 位' : 'Steam uses 5'})</span>}
                                    </label>
                                    <Select
                                        value={form.digits}
                                        onChange={(e) => setForm({ ...form, digits: Number.parseInt(e.target.value, 10) })}
                                        disabled={form.otpType === OtpType.STEAM}
                                        style={{ opacity: form.otpType === OtpType.STEAM ? 0.6 : 1 }}
                                    >
                                        <option value={5}>5</option>
                                        <option value={6}>6</option>
                                        <option value={7}>7</option>
                                        <option value={8}>8</option>
                                    </Select>
                                </div>

                                <div>
                                    <label style={{ fontSize: 12, color: 'gray' }}>{isZh ? '算法' : 'Algorithm'}</label>
                                    <Select value={form.algorithm} onChange={(e) => setForm({ ...form, algorithm: e.target.value })}>
                                        <option value="SHA1">SHA-1</option>
                                        <option value="SHA256">SHA-256</option>
                                        <option value="SHA512">SHA-512</option>
                                    </Select>
                                </div>
                            </div>
                        )}

                        <ButtonRow>
                            <Button variant="text" onClick={() => { setShowModal(false); resetForm(); }}>{t('common.cancel')}</Button>
                            <Button onClick={() => { void handleSave(); }} disabled={!form.title || !form.secret}>{t('common.save')}</Button>
                        </ButtonRow>
                    </ModalContent>
                </Modal>
            )}

            <InteractionHint $show={!!interactionHint}>{interactionHint}</InteractionHint>
        </ManagerContainer>
    );
};
