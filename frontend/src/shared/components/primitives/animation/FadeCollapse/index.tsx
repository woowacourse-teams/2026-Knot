import { css, keyframes } from "@emotion/react";
import styled from "@emotion/styled";
import type { ReactNode } from "react";

interface FadeCollapseProps {
  /** `true`가 되면 흐려지며 차지하던 높이를 접어요 */
  isLeaving: boolean;
  children: ReactNode;
}

const ENTER_MS = 260;

/**
 * 접히는 애니메이션이 끝나는 시간. 다 접힌 뒤 요소를 빼는 타이머에 써요.
 * animationend는 jsdom에서 일어나지 않아 시간으로 맞춰요.
 */
export const FADE_COLLAPSE_LEAVE_MS = 250;

// 여럿이 잇달아 펼쳐질 때 형제 요소가 밀렸다 멈췄다 하지 않도록, 속도 0에서 출발하는 곡선을 써요
const ENTER_EASING = "cubic-bezier(0.37, 0, 0.63, 1)";
const LEAVE_EASING = "cubic-bezier(0.4, 0, 1, 1)";

/**
 * 자식이 나타날 때 차지할 높이를 펼치며 떠오르고, 사라질 때 흐려지며 높이를 접는 애니메이션.
 *
 * 높이가 함께 움직여서 위아래 형제 요소가 끊기지 않고 밀리거나 내려와요.
 * 높이를 재지 않으므로 자식의 높이가 정해져 있지 않아도 돼요.
 */
export default function FadeCollapse({ isLeaving, children }: FadeCollapseProps) {
  return (
    <Root $isLeaving={isLeaving}>
      <Content $isLeaving={isLeaving}>{children}</Content>
    </Root>
  );
}

const expand = keyframes`
  from {
    grid-template-rows: 0fr;
  }

  to {
    grid-template-rows: 1fr;
  }
`;

const collapse = keyframes`
  from {
    grid-template-rows: 1fr;
  }

  to {
    grid-template-rows: 0fr;
  }
`;

const rise = keyframes`
  from {
    opacity: 0;
    transform: translateY(0.375rem) scale(0.98); /* 6px */
  }

  to {
    opacity: 1;
    transform: none;
  }
`;

const fade = keyframes`
  from {
    opacity: 1;
    transform: none;
  }

  to {
    opacity: 0;
    transform: scale(0.98);
  }
`;

// 높이와 겉모양이 같은 시간·곡선으로 움직여야 한 덩어리로 보여요
const Root = styled.div<{ $isLeaving: boolean }>`
  display: grid;
  grid-template-rows: 1fr;
  ${({ $isLeaving }) =>
    $isLeaving
      ? css`
          animation: ${collapse} ${FADE_COLLAPSE_LEAVE_MS}ms ${LEAVE_EASING}
            forwards;
        `
      : css`
          animation: ${expand} ${ENTER_MS}ms ${ENTER_EASING};
        `}
`;

const Content = styled.div<{ $isLeaving: boolean }>`
  min-height: 0;
  overflow: hidden;
  ${({ $isLeaving }) =>
    $isLeaving
      ? css`
          animation: ${fade} ${FADE_COLLAPSE_LEAVE_MS}ms ${LEAVE_EASING}
            forwards;
        `
      : css`
          animation: ${rise} ${ENTER_MS}ms ${ENTER_EASING};
        `}
`;
