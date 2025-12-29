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
  padding: 16px 16px;
  border-radius: 12px;
  border: 1px solid ${({ theme, hasError }) => hasError ? theme.colors.error : theme.colors.outline};
  background-color: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-size: 16px;
  font-family: inherit;
  transition: all 0.2s ease;
  outline: none;

  &:focus {
    border-color: ${({ theme, hasError }) => hasError ? theme.colors.error : theme.colors.primary};
    border-width: 2px;
    padding: 15px 15px; /* Adjust for border width inc */
  }

  &::placeholder {
    color: ${({ theme }) => theme.colors.onSurfaceVariant};
  }
`;

const Label = styled.label`
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  margin-left: 4px;
`;

const ErrorText = styled(motion.span)`
  color: ${({ theme }) => theme.colors.error};
  font-size: 12px;
  margin-top: 4px;
  display: block;
  margin-left: 4px;
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
                    initial={{ opacity: 0, y: -5 }}
                    animate={{ opacity: 1, y: 0 }}
                >
                    {error}
                </ErrorText>
            )}
        </InputContainer>
    );
});
