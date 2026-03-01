import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const Container = styled.div<{ $manager: boolean }>`
  padding: 16px;
  padding-bottom: 100px;

  ${({ $manager }) => $manager && `
    max-width: 980px;
    margin: 0 auto;
    padding: 22px 26px 120px;
  `}
`;

const TopBar = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
`;

const SearchWrap = styled.div`
  position: relative;
  flex: 1;
  min-width: 220px;

  svg {
    position: absolute;
    right: 14px;
    top: 50%;
    transform: translateY(-50%);
    color: #8fa9d8;
    width: 18px;
    height: 18px;
    pointer-events: none;
  }
`;

const SearchInput = styled.input`
  width: 100%;
  min-height: 42px;
  border-radius: 12px;
  border: 1px solid rgba(120, 145, 203, 0.35);
  background: rgba(17, 27, 50, 0.8);
  color: #e8efff;
  padding: 0 38px 0 12px;
  font-size: 14px;
  outline: none;

  &::placeholder {
    color: #8fa9d8;
  }
`;

const Badge = styled.div`
  min-height: 42px;
  border-radius: 12px;
  border: 1px solid rgba(120, 145, 203, 0.35);
  background: rgba(17, 27, 50, 0.8);
  color: #b8caef;
  font-size: 12px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
`;

const Board = styled.section`
  border: 1px solid rgba(115, 145, 201, 0.26);
  border-radius: 16px;
  background: rgba(9, 17, 34, 0.74);
  min-height: calc(100vh - 260px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
`;

const Empty = styled.div`
  text-align: center;
  max-width: 420px;

  h2 {
    margin: 0 0 10px;
    color: #e8efff;
    font-size: 26px;
  }

  p {
    margin: 0;
    color: #9db2dc;
    font-size: 14px;
    line-height: 1.5;
  }
`;

interface ManagerUtilityPageProps {
  title: string;
  description: string;
}

export const ManagerUtilityPage = ({ title, description }: ManagerUtilityPageProps) => {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const isManagerMode = useMemo(() => new URLSearchParams(window.location.search).get('manager') === '1', []);

  return (
    <Container $manager={isManagerMode}>
      <TopBar>
        <SearchWrap>
          <SearchInput
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('app.searchPlaceholder')}
          />
          <Search />
        </SearchWrap>
        <Badge>{isManagerMode ? (t('app.openManagerShort') || 'Manager') : 'Popup'}</Badge>
      </TopBar>

      <Board>
        <Empty>
          <h2>{title}</h2>
          <p>{description}</p>
        </Empty>
      </Board>
    </Container>
  );
};

