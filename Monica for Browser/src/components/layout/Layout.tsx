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

const LayoutContainer = styled.div<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background: ${({ theme }) => theme.colors.background};

  @media (min-width: 960px) {
    flex-direction: ${({ $manager }) => ($manager ? 'row' : 'column')};
    ${({ $manager }) => $manager && css`
      background: radial-gradient(circle at 20% 0%, #1a3157 0%, #121f3a 38%, #0c1528 100%);
    `}
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
      min-height: 72px;
      padding: 20px 28px;
      background: rgba(13, 24, 45, 0.88);
      border-bottom: 1px solid rgba(112, 146, 204, 0.26);
      backdrop-filter: blur(12px);
    `}
  }
`;

const Title = styled.h1<{ $manager: boolean }>`
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: ${({ theme, $manager }) => ($manager ? '#e9ebff' : theme.colors.primary)};
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
    transform: translateY(0) scale(0.95);
    opacity: 0.8;
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
    background: transparent;

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
      width: 84px;
      background: rgba(12, 22, 41, 0.96);
      border-top: none;
      border-right: 1px solid rgba(112, 146, 204, 0.26);
      justify-content: flex-start;
      align-items: stretch;
      flex-direction: column;
      gap: 6px;
      padding: 16px 10px;
      overflow-y: auto;
    `}
  }
`;

const ManagerNavTop = styled.div`
  display: none;

  @media (min-width: 960px) {
    display: flex;
    justify-content: center;
    margin-bottom: 10px;
  }
`;

const ManagerLogo = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 12px;
  border: 1px solid rgba(138, 177, 243, 0.48);
  background: linear-gradient(135deg, rgba(70, 124, 220, 0.28), rgba(54, 97, 198, 0.26));
  color: #b8d5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(22, 66, 150, 0.35);

  svg {
    width: 22px;
    height: 22px;
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

    &:active {
      transform: scale(0.92);
      opacity: 0.8;
    }
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
      padding: 10px 6px;
      border-radius: 12px;
      font-size: 10px;
      color: #8fa9d8;

      &.active {
        background: linear-gradient(135deg, rgba(64, 121, 221, 0.5), rgba(47, 96, 196, 0.38));
        color: #f5f6ff;
        border: 1px solid rgba(126, 168, 235, 0.45);
        box-shadow: 0 8px 24px rgba(25, 74, 169, 0.3);
      }

      &:hover {
        color: #dce1ff;
        background: rgba(107, 145, 216, 0.16);
      }

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
      {isManagerMode && (
        <Navigation $manager={isManagerMode}>
          <ManagerNavTop>
            <ManagerLogo>
              <Infinity />
            </ManagerLogo>
          </ManagerNavTop>
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
        <Header $manager={isManagerMode}>
          <Title $manager={isManagerMode}>{t('app.title')}</Title>
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
