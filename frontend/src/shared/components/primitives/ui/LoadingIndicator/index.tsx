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
 *
 * 스피너 자체는 장식이라 낭독기가 무시하므로, 여기서 `role="status"`와 이름을 붙여
 * 기다리는 중이라는 사실을 소리로도 전합니다.
 *
 * 부모가 준 자리의 가운데에 놓이고, 그 자리의 크기는 부모가 정해요.
 * 색은 `currentColor`를 따라가므로 부모의 `color`만 맞으면 됩니다.
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
