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
 * 입력창 위에 `3/20`을 오른쪽 정렬로 보여줘요.
 * 에러 메시지는 `TextField`가 입력창 아래에 그리므로, 카운터는 위·메시지는 아래가 됩니다.
 *
 * `maxLength`를 입력창에도 그대로 넘기기 때문에 최대 글자 수를 넘겨 입력할 수 없어요.
 * 그래서 카운터가 분모를 넘는 일은 생기지 않습니다.
 *
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
