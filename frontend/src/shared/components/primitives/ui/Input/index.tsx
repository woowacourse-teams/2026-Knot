import { css, type Theme } from "@emotion/react";
import styled from "@emotion/styled";
import type { ComponentProps } from "react";

export type InputStatus = "empty" | "filled" | "error" | "success";

export type InputVariant = "text" | "code" | "copy";

/**
 * `InputHTMLAttributes` 대신 `ComponentProps<"input">`을 쓰는 이유가 있어요.
 * 전자에는 `ref`가 없어서, 리액트 19에서 `ref`가 일반 prop이 되었는데도
 * 타입에서 막힙니다. 후자는 `ref`까지 포함한 `<input>`의 전체 props예요.
 */
interface InputProps extends ComponentProps<"input"> {
  status: InputStatus;
  variant?: InputVariant;
}

const VARIANT_STYLE = {
  text: (theme: Theme) => css`
    ${theme.text.body01};
    padding: 0.96875rem 1rem; /* 15.5px 16px, 높이 52px */

    &::placeholder {
      ${theme.text.caption02};
    }
  `,
  code: (theme: Theme) => css`
    padding: 0.6875rem 1rem; /* 높이 60px */
    text-align: center;
    font-size: 1.5rem; /* 24px */
    font-weight: 700;
    line-height: 1.5;
    letter-spacing: 0.25em; /* 6px */

    &::placeholder {
      ${theme.text.heading01};
    }
  `,
  copy: (theme: Theme) => css`
    padding: 0.71875rem 0.75rem; /* 높이 52px */
    color: ${theme.neutral[700]};
    ${theme.text.body02};
  `,
} as const satisfies Record<
  InputVariant,
  (theme: Theme) => ReturnType<typeof css>
>;

const STATUS_STYLE = {
  empty: (theme: Theme) => css`
    background-color: ${theme.neutral[100]};
    border-color: ${theme.neutral[300]};

    &:focus {
      outline: none;
      background-color: ${theme.neutral[0]};
    }
  `,
  filled: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    border-color: ${theme.neutral[200]};

    &:focus {
      outline: none;
      border-color: ${theme.neutral[400]};
    }
  `,
  error: (theme: Theme) => css`
    background-color: ${theme.sub.warning[50]};
    border-color: ${theme.sub.warning[200]};

    &:focus {
      outline: none;
      border-color: ${theme.sub.warning[600]};
    }
  `,
  success: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    border-color: ${theme.sub.accent[200]};

    &:focus {
      outline: none;
      border-color: ${theme.sub.accent[500]};
    }
  `,
} as const satisfies Record<
  InputStatus,
  (theme: Theme) => ReturnType<typeof css>
>;

/**
 * 단일 줄 텍스트 입력 UI.
 *
 * 동작 규칙은 스토리북 `Shared/Input`에서 확인해요.
 */
export default function Input({
  status,
  variant = "text",
  ...props
}: InputProps) {
  return (
    <StyledInput
      $status={status}
      variant={variant}
      aria-invalid={status === "error"}
      {...props}
    />
  );
}

const StyledInput = styled.input<{
  $status: InputStatus;
  variant: InputVariant;
}>`
  border: 1px solid;
  border-radius: 0.875rem;
  color: ${({ theme }) => theme.neutral[900]};
  transition: all 0.3s ease-in;

  &::placeholder {
    color: ${({ theme }) => theme.neutral[500]};
  }

  ${({ variant, theme }) => VARIANT_STYLE[variant](theme)};
  ${({ $status, theme }) => STATUS_STYLE[$status](theme)};
`;
