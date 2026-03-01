import { useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { History, Search } from 'lucide-react';
import { getTimelineEntries, type TimelineEntry } from '../../utils/storage';

const Container = styled.div`padding:22px 26px 120px; max-width:980px; margin:0 auto;`;
const TopBar = styled.div`display:flex; gap:10px; align-items:center; margin-bottom:14px;`;
const SearchWrap = styled.div`position:relative; flex:1; svg{position:absolute; right:12px; top:50%; transform:translateY(-50%); color:#8fa9d8;}`;
const SearchInput = styled.input`width:100%; min-height:42px; border-radius:12px; border:1px solid rgba(120,145,203,.35); background:rgba(17,27,50,.8); color:#e8efff; padding:0 36px 0 12px; font-size:14px;`;
const Select = styled.select`min-height:42px; border-radius:12px; border:1px solid rgba(120,145,203,.35); background:rgba(17,27,50,.8); color:#e8efff; padding:0 12px;`;
const Row = styled.div`display:flex; gap:12px; align-items:flex-start; border:1px solid rgba(115,145,201,.26); border-radius:12px; background:rgba(9,17,34,.74); padding:12px; margin-bottom:10px;`;
const Dot = styled.div`width:10px; height:10px; border-radius:50%; background:#6fa3ff; margin-top:6px;`;
const Title = styled.div`font-size:14px; color:#eaf1ff;`;
const Sub = styled.div`font-size:12px; color:#9db2dc; margin-top:4px;`;

export const TimelinePage = () => {
  const { t, i18n } = useTranslation();
  const [items, setItems] = useState<TimelineEntry[]>([]);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<'all' | TimelineEntry['action']>('all');
  const isZh = i18n.language.startsWith('zh');

  useEffect(() => { getTimelineEntries().then(setItems); }, []);

  const filtered = useMemo(() => items.filter((it) => {
    const q = search.trim().toLowerCase();
    const title = (it.title || '').toLowerCase();
    const action = String(it.action || '').toLowerCase();
    const actionMatch = filter === 'all' ? true : it.action === filter;
    return actionMatch && (title.includes(q) || action.includes(q));
  }), [items, search, filter]);

  const formatTimestamp = (value?: string) => {
    if (!value) return '-';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return '-';
    return d.toLocaleString();
  };

  const formatAction = (value?: string) => {
    const action = String(value || '').trim();
    if (!action) return isZh ? '未知动作' : 'UNKNOWN';
    return action.toUpperCase();
  };

  return (
    <Container>
      <TopBar>
        <SearchWrap>
          <SearchInput placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
          <Search size={16} />
        </SearchWrap>
        <Select value={filter} onChange={(e) => setFilter(e.target.value as 'all' | TimelineEntry['action'])}>
          <option value="all">All</option>
          <option value="created">Created</option>
          <option value="updated">Updated</option>
          <option value="deleted">Deleted</option>
          <option value="restored">Restored</option>
          <option value="purged">Purged</option>
        </Select>
      </TopBar>
      {filtered.map((it) => (
        <Row key={it.id}>
          <Dot />
          <div>
            <Title>
              <History size={14} style={{ marginRight: 8, verticalAlign: 'middle' }} />
              {it.title || (isZh ? '未命名条目' : 'Untitled Item')}
            </Title>
            <Sub>{formatAction(it.action)} · {formatTimestamp(it.timestamp)}</Sub>
          </div>
        </Row>
      ))}
    </Container>
  );
};
