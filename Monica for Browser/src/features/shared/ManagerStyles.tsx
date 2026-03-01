import styled, { keyframes } from 'styled-components';

export const ManagerContainer = styled.div<{ $manager: boolean }>`
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

export const ManagerDashboard = styled.section`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

export const ManagerTopBar = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
`;

export const ManagerSearchWrap = styled.div`
  position: relative;
  flex: 1;
  min-width: 240px;

  svg {
    position: absolute;
    right: 16px;
    top: 50%;
    transform: translateY(-50%);
    color: #8d94bf;
    width: 20px;
    height: 20px;
    pointer-events: none;
  }
`;

export const ManagerSearchInput = styled.input`
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

export const ManagerPrimaryButton = styled.button`
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

export const ManagerWorkspace = styled.div`
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

export const ManagerListPane = styled.div`
  border-right: 1px solid rgba(115, 145, 201, 0.2);
  background: rgba(9, 17, 34, 0.78);

  @media (max-width: 1120px) {
    border-right: 0;
    border-bottom: 1px solid rgba(115, 145, 201, 0.2);
  }
`;

export const ManagerRows = styled.div`
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

export const ManagerRow = styled.button<{ $active: boolean }>`
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

export const ManagerRowHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
`;

export const ManagerRowTitle = styled.div`
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  padding-right: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const ManagerMeta = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 8px;
  margin-top: 9px;
`;

export const ManagerMetaItem = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #9db2dc;
  font-size: 12px;
  line-height: 1.3;
  min-width: 0;

  svg {
    flex-shrink: 0;
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

export const ManagerDetailPane = styled.aside`
  background: rgba(13, 22, 42, 0.88);
  display: flex;
  flex-direction: column;
`;

export const ManagerDetailHeader = styled.div`
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

export const ManagerDetailBody = styled.div`
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
`;

export const ManagerDetailTitle = styled.h3`
  margin: 0;
  color: #f0f5ff;
  font-size: clamp(24px, 3vw, 32px);
  line-height: 1.05;
`;

export const ManagerDetailLabel = styled.label`
  color: #91a7d3;
  font-size: 11px;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  margin-top: 4px;
`;

export const ManagerDetailField = styled.div`
  border: 1px solid rgba(116, 147, 203, 0.25);
  border-radius: 10px;
  background: rgba(8, 17, 34, 0.72);
  color: #ecf3ff;
  padding: 10px 12px;
  font-size: 14px;
  word-break: break-all;
`;

export const ManagerDetailNotes = styled.div`
  min-height: 92px;
  border: 1px solid rgba(116, 147, 203, 0.25);
  border-radius: 10px;
  background: rgba(8, 17, 34, 0.72);
  color: #a2b6df;
  padding: 10px 12px;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
`;

export const ManagerDetailFooter = styled.div`
  padding: 14px 16px 16px;
  border-top: 1px solid rgba(115, 145, 201, 0.2);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;

  @media (max-width: 520px) {
    grid-template-columns: 1fr;
  }
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

export const ManagerGhostButton = styled.button<{ $feedback?: boolean; $busy?: boolean }>`
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

export const ManagerDangerButton = styled(ManagerGhostButton)`
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

export const ManagerEmptyState = styled.div`
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

export const ManagerFavorite = styled.button<{ $active: boolean }>`
  border: 0;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: ${({ $active }) => ($active ? '#ffd34a' : '#6f86b8')};
`;

export const ManagerChip = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(126, 150, 205, 0.4);
  background: rgba(18, 31, 57, 0.76);
  color: #bfd1f2;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.25px;
`;

export const InteractionHint = styled.div<{ $show: boolean }>`
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
