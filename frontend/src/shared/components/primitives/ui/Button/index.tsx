import { css, type SerializedStyles, type Theme } from "@emotion/react";
import styled from "@emotion/styled";
import type { ButtonHTMLAttributes } from "react";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  size?: ButtonSize;
  variant?: ButtonVariant;
  isLoading?: boolean;
}

export default function Button({
  size = "md",
  variant = "filled",
  isLoading = false,
  disabled = false,
  type = "button",
  children,
  ...props
}: ButtonProps) {
  const status: ButtonStatus = isLoading
    ? "loading"
    : disabled
      ? "inactive"
      : "active";

  return (
    <Wrapper
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading}
      $size={size}
      $variant={variant}
      $status={status}
      {...props}
    >
      {children}
    </Wrapper>
  );
}

const Wrapper = styled.button<{
  $size: ButtonSize;
  $variant: ButtonVariant;
  $status: ButtonStatus;
}>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  transition:
    background-color 0.3s ease-in,
    color 0.3s ease-in,
    box-shadow 0.3s ease-in;

  ${({ theme }) => theme.text.label01};

  width: ${({ $size }) => BUTTON_SIZE[$size].width};
  height: ${({ $size }) => BUTTON_SIZE[$size].height};
  padding: 0 ${({ $size }) => BUTTON_SIZE[$size].paddingX};
  gap: ${({ $size }) => BUTTON_SIZE[$size].gap};
  border-radius: ${({ $size }) => BUTTON_SIZE[$size].borderRadius};

  ${({ theme, $variant, $status }) =>
    buttonAppearance(theme)[$variant][$status]};

  & > svg {
    flex-shrink: 0;
    width: ${({ $size }) => BUTTON_SIZE[$size].iconSize};
    height: ${({ $size }) => BUTTON_SIZE[$size].iconSize};
  }

  &[aria-busy="true"] {
    cursor: progress;
  }

  /* 포커스 링 아웃라인 논의 필요 */
  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

export type ButtonSize = "lg" | "md" | "sm";

export type ButtonVariant = "filled" | "outline";

type ButtonStatus = "active" | "loading" | "inactive";

const BUTTON_SIZE = {
  lg: {
    width: "22.5rem" /* 360px */,
    height: "3.5rem" /* 56px */,
    paddingX: "1.25rem" /* 20px */,
    borderRadius: "0.875rem" /* 14px */,
    gap: "0.5rem" /* 8px */,
    iconSize: "1.5rem" /* 24px */,
    spinnerSize: "1.5rem" /* 24px */,
  },
  md: {
    width: "auto",
    height: "3rem" /* 48px */,
    paddingX: "1.125rem" /* 18px */,
    borderRadius: "0.8125rem" /* 13px */,
    gap: "0.4375rem" /* 7px */,
    iconSize: "1.125rem" /* 18px */,
    spinnerSize: "1.375rem" /* 22px */,
  },
  sm: {
    width: "auto",
    height: "2.5rem" /* 40px */,
    paddingX: "1rem" /* 16px */,
    borderRadius: "0.75rem" /* 12px */,
    gap: "0.375rem" /* 6px */,
    iconSize: "1rem" /* 16px */,
    spinnerSize: "1.25rem" /* 20px */,
  },
} as const satisfies Record<ButtonSize, Record<string, string>>;

const buttonAppearance = (theme: Theme) => {
  return {
    filled: {
      active: css`
        background-color: ${theme.primary};
        color: ${theme.neutral[0]};
      `,
      loading: css`
        background-color: ${theme.primary};
        color: ${theme.neutral[0]};
      `,
      inactive: css`
        background-color: ${theme.neutral[200]};
        color: ${theme.neutral[400]};
      `,
    },
    outline: {
      active: css`
        background-color: ${theme.neutral[0]};
        color: ${theme.neutral[700]};
        box-shadow: inset 0 0 0 1px ${theme.neutral[200]};
      `,
      loading: css`
        background-color: ${theme.neutral[0]};
        color: ${theme.neutral[700]};
        box-shadow: inset 0 0 0 1px ${theme.neutral[200]};
      `,
      inactive: css`
        background-color: ${theme.neutral[0]};
        color: ${theme.neutral[400]};
        box-shadow: inset 0 0 0 1px ${theme.neutral[200]};
      `,
    },
  } satisfies Record<ButtonVariant, Record<ButtonStatus, SerializedStyles>>;
};
