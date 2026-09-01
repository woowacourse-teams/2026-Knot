import { css, type Theme } from "@emotion/react";
import styled from "@emotion/styled";
import type { ComponentProps } from "react";

/**
 * 입력창이 그려야 할 상태.
 *
 * - `empty` : 아직 입력하지 않음 (피그마 status=입력 전)
 * - `filled` : 값이 들어 있음 (피그마 status=입력 중)
 * - `error` : 유효하지 않은 값 (피그마 status=입력 에러)
 * - `success` : 검증을 통과한 값 (피그마 Field/TextField/Code status=인증 완료)
 */
export type InputStatus = "empty" | "filled" | "error" | "success";

/**
 * 입력창의 형태·타이포 묶음.
 *
 * - `text` : 좌측 정렬 본문 텍스트, 높이 52 (피그마 Field/TextField)
 * - `code` : 중앙 정렬·넓은 자간의 코드 텍스트, 높이 60 (피그마 Field/TextField/Code)
 * - `copy` : 복사용 읽기 전용 링크 텍스트, 높이 52 (피그마 Field/Copy). `readOnly`와 함께 써요
 */
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
    padding: 0.96875rem 1rem; /* 15.5px 16px */

    &::placeholder {
      ${theme.text.caption02};
    }
  `,
  code: (theme: Theme) => css`
    padding: 0.6875rem 1rem;
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
    padding: 0.71875rem 0.75rem;
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
 * 값이 비었는지·에러인지를 스스로 판단하지 않고 `status`로 받아 그리기만 해요.
 * 상태 판단은 값을 들고 있는 상위 컴포넌트가 담당합니다.
 *
 * `status`는 색(배경·테두리)만, `variant`는 형태·타이포만 결정하며 서로 독립적으로 조합돼요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596 Field/TextField 컴포넌트 세트
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-595 status=입력 전 (`empty`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-597 status=입력 중 (`filled`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325 status=입력 에러 (`error`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=664-552 Field/TextField/Code status=인증 완료 (`success`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-910 Field/TextField/Code 컴포넌트 세트 (`variant="code"`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4925 Field/Copy 컴포넌트 세트 (`variant="copy"`)
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
