import { css, type Theme } from "@emotion/react";
import styled from "@emotion/styled";
import type { InputHTMLAttributes } from "react";

/**
 * 입력창이 그려야 할 상태.
 *
 * - `empty` : 아직 입력하지 않음 (피그마 status=입력 전)
 * - `filled` : 값이 들어 있음 (피그마 status=입력 중)
 * - `error` : 유효하지 않은 값 (피그마 status=입력 에러)
 * - `success` : 검증을 통과한 값 (피그마 Field/TextField/Code status=인증 완료)
 */
export type InputStatus = "empty" | "filled" | "error" | "success";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  status: InputStatus;
}

const STATUS_STYLE = {
  empty: (theme: Theme) => css`
    background-color: ${theme.neutral[100]};
    border-color: ${theme.neutral[300]};

    &:focus {
      background-color: ${theme.neutral[0]};
      border-color: ${theme.primary};
    }
  `,
  filled: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    border-color: ${theme.neutral[200]};

    &:focus {
      border-color: ${theme.primary};
    }
  `,
  error: (theme: Theme) => css`
    background-color: ${theme.sub.warning[50]};
    border-color: ${theme.sub.warning[600]};
  `,
  success: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    border-color: ${theme.sub.accent[500]};
  `,
};

/**
 * 단일 줄 텍스트 입력 UI.
 *
 * 값이 비었는지·에러인지를 스스로 판단하지 않고 `status`로 받아 그리기만 해요.
 * 상태 판단은 값을 들고 있는 상위 컴포넌트가 담당합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596 Field/TextField 컴포넌트 세트
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-595 status=입력 전 (`empty`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-597 status=입력 중 (`filled`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325 status=입력 에러 (`error`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=664-552 Field/TextField/Code status=인증 완료 (`success`)
 */
export default function Input({ status, ...props }: InputProps) {
  return (
    <StyledInput status={status} aria-invalid={status === "error"} {...props} />
  );
}

const StyledInput = styled.input<{ status: InputStatus }>`
  padding: 0.96rem 1rem;
  border: 1px solid;
  border-radius: 0.875rem;
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.body01};

  &::placeholder {
    color: ${({ theme }) => theme.neutral[500]};
    ${({ theme }) => theme.text.caption02};
  }

  &:focus {
    outline: none;
  }

  ${({ status, theme }) => STATUS_STYLE[status](theme)};
`;
