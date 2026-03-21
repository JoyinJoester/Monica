import React from 'react';
import styled from 'styled-components';
import { motion } from 'framer-motion';

const InputContainer = styled.div`
  position: relative;
  margin-bottom: 16px;
  width: 100%;
`;

const StyledInput = styled.input<{ hasError?: boolean }>`
  width: 100%;
  padding: 11px 14px;
  border-radius: 10px;
  border: 1px solid ${({ theme, hasError }) => hasError ? theme.colors.error : theme.colors.outline};
  background-color: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-size: 14px;
  font-family: inherit;
  transition:
    border-color 140ms cubic-bezier(0.2, 0.6, 0.2, 1),
    box-shadow 140ms cubic-bezier(0.2, 0.6, 0.2, 1);
  outline: none;

  &:focus {
    border-color: ${({ theme, hasError }) => hasError ? theme.colors.error : theme.colors.primary};
    box-shadow: 0 0 0 3px ${({ theme, hasError }) =>
      hasError ? `${theme.colors.errorContainer}` : theme.colors.primaryContainer};
  }

  &::placeholder {
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
    opacity: 0.7;
  }
`;

const Label = styled.label`
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  letter-spacing: 0.02em;
`;

const ErrorText = styled(motion.span)`
  color: ${({ theme }) => theme.colors.error};
  font-size: 12px;
  margin-top: 4px;
  display: block;
  margin-left: 2px;
`;

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
    label?: string;
    error?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(({ label, error, ...props }, ref) => {
    return (
        <InputContainer>
            {label && <Label>{label}</Label>}
            <StyledInput ref={ref} hasError={!!error} {...props} />
            {error && (
                <ErrorText
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.14 }}
                >
                    {error}
                </ErrorText>
            )}
        </InputContainer>
    );
});
