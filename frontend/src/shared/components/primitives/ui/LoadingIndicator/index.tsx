import styled from "@emotion/styled";
import Spinner from "@primitives/ui/Spinner";
import type { HTMLAttributes } from "react";

interface LoadingIndicatorProps extends HTMLAttributes<HTMLDivElement> {
  /** 낭독기가 읽어 줄 문구. 화면에는 보이지 않아요. */
  label?: string;
  /** 스피너 지름 */
  size?: string;
}

/**
 * 무언가를 기다리는 중임을 알리는 표시.
 */
export default function LoadingIndicator({
  label = "불러오는 중",
  size,
  ...props
}: LoadingIndicatorProps) {
  return (
    <Root role="status" aria-label={label} {...props}>
      <Spinner size={size} />
    </Root>
  );
}

const Root = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
`;
