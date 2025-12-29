import { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Plus, Trash2, Star, Edit3 } from 'lucide-react';
import { getNotes, saveItem, deleteItem, updateItem } from '../../utils/storage';
import type { SecureItem, NoteData } from '../../types/models';
import { ItemType } from '../../types/models';

const Container = styled.div`
  padding: 16px;
  padding-bottom: 100px;
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
  border-radius: 16px; padding: 24px; width: 100%; max-width: 400px;
  margin: 20px 0;
`;

const ModalHeader = styled.div`
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
`;

const TextArea = styled.textarea`
  width: 100%; min-height: 200px; padding: 16px; border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-family: inherit; font-size: 14px; resize: vertical; margin-top: 12px;
  &:focus { outline: none; border-color: ${({ theme }) => theme.colors.primary}; }
`;

const CardActions = styled.div`
  display: flex; gap: 8px; margin-top: 12px;
`;

const IconButton = styled.button`
  background: none; border: none; cursor: pointer; padding: 8px; border-radius: 8px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  &:hover { background-color: ${({ theme }) => theme.colors.surfaceVariant}; }
  svg { width: 18px; height: 18px; }
`;

const ButtonRow = styled.div`
  display: flex; gap: 12px; margin-top: 16px;
`;

const EmptyState = styled.div`
  text-align: center; padding: 48px 0; color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const FavoriteIcon = styled(Star) <{ active: boolean }>`
  color: ${({ active }) => active ? '#FFC107' : '#999'};
  fill: ${({ active }) => active ? '#FFC107' : 'none'};
  cursor: pointer;
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

export const NoteList = () => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');

    const [notes, setNotes] = useState<SecureItem[]>([]);
    const [showModal, setShowModal] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState({ title: '', content: '', isFavorite: false });

    const resetForm = () => {
        setForm({ title: '', content: '', isFavorite: false });
        setEditingId(null);
    };

    const loadNotes = async () => {
        const data = await getNotes();
        setNotes(data);
    };

    useEffect(() => { loadNotes(); }, []);

    const handleSave = async () => {
        if (!form.title) return;

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
        loadNotes();
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
        if (confirm(t('common.confirmDelete'))) {
            await deleteItem(id);
            loadNotes();
        }
    };

    return (
        <Container>
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
                            <IconButton onClick={() => handleDelete(item.id)} title={t('common.delete')}>
                                <Trash2 />
                            </IconButton>
                        </CardActions>
                    </Card>
                );
            })}

            <FAB onClick={() => { resetForm(); setShowModal(true); }}><Plus /></FAB>

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
                            <Button onClick={handleSave} disabled={!form.title}>{t('common.save')}</Button>
                        </ButtonRow>
                    </ModalContent>
                </Modal>
            )}
        </Container>
    );
};
