import React from 'react';
import styled from 'styled-components';
import { NavLink } from 'react-router-dom';
import { KeyRound, StickyNote, Settings as SettingsIcon, CreditCard, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const LayoutContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
`;

const Header = styled.header`
  padding: 16px 20px;
  background-color: ${({ theme }) => theme.colors.surface};
  border-bottom: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 10;
`;

const Title = styled.h1`
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: ${({ theme }) => theme.colors.primary};
  letter-spacing: -0.5px;
`;

const Content = styled.main`
  flex: 1;
  overflow-y: auto;
  position: relative;
`;

const BottomNav = styled.nav`
  padding: 8px 8px;
  background-color: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  display: flex;
  justify-content: space-around;
  align-items: center;
`;

const NavItem = styled(NavLink)`
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

  &.active {
    color: ${({ theme }) => theme.colors.primary};
    background-color: ${({ theme }) => theme.colors.primaryContainer};
  }

  svg {
    width: 22px;
    height: 22px;
    margin-bottom: 2px;
  }
`;

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout = ({ children }: LayoutProps) => {
  const { t } = useTranslation();
  return (
    <LayoutContainer>
      <Header>
        <Title>{t('app.title')}</Title>
      </Header>
      <Content>
        {children}
      </Content>
      <BottomNav>
        <NavItem to="/">
          <KeyRound />
          {t('nav.passwords')}
        </NavItem>
        <NavItem to="/notes">
          <StickyNote />
          {t('nav.notes')}
        </NavItem>
        <NavItem to="/documents">
          <CreditCard />
          {t('nav.documents')}
        </NavItem>
        <NavItem to="/authenticator">
          <ShieldCheck />
          {t('nav.authenticator')}
        </NavItem>
        <NavItem to="/settings">
          <SettingsIcon />
          {t('nav.settings')}
        </NavItem>
      </BottomNav>
    </LayoutContainer>
  );
};
