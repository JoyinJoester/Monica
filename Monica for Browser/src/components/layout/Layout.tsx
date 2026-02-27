import React from 'react';
import styled, { css } from 'styled-components';
import { NavLink } from 'react-router-dom';
import { KeyRound, StickyNote, Settings as SettingsIcon, CreditCard, ShieldCheck, ExternalLink } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const LayoutContainer = styled.div<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background: ${({ theme }) => theme.colors.background};

  @media (min-width: 960px) {
    flex-direction: ${({ $manager }) => ($manager ? 'row' : 'column')};
  }
`;

const MainArea = styled.section<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;

  @media (min-width: 960px) {
    ${({ $manager }) => $manager && css`
      max-width: calc(100% - 100px);
    `}
  }
`;

const Header = styled.header<{ $manager: boolean }>`
  padding: 16px 20px;
  background-color: ${({ theme }) => `${theme.colors.surface}E8`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(8px);

  @media (min-width: 960px) {
    ${({ $manager }) => $manager && css`
      padding: 20px 28px;
    `}
  }
`;

const Title = styled.h1`
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: ${({ theme }) => theme.colors.primary};
  letter-spacing: -0.5px;
`;

const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const OpenManagerButton = styled.button`
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  border-radius: 18px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: transform 0.18s ease, background-color 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-1px);
    background: ${({ theme }) => theme.colors.primaryContainer};
    border-color: ${({ theme }) => theme.colors.primary};
  }

  &:active {
    transform: translateY(0);
  }

  svg {
    width: 14px;
    height: 14px;
  }
`;

const Content = styled.main<{ $manager: boolean }>`
  flex: 1;
  overflow-y: auto;
  position: relative;
  padding-bottom: 4px;

  ${({ $manager }) => $manager && css`
    animation: content-fade 220ms ease-out;

    @keyframes content-fade {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
  `}
`;

const Navigation = styled.nav<{ $manager: boolean }>`
  padding: 8px 8px;
  background-color: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  display: flex;
  justify-content: space-around;
  align-items: center;
  gap: 4px;
  position: relative;
  z-index: 15;

  @media (min-width: 960px) {
    ${({ $manager }) => $manager && css`
      width: 100px;
      border-top: none;
      border-right: 1px solid ${({ theme }) => theme.colors.outlineVariant};
      justify-content: flex-start;
      align-items: stretch;
      flex-direction: column;
      gap: 8px;
      padding: 20px 10px;
    `}
  }
`;

const NavItem = styled(NavLink)<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  text-decoration: none;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  font-size: 10px;
  font-weight: 500;
  padding: 6px 10px;
  border-radius: 12px;
  transition: all 0.2s ease;
  min-width: 0;
  flex: 1;
  cursor: pointer;

  &.active {
    color: ${({ theme }) => theme.colors.primary};
    background-color: ${({ theme }) => theme.colors.primaryContainer};
  }

  svg {
    width: 22px;
    height: 22px;
    margin-bottom: 2px;
  }

  @media (min-width: 960px) {
    ${({ $manager }) => $manager && css`
      flex: unset;
      width: 100%;
      padding: 10px 8px;
      border-radius: 14px;
      font-size: 11px;

      svg {
        margin-bottom: 4px;
      }
    `}
  }
`;

interface LayoutProps {
  children: React.ReactNode;
  isManagerMode?: boolean;
}

export const Layout = ({ children, isManagerMode = false }: LayoutProps) => {
  const { t } = useTranslation();

  const handleOpenManagerTab = () => {
    const managerUrl = chrome.runtime.getURL('index.html?manager=1#/');
    chrome.tabs.create({ url: managerUrl });
  };

  return (
    <LayoutContainer $manager={isManagerMode}>
      <MainArea $manager={isManagerMode}>
        <Header $manager={isManagerMode}>
          <Title>{t('app.title')}</Title>
          <HeaderActions>
            {!isManagerMode && (
              <OpenManagerButton type="button" onClick={handleOpenManagerTab} title={t('app.openManager')}>
                <ExternalLink />
                {t('app.openManagerShort')}
              </OpenManagerButton>
            )}
          </HeaderActions>
        </Header>
        <Content $manager={isManagerMode}>
          {children}
        </Content>
      </MainArea>
      <Navigation $manager={isManagerMode}>
        <NavItem $manager={isManagerMode} to="/">
          <KeyRound />
          {t('nav.passwords')}
        </NavItem>
        <NavItem $manager={isManagerMode} to="/notes">
          <StickyNote />
          {t('nav.notes')}
        </NavItem>
        <NavItem $manager={isManagerMode} to="/documents">
          <CreditCard />
          {t('nav.documents')}
        </NavItem>
        <NavItem $manager={isManagerMode} to="/authenticator">
          <ShieldCheck />
          {t('nav.authenticator')}
        </NavItem>
        <NavItem $manager={isManagerMode} to="/settings">
          <SettingsIcon />
          {t('nav.settings')}
        </NavItem>
      </Navigation>
    </LayoutContainer>
  );
};
