import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Copy, RefreshCw, Search } from 'lucide-react';
import { Button } from '../../components/common/Button';

const Container = styled.div`padding:22px 26px 120px; max-width:980px; margin:0 auto;`;
const TopBar = styled.div`display:flex; gap:10px; align-items:center; margin-bottom:14px;`;
const SearchWrap = styled.div`position:relative; flex:1; svg{position:absolute; right:12px; top:50%; transform:translateY(-50%); color:#8fa9d8;}`;
const SearchInput = styled.input`width:100%; min-height:42px; border-radius:12px; border:1px solid rgba(120,145,203,.35); background:rgba(17,27,50,.8); color:#e8efff; padding:0 36px 0 12px; font-size:14px;`;
const Board = styled.div`border:1px solid rgba(115,145,201,.26); border-radius:16px; background:rgba(9,17,34,.74); padding:20px;`;
const Output = styled.div`font-family:Consolas,monospace; font-size:18px; color:#eef3ff; padding:12px; background:rgba(0,0,0,.24); border-radius:10px; word-break:break-all; margin:12px 0;`;
const Row = styled.div`display:flex; gap:10px; align-items:center; flex-wrap:wrap;`;

const generatePassword = (length: number, useSymbols: boolean) => {
  let chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  if (useSymbols) chars += '!@#$%^&*()_+-=';
  let out = '';
  for (let i = 0; i < length; i++) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
};

export const GeneratorPage = () => {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [length, setLength] = useState(16);
  const [useSymbols, setUseSymbols] = useState(true);
  const [token, setToken] = useState(() => generatePassword(16, true));

  const username = useMemo(() => `user_${token.slice(0, 8).toLowerCase()}`, [token]);

  return (
    <Container>
      <TopBar>
        <SearchWrap>
          <SearchInput placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
          <Search size={16} />
        </SearchWrap>
      </TopBar>

      <Board>
        <Row>
          <label>Length</label>
          <input type="range" min={8} max={64} value={length} onChange={(e) => setLength(Number(e.target.value))} />
          <span>{length}</span>
          <label><input type="checkbox" checked={useSymbols} onChange={(e) => setUseSymbols(e.target.checked)} />Symbols</label>
          <Button onClick={() => setToken(generatePassword(length, useSymbols))}><RefreshCw size={14} />Regenerate</Button>
        </Row>
        <Output>{token}</Output>
        <Row>
          <Button onClick={() => navigator.clipboard.writeText(token)}><Copy size={14} />Copy Password</Button>
          <Button variant="text" onClick={() => navigator.clipboard.writeText(username)}><Copy size={14} />Copy Username ({username})</Button>
        </Row>
      </Board>
    </Container>
  );
};
