import styled from "@emotion/styled";
import { keyframes } from "@emotion/react";
import type { HTMLAttributes } from "react";

import spinnerMask from "@/assets/spinnerMask.svg?url";

interface SpinnerProps extends HTMLAttributes<HTMLSpanElement> {
  /** 지름. 부모 글자 크기를 따라가게 하려면 `1em`을 넘기면 돼요. */
  size?: string;
}

/**
 * 회전하는 로딩 표시.
 *
 * 동작 규칙은 스토리북 `Shared/Spinner`에서 확인해요.
 */
export default function Spinner({ size = "1.5rem", ...props }: SpinnerProps) {
  return <Root $size={size} aria-hidden {...props} />;
}

const spin = keyframes`
    to {
        transform: rotate(1turn);
    }
`;

const Root = styled.span<{ $size: string }>`
  display: block;
  flex-shrink: 0;
  width: ${({ $size }) => $size};
  height: ${({ $size }) => $size};

  background: conic-gradient(from 90deg, currentColor, transparent);

  -webkit-mask: url("${spinnerMask}") center / contain no-repeat;
  mask: url("${spinnerMask}") center / contain no-repeat;

  animation: ${spin} 0.6s linear infinite;
`;
