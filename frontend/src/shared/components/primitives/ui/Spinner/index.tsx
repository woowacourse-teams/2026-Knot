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
 * 색은 `currentColor`를 따라가므로 부모의 `color`만 맞으면 됩니다.
 * 흰 버튼 위에서는 흰색, 밝은 버튼 위에서는 어두운 색으로 알아서 그려져요.
 *
 * 위치는 잡지 않습니다. 어디에 놓을지는 쓰는 쪽이 정해요.
 *
 * 장식이라 `aria-hidden`을 붙였습니다.
 * 로딩 중이라는 사실은 부모가 `aria-busy`로 알립니다.
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-263 loading spinner}
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
