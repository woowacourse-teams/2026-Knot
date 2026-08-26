import { css } from "@emotion/react";
import styled from "@emotion/styled";
import type { InputHTMLAttributes } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  isError?: boolean;
}

/**
 * 단일 줄 텍스트 입력 UI.
 *
 * 값이 비어 있을 때(입력 전)와 값이 있을 때(입력 중)의 스타일은
 * `:placeholder-shown`으로 구분하므로 제어·비제어 어느 쪽으로 써도 동작해요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596 Field/TextField 컴포넌트 세트
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-595 status=입력 전
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-597 status=입력 중
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325 status=입력 에러
 */
export default function Input({ isError = false, ...props }: InputProps) {
  return <StyledInput isError={isError} aria-invalid={isError} {...props} />;
}

const StyledInput = styled.input<{ isError: boolean }>`
  padding: 0.96rem 1rem;
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 0.875rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.body01};

  &::placeholder {
    color: ${({ theme }) => theme.neutral[500]};
    ${({ theme }) => theme.text.caption02};
  }

  /* 아직 입력하지 않은 상태 */
  &:placeholder-shown {
    background-color: ${({ theme }) => theme.neutral[100]};
    border-color: ${({ theme }) => theme.neutral[300]};
  }

  &:focus {
    outline: none;
    background-color: ${({ theme }) => theme.neutral[0]};
    border-color: ${({ theme }) => theme.primary};
  }

  ${({ isError, theme }) =>
    isError &&
    css`
      &,
      &:placeholder-shown,
      &:focus {
        background-color: ${theme.sub.warning[50]};
        border-color: ${theme.sub.warning[600]};
      }
    `};
`;
