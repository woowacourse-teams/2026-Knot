import styled from "@emotion/styled";
import type { HTMLAttributes, ReactNode } from "react";

interface ChipProps extends HTMLAttributes<HTMLSpanElement> {
  /** 칩에 보여줄 짧은 글자. 값을 문구로 바꾸는 일은 쓰는 쪽에서 끝내고 넘겨요. */
  children: ReactNode;
}

/**
 * 짧은 정보를 담는 작은 칩.
 *
 * 받은 글자를 그대로 보여줘요. 녹음 길이처럼 값을 바꿔야 하면
 * `<Chip>{formatDurationFromSeconds(durationSeconds)}</Chip>`처럼 바꾼 결과를 넘깁니다.
 * 다른 요소 옆에 붙여 쓰므로 간격은 쓰는 쪽이 정해요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2062-7288 Chip/Duration}
 */
export default function Chip({ children, ...props }: ChipProps) {
  return <Root {...props}>{children}</Root>;
}

const Root = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 0.0625rem 0.375rem; /* 1px 6px */
  border-radius: 0.25rem; /* 4px */
  background-color: ${({ theme }) => theme.neutral[100]};
  color: ${({ theme }) => theme.neutral[600]};
  white-space: nowrap;

  ${({ theme }) => theme.text.caption01};
`;
