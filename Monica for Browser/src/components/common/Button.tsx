import styled from 'styled-components';
import { motion } from 'framer-motion';

interface ButtonProps {
    variant?: 'primary' | 'secondary' | 'text';
    fullWidth?: boolean;
}

export const Button = styled(motion.button) <ButtonProps>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 24px;
  border-radius: 100px; /* Pillow shape for M3 */
  font-family: inherit;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  border: none;
  outline: none;
  width: ${({ fullWidth }) => (fullWidth ? '100%' : 'auto')};
  
  /* Primary Variant */
  background-color: ${({ theme, variant }) =>
        variant === 'secondary' ? theme.colors.secondaryContainer :
            variant === 'text' ? 'transparent' :
                theme.colors.primary};
    
  color: ${({ theme, variant }) =>
        variant === 'secondary' ? theme.colors.onSecondaryContainer :
            variant === 'text' ? theme.colors.primary :
                theme.colors.onPrimary};

  box-shadow: ${({ variant }) => variant === 'primary' ? '0 2px 4px rgba(0,0,0,0.1)' : 'none'};
  transition: box-shadow 0.2s ease;

  &:hover {
    box-shadow: ${({ variant }) => variant === 'primary' ? '0 4px 8px rgba(0,0,0,0.15)' : 'none'};
    background-color: ${({ theme, variant }) =>
        variant === 'secondary' ? theme.colors.secondary : // Hover state approx
            variant === 'text' ? theme.colors.surfaceVariant : // subtle hover
                theme.colors.primary}; // Need darker shade ideally, using filter
    filter: brightness(0.95);
  }

  &:disabled {
    background-color: ${({ theme }) => theme.colors.surfaceVariant};
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    cursor: not-allowed;
    box-shadow: none;
  }
`;

Button.defaultProps = {
    whileTap: { scale: 0.95 },
    variant: 'primary',
};
