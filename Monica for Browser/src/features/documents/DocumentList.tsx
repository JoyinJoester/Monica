import { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Plus, Trash2, CreditCard, Star } from 'lucide-react';
import { getDocuments, saveItem, deleteItem, updateItem } from '../../utils/storage';
import type { SecureItem, DocumentData } from '../../types/models';
import { ItemType, DocumentType } from '../../types/models';

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
  border-radius: 16px; padding: 24px; width: 100%; max-width: 400px;
  margin: 20px 0;
`;

const ModalHeader = styled.div`
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
`;

const Select = styled.select`
  width: 100%; padding: 16px; border-radius: 12px; margin-bottom: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface}; font-size: 16px;
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

const TypeBadge = styled.span`
  display: inline-block; padding: 4px 8px; border-radius: 4px;
  background: ${({ theme }) => theme.colors.primaryContainer};
  color: ${({ theme }) => theme.colors.onPrimaryContainer};
  font-size: 11px; font-weight: 600; margin-top: 8px;
`;



const FavoriteIcon = styled(Star) <{ active: boolean }>`
  color: ${({ active }) => active ? '#FFC107' : '#999'};
  fill: ${({ active }) => active ? '#FFC107' : 'none'};
  cursor: pointer;
`;

const DOC_TYPES = [
    { value: DocumentType.ID_CARD, labelEn: 'ID Card', labelZh: '身份证' },
    { value: DocumentType.PASSPORT, labelEn: 'Passport', labelZh: '护照' },
    { value: DocumentType.DRIVER_LICENSE, labelEn: 'Driver License', labelZh: '驾驶证' },
    { value: DocumentType.OTHER, labelEn: 'Other', labelZh: '其他' },
];

export const DocumentList = () => {
    const { t, i18n } = useTranslation();
    const isZh = i18n.language?.startsWith('zh');

    const [documents, setDocuments] = useState<SecureItem[]>([]);
    const [showModal, setShowModal] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form, setForm] = useState<{
        title: string;
        documentType: typeof DocumentType[keyof typeof DocumentType];
        documentNumber: string;
        fullName: string;
        issuedDate: string;
        expiryDate: string;
        issuedBy: string;
        notes: string;
        isFavorite: boolean;
    }>({
        title: '', documentType: DocumentType.ID_CARD, documentNumber: '',
        fullName: '', issuedDate: '', expiryDate: '', issuedBy: '', notes: '', isFavorite: false,
    });

    const resetForm = () => {
        setForm({
            title: '', documentType: DocumentType.ID_CARD, documentNumber: '',
            fullName: '', issuedDate: '', expiryDate: '', issuedBy: '', notes: '', isFavorite: false,
        });
        setEditingId(null);
    };

    const loadDocuments = useCallback(async () => {
        const data = await getDocuments();
        setDocuments(data);
    }, []);

    useEffect(() => { loadDocuments(); }, [loadDocuments]);

    const handleSave = async () => {
        if (!form.title || !form.documentNumber) return;

        const itemData: DocumentData = {
            documentType: form.documentType,
            documentNumber: form.documentNumber,
            fullName: form.fullName,
            issuedDate: form.issuedDate,
            expiryDate: form.expiryDate,
            issuedBy: form.issuedBy,
            additionalInfo: form.notes,
        };

        if (editingId) {
            await updateItem(editingId, { title: form.title, isFavorite: form.isFavorite, itemData });
        } else {
            await saveItem({
                itemType: ItemType.Document, title: form.title, notes: '',
                isFavorite: form.isFavorite, sortOrder: 0, itemData,
            });
        }
        resetForm();
        setShowModal(false);
        loadDocuments();
    };

    const handleEdit = (item: SecureItem) => {
        const data = item.itemData as DocumentData;
        setForm({
            title: item.title,
            documentType: data.documentType,
            documentNumber: data.documentNumber,
            fullName: data.fullName,
            issuedDate: data.issuedDate || '',
            expiryDate: data.expiryDate || '',
            issuedBy: data.issuedBy || '',
            notes: data.additionalInfo || '',
            isFavorite: item.isFavorite,
        });
        setEditingId(item.id);
        setShowModal(true);
    };

    const handleDelete = async (id: number) => {
        if (confirm(t('common.confirmDelete'))) {
            await deleteItem(id);
            loadDocuments();
        }
    };

    return (
        <Container>
            <h2 style={{ fontSize: 18, margin: '0 0 16px 0' }}>{t('documents.title')}</h2>

            {documents.length === 0 && <EmptyState>{t('common.noItems')}</EmptyState>}

            {documents.map((item) => {
                const data = item.itemData as DocumentData;
                const docType = DOC_TYPES.find(d => d.value === data.documentType);
                return (
                    <Card key={item.id} onClick={() => handleEdit(item)} style={{ cursor: 'pointer' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                            <div>
                                <CardTitle><CreditCard size={14} style={{ marginRight: 8, verticalAlign: 'middle' }} />{item.title}</CardTitle>
                                <CardSubtitle>{data.fullName}</CardSubtitle>
                                <TypeBadge>{isZh ? docType?.labelZh : docType?.labelEn}</TypeBadge>
                            </div>
                            {item.isFavorite && <Star size={16} fill="#FFC107" color="#FFC107" />}
                        </div>
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
                            <h3 style={{ margin: 0 }}>{editingId ? t('common.edit') : t('common.add')} {t('documents.title')}</h3>
                            <FavoriteIcon active={form.isFavorite} onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })} size={24} />
                        </ModalHeader>

                        <Input label={`${isZh ? '标题' : 'Title'} *`} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />

                        <label style={{ fontSize: 12, color: 'gray', marginTop: 8, display: 'block' }}>{t('documents.type')}</label>
                        <Select value={form.documentType} onChange={(e) => setForm({ ...form, documentType: e.target.value as typeof DocumentType[keyof typeof DocumentType] })}>
                            {DOC_TYPES.map(d => <option key={d.value} value={d.value}>{isZh ? d.labelZh : d.labelEn}</option>)}
                        </Select>

                        <Input label={t('documents.fullName')} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
                        <Input label={`${t('documents.number')} *`} value={form.documentNumber} onChange={(e) => setForm({ ...form, documentNumber: e.target.value })} />

                        <Input label={isZh ? '签发日期' : 'Issued Date'} type="date" value={form.issuedDate} onChange={(e) => setForm({ ...form, issuedDate: e.target.value })} />
                        <Input label={isZh ? '有效期至' : 'Expiry Date'} type="date" value={form.expiryDate} onChange={(e) => setForm({ ...form, expiryDate: e.target.value })} />

                        <Input label={isZh ? '签发机关' : 'Issued By'} value={form.issuedBy} onChange={(e) => setForm({ ...form, issuedBy: e.target.value })} />
                        <Input label={isZh ? '备注' : 'Notes'} value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />

                        <ButtonRow>
                            <Button variant="text" onClick={() => { setShowModal(false); resetForm(); }}>{t('common.cancel')}</Button>
                            <Button onClick={handleSave} disabled={!form.title || !form.documentNumber}>{t('common.save')}</Button>
                        </ButtonRow>
                    </ModalContent>
                </Modal>
            )}
        </Container>
    );
};
