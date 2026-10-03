import { css, keyframes } from "@emotion/react";
import styled from "@emotion/styled";
import { useId, type ReactNode } from "react";
import { createPortal } from "react-dom";

import useDockablePanel from "./model/useDockablePanel";

interface DockablePanelProps {
  /** 트리거 버튼의 접근성 이름 */
  label: string;
  /** 트리거 버튼 안에 그릴 아이콘 */
  icon: ReactNode;
  /** 고정했을 때 패널이 옮겨 갈 자리의 DOM id */
  dockTargetId: string;
  /**
   * 고정 여부를 바깥에서 정할 때 넘겨요.
   *
   * 넘기지 않으면 패널이 스스로 여닫아요. 같은 자리를 여러 패널이 나눠 써서
   * 하나만 고정돼야 할 때만 `onDockedChange`와 함께 넘기면 돼요.
   */
  isDocked?: boolean;
  /** 트리거를 눌러 고정 여부가 바뀌었을 때 알려줘요 */
  onDockedChange?: (isDocked: boolean) => void;
  /** 패널에 그릴 내용. 패널의 껍데기(너비·배경·라운드)는 이 내용이 스스로 가져요 */
  children: ReactNode;
}

/**
 * 스치면 띄우고 누르면 자리를 차지하는 패널.
 *
 * 동작 규칙은 스토리북 `Shared/DockablePanel`에서 확인해요.
 */
export default function DockablePanel({
  label,
  icon,
  dockTargetId,
  isDocked: dockedProp,
  onDockedChange,
  children,
}: DockablePanelProps) {
  const panelId = useId();
  const { isDocked, isPeeking, dockTarget, rootProps, triggerProps } =
    useDockablePanel({ dockTargetId, dockedProp, onDockedChange });

  return (
    <Root {...rootProps}>
      <Trigger
        type="button"
        aria-label={label}
        aria-controls={panelId}
        aria-expanded={isDocked || isPeeking}
        aria-pressed={isDocked}
        $isDocked={isDocked}
        {...triggerProps}
      >
        {icon}
      </Trigger>

      {isPeeking && <FloatingPanel id={panelId}>{children}</FloatingPanel>}

      {isDocked &&
        dockTarget !== null &&
        createPortal(
          <DockedPanel id={panelId}>{children}</DockedPanel>,
          dockTarget,
        )}
    </Root>
  );
}

const Root = styled.div`
  display: flex;
`;

const Trigger = styled.button<{ $isDocked: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5625rem; /* 9px */
  border: 1px solid
    ${({ theme, $isDocked }) =>
      $isDocked ? theme.neutral[700] : theme.neutral[200]};
  border-radius: 62.4375rem; /* 999px */
  background-color: ${({ theme, $isDocked }) =>
    $isDocked ? theme.neutral[700] : theme.neutral[0]};
  color: ${({ theme, $isDocked }) =>
    $isDocked ? theme.neutral[0] : theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow02};
  transition:
    background-color 0.2s ease-in,
    color 0.2s ease-in;

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const slideInFromLeft = keyframes`
  from {
    transform: translateX(-1.5rem); /* 24px */
    opacity: 0;
  }

  to {
    transform: translateX(0);
    opacity: 1;
  }
`;

/** 길이와 감속을 레일이 벌어지는 속도와 맞춰야 함께 밀리는 것처럼 보여요 */
const panelEnterStyle = css`
  animation: ${slideInFromLeft} 0.28s cubic-bezier(0.22, 1, 0.36, 1);

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
`;

const FloatingPanel = styled.div`
  position: fixed;
  top: 5.5rem; /* 88px */
  bottom: 8.5rem; /* 136px */
  left: 2.5rem; /* 40px */
  z-index: 20;
  ${panelEnterStyle}
`;

const DockedPanel = styled.div`
  width: 100%;
  height: 100%;
  ${panelEnterStyle}
`;
