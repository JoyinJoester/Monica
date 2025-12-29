import { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Plus, Trash2, Clock, Copy, Star, Settings } from 'lucide-react';
import { getTotps, saveItem, deleteItem, updateItem } from '../../utils/storage';
import type { SecureItem, TotpData } from '../../types/models';
import { ItemType, OtpType } from '../../types/models';
import * as OTPAuth from 'otpauth';

const Container = styled.div`
  padding: 16px; padding-bottom: 100px;
`;

const FAB = styled(Button)`
  position: fixed; bottom: 90px; right: 20px; width: 56px; height: 56px;
  border-radius: 16px; padding: 0; box-shadow: 0 4px 12px rgba(0,0,0,0.2);
`;

const Modal = styled.div`
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: flex-start; justify-content: center;
  z-index: 100; padding: 16px; overflow-y: auto;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 16px; padding: 24px; width: 100%; max-width: 400px; margin: 20px 0;
`;

const ModalHeader = styled.div`
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
`;

const OTPCode = styled.div`
  font-size: 28px; font-weight: 700; letter-spacing: 4px; font-family: monospace;
  color: ${({ theme }) => theme.colors.primary};
  margin: 8px 0; text-align: center;
`;

const Timer = styled.div<{ time: number }>`
  display: flex; align-items: center; justify-content: center; gap: 4px;
  font-size: 12px; font-weight: 500;
  color: ${({ theme, time }) => time < 10 ? theme.colors.error : theme.colors.onSurfaceVariant};
`;

const ProgressBar = styled.div<{ progress: number }>`
  height: 4px; border-radius: 2px;
  background: ${({ theme }) => theme.colors.outlineVariant};
  margin-top: 4px; overflow: hidden;
  &::after {
    content: ''; display: block;
    width: ${({ progress }) => progress}%; height: 100%;
    background: ${({ theme }) => theme.colors.primary};
    transition: width 1s linear;
  }
`;



const IconButton = styled.button`
  background: none; border: none; cursor: pointer; padding: 10px; border-radius: 8px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  &:hover { background-color: ${({ theme }) => theme.colors.surfaceVariant}; }
  svg { width: 20px; height: 20px; }
`;

const ButtonRow = styled.div`
  display: flex; gap: 12px; margin-top: 16px;
`;

const EmptyState = styled.div`
  text-align: center; padding: 48px 0; color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const FavoriteIcon = styled(Star) <{ active: boolean }>`
  color: ${({ active }) => active ? '#FFC107' : '#999'};
  fill: ${({ active }) => active ? '#FFC107' : 'none'}; cursor: pointer;
`;



const Select = styled.select`
  width: 100%; padding: 14px; border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface}; font-size: 14px;
`;

// ========== TOTP Validation ==========
const isValidBase32 = (secret: string): boolean => {
    // Base32 uses A-Z and 2-7 only
    const cleaned = secret.replace(/[\s=]/g, '').toUpperCase();
    return /^[A-Z2-7]+$/.test(cleaned) && cleaned.length >= 8;
};

const ErrorBadge = styled.div`
  background: ${({ theme }) => theme.colors.errorContainer || '#ffebee'};
  color: ${({ theme }) => theme.colors.error || '#b71c1c'};
  padding: 8px 12px; border-radius: 8px; margin: 12px 0;
  font-size: 13px; text-align: center;
`;

// ========== TOTP Item Component ==========
interface TotpItemProps {
    item: SecureItem;
    onEdit: () => void;
    onDelete: () => void;
}

const TotpItem = ({ item, onEdit, onDelete }: TotpItemProps) => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');
    const data = item.itemData as TotpData;
    const [code, setCode] = useState('------');
    const [timeRemaining, setTimeRemaining] = useState(30);
    const [hasError, setHasError] = useState(false);
    const totpRef = useRef<OTPAuth.TOTP | null>(null);

    useEffect(() => {
        setHasError(false);

        // Validate first
        if (!data.secret || !isValidBase32(data.secret)) {
            setHasError(true);
            setCode('------');
            return;
        }

        try {
            const cleanedSecret = data.secret.replace(/[\s=]/g, '').toUpperCase();
            totpRef.current = new OTPAuth.TOTP({
                secret: OTPAuth.Secret.fromBase32(cleanedSecret),
                digits: data.digits || 6,
                period: data.period || 30,
                algorithm: data.algorithm || 'SHA1',
            });
        } catch (e) {
            console.error('Invalid TOTP secret', e);
            setHasError(true);
            setCode('------');
            return;
        }

        const updateCode = () => {
            if (totpRef.current) {
                try {
                    setCode(totpRef.current.generate());
                    const epoch = Math.floor(Date.now() / 1000);
                    const period = data.period || 30;
                    setTimeRemaining(period - (epoch % period));
                } catch {
                    setHasError(true);
                    setCode('------');
                }
            }
        };

        updateCode();
        const interval = setInterval(updateCode, 1000);
        return () => clearInterval(interval);
    }, [data.secret, data.digits, data.period, data.algorithm]);

    const handleCopy = () => {
        if (code !== '------') {
            navigator.clipboard.writeText(code);
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
                    {isZh ? '⚠️ 密钥无效' : '⚠️ Invalid secret'}
                </ErrorBadge>
            ) : (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '8px 0' }}>
                    <OTPCode style={{ margin: 0, fontSize: 24 }}>{code.slice(0, 3)} {code.slice(3)}</OTPCode>
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
            <ProgressBar progress={(timeRemaining / (data.period || 30)) * 100} style={{ height: 3 }} />
        </Card>
    );
};

// ========== Main Component ==========
export const AuthenticatorList = () => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');

    const [totps, setTotps] = useState<SecureItem[]>([]);
    const [showModal, setShowModal] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [showAdvanced, setShowAdvanced] = useState(false);
    const [form, setForm] = useState({
        title: '', secret: '', issuer: '', accountName: '',
        period: 30, digits: 6, algorithm: 'SHA1', otpType: OtpType.TOTP as string, isFavorite: false,
    });

    const resetForm = () => {
        setForm({
            title: '', secret: '', issuer: '', accountName: '',
            period: 30, digits: 6, algorithm: 'SHA1', otpType: OtpType.TOTP, isFavorite: false,
        });
        setEditingId(null);
        setShowAdvanced(false);
    };

    const loadTotps = async () => {
        const data = await getTotps();
        setTotps(data);
    };

    useEffect(() => { loadTotps(); }, []);

    const handleSave = async () => {
        if (!form.title || !form.secret) return;

        const itemData: TotpData = {
            secret: form.secret,
            issuer: form.issuer,
            accountName: form.accountName,
            period: form.period,
            digits: form.digits,
            algorithm: form.algorithm,
            otpType: form.otpType as typeof OtpType[keyof typeof OtpType],
        };

        if (editingId) {
            await updateItem(editingId, { title: form.title, isFavorite: form.isFavorite, itemData });
        } else {
            await saveItem({
                itemType: ItemType.Totp, title: form.title, notes: '',
                isFavorite: form.isFavorite, sortOrder: 0, itemData,
            });
        }
        resetForm();
        setShowModal(false);
        loadTotps();
    };

    const handleEdit = (item: SecureItem) => {
        const data = item.itemData as TotpData;
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
        if (confirm(t('common.confirmDelete'))) {
            await deleteItem(id);
            loadTotps();
        }
    };

    return (
        <Container>
            <h2 style={{ fontSize: 18, margin: '0 0 16px 0' }}>{t('authenticator.title')}</h2>

            {totps.length === 0 && <EmptyState>{t('common.noItems')}</EmptyState>}

            {totps.map((item) => (
                <TotpItem
                    key={item.id}
                    item={item}
                    onEdit={() => handleEdit(item)}
                    onDelete={() => handleDelete(item.id)}
                />
            ))}

            <FAB onClick={() => { resetForm(); setShowModal(true); }}><Plus /></FAB>

            {showModal && (
                <Modal onClick={() => { setShowModal(false); resetForm(); }}>
                    <ModalContent onClick={(e) => e.stopPropagation()}>
                        <ModalHeader>
                            <h3 style={{ margin: 0 }}>{editingId ? t('common.edit') : t('common.add')} {t('authenticator.title')}</h3>
                            <FavoriteIcon active={form.isFavorite} onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })} size={24} />
                        </ModalHeader>

                        <Input label={`${isZh ? '标题' : 'Title'} *`} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />

                        {/* OTP Type Selector */}
                        <div style={{ marginTop: 12 }}>
                            <label style={{ fontSize: 12, color: 'gray' }}>{isZh ? 'OTP 类型' : 'OTP Type'}</label>
                            <Select
                                value={form.otpType}
                                onChange={(e) => {
                                    const newType = e.target.value;
                                    let newDigits = form.digits;
                                    // Auto-adjust digits based on OTP type
                                    if (newType === OtpType.STEAM) {
                                        newDigits = 5;
                                    } else if (newType === OtpType.YANDEX) {
                                        newDigits = 8;
                                    } else if (form.otpType === OtpType.STEAM || form.otpType === OtpType.YANDEX) {
                                        // Switching from Steam/Yandex to standard type, reset to 6
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

                        {/* Advanced Options Toggle */}
                        <div
                            onClick={() => setShowAdvanced(!showAdvanced)}
                            style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 16, cursor: 'pointer', color: '#666' }}
                        >
                            <Settings size={16} />
                            <span style={{ fontSize: 13 }}>{isZh ? '高级选项' : 'Advanced Options'}</span>
                        </div>

                        {showAdvanced && (
                            <div style={{ marginTop: 12 }}>
                                {/* Period - not for HOTP */}
                                {form.otpType !== OtpType.HOTP && (
                                    <div style={{ marginBottom: 12 }}>
                                        <label style={{ fontSize: 12, color: 'gray' }}>{t('authenticator.period')}</label>
                                        <Select value={form.period} onChange={(e) => setForm({ ...form, period: parseInt(e.target.value) })}>
                                            <option value={30}>30s</option>
                                            <option value={60}>60s</option>
                                        </Select>
                                    </div>
                                )}

                                {/* Digits - disabled for Steam (fixed 5), shown for others */}
                                <div style={{ marginBottom: 12 }}>
                                    <label style={{ fontSize: 12, color: 'gray' }}>
                                        {t('authenticator.digits')}
                                        {form.otpType === OtpType.STEAM && <span style={{ marginLeft: 8, color: '#888' }}>({isZh ? 'Steam固定5位' : 'Steam uses 5'})</span>}
                                    </label>
                                    <Select
                                        value={form.digits}
                                        onChange={(e) => setForm({ ...form, digits: parseInt(e.target.value) })}
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
                            <Button onClick={handleSave} disabled={!form.title || !form.secret}>{t('common.save')}</Button>
                        </ButtonRow>
                    </ModalContent>
                </Modal>
            )}
        </Container>
    );
};
