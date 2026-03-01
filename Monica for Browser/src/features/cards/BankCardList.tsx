import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Plus, Trash2, CreditCard, Star, Search, Copy, Building2, UserRound, CalendarClock, PencilLine } from 'lucide-react';
import { getBankCards, saveItem, deleteItem, updateItem } from '../../utils/storage';
import type { SecureItem, BankCardData } from '../../types/models';
import { ItemType } from '../../types/models';
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
    ManagerDetailNotes,
    ManagerDetailFooter,
    ManagerGhostButton,
    ManagerDangerButton,
    ManagerEmptyState,
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

const CardActions = styled.div`
  display: flex;
  gap: 8px;
  margin-top: 12px;
`;

const IconButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
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

const maskCard = (cardNumber: string) => {
    if (!cardNumber) return '';
    const plain = cardNumber.replace(/\s+/g, '');
    if (plain.length <= 4) return plain;
    return `**** **** **** ${plain.slice(-4)}`;
};

const formatExpiry = (month?: string, year?: string): string => {
    const m = month || '--';
    const y = year || '--';
    return `${m}/${y}`;
};

export const BankCardList = () => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');
    const isManagerMode = useMemo(() => new URLSearchParams(window.location.search).get('manager') === '1', []);

    const [cards, setCards] = useState<SecureItem[]>([]);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [interactionHint, setInteractionHint] = useState('');
    const interactionHintTimerRef = useRef<number | null>(null);
    const [form, setForm] = useState({
        title: '',
        cardNumber: '',
        cardholderName: '',
        expiryMonth: '',
        expiryYear: '',
        cvv: '',
        bankName: '',
        notes: '',
        isFavorite: false,
    });

    const resetForm = () => {
        setForm({
            title: '',
            cardNumber: '',
            cardholderName: '',
            expiryMonth: '',
            expiryYear: '',
            cvv: '',
            bankName: '',
            notes: '',
            isFavorite: false,
        });
        setEditingId(null);
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

    const loadCards = useCallback(async () => {
        const data = await getBankCards();
        setCards(data);
    }, []);

    useEffect(() => {
        void loadCards();
    }, [loadCards]);

    const filteredCards = useMemo(() => {
        const term = search.trim().toLowerCase();
        if (!term) return cards;
        return cards.filter((item) => {
            const data = item.itemData as BankCardData;
            return (
                (item.title || '').toLowerCase().includes(term) ||
                (data.cardholderName || '').toLowerCase().includes(term) ||
                (data.bankName || '').toLowerCase().includes(term) ||
                (data.cardNumber || '').toLowerCase().includes(term)
            );
        });
    }, [cards, search]);

    useEffect(() => {
        if (filteredCards.length === 0) {
            setSelectedId(null);
            return;
        }
        if (!selectedId || !filteredCards.some((item) => item.id === selectedId)) {
            setSelectedId(filteredCards[0].id);
        }
    }, [filteredCards, selectedId]);

    const selectedItem = useMemo(
        () => filteredCards.find((item) => item.id === selectedId) || null,
        [filteredCards, selectedId]
    );

    const handleSave = async () => {
        if (!form.title.trim() || !form.cardNumber.trim() || !form.cardholderName.trim()) return;

        const itemData: BankCardData = {
            cardNumber: form.cardNumber,
            cardholderName: form.cardholderName,
            expiryMonth: form.expiryMonth,
            expiryYear: form.expiryYear,
            cvv: form.cvv || undefined,
            bankName: form.bankName || undefined,
        };

        if (editingId) {
            await updateItem(editingId, {
                title: form.title,
                notes: form.notes,
                isFavorite: form.isFavorite,
                itemData,
            });
        } else {
            await saveItem({
                itemType: ItemType.BankCard,
                title: form.title,
                notes: form.notes,
                isFavorite: form.isFavorite,
                sortOrder: 0,
                itemData,
            });
        }
        resetForm();
        setShowModal(false);
        await loadCards();
        showInteractionHint(isZh ? '已保存银行卡' : 'Card saved');
    };

    const handleEdit = (item: SecureItem) => {
        const data = item.itemData as BankCardData;
        setForm({
            title: item.title,
            cardNumber: data.cardNumber || '',
            cardholderName: data.cardholderName || '',
            expiryMonth: data.expiryMonth || '',
            expiryYear: data.expiryYear || '',
            cvv: data.cvv || '',
            bankName: data.bankName || '',
            notes: item.notes || '',
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
        await loadCards();
        showInteractionHint(isZh ? '已删除' : 'Deleted');
    };

    const handleToggleFavorite = async (item: SecureItem) => {
        await updateItem(item.id, { isFavorite: !item.isFavorite });
        await loadCards();
        showInteractionHint(!item.isFavorite ? (isZh ? '已加入收藏' : 'Added to favorites') : (isZh ? '已取消收藏' : 'Removed from favorites'));
    };

    const handleCopyCardNumber = async () => {
        if (!selectedItem) return;
        const data = selectedItem.itemData as BankCardData;
        try {
            await navigator.clipboard.writeText(data.cardNumber || '');
            showInteractionHint(isZh ? '已复制卡号' : 'Card number copied');
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
                            {t('common.add')} {t('cards.title')}
                        </ManagerPrimaryButton>
                    </ManagerTopBar>

                    <ManagerWorkspace>
                        <ManagerListPane>
                            <ManagerRows>
                                {cards.length === 0 ? (
                                    <ManagerEmptyState>
                                        <h3>{isZh ? '还没有银行卡条目' : 'No cards yet'}</h3>
                                        <p>{isZh ? '添加银行卡后，这里会展示可快速复制和查看的卡片信息。' : 'Add your cards to manage and copy them quickly.'}</p>
                                    </ManagerEmptyState>
                                ) : filteredCards.length === 0 ? (
                                    <ManagerEmptyState>
                                        <h3>{isZh ? '未找到匹配结果' : 'No matching cards'}</h3>
                                        <p>{isZh ? '可按卡片标题、银行名或持卡人进行搜索。' : 'Search by title, bank name, or cardholder.'}</p>
                                    </ManagerEmptyState>
                                ) : filteredCards.map((item) => {
                                    const data = item.itemData as BankCardData;
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
                                            <ManagerMeta>
                                                <ManagerMetaItem>
                                                    <CreditCard size={13} />
                                                    <span>{maskCard(data.cardNumber || '')}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <CalendarClock size={13} />
                                                    <span>{formatExpiry(data.expiryMonth, data.expiryYear)}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <UserRound size={13} />
                                                    <span>{data.cardholderName || (isZh ? '未填写持卡人' : 'No holder')}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <Building2 size={13} />
                                                    <span>{data.bankName || (isZh ? '未填写银行' : 'No bank')}</span>
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
                                {selectedItem ? (
                                    <>
                                        {(() => {
                                            const data = selectedItem.itemData as BankCardData;
                                            return (
                                                <>
                                                    <ManagerDetailTitle>{selectedItem.title}</ManagerDetailTitle>
                                                    <ManagerDetailLabel>{t('cards.number')}</ManagerDetailLabel>
                                                    <ManagerDetailField>{data.cardNumber || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                                    <ManagerDetailLabel>{t('cards.holder')}</ManagerDetailLabel>
                                                    <ManagerDetailField>{data.cardholderName || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                                    <ManagerDetailLabel>{t('cards.bankName')}</ManagerDetailLabel>
                                                    <ManagerDetailField>{data.bankName || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                                    <ManagerDetailLabel>{isZh ? '到期时间' : 'Expiry'}</ManagerDetailLabel>
                                                    <ManagerDetailField>{formatExpiry(data.expiryMonth, data.expiryYear)}</ManagerDetailField>
                                                    <ManagerDetailLabel>{t('cards.cvv')}</ManagerDetailLabel>
                                                    <ManagerDetailField>{data.cvv || (isZh ? '未填写' : 'Not set')}</ManagerDetailField>
                                                    <ManagerDetailLabel>{isZh ? '备注' : 'Notes'}</ManagerDetailLabel>
                                                    <ManagerDetailNotes>{selectedItem.notes || (isZh ? '暂无备注' : 'No notes')}</ManagerDetailNotes>
                                                </>
                                            );
                                        })()}
                                    </>
                                ) : (
                                    <ManagerEmptyState>
                                        <CreditCard size={24} />
                                        <h3>{isZh ? '选择一张银行卡' : 'Select a card'}</h3>
                                        <p>{isZh ? '从左侧选中条目后，右侧会展示完整银行卡信息。' : 'Select a card on the left to view complete details.'}</p>
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
                                    onClick={() => {
                                        void handleCopyCardNumber();
                                    }}
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
                    <h2 style={{ fontSize: 18, margin: '0 0 16px 0' }}>{t('cards.title')}</h2>

                    {cards.length === 0 && <EmptyState>{t('common.noItems')}</EmptyState>}

                    {cards.map((item) => {
                        const data = item.itemData as BankCardData;
                        const expiry = formatExpiry(data.expiryMonth, data.expiryYear);
                        return (
                            <Card key={item.id} onClick={() => handleEdit(item)} style={{ cursor: 'pointer' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                    <div>
                                        <CardTitle><CreditCard size={14} style={{ marginRight: 8, verticalAlign: 'middle' }} />{item.title}</CardTitle>
                                        <CardSubtitle>{maskCard(data.cardNumber)}</CardSubtitle>
                                        <CardSubtitle style={{ opacity: 0.7 }}>{data.cardholderName} · {expiry}</CardSubtitle>
                                    </div>
                                    {item.isFavorite && <Star size={16} fill="#FFC107" color="#FFC107" />}
                                </div>
                                <CardActions onClick={(e) => e.stopPropagation()}>
                                    <IconButton onClick={() => { void handleDelete(item.id); }} title={t('common.delete')}>
                                        <Trash2 size={18} />
                                    </IconButton>
                                </CardActions>
                            </Card>
                        );
                    })}
                </>
            )}

            <FAB $manager={isManagerMode} onClick={() => { resetForm(); setShowModal(true); }}><Plus /></FAB>

            {showModal && (
                <Modal onClick={() => { setShowModal(false); resetForm(); }}>
                    <ModalContent onClick={(e) => e.stopPropagation()}>
                        <ModalHeader>
                            <h3 style={{ margin: 0 }}>{editingId ? t('common.edit') : t('common.add')} {t('cards.title')}</h3>
                            <FavoriteIcon
                                active={form.isFavorite}
                                onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })}
                                size={24}
                            />
                        </ModalHeader>

                        <Input label={`${isZh ? '标题' : 'Title'} *`} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
                        <Input label={`${t('cards.number')} *`} value={form.cardNumber} onChange={(e) => setForm({ ...form, cardNumber: e.target.value })} />
                        <Input label={`${t('cards.holder')} *`} value={form.cardholderName} onChange={(e) => setForm({ ...form, cardholderName: e.target.value })} />
                        <Input label={t('cards.bankName')} value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} />
                        <div style={{ display: 'flex', gap: 8 }}>
                            <Input label={t('cards.expiryMonth')} value={form.expiryMonth} onChange={(e) => setForm({ ...form, expiryMonth: e.target.value })} />
                            <Input label={t('cards.expiryYear')} value={form.expiryYear} onChange={(e) => setForm({ ...form, expiryYear: e.target.value })} />
                        </div>
                        <Input label={t('cards.cvv')} value={form.cvv} onChange={(e) => setForm({ ...form, cvv: e.target.value })} />
                        <Input label={isZh ? '备注' : 'Notes'} value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />

                        <ButtonRow>
                            <Button variant="text" onClick={() => { setShowModal(false); resetForm(); }}>{t('common.cancel')}</Button>
                            <Button onClick={() => { void handleSave(); }} disabled={!form.title || !form.cardNumber || !form.cardholderName}>{t('common.save')}</Button>
                        </ButtonRow>
                    </ModalContent>
                </Modal>
            )}

            <InteractionHint $show={!!interactionHint}>{interactionHint}</InteractionHint>
        </ManagerContainer>
    );
};
