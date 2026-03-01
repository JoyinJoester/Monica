import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { KeyRound, Plus, RefreshCw, Search, Trash2 } from 'lucide-react';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { deleteItem, getPasskeys, saveItem, updateItem } from '../../utils/storage';
import { ItemType, type PasskeyData, type SecureItem } from '../../types/models';

const Container = styled.div`
  padding: 22px 26px 120px;
  max-width: 980px;
  margin: 0 auto;
`;
const TopBar = styled.div`display:flex; gap:10px; align-items:center; margin-bottom:14px;`;
const SearchWrap = styled.div`position:relative; flex:1; svg{position:absolute; right:12px; top:50%; transform:translateY(-50%); color:#8fa9d8;}`;
const SearchInput = styled.input`
  width:100%; min-height:42px; border-radius:12px; border:1px solid rgba(120,145,203,.35);
  background:rgba(17,27,50,.8); color:#e8efff; padding:0 36px 0 12px; font-size:14px;
`;
const Primary = styled(Button)`min-height:42px;`;
const Modal = styled.div`position:fixed; inset:0; background:rgba(0,0,0,.45); display:flex; align-items:center; justify-content:center; z-index:100;`;
const ModalInner = styled.div`width:min(520px,calc(100vw - 32px)); background:${({ theme }) => theme.colors.surface}; border-radius:14px; padding:18px;`;
const Hint = styled.div`font-size:12px; color:${({ theme }) => theme.colors.onSurfaceVariant}; margin:6px 0 10px;`;
const ErrorText = styled.div`font-size:12px; color:#ef6b6b; margin-top:6px;`;

const isRpIdValid = (value: string) => /^[a-z0-9.-]+\.[a-z]{2,}$/i.test(value.trim());

const generateCredentialId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

export const PasskeyPage = () => {
  const { t } = useTranslation();
  const [items, setItems] = useState<SecureItem[]>([]);
  const [search, setSearch] = useState('');
  const [show, setShow] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [form, setForm] = useState<PasskeyData & { title: string; notes: string }>({
    title: '', username: '', rpId: '', credentialId: '', userDisplayName: '', notes: ''
  });

  const load = useCallback(async () => setItems(await getPasskeys()), []);
  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => items.filter((item) => {
    const data = item.itemData as PasskeyData;
    const q = search.trim().toLowerCase();
    return (item.title || '').toLowerCase().includes(q)
      || (data.username || '').toLowerCase().includes(q)
      || (data.rpId || '').toLowerCase().includes(q);
  }), [items, search]);

  const save = async () => {
    if (!form.title || !form.username || !form.rpId) {
      setError('Title / Username / RP ID required');
      return;
    }
    if (!isRpIdValid(form.rpId)) {
      setError('RP ID format invalid (example.com)');
      return;
    }
    const itemData: PasskeyData = {
      username: form.username,
      rpId: form.rpId,
      credentialId: form.credentialId || generateCredentialId(),
      userDisplayName: form.userDisplayName || undefined,
    };
    if (editing) {
      await updateItem(editing, { title: form.title, notes: form.notes, itemData });
    } else {
      await saveItem({ itemType: ItemType.Passkey, title: form.title, notes: form.notes, isFavorite: false, sortOrder: 0, itemData });
    }
    setShow(false);
    setEditing(null);
    setError('');
    setForm({ title: '', username: '', rpId: '', credentialId: '', userDisplayName: '', notes: '' });
    load();
  };

  const openEdit = (item: SecureItem) => {
    const data = item.itemData as PasskeyData;
    setEditing(item.id);
    setForm({
      title: item.title,
      username: data.username || '',
      rpId: data.rpId || '',
      credentialId: data.credentialId || '',
      userDisplayName: data.userDisplayName || '',
      notes: item.notes || '',
    });
    setShow(true);
  };

  return (
    <Container>
      <TopBar>
        <SearchWrap>
          <SearchInput placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
          <Search size={16} />
        </SearchWrap>
        <Primary onClick={() => { setShow(true); setEditing(null); }}>{<><Plus size={16} />{t('common.add')}</>}</Primary>
      </TopBar>

      {filtered.map((item) => {
        const data = item.itemData as PasskeyData;
        return (
          <Card key={item.id} onClick={() => openEdit(item)} style={{ cursor: 'pointer' }}>
            <CardTitle><KeyRound size={14} style={{ marginRight: 8 }} />{item.title}</CardTitle>
            <CardSubtitle>{data.username} · {data.rpId}</CardSubtitle>
            <Button variant="text" onClick={(e) => { e.stopPropagation(); deleteItem(item.id).then(load); }}>
              <Trash2 size={14} />
            </Button>
          </Card>
        );
      })}

      {show && (
        <Modal onClick={() => setShow(false)}>
          <ModalInner onClick={(e) => e.stopPropagation()}>
            <Input label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <Input label="Username" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
            <Input label="RP ID" value={form.rpId} onChange={(e) => setForm({ ...form, rpId: e.target.value })} />
            <TopBar>
              <Input label="Credential ID" value={form.credentialId} onChange={(e) => setForm({ ...form, credentialId: e.target.value })} />
              <Button variant="text" onClick={() => setForm({ ...form, credentialId: generateCredentialId() })}>
                <RefreshCw size={14} />
              </Button>
            </TopBar>
            <Hint>RP ID example: `github.com`, `accounts.google.com`</Hint>
            <Input label="Display Name" value={form.userDisplayName || ''} onChange={(e) => setForm({ ...form, userDisplayName: e.target.value })} />
            <Input label="Notes" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
            {error && <ErrorText>{error}</ErrorText>}
            <TopBar>
              <Button variant="text" onClick={() => setShow(false)}>{t('common.cancel')}</Button>
              <Button onClick={save}>{t('common.save')}</Button>
            </TopBar>
          </ModalInner>
        </Modal>
      )}
    </Container>
  );
};
