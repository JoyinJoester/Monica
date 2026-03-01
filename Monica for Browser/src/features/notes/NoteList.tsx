import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Plus, Trash2, Star, Edit3, Search, Clock3, FileText, Copy } from 'lucide-react';
import { getNotes, saveItem, deleteItem, updateItem } from '../../utils/storage';
import type { SecureItem, NoteData } from '../../types/models';
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

const TextArea = styled.textarea`
  width: 100%;
  min-height: 200px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
  margin-top: 12px;

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
  }
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

  svg {
    width: 18px;
    height: 18px;
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

const NotePreview = styled.div`
  margin-top: 8px;
  font-size: 13px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
`;

const ManagerRowPreview = styled.div`
  margin-top: 8px;
  color: #9cb1dd;
  font-size: 12px;
  line-height: 1.35;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
`;

const trimPreview = (value: string, maxLength: number): string => {
    const normalized = value.replace(/\s+/g, ' ').trim();
    if (normalized.length <= maxLength) {
        return normalized;
    }
    return `${normalized.slice(0, maxLength)}...`;
};

export const NoteList = () => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');
    const isManagerMode = useMemo(() => new URLSearchParams(window.location.search).get('manager') === '1', []);

    const [notes, setNotes] = useState<SecureItem[]>([]);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [interactionHint, setInteractionHint] = useState('');
    const interactionHintTimerRef = useRef<number | null>(null);
    const [form, setForm] = useState({ title: '', content: '', isFavorite: false });

    const resetForm = () => {
        setForm({ title: '', content: '', isFavorite: false });
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

    const loadNotes = useCallback(async () => {
        const data = await getNotes();
        setNotes(data);
    }, []);

    useEffect(() => {
        void loadNotes();
    }, [loadNotes]);

    const filteredNotes = useMemo(() => {
        const term = search.trim().toLowerCase();
        if (!term) return notes;
        return notes.filter((item) => {
            const data = item.itemData as NoteData;
            return (item.title || '').toLowerCase().includes(term) || (data.content || '').toLowerCase().includes(term);
        });
    }, [notes, search]);

    useEffect(() => {
        if (filteredNotes.length === 0) {
            setSelectedId(null);
            return;
        }
        if (!selectedId || !filteredNotes.some((item) => item.id === selectedId)) {
            setSelectedId(filteredNotes[0].id);
        }
    }, [filteredNotes, selectedId]);

    const selectedItem = useMemo(
        () => filteredNotes.find((item) => item.id === selectedId) || null,
        [filteredNotes, selectedId]
    );

    const handleSave = async () => {
        if (!form.title.trim()) return;

        const itemData: NoteData = { content: form.content };

        if (editingId) {
            await updateItem(editingId, {
                title: form.title,
                isFavorite: form.isFavorite,
                itemData,
            });
        } else {
            await saveItem({
                itemType: ItemType.Note,
                title: form.title,
                notes: '',
                isFavorite: form.isFavorite,
                sortOrder: 0,
                itemData,
            });
        }
        resetForm();
        setShowModal(false);
        await loadNotes();
        showInteractionHint(isZh ? '已保存笔记' : 'Note saved');
    };

    const handleEdit = (item: SecureItem) => {
        const data = item.itemData as NoteData;
        setForm({
            title: item.title,
            content: data.content || '',
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
        await loadNotes();
        showInteractionHint(isZh ? '已删除' : 'Deleted');
    };

    const handleToggleFavorite = async (item: SecureItem) => {
        await updateItem(item.id, { isFavorite: !item.isFavorite });
        await loadNotes();
        showInteractionHint(!item.isFavorite ? (isZh ? '已加入收藏' : 'Added to favorites') : (isZh ? '已取消收藏' : 'Removed from favorites'));
    };

    const handleCopyContent = async () => {
        if (!selectedItem) return;
        const data = selectedItem.itemData as NoteData;
        try {
            await navigator.clipboard.writeText(data.content || '');
            showInteractionHint(isZh ? '已复制内容' : 'Content copied');
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
                            {t('common.add')} {t('notes.title')}
                        </ManagerPrimaryButton>
                    </ManagerTopBar>

                    <ManagerWorkspace>
                        <ManagerListPane>
                            <ManagerRows>
                                {notes.length === 0 ? (
                                    <ManagerEmptyState>
                                        <h3>{isZh ? '还没有安全笔记' : 'No secure notes yet'}</h3>
                                        <p>{isZh ? '点击右上角新增按钮，开始记录你的安全笔记。' : 'Use the add button to create your first secure note.'}</p>
                                    </ManagerEmptyState>
                                ) : filteredNotes.length === 0 ? (
                                    <ManagerEmptyState>
                                        <h3>{isZh ? '未找到匹配结果' : 'No matching notes'}</h3>
                                        <p>{isZh ? '试试更短的关键词，或清空搜索条件。' : 'Try a shorter keyword or clear the search filter.'}</p>
                                    </ManagerEmptyState>
                                ) : filteredNotes.map((item) => {
                                    const data = item.itemData as NoteData;
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
                                            <ManagerRowPreview>{trimPreview(data.content || (isZh ? '暂无内容' : 'No content'), 72)}</ManagerRowPreview>
                                            <ManagerMeta>
                                                <ManagerMetaItem>
                                                    <Edit3 size={13} />
                                                    <span>{isZh ? '安全笔记' : 'Secure Note'}</span>
                                                </ManagerMetaItem>
                                                <ManagerMetaItem>
                                                    <Clock3 size={13} />
                                                    <span>{new Date(item.updatedAt).toLocaleDateString(isZh ? 'zh-CN' : 'en-US')}</span>
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
                                        <ManagerDetailTitle>{selectedItem.title}</ManagerDetailTitle>
                                        <ManagerDetailLabel>{isZh ? '类型' : 'Type'}</ManagerDetailLabel>
                                        <ManagerDetailField>{isZh ? '安全笔记' : 'Secure Note'}</ManagerDetailField>
                                        <ManagerDetailLabel>{t('notes.content')}</ManagerDetailLabel>
                                        <ManagerDetailNotes>{(selectedItem.itemData as NoteData).content || (isZh ? '暂无内容' : 'No content')}</ManagerDetailNotes>
                                        <ManagerDetailLabel>{isZh ? '更新时间' : 'Updated'}</ManagerDetailLabel>
                                        <ManagerDetailField>{new Date(selectedItem.updatedAt).toLocaleString(isZh ? 'zh-CN' : 'en-US')}</ManagerDetailField>
                                    </>
                                ) : (
                                    <ManagerEmptyState>
                                        <FileText size={24} />
                                        <h3>{isZh ? '选择一条笔记' : 'Select a note'}</h3>
                                        <p>{isZh ? '从左侧选择笔记后，在这里查看详情并执行操作。' : 'Pick a note on the left to view details and actions.'}</p>
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
                                    <Edit3 size={14} />
                                    {t('common.edit')}
                                </ManagerGhostButton>
                                <ManagerGhostButton
                                    onClick={() => {
                                        void handleCopyContent();
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
                    <h2 style={{ fontSize: 18, margin: '0 0 16px 0' }}>{t('notes.title')}</h2>

                    {notes.length === 0 && <EmptyState>{t('common.noItems')}</EmptyState>}

                    {notes.map((item) => {
                        const data = item.itemData as NoteData;
                        return (
                            <Card key={item.id} onClick={() => handleEdit(item)} style={{ cursor: 'pointer' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                    <CardTitle><Edit3 size={14} style={{ marginRight: 8, verticalAlign: 'middle' }} />{item.title}</CardTitle>
                                    {item.isFavorite && <Star size={16} fill="#FFC107" color="#FFC107" />}
                                </div>
                                <NotePreview>{data.content}</NotePreview>
                                <CardActions onClick={(e) => e.stopPropagation()}>
                                    <IconButton onClick={() => { void handleDelete(item.id); }} title={t('common.delete')}>
                                        <Trash2 />
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
                            <h3 style={{ margin: 0 }}>{editingId ? t('common.edit') : t('common.add')} {t('notes.title')}</h3>
                            <FavoriteIcon
                                active={form.isFavorite}
                                onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })}
                                size={24}
                            />
                        </ModalHeader>
                        <Input
                            label={`${isZh ? '标题' : 'Title'} *`}
                            value={form.title}
                            onChange={(e) => setForm({ ...form, title: e.target.value })}
                        />
                        <TextArea
                            placeholder={t('notes.content')}
                            value={form.content}
                            onChange={(e) => setForm({ ...form, content: e.target.value })}
                        />
                        <ButtonRow>
                            <Button variant="text" onClick={() => { setShowModal(false); resetForm(); }}>{t('common.cancel')}</Button>
                            <Button onClick={() => { void handleSave(); }} disabled={!form.title}>{t('common.save')}</Button>
                        </ButtonRow>
                    </ModalContent>
                </Modal>
            )}

            <InteractionHint $show={!!interactionHint}>{interactionHint}</InteractionHint>
        </ManagerContainer>
    );
};
