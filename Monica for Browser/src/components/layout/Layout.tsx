import React from 'react';
import styled, { css } from 'styled-components';
import { NavLink } from 'react-router-dom';
import {
  KeyRound,
  StickyNote,
  Settings as SettingsIcon,
  CreditCard,
  IdCard,
  ShieldCheck,
  ExternalLink,
  Infinity,
  Key,
  Send,
  Shield,
  WandSparkles,
  History,
  Trash2
} from 'lucide-react';
import { useTranslation } from 'react-i18next';

// ─── Layout Container ────────────────────────────────────────────────────────

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

// ─── Sidebar Nav (manager mode, desktop) ─────────────────────────────────────

const Navigation = styled.nav<{ $manager: boolean }>`
  padding: 8px;
  background-color: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.outline};
  display: flex;
  justify-content: space-around;
  align-items: center;
  gap: 4px;
  position: relative;
  z-index: 15;

  @media (min-width: 960px) {
    ${({ $manager }) =>
      $manager &&
      css`
        width: 80px;
        min-width: 80px;
        background: ${({ theme }: any) => theme.colors.surface};
        border-top: none;
        border-right: 1px solid ${({ theme }: any) => theme.colors.outline};
        justify-content: flex-start;
        align-items: stretch;
        flex-direction: column;
        gap: 2px;
        padding: 12px 8px;
        overflow-y: auto;
      `}
  }
`;

const SidebarLogo = styled.div`
  display: none;

  @media (min-width: 960px) {
    display: flex;
    justify-content: center;
    margin-bottom: 12px;
    padding-bottom: 12px;
    border-bottom: 1px solid ${({ theme }) => theme.colors.outline};
  }
`;

const LogoMark = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: ${({ theme }) => theme.colors.primaryContainer};
  border: 1px solid ${({ theme }) => theme.colors.outline};
  color: ${({ theme }) => theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;

  svg {
    width: 20px;
    height: 20px;
  }
`;

// ─── Nav Items ────────────────────────────────────────────────────────────────

const NavItem = styled(NavLink)<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  text-decoration: none;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  font-size: 10px;
  font-weight: 500;
  padding: 6px 8px;
  border-radius: 10px;
  transition:
    color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    background-color 140ms cubic-bezier(0.2, 0.6, 0.2, 1);
  min-width: 0;
  flex: 1;
  cursor: pointer;

  &.active {
    color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme }) => theme.colors.primaryContainer};
  }

  &:hover:not(.active) {
    color: ${({ theme }) => theme.colors.onSurface};
    background: ${({ theme }) => theme.colors.surfaceVariant};
  }

  &:active {
    transform: scale(0.94);
  }

  svg {
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
  }

  @media (min-width: 960px) {
    ${({ $manager }) =>
      $manager &&
      css`
        flex: unset;
        width: 100%;
        padding: 9px 6px;
        border-radius: 10px;
        font-size: 10px;

        svg {
          margin-bottom: 3px;
        }
      `}
  }
`;

// ─── Main Area ────────────────────────────────────────────────────────────────

const MainArea = styled.section<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
`;

// ─── Header ──────────────────────────────────────────────────────────────────

const Header = styled.header`
  padding: 14px 16px;
  background-color: ${({ theme }) => `${theme.colors.surface}F0`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.outline};
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(10px);
`;

const Title = styled.h1`
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: ${({ theme }) => theme.colors.primary};
  letter-spacing: -0.3px;
  font-family: 'Source Sans 3', 'Noto Sans SC', sans-serif;
`;

const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const OpenManagerButton = styled.button`
  border: 1px solid ${({ theme }) => theme.colors.outline};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  transition:
    background-color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    border-color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    transform 140ms cubic-bezier(0.2, 0.6, 0.2, 1);

  &:hover {
    transform: translateY(-1px);
    background: ${({ theme }) => theme.colors.primaryContainer};
    border-color: ${({ theme }) => theme.colors.primary};
    color: ${({ theme }) => theme.colors.onPrimaryContainer};
  }

  &:active {
    transform: scale(0.96);
  }

  svg {
    width: 13px;
    height: 13px;
  }
`;

// ─── Content ──────────────────────────────────────────────────────────────────

const Content = styled.main<{ $manager: boolean }>`
  flex: 1;
  overflow-y: auto;
  position: relative;
  padding-bottom: 4px;

  ${({ $manager }) =>
    $manager &&
    css`
      animation: content-fade 220ms cubic-bezier(0.16, 1, 0.3, 1);

      @keyframes content-fade {
        from {
          opacity: 0;
          transform: translateY(6px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
    `}
`;

// ─── Component ───────────────────────────────────────────────────────────────

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
      {isManagerMode && (
        <Navigation $manager={isManagerMode}>
          <SidebarLogo>
            <LogoMark>
              <Infinity />
            </LogoMark>
          </SidebarLogo>
          <NavItem $manager={isManagerMode} to="/" title={t('nav.passwords')}>
            <KeyRound />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/notes" title={t('nav.notes')}>
            <StickyNote />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/documents" title={t('nav.documents')}>
            <IdCard />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/cards" title={t('nav.cards')}>
            <CreditCard />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/authenticator" title={t('nav.authenticator')}>
            <ShieldCheck />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/passkeys" title={t('nav.passkeys')}>
            <Key />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/send" title={t('nav.send')}>
            <Send />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/bitwarden" title={t('nav.bitwarden')}>
            <Shield />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/generator" title={t('nav.generator')}>
            <WandSparkles />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/timeline" title={t('nav.timeline')}>
            <History />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/recycle-bin" title={t('nav.recycleBin')}>
            <Trash2 />
          </NavItem>
          <NavItem $manager={isManagerMode} to="/settings" title={t('nav.settings')}>
            <SettingsIcon />
          </NavItem>
        </Navigation>
      )}
      <MainArea $manager={isManagerMode}>
        <Header>
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
      {!isManagerMode && (
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
      )}
    </LayoutContainer>
  );
};
