import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { RotateCcw, Search, Trash2 } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { getRecycleBinItems, permanentlyDeleteItem, restoreItem } from '../../utils/storage';
import type { SecureItem } from '../../types/models';

const Container = styled.div`padding:22px 26px 120px; max-width:980px; margin:0 auto;`;
const TopBar = styled.div`display:flex; gap:10px; align-items:center; margin-bottom:14px;`;
const SearchWrap = styled.div`position:relative; flex:1; svg{position:absolute; right:12px; top:50%; transform:translateY(-50%); color:#8fa9d8;}`;
const SearchInput = styled.input`width:100%; min-height:42px; border-radius:12px; border:1px solid rgba(120,145,203,.35); background:rgba(17,27,50,.8); color:#e8efff; padding:0 36px 0 12px; font-size:14px;`;
const Row = styled.div`display:flex; justify-content:space-between; gap:12px; align-items:center; border:1px solid rgba(115,145,201,.26); border-radius:12px; background:rgba(9,17,34,.74); padding:12px; margin-bottom:10px;`;
const Title = styled.div`font-size:14px; color:#eaf1ff;`;
const Sub = styled.div`font-size:12px; color:#9db2dc; margin-top:4px;`;
const Empty = styled.div`padding:28px; text-align:center; color:#9db2dc; border:1px dashed rgba(115,145,201,.26); border-radius:12px;`;

export const RecycleBinPage = () => {
  const { t, i18n } = useTranslation();
  const isZh = i18n.language.startsWith('zh');
  const [items, setItems] = useState<SecureItem[]>([]);
  const [search, setSearch] = useState('');
  const load = useCallback(async () => setItems(await getRecycleBinItems()), []);
  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((it) => (it.title || '').toLowerCase().includes(q));
  }, [items, search]);
  const getDaysLeft = (deletedAt?: string) => {
    if (!deletedAt) return '-';
    const expiresAt = new Date(deletedAt).getTime() + 30 * 24 * 60 * 60 * 1000;
    const diff = expiresAt - Date.now();
    return diff <= 0 ? '0d' : `${Math.ceil(diff / (24 * 60 * 60 * 1000))}d`;
  };

  return (
    <Container>
      <TopBar>
        <SearchWrap>
          <SearchInput placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
          <Search size={16} />
        </SearchWrap>
      </TopBar>
      {!filtered.length && <Empty>{t('common.noItems')}</Empty>}
      {filtered.map((it) => (
        <Row key={it.id}>
          <div>
            <Title>{it.title || (isZh ? '未命名条目' : 'Untitled Item')}</Title>
            <Sub>
              {it.deletedAt ? new Date(it.deletedAt).toLocaleString() : '-'} · {getDaysLeft(it.deletedAt)} {isZh ? '天' : 'left'}
            </Sub>
          </div>
          <TopBar>
            <Button variant="text" onClick={() => restoreItem(it.id).then(load)}><RotateCcw size={14} />{t('common.restore')}</Button>
            <Button variant="text" onClick={() => permanentlyDeleteItem(it.id).then(load)}><Trash2 size={14} />{t('common.permanentDelete')}</Button>
          </TopBar>
        </Row>
      ))}
    </Container>
  );
};
