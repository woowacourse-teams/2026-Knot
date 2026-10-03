import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";
import TextField from "@primitives/ui/TextField";
import type { ComponentProps } from "react";

interface CountTextFieldProps extends ComponentProps<typeof TextField> {
  /** 입력할 수 있는 최대 글자 수. 카운터의 분모이자 입력창의 `maxlength`가 돼요. */
  maxLength: number;
}

/**
 * 글자 수 카운터가 달린 입력 필드.
 *
 * 상태별 모양과 동작 규칙은 스토리북 `Shared/CountTextField`에서 확인해요.
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1193 온보딩/닉네임 입력}
 */
export default function CountTextField({
  value,
  maxLength,
  className,
  ...props
}: CountTextFieldProps) {
  return (
    <Stack className={className} gap={0.5}>
      <Count>
        {value.length}/{maxLength}
      </Count>
      <TextField value={value} maxLength={maxLength} {...props} />
    </Stack>
  );
}

const Count = styled.p`
  ${({ theme }) => theme.text.caption01};
  color: ${({ theme }) => theme.neutral[600]};
  text-align: right;
`;
