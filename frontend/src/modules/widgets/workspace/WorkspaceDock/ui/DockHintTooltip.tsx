import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { DOCK_HINT_TEXT } from "../constants/dockHint";

/**
 * 독 위에 떠서 채팅을 시작하는 방법을 알려주는 말풍선.
 *
 * 스토리북 `Workspace/WorkspaceDock`
 */
export default function DockHintTooltip() {
  return <Bubble>{DOCK_HINT_TEXT}</Bubble>;
}

const riseIn = keyframes`
  from {
    transform: translate(-50%, 0.5rem);
    opacity: 0;
  }

  to {
    transform: translate(-50%, 0);
    opacity: 1;
  }
`;

/** 꼬리(`::after`)는 밑변 16px·높이 10px 삼각형이라 말풍선 아래 가운데에서 독을 가리켜요 */
const Bubble = styled.p`
  position: absolute;
  bottom: calc(100% + 1.25rem); /* 꼬리 10px + 독과의 사이 10px */
  left: 50%;
  transform: translateX(-50%);
  padding: 0.75rem 1rem; /* 12px 16px */
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
  color: ${({ theme }) => theme.neutral[800]};
  white-space: nowrap;
  pointer-events: none;
  animation: ${riseIn} 0.28s cubic-bezier(0.22, 1, 0.36, 1);
  ${({ theme }) => theme.text.caption02};

  &::after {
    content: "";
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translateX(-50%);
    border-right: 0.5rem solid transparent; /* 8px */
    border-left: 0.5rem solid transparent;
    border-top: 0.625rem solid ${({ theme }) => theme.neutral[0]}; /* 10px */
  }

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
`;
