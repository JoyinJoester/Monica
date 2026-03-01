import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import styled, { keyframes } from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import {
  Plus, Search, Copy, Trash2, Eye, EyeOff,
  RefreshCw, ChevronDown, ChevronUp, Star, FolderPlus, UserRound, Link2, PencilLine, Shield, Archive, ArchiveRestore, SlidersHorizontal, X
} from 'lucide-react';
import { getPasswords, saveItem, deleteItem, updateItem, getCategories, saveCategory } from '../../utils/storage';
import type { SecureItem, PasswordEntry } from '../../types/models';
import type { Category } from '../../utils/storage';
import { ItemType } from '../../types/models';

type QuickFilter = 'all' | 'starred' | 'uncategorized' | 'localOnly' | 'archived';
type SourceFilter = 'all' | 'local' | 'keepass' | 'bitwarden';

// ========== Styled Components ==========
const Container = styled.div<{ $manager: boolean }>`
  padding: 16px;
  padding-bottom: 100px;

  ${({ $manager }) => $manager && `
    max-width: 980px;
    margin: 0 auto;
    padding: 22px 26px 120px;
  `}

  @media (max-width: 900px) {
    max-width: none;
    margin: 0;
    padding: 16px;
    padding-bottom: 100px;
  }
`;

const SearchContainer = styled.div<{ $manager: boolean }>`
  position: relative;
  margin-bottom: ${({ $manager }) => ($manager ? '0' : '14px')};
  ${({ $manager }) => $manager && `
    flex: 1;
    min-width: 240px;
  `}

  svg {
    position: absolute;
    right: 16px;
    top: 50%;
    transform: translateY(-50%);
    color: ${({ theme, $manager }) => ($manager ? '#8d94bf' : theme.colors.onSurfaceVariant)};
    width: 20px;
    height: 20px;
    pointer-events: none;
  }
`;

const ManagerSearchInput = styled.input`
  width: 100%;
  border-radius: 14px;
  padding: 14px 46px 14px 16px;
  border: 1px solid rgba(122, 133, 203, 0.36);
  background: rgba(21, 24, 46, 0.82);
  color: #f4f6ff;
  font-size: 15px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &::placeholder {
    color: #8d94bf;
  }

  &:focus {
    border-color: rgba(157, 141, 255, 0.72);
    box-shadow: 0 0 0 3px rgba(114, 99, 214, 0.26);
  }
`;

const FAB = styled(Button)<{ $manager: boolean }>`
  position: fixed; bottom: 90px; right: 20px; width: 56px; height: 56px;
  border-radius: 16px; padding: 0; box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  svg { width: 24px; height: 24px; }

  ${({ $manager }) => $manager && `
    right: 24px;
    bottom: 24px;
    border-radius: 18px;
    background: linear-gradient(135deg, #7e60ff 0%, #6e49fb 100%);
    border: 1px solid rgba(182, 167, 255, 0.55);
    box-shadow: 0 12px 28px rgba(90, 58, 206, 0.45);
  `}
`;

const CardActions = styled.div`
  display: flex; gap: 8px; margin-top: 12px;
`;

const IconButton = styled.button<{ $manager?: boolean }>`
  background: none; border: none; cursor: pointer; padding: 8px; border-radius: 8px;
  color: ${({ theme, $manager }) => ($manager ? '#aeb5e0' : theme.colors.onSurfaceVariant)};
  transition: all 0.15s ease;
  &:hover {
    background-color: ${({ theme, $manager }) => ($manager ? 'rgba(122, 133, 203, 0.18)' : theme.colors.surfaceVariant)};
    color: ${({ $manager }) => ($manager ? '#f1f2ff' : 'inherit')};
  }
  &:active {
    transform: scale(0.92);
    opacity: 0.8;
  }
  svg { width: 18px; height: 18px; }
`;

const Modal = styled.div`
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: flex-start; justify-content: center;
  z-index: 100; padding: 16px; overflow-y: auto;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 16px; padding: 24px; width: 100%; max-width: 400px;
  margin: 20px 0;
`;

const ModalHeader = styled.div`
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
`;

const ModalTitle = styled.h2`
  margin: 0; font-size: 18px; color: ${({ theme }) => theme.colors.onSurface};
`;

const ButtonRow = styled.div`
  display: flex; gap: 12px; margin-top: 20px;
`;

const EmptyState = styled.div<{ $manager: boolean }>`
  text-align: center;
  padding: 48px 0;
  color: ${({ theme, $manager }) => ($manager ? '#9ba2cd' : theme.colors.onSurfaceVariant)};
`;

const PasswordField = styled.div`
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
`;

const PasswordActions = styled.div`
  display: flex;
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
`;

const SectionHeader = styled.div<{ expanded: boolean }>`
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px; margin-top: 12px;
  background: ${({ theme }) => theme.colors.surfaceVariant};
  border-radius: 8px; cursor: pointer;
  font-weight: 600; font-size: 14px;
  color: ${({ theme }) => theme.colors.primary};

  transition: transform 0.15s ease, opacity 0.15s ease;
  &:active {
    transform: scale(0.98);
    opacity: 0.8;
  }`;

const SectionContent = styled.div`
  padding: 12px 0;
`;

const CategoryBadge = styled.span`
  display: inline-block; padding: 4px 10px; border-radius: 12px;
  background: ${({ theme }) => theme.colors.secondaryContainer};
  color: ${({ theme }) => theme.colors.onSecondaryContainer};
  font-size: 11px; font-weight: 500; margin-right: 8px;
`;

const FavoriteIcon = styled(Star) <{ active: boolean }>`
  color: ${({ theme, active }) => active ? '#FFC107' : theme.colors.outline};
  fill: ${({ active }) => active ? '#FFC107' : 'none'};
  cursor: pointer;

  transition: transform 0.15s ease;
  &:active {
    transform: scale(0.85);
  }`;

const StyledSelect = styled.select`
  flex: 1;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  font-size: 15px;
  background: ${({ theme }) => theme.colors.surfaceVariant};
  color: ${({ theme }) => theme.colors.onSurface};
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' fill='%23999' viewBox='0 0 16 16'%3E%3Cpath d='M4.5 6l3.5 4 3.5-4z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 40px;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
  }

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: 0 0 0 2px ${({ theme }) => theme.colors.primary}20;
  }

  option {
    background: ${({ theme }) => theme.colors.surface};
    color: ${({ theme }) => theme.colors.onSurface};
    padding: 12px;
  }
`;

const MobileFilterRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
`;

const MobileFilterSummary = styled.div`
  color: #97aad3;
  font-size: 12px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const MobileFilterButton = styled.button`
  border: 1px solid rgba(140, 164, 221, 0.46);
  background: rgba(19, 29, 53, 0.9);
  color: #e5edff;
  border-radius: 10px;
  min-height: 34px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
`;

const MobileFilterBadge = styled.span`
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #f6f7ff;
  background: linear-gradient(135deg, rgba(130, 98, 255, 0.9), rgba(105, 81, 252, 0.9));
`;

const FilterModalContent = styled(ModalContent)`
  width: min(92vw, 520px);
  max-width: none;
  max-height: min(86vh, 780px);
  padding: 0;
  overflow: hidden;
  border-radius: 18px;
  background:
    radial-gradient(circle at 88% 6%, rgba(115, 94, 255, 0.2), rgba(115, 94, 255, 0) 34%),
    linear-gradient(170deg, rgba(22, 27, 54, 0.98), rgba(14, 19, 40, 0.98));
  border: 1px solid rgba(125, 147, 209, 0.4);
  box-shadow: 0 22px 52px rgba(5, 10, 30, 0.6);
`;

const FilterModalHeader = styled.div`
  padding: 18px 18px 12px;
  border-bottom: 1px solid rgba(122, 139, 200, 0.28);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
`;

const FilterHeaderMeta = styled.div`
  min-width: 0;
`;

const FilterHeaderTitle = styled.h2`
  margin: 0;
  font-size: 26px;
  color: #f0f4ff;
  letter-spacing: 0.2px;
`;

const FilterHeaderSub = styled.p`
  margin: 6px 0 0;
  color: #91a7d8;
  font-size: 12px;
`;

const FilterCloseButton = styled.button`
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid rgba(127, 146, 206, 0.44);
  background: rgba(21, 30, 58, 0.9);
  color: #d8e2ff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
`;

const FilterModalScroll = styled.div`
  padding: 14px 18px 14px;
  overflow-y: auto;
  max-height: min(62vh, 560px);
`;

const FilterBlock = styled.section`
  border: 1px solid rgba(117, 137, 198, 0.3);
  background: rgba(13, 22, 46, 0.62);
  border-radius: 14px;
  padding: 12px;
  margin-bottom: 10px;
`;

const FilterSectionTitle = styled.h3`
  margin: 0 0 8px;
  font-size: 11px;
  letter-spacing: 0.72px;
  text-transform: uppercase;
  color: #a2b5e3;
  font-weight: 700;
`;

const FilterSelectGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
`;

const FilterSelectLabel = styled.label`
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #8ea4d8;
`;

const FilterSelect = styled(StyledSelect)`
  width: 100%;
  min-height: 44px;
  border-radius: 12px;
  border-color: rgba(126, 147, 210, 0.34);
  background: rgba(31, 38, 72, 0.78);
  color: #f0f5ff;
  font-weight: 600;
  font-size: 14px;
  padding-left: 12px;

  &:hover {
    border-color: rgba(150, 174, 244, 0.74);
  }

  &:focus {
    border-color: rgba(153, 132, 255, 0.86);
    box-shadow: 0 0 0 3px rgba(115, 91, 240, 0.3);
  }

  option {
    background: #171d3a;
    color: #f0f5ff;
  }
`;

const AddCategoryButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 12px;
  border: 1px dashed ${({ theme }) => theme.colors.outlineVariant};
  background: transparent;
  color: ${({ theme }) => theme.colors.primary};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${({ theme }) => theme.colors.primaryContainer};
    border-style: solid;
    border-color: ${({ theme }) => theme.colors.primary};
  }

  svg {
    width: 20px;
    height: 20px;
  }
`;

const FilterContainer = styled.div<{ $manager: boolean }>`
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0;
`;

const FilterChip = styled.button<{ active: boolean; $manager: boolean }>`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 20px;
  border: 1px solid ${({ active }) => (active ? 'rgba(178, 154, 255, 0.86)' : 'rgba(122, 137, 193, 0.46)')};
  background: ${({ active }) => (active
    ? 'linear-gradient(135deg, rgba(143, 108, 255, 0.88), rgba(116, 83, 246, 0.84))'
    : 'rgba(25, 34, 66, 0.72)')};
  color: ${({ active }) => (active ? '#f7f9ff' : '#b4c2ea')};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${({ active }) => (active ? 'rgba(190, 169, 255, 0.92)' : 'rgba(150, 169, 229, 0.86)')};
    background: ${({ active }) => (active
      ? 'linear-gradient(135deg, rgba(154, 123, 255, 0.94), rgba(126, 96, 252, 0.9))'
      : 'rgba(40, 52, 91, 0.8)')};
  }

  svg {
    width: 14px;
    height: 14px;
  }
`;

const FilterActionBar = styled.div`
  padding: 12px 18px 18px;
  border-top: 1px solid rgba(122, 139, 200, 0.28);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  background: rgba(15, 20, 40, 0.86);
`;

const buttonRipple = keyframes`
  0% {
    transform: translate(-50%, -50%) scale(0.2);
    opacity: 0.5;
  }
  100% {
    transform: translate(-50%, -50%) scale(2.4);
    opacity: 0;
  }
`;

const InteractionHint = styled.div<{ $show: boolean }>`
  position: fixed;
  left: 50%;
  bottom: 32px;
  z-index: 9999;
  pointer-events: none;
  border-radius: 12px;
  border: 1px solid rgba(126, 176, 241, 0.55);
  background: rgba(16, 28, 56, 0.96);
  color: #e6efff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  padding: 12px 22px;
  box-shadow: 0 8px 32px rgba(10, 30, 80, 0.55), 0 0 0 1px rgba(100, 140, 220, 0.15);
  backdrop-filter: blur(12px);
  opacity: ${({ $show }) => ($show ? 1 : 0)};
  transform: translate(-50%, ${({ $show }) => ($show ? '0' : '12px')});
  transition: opacity 0.2s ease, transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
`;

const PasswordStack = styled.div<{ $manager: boolean }>`
  display: flex;
  flex-direction: column;
  gap: ${({ $manager }) => ($manager ? '12px' : '0')};
`;

const ManagerDashboard = styled.section`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ManagerTopBar = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
`;

const HeroPanel = styled.div`
  border: 1px solid rgba(119, 145, 203, 0.34);
  background: linear-gradient(140deg, rgba(19, 31, 60, 0.94), rgba(16, 25, 44, 0.88));
  border-radius: 16px;
  padding: 18px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
`;

const HeroTitleWrap = styled.div`
  min-width: 240px;
`;

const HeroBadge = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  border: 1px solid rgba(140, 176, 255, 0.42);
  background: rgba(23, 39, 75, 0.76);
  color: #c7ddff;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  padding: 4px 10px;
`;

const HeroTitle = styled.h2`
  margin: 10px 0 8px;
  color: #eff4ff;
  font-size: clamp(22px, 2.8vw, 30px);
  line-height: 1.15;
`;

const HeroSub = styled.p`
  margin: 0;
  color: #9fb0d4;
  font-size: 14px;
`;

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 10px;
  width: min(100%, 360px);
`;

const StatCard = styled.div`
  border: 1px solid rgba(108, 140, 202, 0.32);
  background: rgba(13, 23, 44, 0.78);
  border-radius: 12px;
  padding: 10px 12px;
`;

const StatLabel = styled.div`
  color: #89a0cf;
  font-size: 11px;
  letter-spacing: 0.6px;
  text-transform: uppercase;
`;

const StatValue = styled.div`
  color: #edf3ff;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  margin-top: 3px;
`;

const ManagerToolbar = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
`;

const ManagerPrimaryButton = styled.button`
  border: 1px solid rgba(96, 150, 255, 0.55);
  background: linear-gradient(135deg, #3772ff, #3f61f5);
  color: #f7f9ff;
  border-radius: 12px;
  min-height: 42px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 24px rgba(45, 105, 255, 0.35);
  }
`;

const ManagerWorkspace = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.8fr);
  border: 1px solid rgba(115, 145, 201, 0.26);
  border-radius: 16px;
  overflow: hidden;
  background: rgba(9, 17, 34, 0.74);
  min-height: calc(100vh - 280px);

  @media (max-width: 1120px) {
    grid-template-columns: 1fr;
    min-height: auto;
  }
`;

const ManagerListPane = styled.div`
  border-right: 1px solid rgba(115, 145, 201, 0.2);
  background: rgba(9, 17, 34, 0.78);

  @media (max-width: 1120px) {
    border-right: 0;
    border-bottom: 1px solid rgba(115, 145, 201, 0.2);
  }
`;

const ManagerRows = styled.div`
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 360px);
  overflow: auto;
  scrollbar-gutter: stable;

  @media (max-width: 1120px) {
    max-height: none;
  }
`;

const ManagerRow = styled.button<{ $active: boolean }>`
  width: 100%;
  border: 1px solid ${({ $active }) => ($active ? 'rgba(100, 151, 255, 0.65)' : 'rgba(119, 145, 203, 0.26)')};
  background: ${({ $active }) => ($active ? 'rgba(31, 52, 92, 0.86)' : 'rgba(13, 24, 47, 0.7)')};
  border-radius: 12px;
  position: relative;
  overflow: hidden;
  text-align: left;
  color: #edf4ff;
  min-height: 64px;
  padding: 13px 12px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.12s ease, box-shadow 0.18s ease;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: linear-gradient(180deg, rgba(126, 171, 255, 0.95), rgba(114, 93, 255, 0.95));
    opacity: ${({ $active }) => ($active ? 1 : 0)};
    transition: opacity 0.18s ease;
  }

  &:hover {
    border-color: rgba(132, 168, 243, 0.66);
    background: rgba(28, 44, 78, 0.84);
    box-shadow: inset 0 0 0 1px rgba(118, 156, 236, 0.26);
  }

  &:active {
    transform: translateY(1px) scale(0.997);
  }
`;

const ManagerRowHead = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
`;

const ManagerRowTitle = styled.div`
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  padding-right: 6px;
`;

const ManagerRowHeadRight = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
`;

const ManagerRowTime = styled.div`
  color: #9cb3df;
  font-size: 11px;
  line-height: 1;
  padding: 2px 0;
  white-space: nowrap;
`;

const ManagerMeta = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 9px;
`;

const MetaItem = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #bfd1f3;
  font-size: 12px;
  line-height: 1.3;
  min-width: 0;
  max-width: 100%;
  flex: 1 1 calc(50% - 4px);
  border: 1px solid rgba(106, 132, 184, 0.28);
  background: rgba(20, 34, 62, 0.52);
  border-radius: 999px;
  padding: 4px 8px;

  svg {
    flex-shrink: 0;
    color: #8da7d8;
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

const ManagerDetailPane = styled.aside`
  background: rgba(13, 22, 42, 0.88);
  display: flex;
  flex-direction: column;
`;

const ManagerDetailHeader = styled.div`
  padding: 16px;
  border-bottom: 1px solid rgba(115, 145, 201, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #9bb0d9;
  font-size: 12px;
  letter-spacing: 0.8px;
  text-transform: uppercase;
`;

const ManagerFavorite = styled(Star)<{ $active: boolean }>`
  width: 18px;
  height: 18px;
  color: ${({ $active }) => ($active ? '#ffd34a' : '#6f86b8')};
  fill: ${({ $active }) => ($active ? '#ffd34a' : 'none')};
  flex-shrink: 0;
  cursor: pointer;
`;

const ManagerDetailBody = styled.div`
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
`;

const ManagerDetailTitle = styled.h3`
  margin: 0;
  color: #f0f5ff;
  font-size: clamp(24px, 3vw, 32px);
  line-height: 1.05;
`;

const ManagerDetailSub = styled.a`
  color: #8eb5ff;
  text-decoration: none;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  word-break: break-all;
`;

const ManagerDetailLabel = styled.label`
  color: #91a7d3;
  font-size: 11px;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  margin-top: 4px;
`;

const ManagerDetailField = styled.div`
  border: 1px solid rgba(116, 147, 203, 0.25);
  border-radius: 10px;
  background: rgba(8, 17, 34, 0.72);
  color: #ecf3ff;
  padding: 10px 12px;
  font-size: 14px;
  word-break: break-all;
`;

const ManagerDetailNotes = styled.div`
  min-height: 92px;
  border: 1px solid rgba(116, 147, 203, 0.25);
  border-radius: 10px;
  background: rgba(8, 17, 34, 0.72);
  color: #a2b6df;
  padding: 10px 12px;
  font-size: 14px;
  white-space: pre-wrap;
`;

const DetailActionRow = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
`;

const ManagerDetailFooter = styled.div`
  padding: 14px 16px 16px;
  border-top: 1px solid rgba(115, 145, 201, 0.2);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
`;

const ManagerGhostButton = styled.button<{ $feedback?: boolean; $busy?: boolean }>`
  border-radius: 10px;
  border: 1px solid rgba(116, 147, 203, 0.35);
  background: ${({ $feedback }) => ($feedback ? 'linear-gradient(135deg, rgba(68, 196, 142, 0.85), rgba(48, 168, 122, 0.85))' : 'rgba(15, 27, 52, 0.7)')};
  color: ${({ $feedback }) => ($feedback ? '#f2fff9' : '#dce8ff')};
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: ${({ $busy }) => ($busy ? 'progress' : 'pointer')};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s ease, filter 0.15s ease;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    left: 50%;
    top: 50%;
    width: 28px;
    height: 28px;
    border-radius: 999px;
    transform: translate(-50%, -50%) scale(0.2);
    opacity: 0;
    pointer-events: none;
    background: ${({ $feedback }) => ($feedback ? 'rgba(221, 255, 236, 0.5)' : 'rgba(183, 206, 255, 0.45)')};
  }

  &:hover {
    background: ${({ $feedback }) => ($feedback ? 'linear-gradient(135deg, rgba(84, 213, 160, 0.88), rgba(56, 184, 132, 0.88))' : 'rgba(50, 80, 140, 0.55)')};
    border-color: rgba(140, 170, 230, 0.6);
    color: #fff;
    transform: translateY(-1px);
    box-shadow: 0 4px 14px rgba(30, 70, 160, 0.35);
  }

  &:active {
    transform: translateY(0) scale(0.96);
    background: ${({ $feedback }) => ($feedback ? 'linear-gradient(135deg, rgba(66, 193, 143, 0.9), rgba(45, 161, 116, 0.9))' : 'rgba(60, 100, 180, 0.65)')};
    border-color: rgba(160, 190, 240, 0.7);
    box-shadow: 0 0 0 3px rgba(100, 140, 220, 0.25);

    &::after {
      animation: ${buttonRipple} 0.38s ease-out;
    }
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px rgba(124, 156, 234, 0.38);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
`;

const ManagerDangerButton = styled(ManagerGhostButton)`
  color: #ffc2c2;
  border-color: rgba(230, 122, 122, 0.5);
  background: rgba(66, 18, 27, 0.46);

  &:hover {
    background: rgba(120, 30, 40, 0.65);
    border-color: rgba(255, 140, 140, 0.65);
    color: #ffe0e0;
    box-shadow: 0 4px 14px rgba(180, 40, 50, 0.3);
  }

  &:active {
    background: rgba(150, 40, 50, 0.7);
    border-color: rgba(255, 160, 160, 0.7);
    box-shadow: 0 0 0 3px rgba(200, 80, 80, 0.2);
  }
`;

const ManagerEmptyState = styled.div`
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #8fa4cd;
  text-align: center;
  padding: 24px;

  h3 {
    margin: 0;
    font-size: 18px;
    color: #d5e3ff;
  }

  p {
    margin: 0;
    max-width: 320px;
    font-size: 13px;
  }
`;

// ========== Password Generator ==========
const generatePassword = (length = 16, options = { upper: true, lower: true, numbers: true, symbols: true }) => {
  let chars = '';
  if (options.upper) chars += 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  if (options.lower) chars += 'abcdefghijklmnopqrstuvwxyz';
  if (options.numbers) chars += '0123456789';
  if (options.symbols) chars += '!@#$%^&*()_+-=[]{}|;:,.<>?';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
};

// Categories are now user-created (no static presets)

// ========== Main Component ==========
export const PasswordList = () => {
  const { t, i18n } = useTranslation();
  const isZh = i18n.language?.startsWith('zh');
  const isManagerMode = useMemo(() => new URLSearchParams(window.location.search).get('manager') === '1', []);
  const prefersReducedMotion = useMemo(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    []
  );
  const FILTER_STATE_KEY = 'monica_password_filter_state_v1';

  const readSavedFilterState = useCallback(() => {
    try {
      const raw = localStorage.getItem(FILTER_STATE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<{
        quick: QuickFilter;
        category: 'all' | number;
        source: SourceFilter;
        database: 'all' | string;
        folder: 'all' | string;
        sortBy: 'updated' | 'created' | 'title';
      }>;
      return parsed;
    } catch {
      return null;
    }
  }, []);

  const [passwords, setPasswords] = useState<SecureItem[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState<'updated' | 'created' | 'title'>(() => {
    const saved = readSavedFilterState()?.sortBy;
    return saved === 'created' || saved === 'title' ? saved : 'updated';
  });
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [passwordVisible, setPasswordVisible] = useState(false);

  // Form State - Complete fields matching Android
  const [form, setForm] = useState({
    title: '',
    website: '',
    username: '',
    password: '',
    notes: '',
    email: '',
    phone: '',
    categoryId: 0, // Changed from string to number
    isFavorite: false,
    sourceType: 'local' as SourceFilter,
    databaseId: '',
    folderPath: '',
    isArchived: false,
    archivedAt: '',
  });

  // Collapsible sections
  const [showPersonalInfo, setShowPersonalInfo] = useState(false);

  const [activeQuickFilter, setActiveQuickFilter] = useState<QuickFilter>(() => {
    const saved = readSavedFilterState()?.quick;
    return saved === 'starred' || saved === 'uncategorized' || saved === 'localOnly' || saved === 'archived' ? saved : 'all';
  });
  const [activeCategoryFilter, setActiveCategoryFilter] = useState<'all' | number>(() => {
    const saved = readSavedFilterState()?.category;
    return typeof saved === 'number' || saved === 'all' ? saved : 'all';
  });
  const [activeSourceFilter, setActiveSourceFilter] = useState<SourceFilter>(() => {
    const saved = readSavedFilterState()?.source;
    return saved === 'local' || saved === 'keepass' || saved === 'bitwarden' ? saved : 'all';
  });
  const [activeDatabaseFilter, setActiveDatabaseFilter] = useState<'all' | string>(() => {
    const saved = readSavedFilterState()?.database;
    return typeof saved === 'string' ? saved : 'all';
  });
  const [activeFolderFilter, setActiveFolderFilter] = useState<'all' | string>(() => {
    const saved = readSavedFilterState()?.folder;
    return typeof saved === 'string' ? saved : 'all';
  });
  const [selectedItemId, setSelectedItemId] = useState<number | null>(null);
  const [showFilterModal, setShowFilterModal] = useState(false);
  const [filterDraft, setFilterDraft] = useState<{
    quick: QuickFilter;
    category: 'all' | number;
    source: SourceFilter;
    database: 'all' | string;
    folder: 'all' | string;
    sortBy: 'updated' | 'created' | 'title';
  }>({
    quick: activeQuickFilter,
    category: activeCategoryFilter,
    source: activeSourceFilter,
    database: activeDatabaseFilter,
    folder: activeFolderFilter,
    sortBy,
  });
  const [interactionHint, setInteractionHint] = useState('');
  const hintTimerRef = useRef<number | null>(null);
  const detailActionTimerRef = useRef<Record<string, number>>({});
  const [detailActionFeedback, setDetailActionFeedback] = useState({
    copyUsername: false,
    copyPassword: false,
  });
  const [detailActionBusy, setDetailActionBusy] = useState<{
    archive: boolean;
    delete: boolean;
  }>({
    archive: false,
    delete: false,
  });

  const showInteractionHint = useCallback((message: string) => {
    setInteractionHint(message);
    if (hintTimerRef.current !== null) {
      window.clearTimeout(hintTimerRef.current);
    }
    hintTimerRef.current = window.setTimeout(() => {
      setInteractionHint('');
      hintTimerRef.current = null;
    }, 1200);
  }, []);

  const flashDetailAction = useCallback((key: 'copyUsername' | 'copyPassword') => {
    setDetailActionFeedback((prev) => ({ ...prev, [key]: true }));
    const existing = detailActionTimerRef.current[key];
    if (existing) {
      window.clearTimeout(existing);
    }
    detailActionTimerRef.current[key] = window.setTimeout(() => {
      setDetailActionFeedback((prev) => ({ ...prev, [key]: false }));
      delete detailActionTimerRef.current[key];
    }, 900);
  }, []);

  const resetForm = () => {
    setForm({
      title: '', website: '', username: '', password: '', notes: '',
      email: '', phone: '', categoryId: 0, isFavorite: false,
      sourceType: 'local', databaseId: '', folderPath: '', isArchived: false, archivedAt: '',
    });
    setEditingId(null);
    setPasswordVisible(false);
    setShowPersonalInfo(false);
  };

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [pwds, cats] = await Promise.all([getPasswords(), getCategories()]);
      setPasswords(pwds);
      setCategories(cats);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  useEffect(() => () => {
    if (hintTimerRef.current !== null) {
      window.clearTimeout(hintTimerRef.current);
    }
    Object.values(detailActionTimerRef.current).forEach((timer) => window.clearTimeout(timer));
  }, []);

  // Listen for storage changes to auto-refresh
  useEffect(() => {
    const handleStorageChange = (changes: { [key: string]: chrome.storage.StorageChange }, areaName: string) => {
      if (areaName === 'local' && changes['monica_vault']) {
        loadData();
      }
    };
    chrome.storage.onChanged.addListener(handleStorageChange);
    return () => {
      chrome.storage.onChanged.removeListener(handleStorageChange);
    };
  }, [loadData]);

  const getEntrySource = useCallback((data: PasswordEntry): SourceFilter => {
    if (data.keepassDatabaseId != null) return 'keepass';
    if (data.bitwardenVaultId != null) return 'bitwarden';
    return 'local';
  }, []);

  const databaseOptions = useMemo(() => {
    const keepassIds = new Set<number>();
    const bitwardenIds = new Set<number>();
    passwords.forEach((item) => {
      const data = item.itemData as PasswordEntry;
      if (data.keepassDatabaseId != null) keepassIds.add(data.keepassDatabaseId);
      if (data.bitwardenVaultId != null) bitwardenIds.add(data.bitwardenVaultId);
    });
    return {
      keepass: Array.from(keepassIds).sort((a, b) => a - b),
      bitwarden: Array.from(bitwardenIds).sort((a, b) => a - b),
    };
  }, [passwords]);

  const resolveFolderOptions = useCallback((source: SourceFilter, database: 'all' | string): string[] => {
    const folders = new Set<string>();
    passwords.forEach((item) => {
      const data = item.itemData as PasswordEntry;
      if (source === 'keepass' || (database !== 'all' && String(database).startsWith('kp:'))) {
        if (data.keepassDatabaseId == null) return;
        if (database !== 'all') {
          const dbId = parseInt(String(database).replace('kp:', ''), 10);
          if (data.keepassDatabaseId !== dbId) return;
        }
        folders.add(data.keepassGroupPath?.trim() || '__root__');
        return;
      }
      if (source === 'bitwarden' || (database !== 'all' && String(database).startsWith('bw:'))) {
        if (data.bitwardenVaultId == null) return;
        if (database !== 'all') {
          const vaultId = parseInt(String(database).replace('bw:', ''), 10);
          if (data.bitwardenVaultId !== vaultId) return;
        }
        folders.add(data.bitwardenFolderId?.trim() || '__none__');
      }
    });
    return Array.from(folders).sort();
  }, [passwords]);

  const draftFolderOptions = useMemo(
    () => resolveFolderOptions(filterDraft.source, filterDraft.database),
    [filterDraft.database, filterDraft.source, resolveFolderOptions]
  );

  useEffect(() => {
    try {
      localStorage.setItem(FILTER_STATE_KEY, JSON.stringify({
        quick: activeQuickFilter,
        category: activeCategoryFilter,
        source: activeSourceFilter,
        database: activeDatabaseFilter,
        folder: activeFolderFilter,
        sortBy,
      }));
    } catch {
      // ignore persistence errors
    }
  }, [
    activeCategoryFilter,
    activeDatabaseFilter,
    activeFolderFilter,
    activeQuickFilter,
    activeSourceFilter,
    sortBy,
  ]);

  const filteredPasswords = useMemo(() => {
    const result = passwords.filter((p) => {
      const data = p.itemData as PasswordEntry;
      const searchLower = search.toLowerCase();
      const matchesSearch =
        (p.title || '').toLowerCase().includes(searchLower) ||
        data.username?.toLowerCase().includes(searchLower) ||
        data.website?.toLowerCase().includes(searchLower);

      if (!matchesSearch) return false;

      const isArchived = !!data.isArchived;
      if (activeQuickFilter === 'archived') {
        if (!isArchived) return false;
      } else if (isArchived) {
        return false;
      }

      if (activeQuickFilter === 'starred' && !p.isFavorite) return false;
      if (activeQuickFilter === 'uncategorized' && data.categoryId != null) return false;
      if (activeQuickFilter === 'localOnly' && (data.keepassDatabaseId != null || data.bitwardenVaultId != null)) return false;

      const source = getEntrySource(data);
      if (activeSourceFilter !== 'all' && source !== activeSourceFilter) return false;

      if (activeCategoryFilter !== 'all' && data.categoryId !== activeCategoryFilter) return false;

      if (activeDatabaseFilter !== 'all') {
        const encoded = String(activeDatabaseFilter);
        if (encoded.startsWith('kp:')) {
          const dbId = parseInt(encoded.replace('kp:', ''), 10);
          if (data.keepassDatabaseId !== dbId) return false;
        } else if (encoded.startsWith('bw:')) {
          const vaultId = parseInt(encoded.replace('bw:', ''), 10);
          if (data.bitwardenVaultId !== vaultId) return false;
        }
      }

      if (activeFolderFilter !== 'all') {
        if ((activeSourceFilter === 'keepass' || String(activeDatabaseFilter).startsWith('kp:'))) {
          const key = data.keepassGroupPath?.trim() || '__root__';
          if (key !== activeFolderFilter) return false;
        } else if ((activeSourceFilter === 'bitwarden' || String(activeDatabaseFilter).startsWith('bw:'))) {
          const key = data.bitwardenFolderId?.trim() || '__none__';
          if (key !== activeFolderFilter) return false;
        }
      }
      return true;
    });

    const toTs = (value?: string) => {
      if (!value) return 0;
      const time = new Date(value).getTime();
      return Number.isNaN(time) ? 0 : time;
    };

    return result.sort((a, b) => {
      if (sortBy === 'title') return a.title.localeCompare(b.title);
      if (sortBy === 'created') return toTs(b.createdAt) - toTs(a.createdAt);
      return toTs(b.updatedAt) - toTs(a.updatedAt);
    });
  }, [
    activeCategoryFilter,
    activeDatabaseFilter,
    activeFolderFilter,
    activeQuickFilter,
    activeSourceFilter,
    getEntrySource,
    passwords,
    search,
    sortBy,
  ]);

  useEffect(() => {
    if (!filteredPasswords.length) {
      setSelectedItemId(null);
      return;
    }

    if (!selectedItemId || !filteredPasswords.some((item) => item.id === selectedItemId)) {
      setSelectedItemId(filteredPasswords[0].id);
    }
  }, [filteredPasswords, selectedItemId]);

  const selectedItem = filteredPasswords.find((item) => item.id === selectedItemId) || null;

  const formatRelativeTime = (dateStr: string) => {
    const time = new Date(dateStr).getTime();
    if (Number.isNaN(time)) return '--';
    const diffMs = Date.now() - time;
    const diffMinutes = Math.floor(diffMs / (1000 * 60));
    if (diffMinutes < 1) return isZh ? '刚刚' : 'Just now';
    if (diffMinutes < 60) return isZh ? `${diffMinutes} 分钟前` : `${diffMinutes}m ago`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return isZh ? `${diffHours} 小时前` : `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return isZh ? `${diffDays} 天前` : `${diffDays}d ago`;
    return new Date(dateStr).toLocaleDateString(isZh ? 'zh-CN' : 'en-US', { month: 'short', day: 'numeric' });
  };

  const formatWebsiteForList = (website?: string) => {
    const raw = (website || '').trim();
    if (!raw) return isZh ? '无网址' : 'No Website';
    try {
      const normalized = raw.includes('://') ? raw : `https://${raw}`;
      const hostname = new URL(normalized).hostname.replace(/^www\./i, '');
      return hostname || raw;
    } catch {
      const simplified = raw.replace(/^https?:\/\//i, '').replace(/^www\./i, '').split('/')[0];
      return simplified || raw;
    }
  };

  const handleSave = async () => {
    if (!form.title || !form.password) return;
    const existingItem = editingId ? passwords.find((item) => item.id === editingId) : undefined;
    const existingPasswordData = existingItem?.itemData as PasswordEntry | undefined;

    const keepassDatabaseId = form.sourceType === 'keepass' && form.databaseId
      ? Number(form.databaseId)
      : undefined;
    const bitwardenVaultId = form.sourceType === 'bitwarden' && form.databaseId
      ? Number(form.databaseId)
      : undefined;

    const itemData: PasswordEntry = {
      username: form.username,
      password: form.password,
      website: form.website,
      categoryId: form.categoryId || undefined,
      keepassDatabaseId,
      keepassGroupPath: form.sourceType === 'keepass' ? form.folderPath || undefined : undefined,
      bitwardenVaultId,
      bitwardenFolderId: form.sourceType === 'bitwarden' ? form.folderPath || undefined : undefined,
      bitwardenCipherId: existingPasswordData?.bitwardenCipherId,
      isArchived: form.isArchived,
      archivedAt: form.archivedAt || undefined,
    };

    if (editingId) {
      await updateItem(editingId, {
        title: form.title,
        notes: form.notes,
        isFavorite: form.isFavorite,
        itemData,
      });
    } else {
      await saveItem({
        itemType: ItemType.Password,
        title: form.title,
        notes: form.notes,
        isFavorite: form.isFavorite,
        sortOrder: 0,
        itemData,
      });
    }

    resetForm();
    setShowModal(false);
    loadData();
  };

  const handleEdit = (item: SecureItem) => {
    const data = item.itemData as PasswordEntry;
    const sourceType: SourceFilter = data.keepassDatabaseId != null
      ? 'keepass'
      : (data.bitwardenVaultId != null ? 'bitwarden' : 'local');
    setForm({
      title: item.title,
      website: data.website || '',
      username: data.username || '',
      password: data.password || '',
      notes: item.notes || '',
      email: '',
      phone: '',
      categoryId: data.categoryId || 0,
      isFavorite: item.isFavorite,
      sourceType,
      databaseId: sourceType === 'keepass'
        ? String(data.keepassDatabaseId || '')
        : (sourceType === 'bitwarden' ? String(data.bitwardenVaultId || '') : ''),
      folderPath: sourceType === 'keepass'
        ? (data.keepassGroupPath || '')
        : (sourceType === 'bitwarden' ? (data.bitwardenFolderId || '') : ''),
      isArchived: !!data.isArchived,
      archivedAt: data.archivedAt || '',
    });
    setEditingId(item.id);
    setShowModal(true);
  };

  const handleCopy = async (text: string, kind?: 'username' | 'password') => {
    try {
      await navigator.clipboard.writeText(text);
      if (kind === 'username') {
        showInteractionHint(isZh ? '已复制用户名' : 'Username copied');
      } else if (kind === 'password') {
        showInteractionHint(isZh ? '已复制密码' : 'Password copied');
      } else {
        showInteractionHint(isZh ? '已复制' : 'Copied');
      }
    } catch {
      showInteractionHint(isZh ? '复制失败' : 'Copy failed');
    }
  };

  const handleDelete = async (id: number) => {
    if (confirm(t('common.confirmDelete'))) {
      await deleteItem(id);
      loadData();
    }
  };

  const handleToggleArchive = async (item: SecureItem) => {
    const data = item.itemData as PasswordEntry;
    const isArchived = !!data.isArchived;
    const nextData: PasswordEntry = {
      ...data,
      isArchived: !isArchived,
      archivedAt: !isArchived ? new Date().toISOString() : undefined,
    };
    await updateItem(item.id, { itemData: nextData });
    showInteractionHint(!isArchived ? (isZh ? '已归档' : 'Archived') : (isZh ? '已取消归档' : 'Unarchived'));
    loadData();
  };

  const handleToggleFavorite = async (item: SecureItem) => {
    const next = !item.isFavorite;
    await updateItem(item.id, { isFavorite: next });
    showInteractionHint(next ? (isZh ? '已加入收藏' : 'Added to favorites') : (isZh ? '已取消收藏' : 'Removed from favorites'));
    loadData();
  };

  const handleGeneratePassword = () => {
    setForm({ ...form, password: generatePassword(16) });
  };

  const openFilterModal = () => {
    setFilterDraft({
      quick: activeQuickFilter,
      category: activeCategoryFilter,
      source: activeSourceFilter,
      database: activeDatabaseFilter,
      folder: activeFolderFilter,
      sortBy,
    });
    setShowFilterModal(true);
  };

  const applyFilterDraft = () => {
    setActiveQuickFilter(filterDraft.quick);
    setActiveCategoryFilter(filterDraft.category);
    setActiveSourceFilter(filterDraft.source);
    setActiveDatabaseFilter(filterDraft.database);
    setActiveFolderFilter(filterDraft.folder);
    setSortBy(filterDraft.sortBy);
    setShowFilterModal(false);
  };

  const resetFilterDraft = () => {
    setFilterDraft({
      quick: 'all',
      category: 'all',
      source: 'all',
      database: 'all',
      folder: 'all',
      sortBy: 'updated',
    });
  };

  const activeFilterCount = [
    activeQuickFilter !== 'all',
    activeCategoryFilter !== 'all',
    activeSourceFilter !== 'all',
    activeDatabaseFilter !== 'all',
    activeFolderFilter !== 'all',
    sortBy !== 'updated',
  ].filter(Boolean).length;

  const activeFilterSummary = useMemo(() => {
    const parts: string[] = [];
    if (activeQuickFilter !== 'all') {
      const quickLabel = isZh
        ? ({
          starred: '星标',
          uncategorized: '未分类',
          localOnly: '仅本地',
          archived: '归档',
        } as Record<Exclude<QuickFilter, 'all'>, string>)[activeQuickFilter as Exclude<QuickFilter, 'all'>]
        : ({
          starred: 'Starred',
          uncategorized: 'Uncategorized',
          localOnly: 'Local',
          archived: 'Archived',
        } as Record<Exclude<QuickFilter, 'all'>, string>)[activeQuickFilter as Exclude<QuickFilter, 'all'>];
      if (quickLabel) parts.push(quickLabel);
    }
    if (activeSourceFilter !== 'all') {
      parts.push(
        activeSourceFilter === 'local'
          ? (isZh ? '本地' : 'Local')
          : activeSourceFilter === 'keepass'
            ? 'KeePass'
            : 'Bitwarden'
      );
    }
    if (activeDatabaseFilter !== 'all') {
      parts.push(String(activeDatabaseFilter).replace('kp:', 'KP #').replace('bw:', 'BW #'));
    }
    if (activeFolderFilter !== 'all') {
      parts.push(
        activeFolderFilter === '__root__'
          ? (isZh ? '根目录' : 'Root')
          : activeFolderFilter === '__none__'
            ? (isZh ? '无文件夹' : 'No Folder')
            : activeFolderFilter
      );
    }
    if (activeCategoryFilter !== 'all') {
      const name = categories.find((cat) => cat.id === activeCategoryFilter)?.name;
      if (name) parts.push(name);
    }
    if (sortBy !== 'updated') {
      parts.push(sortBy === 'created' ? (isZh ? '按创建' : 'Created') : (isZh ? '按名称' : 'Name'));
    }
    return parts.join(' · ');
  }, [
    activeCategoryFilter,
    activeDatabaseFilter,
    activeFolderFilter,
    activeQuickFilter,
    activeSourceFilter,
    categories,
    isZh,
    sortBy,
  ]);

  return (
    <Container $manager={isManagerMode}>
      {isManagerMode ? (
        <>
          <ManagerDashboard>
            <ManagerTopBar>
              <SearchContainer $manager>
                <ManagerSearchInput
                  placeholder={t('app.searchPlaceholder')}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                <Search />
              </SearchContainer>
              <MobileFilterButton type="button" onClick={openFilterModal}>
                <SlidersHorizontal size={14} />
                {isZh ? '筛选' : 'Filters'}
                {activeFilterCount > 0 && <MobileFilterBadge>{activeFilterCount}</MobileFilterBadge>}
              </MobileFilterButton>
              <ManagerPrimaryButton onClick={() => { resetForm(); setShowModal(true); }}>
                <Plus size={16} />
                {isZh ? '新建条目' : 'New Item'}
              </ManagerPrimaryButton>
            </ManagerTopBar>

            <HeroPanel>
              <HeroTitleWrap>
                <HeroBadge>
                  <Shield size={12} />
                  {isZh ? '保险库概览' : 'Vault Overview'}
                </HeroBadge>
                <HeroTitle>{isZh ? '密码管理中心' : 'Password Command Center'}</HeroTitle>
                <HeroSub>
                  {isZh
                    ? '在一个清晰的工作区里快速检索、筛选与维护所有账号条目。'
                    : 'Search, filter, and maintain all credentials from one focused workspace.'}
                </HeroSub>
              </HeroTitleWrap>
              <StatsGrid>
                <StatCard>
                  <StatLabel>{isZh ? '总条目' : 'Total'}</StatLabel>
                  <StatValue>{passwords.length}</StatValue>
                </StatCard>
                <StatCard>
                  <StatLabel>{isZh ? '收藏' : 'Favorites'}</StatLabel>
                  <StatValue>{passwords.filter((item) => item.isFavorite).length}</StatValue>
                </StatCard>
                <StatCard>
                  <StatLabel>{isZh ? '分类' : 'Categories'}</StatLabel>
                  <StatValue>{categories.length}</StatValue>
                </StatCard>
              </StatsGrid>
            </HeroPanel>

            <ManagerToolbar>
              <div style={{ color: '#90a4cf', fontSize: 13, flex: '1 1 360px', minWidth: 260 }}>
                {isZh
                  ? `当前显示 ${filteredPasswords.length} / ${passwords.length} 条`
                  : `Showing ${filteredPasswords.length} of ${passwords.length} items`}
                {activeFilterSummary ? ` · ${activeFilterSummary}` : ''}
              </div>
              <MobileFilterButton type="button" onClick={openFilterModal}>
                <SlidersHorizontal size={14} />
                {isZh ? '筛选' : 'Filters'}
                {activeFilterCount > 0 && <MobileFilterBadge>{activeFilterCount}</MobileFilterBadge>}
              </MobileFilterButton>
            </ManagerToolbar>

            <ManagerWorkspace>
              <ManagerListPane>
                <ManagerRows>
                  {!isLoading && !filteredPasswords.length && (
                    <ManagerEmptyState>
                      <Shield size={26} />
                      <h3>{isZh ? '没有匹配条目' : 'No matching items'}</h3>
                      <p>
                        {isZh
                          ? '尝试切换筛选条件，或创建新的密码条目。'
                          : 'Try changing filters, or create a new password item.'}
                      </p>
                    </ManagerEmptyState>
                  )}
                  {isLoading && (
                    <ManagerEmptyState>
                      <h3>{isZh ? '正在加载...' : 'Loading...'}</h3>
                    </ManagerEmptyState>
                  )}
                  {filteredPasswords.map((item) => {
                    const data = (item.itemData || {}) as PasswordEntry;
                    const websiteLabel = formatWebsiteForList(data.website);
                    return (
                      <ManagerRow
                        key={item.id}
                        $active={item.id === selectedItemId}
                        type="button"
                        onClick={() => {
                          if (item.id === selectedItemId) {
                            showInteractionHint(isZh ? '打开编辑' : 'Opening editor');
                            handleEdit(item);
                            return;
                          }
                          setSelectedItemId(item.id);
                          showInteractionHint(isZh ? '已选中条目' : 'Item selected');
                        }}
                        onDoubleClick={() => {
                          showInteractionHint(isZh ? '打开编辑' : 'Opening editor');
                          handleEdit(item);
                        }}
                      >
                        <ManagerRowHead>
                          <ManagerRowTitle>{item.title}</ManagerRowTitle>
                          <ManagerRowHeadRight>
                            <ManagerRowTime>{formatRelativeTime(item.updatedAt)}</ManagerRowTime>
                            <ManagerFavorite
                              $active={item.isFavorite}
                              onClick={(e) => {
                                e.stopPropagation();
                                void handleToggleFavorite(item);
                              }}
                            />
                          </ManagerRowHeadRight>
                        </ManagerRowHead>
                        <ManagerMeta>
                          <MetaItem>
                            <UserRound size={13} />
                            <span>{data.username || (isZh ? '无用户名' : 'No Username')}</span>
                          </MetaItem>
                          <MetaItem>
                            <Link2 size={13} />
                            <span>{websiteLabel}</span>
                          </MetaItem>
                        </ManagerMeta>
                      </ManagerRow>
                    );
                  })}
                </ManagerRows>
              </ManagerListPane>

              <ManagerDetailPane>
                <ManagerDetailHeader>
                  <span>{isZh ? '条目详情' : 'Item Details'}</span>
                  <ManagerFavorite
                    $active={!!selectedItem?.isFavorite}
                    onClick={() => {
                      if (selectedItem) {
                        void handleToggleFavorite(selectedItem);
                      }
                    }}
                  />
                </ManagerDetailHeader>

                <ManagerDetailBody>
                  {selectedItem ? (
                    <>
                      <ManagerDetailTitle>{selectedItem.title}</ManagerDetailTitle>
                      <ManagerDetailSub href={(selectedItem.itemData as PasswordEntry).website || '#'} target="_blank" rel="noreferrer">
                        <Link2 size={14} />
                        {(selectedItem.itemData as PasswordEntry).website || (isZh ? '无网址' : 'No Website')}
                      </ManagerDetailSub>

                      <ManagerDetailLabel>{isZh ? '用户名' : 'Username'}</ManagerDetailLabel>
                      <ManagerDetailField>{(selectedItem.itemData as PasswordEntry).username || (isZh ? '无用户名' : 'No Username')}</ManagerDetailField>

                      <ManagerDetailLabel>{isZh ? '密码' : 'Password'}</ManagerDetailLabel>
                      <ManagerDetailField>{'•'.repeat(Math.max(8, ((selectedItem.itemData as PasswordEntry).password || '').length || 8))}</ManagerDetailField>

                      <DetailActionRow>
                          <ManagerGhostButton
                            type="button"
                            $feedback={detailActionFeedback.copyUsername}
                            onClick={() => {
                              void handleCopy((selectedItem.itemData as PasswordEntry).username || '', 'username');
                              flashDetailAction('copyUsername');
                            }}
                          >
                            <Copy size={14} />
                            {detailActionFeedback.copyUsername
                              ? (isZh ? '已复制' : 'Copied')
                              : (isZh ? '复制用户名' : 'Copy Username')}
                          </ManagerGhostButton>
                          <ManagerGhostButton
                            type="button"
                            $feedback={detailActionFeedback.copyPassword}
                            onClick={() => {
                              void handleCopy((selectedItem.itemData as PasswordEntry).password || '', 'password');
                              flashDetailAction('copyPassword');
                            }}
                          >
                            <Copy size={14} />
                            {detailActionFeedback.copyPassword
                              ? (isZh ? '已复制' : 'Copied')
                              : (isZh ? '复制密码' : 'Copy Password')}
                          </ManagerGhostButton>
                      </DetailActionRow>

                      <ManagerDetailLabel>{isZh ? '备注' : 'Notes'}</ManagerDetailLabel>
                      <ManagerDetailNotes>{selectedItem.notes || (isZh ? '暂无备注' : 'No notes')}</ManagerDetailNotes>

                      <ManagerDetailLabel>{isZh ? '来源 / 数据库 / 文件夹' : 'Source / Database / Folder'}</ManagerDetailLabel>
                      <ManagerDetailField>
                        {(() => {
                          const data = selectedItem.itemData as PasswordEntry;
                          if (data.keepassDatabaseId != null) {
                            return `KeePass #${data.keepassDatabaseId} / ${data.keepassGroupPath || (isZh ? '根目录' : 'Root')}`;
                          }
                          if (data.bitwardenVaultId != null) {
                            return `Bitwarden #${data.bitwardenVaultId} / ${data.bitwardenFolderId || (isZh ? '无文件夹' : 'No Folder')}`;
                          }
                          return isZh ? '本地 Monica' : 'Local Monica';
                        })()}
                      </ManagerDetailField>

                      <ManagerDetailLabel>{isZh ? '归档状态' : 'Archive Status'}</ManagerDetailLabel>
                      <ManagerDetailField>
                        {((selectedItem.itemData as PasswordEntry).isArchived)
                          ? (isZh ? '已归档' : 'Archived')
                          : (isZh ? '正常' : 'Active')}
                      </ManagerDetailField>

                      <ManagerDetailLabel>{isZh ? '创建时间' : 'Created'}</ManagerDetailLabel>
                      <ManagerDetailField>{new Date(selectedItem.createdAt).toLocaleString(isZh ? 'zh-CN' : 'en-US')}</ManagerDetailField>

                      <ManagerDetailLabel>{isZh ? '更新时间' : 'Modified'}</ManagerDetailLabel>
                      <ManagerDetailField>{new Date(selectedItem.updatedAt).toLocaleString(isZh ? 'zh-CN' : 'en-US')}</ManagerDetailField>
                    </>
                  ) : (
                    <ManagerEmptyState>
                      <PencilLine size={24} />
                      <h3>{isZh ? '选择一个条目' : 'Select an item'}</h3>
                      <p>
                        {isZh
                          ? '从左侧列表中选择条目以查看详情与执行操作。'
                          : 'Choose an item from the list to inspect details and actions.'}
                      </p>
                    </ManagerEmptyState>
                  )}
                </ManagerDetailBody>

                <ManagerDetailFooter>
                  <ManagerGhostButton
                    type="button"
                    onClick={() => {
                      if (selectedItem) {
                        showInteractionHint(isZh ? '打开编辑' : 'Opening editor');
                        handleEdit(selectedItem);
                      }
                    }}
                    disabled={!selectedItem}
                  >
                    <PencilLine size={14} />
                    {isZh ? '编辑' : 'Edit'}
                  </ManagerGhostButton>
                  <ManagerGhostButton
                    type="button"
                    $busy={detailActionBusy.archive}
                    onClick={async () => {
                      if (!selectedItem || detailActionBusy.archive) return;
                      setDetailActionBusy((prev) => ({ ...prev, archive: true }));
                      try {
                        await handleToggleArchive(selectedItem);
                      } finally {
                        setDetailActionBusy((prev) => ({ ...prev, archive: false }));
                      }
                    }}
                    disabled={!selectedItem || detailActionBusy.archive}
                  >
                    {((selectedItem?.itemData as PasswordEntry | undefined)?.isArchived)
                      ? <ArchiveRestore size={14} />
                      : <Archive size={14} />}
                    {detailActionBusy.archive
                      ? (isZh ? '处理中...' : 'Working...')
                      : ((selectedItem?.itemData as PasswordEntry | undefined)?.isArchived)
                        ? (isZh ? '取消归档' : 'Unarchive')
                        : (isZh ? '归档' : 'Archive')}
                  </ManagerGhostButton>
                  <ManagerDangerButton
                    type="button"
                    $busy={detailActionBusy.delete}
                    onClick={async () => {
                      if (!selectedItem || detailActionBusy.delete) return;
                      setDetailActionBusy((prev) => ({ ...prev, delete: true }));
                      try {
                        await handleDelete(selectedItem.id);
                      } finally {
                        setDetailActionBusy((prev) => ({ ...prev, delete: false }));
                      }
                    }}
                    disabled={!selectedItem || detailActionBusy.delete}
                  >
                    <Trash2 size={14} />
                    {detailActionBusy.delete ? (isZh ? '处理中...' : 'Working...') : (isZh ? '删除' : 'Delete')}
                  </ManagerDangerButton>
                </ManagerDetailFooter>
              </ManagerDetailPane>
            </ManagerWorkspace>
          </ManagerDashboard>
        </>
      ) : (
        <>
          <SearchContainer $manager={false}>
            <Input placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
            <Search />
          </SearchContainer>

          <MobileFilterRow>
            <MobileFilterSummary>
              {isZh
                ? `当前显示 ${filteredPasswords.length} / ${passwords.length} 条`
                : `Showing ${filteredPasswords.length} of ${passwords.length}`}
              {activeFilterSummary ? ` · ${activeFilterSummary}` : ''}
            </MobileFilterSummary>
            <MobileFilterButton type="button" onClick={openFilterModal}>
              <SlidersHorizontal size={14} />
              {isZh ? '筛选' : 'Filters'}
              {activeFilterCount > 0 && <MobileFilterBadge>{activeFilterCount}</MobileFilterBadge>}
            </MobileFilterButton>
          </MobileFilterRow>

          {filteredPasswords.length === 0 && <EmptyState $manager={false}>{t('common.noItems')}</EmptyState>}

          <PasswordStack $manager={false}>
            {filteredPasswords.map((item) => {
              const data = (item.itemData || {}) as PasswordEntry;
              const fallbackUsername = isZh ? '无用户名' : 'No Username';
              const fallbackWebsite = isZh ? '无网址' : 'No Website';

              return (
                <Card
                  key={item.id}
                  onClick={() => handleEdit(item)}
                  style={{ cursor: 'pointer' }}
                  initial={prefersReducedMotion ? false : { opacity: 0, y: 10 }}
                  animate={prefersReducedMotion ? undefined : { opacity: 1, y: 0 }}
                  transition={prefersReducedMotion ? undefined : { duration: 0.2, ease: 'easeOut' }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <CardTitle>{item.title}</CardTitle>
                      <CardSubtitle>{data.username || fallbackUsername}</CardSubtitle>
                      <CardSubtitle style={{ opacity: 0.6, fontSize: 12 }}>{data.website || fallbackWebsite}</CardSubtitle>
                      {data.categoryId && (
                        <CategoryBadge>
                          {categories.find(c => c.id === data.categoryId)?.name || (isZh ? '未知分类' : 'Unknown')}
                        </CategoryBadge>
                      )}
                      {data.isArchived && (
                        <CategoryBadge style={{ background: '#3a435a', color: '#d7deef' }}>
                          {isZh ? '已归档' : 'Archived'}
                        </CategoryBadge>
                      )}
                    </div>
                    {item.isFavorite && <Star size={16} fill="#FFC107" color="#FFC107" />}
                  </div>
                  <CardActions onClick={(e) => e.stopPropagation()}>
                    <IconButton onClick={() => { void handleCopy(data.password, 'password'); }} title={t('common.copy')}>
                      <Copy />
                    </IconButton>
                    <IconButton onClick={() => handleToggleArchive(item)} title={data.isArchived ? (isZh ? '取消归档' : 'Unarchive') : (isZh ? '归档' : 'Archive')}>
                      {data.isArchived ? <ArchiveRestore /> : <Archive />}
                    </IconButton>
                    <IconButton onClick={() => handleDelete(item.id)} title={t('common.delete')}>
                      <Trash2 />
                    </IconButton>
                  </CardActions>
                </Card>
              );
            })}
          </PasswordStack>

          <FAB $manager={false} onClick={() => { resetForm(); setShowModal(true); }}>
            <Plus />
          </FAB>
        </>
      )}

      {showFilterModal && (
        <Modal onClick={() => setShowFilterModal(false)}>
          <FilterModalContent onClick={(e) => e.stopPropagation()}>
            <FilterModalHeader>
              <FilterHeaderMeta>
                <FilterHeaderTitle>{isZh ? '筛选与排序' : 'Filters & Sort'}</FilterHeaderTitle>
                <FilterHeaderSub>
                  {isZh ? '集中配置来源、数据库、文件夹、分类和排序。' : 'Adjust source, database, folder, category, and sorting in one place.'}
                </FilterHeaderSub>
              </FilterHeaderMeta>
              <FilterCloseButton type="button" onClick={() => setShowFilterModal(false)} aria-label={isZh ? '关闭筛选弹窗' : 'Close filter modal'}>
                <X size={16} />
              </FilterCloseButton>
            </FilterModalHeader>

            <FilterModalScroll>
              <FilterBlock>
                <FilterSectionTitle>{isZh ? '快速筛选' : 'Quick Filter'}</FilterSectionTitle>
                <FilterContainer $manager={false}>
                  <FilterChip $manager={false} active={filterDraft.quick === 'all'} onClick={() => setFilterDraft((prev) => ({ ...prev, quick: 'all' }))}>
                    {isZh ? '全部' : 'All'}
                  </FilterChip>
                  <FilterChip $manager={false} active={filterDraft.quick === 'starred'} onClick={() => setFilterDraft((prev) => ({ ...prev, quick: 'starred' }))}>
                    <Star size={14} />
                    {isZh ? '星标' : 'Starred'}
                  </FilterChip>
                  <FilterChip $manager={false} active={filterDraft.quick === 'uncategorized'} onClick={() => setFilterDraft((prev) => ({ ...prev, quick: 'uncategorized' }))}>
                    {isZh ? '未分类' : 'Uncategorized'}
                  </FilterChip>
                  <FilterChip $manager={false} active={filterDraft.quick === 'localOnly'} onClick={() => setFilterDraft((prev) => ({ ...prev, quick: 'localOnly' }))}>
                    {isZh ? '仅本地' : 'Local Only'}
                  </FilterChip>
                  <FilterChip $manager={false} active={filterDraft.quick === 'archived'} onClick={() => setFilterDraft((prev) => ({ ...prev, quick: 'archived' }))}>
                    <Archive size={14} />
                    {isZh ? '归档' : 'Archived'}
                  </FilterChip>
                </FilterContainer>
              </FilterBlock>

              <FilterBlock>
                <FilterSectionTitle>{isZh ? '数据源' : 'Data Source'}</FilterSectionTitle>
                <FilterSelectGrid>
                  <div>
                    <FilterSelectLabel>{isZh ? '来源' : 'Source'}</FilterSelectLabel>
                    <FilterSelect
                      value={filterDraft.source}
                      onChange={(e) => {
                        const next = e.target.value as SourceFilter;
                        setFilterDraft((prev) => ({
                          ...prev,
                          source: next,
                          database: 'all',
                          folder: 'all',
                        }));
                      }}
                    >
                      <option value="all">{isZh ? '全部来源' : 'All Sources'}</option>
                      <option value="local">{isZh ? '本地 Monica' : 'Local Monica'}</option>
                      <option value="keepass">KeePass</option>
                      <option value="bitwarden">Bitwarden</option>
                    </FilterSelect>
                  </div>
                  <div>
                    <FilterSelectLabel>{isZh ? '数据库' : 'Database'}</FilterSelectLabel>
                    <FilterSelect
                      value={filterDraft.database}
                      onChange={(e) => {
                        const next = e.target.value;
                        setFilterDraft((prev) => ({
                          ...prev,
                          database: next,
                          folder: 'all',
                        }));
                      }}
                    >
                      <option value="all">{isZh ? '全部数据库' : 'All Databases'}</option>
                      {(filterDraft.source === 'all' || filterDraft.source === 'keepass') &&
                        databaseOptions.keepass.map((id) => <option key={`f-kp:${id}`} value={`kp:${id}`}>{`KeePass #${id}`}</option>)}
                      {(filterDraft.source === 'all' || filterDraft.source === 'bitwarden') &&
                        databaseOptions.bitwarden.map((id) => <option key={`f-bw:${id}`} value={`bw:${id}`}>{`Bitwarden #${id}`}</option>)}
                    </FilterSelect>
                  </div>
                  <div>
                    <FilterSelectLabel>{isZh ? '文件夹' : 'Folder'}</FilterSelectLabel>
                    <FilterSelect
                      value={filterDraft.folder}
                      onChange={(e) => setFilterDraft((prev) => ({ ...prev, folder: e.target.value }))}
                    >
                      <option value="all">{isZh ? '全部文件夹' : 'All Folders'}</option>
                      {draftFolderOptions.map((folder) => (
                        <option key={`f-folder:${folder}`} value={folder}>
                          {folder === '__root__'
                            ? (isZh ? 'KeePass 根目录' : 'KeePass Root')
                            : folder === '__none__'
                              ? (isZh ? '无 Bitwarden 文件夹' : 'No Bitwarden Folder')
                              : folder}
                        </option>
                      ))}
                    </FilterSelect>
                  </div>
                </FilterSelectGrid>
              </FilterBlock>

              <FilterBlock>
                <FilterSectionTitle>{isZh ? '分类' : 'Category'}</FilterSectionTitle>
                <FilterContainer $manager={false}>
                  <FilterChip $manager={false} active={filterDraft.category === 'all'} onClick={() => setFilterDraft((prev) => ({ ...prev, category: 'all' }))}>
                    {isZh ? '全部分类' : 'All Categories'}
                  </FilterChip>
                  {categories.map((cat) => (
                    <FilterChip
                      $manager={false}
                      key={`f-cat:${cat.id}`}
                      active={filterDraft.category === cat.id}
                      onClick={() => setFilterDraft((prev) => ({ ...prev, category: cat.id }))}
                    >
                      {cat.name}
                    </FilterChip>
                  ))}
                </FilterContainer>
              </FilterBlock>

              <FilterBlock>
                <FilterSectionTitle>{isZh ? '排序' : 'Sort'}</FilterSectionTitle>
                <FilterSelect
                  value={filterDraft.sortBy}
                  onChange={(e) => setFilterDraft((prev) => ({ ...prev, sortBy: e.target.value as 'updated' | 'created' | 'title' }))}
                >
                  <option value="updated">{isZh ? '按更新时间' : 'Sort: Updated'}</option>
                  <option value="created">{isZh ? '按创建时间' : 'Sort: Created'}</option>
                  <option value="title">{isZh ? '按名称' : 'Sort: Name'}</option>
                </FilterSelect>
              </FilterBlock>
            </FilterModalScroll>

            <FilterActionBar>
              <Button variant="secondary" onClick={resetFilterDraft}>
                {isZh ? '重置' : 'Reset'}
              </Button>
              <Button onClick={applyFilterDraft}>
                {isZh ? '应用筛选' : 'Apply Filters'}
              </Button>
            </FilterActionBar>
          </FilterModalContent>
        </Modal>
      )}

      {showModal && (
        <Modal onClick={() => { setShowModal(false); resetForm(); }}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>{editingId ? t('common.edit') : t('common.add')} {t('passwords.title')}</ModalTitle>
              <FavoriteIcon
                active={form.isFavorite}
                onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })}
                size={24}
              />
            </ModalHeader>

            <Input label={`${t('passwords.title')} *`} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />

            {/* Category Selector with Add New */}
            <div style={{ marginTop: 12 }}>
              <label style={{ fontSize: 12, color: 'gray', marginBottom: 4, display: 'block' }}>{t('passwords.category')}</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <StyledSelect
                  value={form.categoryId}
                  onChange={(e) => setForm({ ...form, categoryId: Number(e.target.value) })}
                >
                  <option value={0}>{isZh ? '无分类' : 'No Category'}</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </StyledSelect>
                <AddCategoryButton
                  onClick={async () => {
                    const name = prompt(isZh ? '输入新分类名称：' : 'Enter new category name:');
                    if (name?.trim()) {
                      const newCat = await saveCategory(name.trim());
                      setCategories([...categories, newCat]);
                      setForm({ ...form, categoryId: newCat.id });
                    }
                  }}
                  title={isZh ? '新建分类' : 'Add Category'}
                >
                  <FolderPlus />
                </AddCategoryButton>
              </div>
            </div>

            <div style={{ marginTop: 12 }}>
              <label style={{ fontSize: 12, color: 'gray', marginBottom: 4, display: 'block' }}>{isZh ? '数据库来源' : 'Database Source'}</label>
              <StyledSelect
                value={form.sourceType}
                onChange={(e) => setForm({ ...form, sourceType: e.target.value as SourceFilter, databaseId: '', folderPath: '' })}
              >
                <option value="local">{isZh ? '本地 Monica' : 'Local Monica'}</option>
                <option value="keepass">KeePass</option>
                <option value="bitwarden">Bitwarden</option>
              </StyledSelect>
            </div>

            {form.sourceType !== 'local' && (
              <>
                <Input
                  label={form.sourceType === 'keepass' ? (isZh ? 'KeePass 数据库 ID' : 'KeePass Database ID') : (isZh ? 'Bitwarden Vault ID' : 'Bitwarden Vault ID')}
                  value={form.databaseId}
                  onChange={(e) => setForm({ ...form, databaseId: e.target.value.replace(/[^\d]/g, '') })}
                />
                <Input
                  label={form.sourceType === 'keepass' ? (isZh ? '分组路径(文件夹)' : 'Group Path (Folder)') : (isZh ? '文件夹 ID' : 'Folder ID')}
                  value={form.folderPath}
                  onChange={(e) => setForm({ ...form, folderPath: e.target.value })}
                />
              </>
            )}

            <Input label={t('passwords.website')} value={form.website} onChange={(e) => setForm({ ...form, website: e.target.value })} />
            <Input label={t('passwords.username')} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />

            {/* Password with visibility toggle and generator */}
            <PasswordField>
              <Input
                label={`${t('passwords.password')} *`}
                type={passwordVisible ? 'text' : 'password'}
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                style={{ flex: 1, paddingRight: 80 }}
              />
              <PasswordActions>
                <IconButton onClick={handleGeneratePassword} title="Generate"><RefreshCw size={18} /></IconButton>
                <IconButton onClick={() => setPasswordVisible(!passwordVisible)}>
                  {passwordVisible ? <EyeOff size={18} /> : <Eye size={18} />}
                </IconButton>
              </PasswordActions>
            </PasswordField>

            {/* Collapsible: Personal Info */}
            <SectionHeader expanded={showPersonalInfo} onClick={() => setShowPersonalInfo(!showPersonalInfo)}>
              <span>{isZh ? '个人信息' : 'Personal Info'}</span>
              {showPersonalInfo ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            </SectionHeader>
            {showPersonalInfo && (
              <SectionContent>
                <Input label={isZh ? '邮箱' : 'Email'} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
                <Input label={isZh ? '手机号' : 'Phone'} type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </SectionContent>
            )}

            {/* Notes */}
            <div style={{ marginTop: 12 }}>
              <label style={{ fontSize: 12, color: 'gray' }}>{isZh ? '备注' : 'Notes'}</label>
              <textarea
                value={form.notes}
                onChange={(e) => setForm({ ...form, notes: e.target.value })}
                style={{
                  width: '100%', minHeight: 80, padding: 12, marginTop: 4,
                  borderRadius: 12, border: '1px solid currentColor', fontSize: 14,
                  resize: 'vertical', fontFamily: 'inherit',
                  background: 'transparent', color: 'inherit'
                }}
              />
            </div>

            <ButtonRow>
              <Button variant="text" onClick={() => { setShowModal(false); resetForm(); }}>{t('common.cancel')}</Button>
              <Button onClick={handleSave} disabled={!form.title || !form.password}>{t('common.save')}</Button>
            </ButtonRow>
          </ModalContent>
        </Modal>
      )}
      <InteractionHint $show={!!interactionHint}>{interactionHint}</InteractionHint>
    </Container>
  );
};
