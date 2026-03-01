import styled from 'styled-components';
import { motion } from 'framer-motion';

export const Card = styled(motion.div)`
  background-color: ${({ theme }) => theme.colors.surface};
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  margin-bottom: 12px;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
  overflow: hidden;

  &:hover {
    background-color: ${({ theme }) => theme.colors.primaryContainer}10; /* 10% opacity */
    transform: translateY(-1px);
    box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  }

  &:active {
    transform: translateY(0) scale(0.98);
    background-color: ${({ theme }) => theme.colors.primaryContainer}20; /* 20% opacity */
  }
`;

export const CardTitle = styled.h3`
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.onSurface};
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const CardSubtitle = styled.p`
  margin: 4px 0 0;
  font-size: 14px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
`;
