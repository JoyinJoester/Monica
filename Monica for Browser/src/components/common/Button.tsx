import styled from 'styled-components';
import { motion } from 'framer-motion';

interface ButtonProps {
    variant?: 'primary' | 'secondary' | 'text';
    fullWidth?: boolean;
}

export const Button = styled(motion.button)<ButtonProps>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 22px;
  border-radius: 999px;
  font-family: inherit;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
  outline: none;
  width: ${({ fullWidth }) => (fullWidth ? '100%' : 'auto')};
  transition:
    background-color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    border-color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    box-shadow 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    transform 140ms cubic-bezier(0.2, 0.6, 0.2, 1);

  background-color: ${
    ({ theme, variant }) =>
      variant === 'secondary' ? theme.colors.surfaceVariant :
      variant === 'text'      ? 'transparent' :
                                theme.colors.primary
  };

  color: ${
    ({ theme, variant }) =>
      variant === 'secondary' ? theme.colors.onSurface :
      variant === 'text'      ? theme.colors.primary :
                                theme.colors.onPrimary
  };

  border-color: ${
    ({ theme, variant }) =>
      variant === 'secondary' ? theme.colors.outline :
      variant === 'text'      ? 'transparent' :
                                'transparent'
  };

  box-shadow: ${
    ({ variant }) =>
      variant === 'primary' ? '0 2px 8px rgba(0,0,0,0.10)' : 'none'
  };

  &:hover {
    transform: translateY(-1px);
    background-color: ${
      ({ theme, variant }) =>
        variant === 'secondary' ? theme.colors.outline :
        variant === 'text'      ? theme.colors.surfaceVariant :
                                  theme.colors.primaryContainer
    };
    color: ${
      ({ theme, variant }) =>
        variant === 'text' ? theme.colors.primary : undefined
    };
    border-color: ${
      ({ theme, variant }) =>
        variant === 'primary' ? theme.colors.primary : undefined
    };
    box-shadow: ${
      ({ variant }) =>
        variant === 'primary' ? '0 4px 16px rgba(0,0,0,0.13)' : 'none'
    };
  }

  &:disabled {
    background-color: ${({ theme }) => theme.colors.surfaceVariant};
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    border-color: transparent;
    cursor: not-allowed;
    box-shadow: none;
    transform: none;
    opacity: 0.6;
  }
`;

Button.defaultProps = {
    whileTap: { scale: 0.97 },
    variant: 'primary',
};
