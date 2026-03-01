import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Link2, Plus, Search, Trash2 } from 'lucide-react';
import { Card, CardSubtitle, CardTitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { deleteItem, getSendItems, saveItem, updateItem } from '../../utils/storage';
import { ItemType, type SecureItem, type SendData } from '../../types/models';

const Container = styled.div`padding:22px 26px 120px; max-width:980px; margin:0 auto;`;
const TopBar = styled.div`display:flex; gap:10px; align-items:center; margin-bottom:14px;`;
const SearchWrap = styled.div`position:relative; flex:1; svg{position:absolute; right:12px; top:50%; transform:translateY(-50%); color:#8fa9d8;}`;
const SearchInput = styled.input`width:100%; min-height:42px; border-radius:12px; border:1px solid rgba(120,145,203,.35); background:rgba(17,27,50,.8); color:#e8efff; padding:0 36px 0 12px; font-size:14px;`;
const Select = styled.select`min-height:42px; border-radius:12px; border:1px solid ${({ theme }) => theme.colors.outlineVariant}; padding:0 12px;`;
const Modal = styled.div`position:fixed; inset:0; background:rgba(0,0,0,.45); display:flex; align-items:center; justify-content:center; z-index:100;`;
const ModalInner = styled.div`width:min(560px,calc(100vw - 32px)); background:${({ theme }) => theme.colors.surface}; border-radius:14px; padding:18px;`;
const Status = styled.span<{ $type: 'ok' | 'warn' | 'danger' }>`
  display:inline-flex; align-items:center; padding:2px 8px; border-radius:999px; font-size:11px; font-weight:600;
  color:${({ $type }) => ($type === 'ok' ? '#87d7a8' : $type === 'warn' ? '#f4ce76' : '#ff9c9c')};
  background:${({ $type }) => ($type === 'ok' ? 'rgba(46,125,50,.24)' : $type === 'warn' ? 'rgba(179,127,0,.24)' : 'rgba(168,47,47,.24)')};
`;

const toLocalDatetime = (iso?: string) => {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (v: number) => String(v).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export const SendPage = () => {
  const { t } = useTranslation();
  const [items, setItems] = useState<SecureItem[]>([]);
  const [search, setSearch] = useState('');
  const [show, setShow] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [expirationLocal, setExpirationLocal] = useState('');
  const [form, setForm] = useState<{ title: string; notes: string } & SendData>({
    title: '', notes: '', sendType: 'text', content: '', expirationAt: '', maxAccessCount: 0, accessCount: 0
  });

  const load = useCallback(async () => setItems(await getSendItems()), []);
  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => items.filter((item) => {
    const data = item.itemData as SendData;
    const q = search.trim().toLowerCase();
    return (item.title || '').toLowerCase().includes(q) || (data.content || '').toLowerCase().includes(q);
  }), [items, search]);

  const save = async () => {
    if (!form.title || !form.content) return;
    const itemData: SendData = {
      sendType: form.sendType,
      content: form.content,
      expirationAt: expirationLocal ? new Date(expirationLocal).toISOString() : undefined,
      maxAccessCount: form.maxAccessCount || undefined,
      accessCount: form.accessCount || 0
    };
    if (editing) {
      await updateItem(editing, { title: form.title, notes: form.notes, itemData });
    } else {
      await saveItem({ itemType: ItemType.Send, title: form.title, notes: form.notes, isFavorite: false, sortOrder: 0, itemData });
    }
    setShow(false);
    setEditing(null);
    setExpirationLocal('');
    setForm({ title: '', notes: '', sendType: 'text', content: '', expirationAt: '', maxAccessCount: 0, accessCount: 0 });
    load();
  };

  return (
    <Container>
      <TopBar>
        <SearchWrap>
          <SearchInput placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
          <Search size={16} />
        </SearchWrap>
        <Button onClick={() => { setShow(true); setEditing(null); }}><Plus size={16} />{t('common.add')}</Button>
      </TopBar>

      {filtered.map((item) => {
        const data = item.itemData as SendData;
        const isExpired = !!data.expirationAt && new Date(data.expirationAt).getTime() < Date.now();
        const isUsedUp = !!data.maxAccessCount && (data.accessCount || 0) >= data.maxAccessCount;
        const status: 'ok' | 'warn' | 'danger' = isExpired ? 'danger' : isUsedUp ? 'warn' : 'ok';
        const statusText = isExpired ? 'Expired' : isUsedUp ? 'Limit Reached' : 'Active';
        return (
          <Card
            key={item.id}
            onClick={() => {
              setEditing(item.id);
              setForm({ title: item.title, notes: item.notes, ...data });
              setExpirationLocal(toLocalDatetime(data.expirationAt));
              setShow(true);
            }}
            style={{ cursor: 'pointer' }}
          >
            <CardTitle><Link2 size={14} style={{ marginRight: 8 }} />{item.title}</CardTitle>
            <CardSubtitle>
              {data.sendType.toUpperCase()} · {data.expirationAt ? new Date(data.expirationAt).toLocaleString() : 'No expiry'} · <Status $type={status}>{statusText}</Status>
            </CardSubtitle>
            <CardSubtitle style={{ opacity: .7 }}>{data.content}</CardSubtitle>
            <Button variant="text" onClick={(e) => { e.stopPropagation(); deleteItem(item.id).then(load); }}><Trash2 size={14} /></Button>
          </Card>
        );
      })}

      {show && (
        <Modal onClick={() => setShow(false)}>
          <ModalInner onClick={(e) => e.stopPropagation()}>
            <Input label="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <TopBar>
              <Select value={form.sendType} onChange={(e) => setForm({ ...form, sendType: e.target.value as 'text' | 'link' })}>
                <option value="text">Text</option>
                <option value="link">Link</option>
              </Select>
              <Input label="Max Access" type="number" value={String(form.maxAccessCount || '')} onChange={(e) => setForm({ ...form, maxAccessCount: Number(e.target.value) || 0 })} />
            </TopBar>
            <Input label="Content" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
            <Input label="Expire At" type="datetime-local" value={expirationLocal} onChange={(e) => setExpirationLocal(e.target.value)} />
            <Input label="Notes" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
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
